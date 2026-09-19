package com.enjirad.qrqueue.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.enjirad.qrqueue.data.QrImageFiles
import com.enjirad.qrqueue.data.QueueRepository
import com.enjirad.qrqueue.data.ShareTargets
import com.enjirad.qrqueue.data.StoredImage
import com.enjirad.qrqueue.domain.ItemOutcome
import com.enjirad.qrqueue.domain.PaymentQueue
import com.enjirad.qrqueue.domain.PaymentStatus
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
    SHARE_TARGET_UNAVAILABLE,
    VIEW_TARGET_UNAVAILABLE,
    QUEUE_NOT_SAVED,
    QUEUE_CLEARED,
}

/** Where the user is in the workflow. */
enum class QueueStage { HOME, REVIEW, PROCESSING, SUMMARY }

/** Progress of the multi-image import, in images. */
data class ImportProgress(val processed: Int, val total: Int)

/** Which external app the queue is about to hand the stored image to. */
enum class ImageIntentKind { SHARE, VIEW }

/**
 * A pending one-shot request to hand one stored QR image to another app.
 * It is transient UI state: it is never persisted with the queue.
 */
data class ImageIntentRequest(
    val kind: ImageIntentKind,
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
    /** Set when the user taps share on an item that was already handed off. */
    val reShareConfirmationVisible: Boolean = false,
    val imageIntent: ImageIntentRequest? = null,
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
 * State holder for the whole workflow: import, decode, validate, review, the
 * sequential payment run and the hand-off to a banking app.
 *
 * Rules this class never breaks (see AI_RULES.md):
 * - the queue is mutated only through [PaymentQueue] transitions, which refuse
 *   any transition that would assume a payment succeeded;
 * - every mutation is persisted before the UI sees it, and a failed save is
 *   reported instead of silently ignored;
 * - an item that was mid-hand-off when the app stopped comes back as UNKNOWN;
 * - an item that was already handed off is not shared again without an explicit
 *   confirmation, so a repeated tap cannot cause a double payment;
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
            persist(resolved)
            sweepOrphanImages(resolved)
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

            val currentQueue = _uiState.value.queue
            if (currentQueue == null && existingQueue != null) {
                // The queue was cleared while these images were being imported.
                // Do not resurrect a deleted queue: drop the result and the image
                // copies that were just written.
                val copiedPaths = importedItems.mapNotNull { item -> item.storedImagePath }
                withContext(Dispatchers.IO) { repository.deleteImages(copiedPaths) }
                _uiState.update { it.copy(importing = false, importProgress = null) }
                return@launch
            }

            val base = currentQueue
                ?: PaymentQueue.create(
                    queueId = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis(),
                )
            val updated = base.appendItems(importedItems).normalized()
            persist(
                updated,
                notice = if (failedImports > 0) QueueNotice.IMAGES_NOT_IMPORTED else null,
            )
            sweepOrphanImages(updated)
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
        val importedAt = System.currentTimeMillis()
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
                    importedAtMillis = importedAt,
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
                importedAtMillis = importedAt,
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

    /**
     * The user wants to open or share the current QR with another app.
     *
     * Sharing is gated: nothing happens while a previous hand-off is still in
     * flight, the device must actually have an app that can receive an image, and
     * an item that was already handed off needs an explicit confirmation first.
     */
    fun onShareQrRequested() {
        if (_uiState.value.imageIntent != null) return
        val item = currentItem() ?: return
        val targets = ShareTargets.query(getApplication<Application>(), item.mimeType)
        if (!targets.canShare) {
            _uiState.update { it.copy(notice = QueueNotice.SHARE_TARGET_UNAVAILABLE) }
            return
        }
        if (item.status != PaymentStatus.READY) {
            // Already shared once. Sharing again could pay the same bill twice.
            _uiState.update { it.copy(reShareConfirmationVisible = true) }
            return
        }
        requestImageIntent(ImageIntentKind.SHARE, item, targets.shareMimeType)
    }

    /** The user explicitly confirmed sharing an already handed-off QR again. */
    fun onReShareConfirmed() {
        _uiState.update { it.copy(reShareConfirmationVisible = false) }
        val item = currentItem() ?: return
        val targets = ShareTargets.query(getApplication<Application>(), item.mimeType)
        if (!targets.canShare) {
            _uiState.update { it.copy(notice = QueueNotice.SHARE_TARGET_UNAVAILABLE) }
            return
        }
        requestImageIntent(ImageIntentKind.SHARE, item, targets.shareMimeType)
    }

    fun onReShareDismissed() {
        _uiState.update { it.copy(reShareConfirmationVisible = false) }
    }

    /** Open the stored QR image in a viewer. This is not a hand-off. */
    fun onViewQrRequested() {
        if (_uiState.value.imageIntent != null) return
        val item = currentItem() ?: return
        requestImageIntent(
            kind = ImageIntentKind.VIEW,
            item = item,
            mimeType = item.mimeType ?: ShareTargets.ANY_IMAGE_MIME_TYPE,
        )
    }

    /**
     * The screen reports whether the intent actually launched.
     *
     * A share moves the item to "waiting for confirmation" — never to a paid
     * state. Viewing the image changes nothing at all.
     */
    fun onImageIntentLaunched(kind: ImageIntentKind, launched: Boolean) {
        _uiState.update { it.copy(imageIntent = null) }
        if (!launched) {
            val notice = when (kind) {
                ImageIntentKind.SHARE -> QueueNotice.SHARE_FAILED
                ImageIntentKind.VIEW -> QueueNotice.VIEW_TARGET_UNAVAILABLE
            }
            _uiState.update { it.copy(notice = notice) }
            return
        }
        if (kind == ImageIntentKind.SHARE) {
            updateQueue { queue -> queue.handOffCurrent() }
        }
    }

    /** The user paid this QR directly in their banking app, without a hand-off. */
    fun onPaymentAlreadyCompletedInBank() {
        updateQueue { queue -> queue.handOffCurrent() }
    }

    /** Explicit "Payment successful" — the only path to a paid item. */
    fun onConfirmSuccess() {
        updateQueue { queue -> queue.confirmSuccess(System.currentTimeMillis()) }
    }

    /** Explicit "Payment failed". */
    fun onConfirmFailure() {
        updateQueue { queue -> queue.confirmFailure(System.currentTimeMillis()) }
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
        updateQueue { queue -> queue.resolveUnknown(success, System.currentTimeMillis()) }
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
            it.copy(
                clearConfirmationVisible = false,
                startConfirmationVisible = false,
                reShareConfirmationVisible = false,
                imageIntent = null,
                importing = false,
            )
        }
        scope.launch {
            withContext(Dispatchers.IO) { repository.clearQueue() }
            _uiState.update { it.copy(queue = null, importProgress = null, notice = QueueNotice.QUEUE_CLEARED) }
        }
    }

    fun onNoticeShown() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun currentItem(): QueueItem? = _uiState.value.queue?.currentItem

    private fun requestImageIntent(kind: ImageIntentKind, item: QueueItem, mimeType: String?) {
        val storedPath = item.storedImagePath ?: return
        val type = mimeType ?: ShareTargets.ANY_IMAGE_MIME_TYPE
        _uiState.update {
            it.copy(
                imageIntent = ImageIntentRequest(
                    kind = kind,
                    itemId = item.id,
                    filePath = storedPath,
                    mimeType = type,
                    fileName = item.fileName,
                ),
            )
        }
    }

    /** Persists the result of a queue transition before the UI shows it. */
    private fun updateQueue(transform: (PaymentQueue) -> PaymentQueue) {
        val queue = _uiState.value.queue ?: return
        persist(transform(queue))
    }

    /**
     * Stamps and persists the queue, then publishes it. A failed write is
     * surfaced: the user must not believe a payment result was recorded when it
     * never reached disk.
     */
    private fun persist(queue: PaymentQueue, notice: QueueNotice? = null) {
        val stamped = queue.copy(updatedAtMillis = System.currentTimeMillis())
        val saved = repository.saveQueue(stamped)
        _uiState.update { state ->
            val nextNotice = when {
                !saved -> QueueNotice.QUEUE_NOT_SAVED
                notice != null -> notice
                else -> state.notice
            }
            state.copy(queue = stamped, notice = nextNotice)
        }
    }

    /** Removes image copies that no queue item references any more. */
    private fun sweepOrphanImages(queue: PaymentQueue) {
        val referenced = queue.items.mapNotNull { item -> item.storedImagePath }.toSet()
        scope.launch {
            withContext(Dispatchers.IO) { repository.sweepOrphanImages(referenced) }
        }
    }

    private companion object {
        const val DEFAULT_FILE_NAME = "QR image"
    }
}
