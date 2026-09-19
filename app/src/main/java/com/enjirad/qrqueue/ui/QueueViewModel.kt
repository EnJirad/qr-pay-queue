package com.enjirad.qrqueue.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.enjirad.qrqueue.data.QrImageFiles
import com.enjirad.qrqueue.data.QueueRepository
import com.enjirad.qrqueue.data.StoredImage
import com.enjirad.qrqueue.domain.ItemOutcome
import com.enjirad.qrqueue.domain.PaymentQueue
import com.enjirad.qrqueue.domain.QrValidation
import com.enjirad.qrqueue.domain.QueueImport
import com.enjirad.qrqueue.domain.QueueItem
import com.enjirad.qrqueue.domain.ValidationIssue
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-off messages the screen should surface to the user. */
enum class QueueNotice {
    IMAGES_NOT_IMPORTED,
    QUEUE_ALREADY_RUNNING,
    NOTHING_TO_PAY,
    SHARE_FAILED,
    QUEUE_CLEARED,
}

/** Where the user is in the workflow. */
enum class QueueStage { HOME, REVIEW, PROCESSING, SUMMARY }

/** Progress of the multi-image import, in images. */
data class ImportProgress(val processed: Int, val total: Int)

/** A pending request to hand one stored QR image to another app. */
data class ShareRequest(
    val itemId: String,
    val filePath: String,
    val mimeType: String,
    val fileName: String,
)

/**
 * Everything the queue screen renders, derived from the persisted queue.
 */
data class QueueUiState(
    val queue: PaymentQueue? = null,
    val importing: Boolean = false,
    val importProgress: ImportProgress? = null,
    val startConfirmationVisible: Boolean = false,
    val clearConfirmationVisible: Boolean = false,
    val shareRequest: ShareRequest? = null,
    val notice: QueueNotice? = null,
) {
    val stage: QueueStage
        get() = when {
            queue == null -> QueueStage.HOME
            !queue.started -> QueueStage.REVIEW
            queue.finished -> QueueStage.SUMMARY
            else -> QueueStage.PROCESSING
        }
}

/**
 * State holder for the whole V2 workflow: import, decode, validate, review and
 * the sequential payment run.
 *
 * Rules this class never breaks (see AI_RULES.md):
 * - the queue is mutated only through [PaymentQueue] transitions, which refuse
 *   any transition that would assume a payment succeeded;
 * - every mutation is persisted before the UI sees it;
 * - an item that was mid-hand-off when the app stopped comes back as UNKNOWN;
 * - no banking credential is ever requested, stored or entered.
 */
class QueueViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = QueueRepository(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    init {
        val restored = repository.loadQueue()
        if (restored != null) {
            // A payment that was in flight when the app stopped has an unknown
            // result: it is never assumed successful, and never retried.
            val resolved = restored.resolveInterrupted()
            repository.saveQueue(resolved)
            _uiState.update { it.copy(queue = resolved) }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    // ---- import -------------------------------------------------------------

    /**
     * Called with the images the user picked. Each image is copied into
     * app-private storage, decoded, validated and added to the queue — invalid
     * and duplicate images are added too, so the review screen can show exactly
     * what could not be used.
     */
    fun onImagesPicked(uris: List<Uri>) {
        val existingQueue = _uiState.value.queue
        if (existingQueue != null && existingQueue.started) {
            _uiState.update { it.copy(notice = QueueNotice.QUEUE_ALREADY_RUNNING) }
            return
        }
        val sourceUris = QueueImport.dedupeSourceUris(uris.map { uri -> uri.toString() })
        if (sourceUris.isEmpty()) {
            _uiState.update { it.copy(importing = false, importProgress = null) }
            return
        }
        _uiState.update {
            it.copy(
                importing = true,
                importProgress = ImportProgress(0, sourceUris.size),
                startConfirmationVisible = false,
            )
        }
        scope.launch {
            val acceptedKeys = QrValidation.acceptedPayloadKeys(existingQueue?.items.orEmpty()).toMutableSet()
            val importedItems = mutableListOf<QueueItem>()
            var failedImports = 0
            sourceUris.forEachIndexed { index, sourceUri ->
                val imported = withContext(Dispatchers.IO) { importOne(sourceUri, acceptedKeys) }
                if (imported.storedCopy == null) failedImports++
                importedItems += imported.item
                imported.item.rawPayload?.let { payload -> acceptedKeys += QrValidation.payloadKey(payload) }
                _uiState.update {
                    it.copy(importProgress = ImportProgress(index + 1, sourceUris.size))
                }
            }
            val base = existingQueue
                ?: PaymentQueue.create(
                    queueId = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis(),
                )
            val updated = base.appendItems(importedItems).normalized()
            repository.saveQueue(updated)
            _uiState.update {
                it.copy(
                    queue = updated,
                    importing = false,
                    importProgress = null,
                    notice = if (failedImports > 0) QueueNotice.IMAGES_NOT_IMPORTED else null,
                )
            }
        }
    }

    /** The picker was dismissed without a selection. */
    fun onImageSelectionCancelled() {
        _uiState.update { it.copy(importing = false, importProgress = null) }
    }

    private class ImportedItem(val item: QueueItem, val storedCopy: StoredImage?)

    /** Copies, decodes and validates one picked image. Runs on the IO dispatcher. */
    private fun importOne(sourceUri: String, acceptedKeys: Set<String>): ImportedItem {
        val itemId = QueueImport.newItemId()
        val stored = runCatching { repository.importImage(Uri.parse(sourceUri), itemId) }.getOrNull()
        if (stored == null) {
            return ImportedItem(
                item = QueueImport.buildItem(
                    id = itemId,
                    fileName = sourceUri.substringAfterLast('/').ifBlank { DEFAULT_FILE_NAME },
                    sourceUri = sourceUri,
                    storedImagePath = null,
                    mimeType = null,
                    outcome = ItemOutcome.Rejected(
                        ValidationIssue.UNREADABLE_IMAGE,
                        "the selected image could not be copied into the app",
                    ),
                ),
                storedCopy = null,
            )
        }
        val decodeResult = QrImageFiles.decodeQr(stored.file)
        val outcome = QrValidation.evaluate(decodeResult, acceptedKeys)
        return ImportedItem(
            item = QueueImport.buildItem(
                id = itemId,
                fileName = stored.displayName,
                sourceUri = sourceUri,
                storedImagePath = stored.file.absolutePath,
                mimeType = stored.mimeType,
                outcome = outcome,
            ),
            storedCopy = stored,
        )
    }

    // ---- review and start ---------------------------------------------------

    fun onStartRequested() {
        _uiState.update { it.copy(startConfirmationVisible = true) }
    }

    fun onStartConfirmationDismissed() {
        _uiState.update { it.copy(startConfirmationVisible = false) }
    }

    /** Starts the sequential run from the real confirmation summary. */
    fun onStartConfirmed() {
        val queue = _uiState.value.queue ?: return
        val started = queue.start()
        if (!started.started) {
            _uiState.update {
                it.copy(startConfirmationVisible = false, notice = QueueNotice.NOTHING_TO_PAY)
            }
            return
        }
        _uiState.update { it.copy(startConfirmationVisible = false) }
        persist(started)
    }

    // ---- hand-off and confirmation -----------------------------------------

    /** The user wants to open or share the current QR with another app. */
    fun onShareQrRequested() {
        val item = _uiState.value.queue?.currentItem ?: return
        val storedPath = item.storedImagePath ?: return
        _uiState.update {
            it.copy(
                shareRequest = ShareRequest(
                    itemId = item.id,
                    filePath = storedPath,
                    mimeType = item.mimeType ?: "image/*",
                    fileName = item.fileName,
                ),
            )
        }
    }

    /**
     * The screen reports whether the hand-off intent actually launched. Sharing
     * a QR is only a hand-off: it moves the item to "waiting for confirmation"
     * and never to a paid state.
     */
    fun onShareLaunched(launched: Boolean) {
        val request = _uiState.value.shareRequest
        _uiState.update { it.copy(shareRequest = null) }
        if (!launched) {
            _uiState.update { it.copy(notice = QueueNotice.SHARE_FAILED) }
            return
        }
        if (request != null) {
            updateQueue { queue -> queue.handOffCurrent() }
        }
    }

    /** The user paid this QR directly in their banking app, without a hand-off. */
    fun onPaymentAlreadyCompletedInBank() {
        updateQueue { queue -> queue.handOffCurrent() }
    }

    /** Explicit "Payment successful" — the only path to a paid item. */
    fun onConfirmSuccess() {
        updateQueue { queue -> queue.confirmSuccess() }
    }

    /** Explicit "Payment failed". */
    fun onConfirmFailure() {
        updateQueue { queue -> queue.confirmFailure() }
    }

    /**
     * "Something went wrong": the result is unknown, the queue waits for the
     * user, and nothing is retried automatically.
     */
    fun onReportUnknown() {
        updateQueue { queue -> queue.reportUnknown() }
    }

    /** Resolves an UNKNOWN result with what actually happened in the bank app. */
    fun onResolveUnknown(success: Boolean) {
        updateQueue { queue -> queue.resolveUnknown(success) }
    }

    // ---- housekeeping -------------------------------------------------------

    fun onClearQueueRequested() {
        _uiState.update { it.copy(clearConfirmationVisible = true) }
    }

    fun onClearQueueDismissed() {
        _uiState.update { it.copy(clearConfirmationVisible = false) }
    }

    fun onClearQueueConfirmed() {
        _uiState.update {
            it.copy(clearConfirmationVisible = false, startConfirmationVisible = false, importing = false)
        }
        scope.launch {
            withContext(Dispatchers.IO) { repository.clearQueue() }
            _uiState.update { it.copy(queue = null, importProgress = null, notice = QueueNotice.QUEUE_CLEARED) }
        }
    }

    fun onNoticeShown() {
        _uiState.update { it.copy(notice = null) }
    }

    /** Persists the result of a queue transition before the UI shows it. */
    private fun updateQueue(transform: (PaymentQueue) -> PaymentQueue) {
        val queue = _uiState.value.queue ?: return
        persist(transform(queue))
    }

    private fun persist(queue: PaymentQueue) {
        repository.saveQueue(queue)
        _uiState.update { it.copy(queue = queue) }
    }

    private companion object {
        const val DEFAULT_FILE_NAME = "QR image"
    }
}
