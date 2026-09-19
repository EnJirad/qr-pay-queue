package com.enjirad.qrqueue.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.enjirad.qrqueue.data.KPlusTarget
import com.enjirad.qrqueue.data.QueueRepository
import com.enjirad.qrqueue.domain.ImportProgress
import com.enjirad.qrqueue.domain.ImportSummary
import com.enjirad.qrqueue.domain.PaymentQueue
import com.enjirad.qrqueue.domain.PaymentStatus
import com.enjirad.qrqueue.domain.QueueImport
import com.enjirad.qrqueue.domain.QueueItem
import java.io.File
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
    SHARE_FAILED,
    K_PLUS_UNAVAILABLE,
    HANDOFF_IN_PROGRESS,
    VIEW_TARGET_UNAVAILABLE,
    IMAGE_MISSING,
    QUEUE_NOT_SAVED,
    QUEUE_CLEARED,
}

/** Which external app the queue is about to hand the stored image to. */
enum class ImageIntentKind { SHARE, VIEW }

/**
 * A pending one-shot request to hand one stored image to another app.
 * It is transient UI state: it is never persisted with the queue, so an activity
 * recreation or a process restart can never trigger a second hand-off.
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
 *
 * @param importProgress non-null exactly while the import screen is up. It is
 *   cleared as soon as every selected image has been handled, which is what makes
 *   the app return to the queue by itself — it never waits on the "5 / 5" step.
 * @param importSummary the result of the last import run, shown until dismissed.
 * @param reShareItemId the item the user asked to hand to K PLUS again; drives
 *   the double-payment warning.
 * @param confirmationDismissedFor id of the WAITING_USER item whose question the
 *   user already answered with "ลองอีกครั้ง".
 */
data class QueueUiState(
    val queue: PaymentQueue? = null,
    val importProgress: ImportProgress? = null,
    val importSummary: ImportSummary? = null,
    val clearConfirmationVisible: Boolean = false,
    val reShareItemId: String? = null,
    val confirmationDismissedFor: String? = null,
    val imageIntent: ImageIntentRequest? = null,
    val notice: QueueNotice? = null,
) {
    /** True while the import screen must be shown instead of the queue. */
    val importing: Boolean get() = QueueImport.isImportRunning(importProgress)

    /** True when the double-payment warning dialog is up. */
    val reShareConfirmationVisible: Boolean get() = reShareItemId != null
}

/**
 * State holder for the whole workflow: import images, queue them, hand one image
 * at a time to K PLUS, and record the user's own confirmation before moving on.
 *
 * The app does not read the QR code and does not decide whether a payment
 * happened. Rules this class never breaks (see AI_RULES.md):
 *
 * - images are copied into app-private storage; the gallery original is never
 *   touched, and no item is created for a copy that failed;
 * - the queue is mutated only through [PaymentQueue] transitions, which refuse
 *   any change that would assume a payment succeeded;
 * - every mutation is persisted before the UI sees it, and a failed save is
 *   reported instead of silently ignored;
 * - a hand-off never marks anything paid; only the user's explicit "done" does;
 * - only one image may be in flight at a time, so a batch cannot make two
 *   payments at once;
 * - an item that was mid hand-off when the app stopped comes back as UNKNOWN;
 * - a hand-off is only ever started by a user action, and an item that may
 *   already have reached K PLUS is not shared again without an explicit
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
            val resolved = restored.resolveInterrupted(INTERRUPTED_DETAIL, System.currentTimeMillis())
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
     * app-private storage and added to the queue. An image that cannot be copied
     * produces no queue item at all — the app never pretends to have an image it
     * does not have — and the run is reported honestly.
     *
     * Once the last selected image has been handled and the queue is persisted,
     * the import state is cleared, so the screen returns to the queue by itself.
     */
    fun onImagesPicked(uris: List<Uri>) {
        val sourceUris = QueueImport.dedupeSourceUris(uris.map { uri -> uri.toString() })
        if (sourceUris.isEmpty()) {
            _uiState.update { it.copy(importProgress = null) }
            return
        }
        val queueBeforeImport = _uiState.value.queue
        _uiState.update {
            it.copy(importProgress = ImportProgress.of(sourceUris.size), importSummary = null)
        }
        scope.launch {
            var progress = ImportProgress.of(sourceUris.size)
            var nextPosition = queueBeforeImport?.nextPosition ?: PaymentQueue.FIRST_POSITION
            val importedItems = mutableListOf<QueueItem>()
            var failedImports = 0
            sourceUris.forEach { sourceUri ->
                val item = withContext(Dispatchers.IO) { importOne(sourceUri, nextPosition) }
                if (item == null) {
                    failedImports++
                } else {
                    importedItems += item
                    nextPosition++
                }
                progress = progress.advance()
                val snapshot = progress
                _uiState.update { it.copy(importProgress = snapshot) }
            }

            val currentQueue = _uiState.value.queue
            if (currentQueue == null && queueBeforeImport != null) {
                // The queue was cleared while these images were being imported.
                // Do not resurrect a deleted queue: drop the result and the image
                // copies that were just written.
                val copiedPaths = importedItems.map { item -> item.storedImagePath }
                withContext(Dispatchers.IO) { repository.deleteImages(copiedPaths) }
                _uiState.update { it.copy(importProgress = null) }
                return@launch
            }

            val summary = ImportSummary(imported = importedItems.size, failed = failedImports)
            if (currentQueue == null && importedItems.isEmpty()) {
                // Nothing was copied and there was no queue to begin with: stay on
                // the home screen and report the failure instead of creating an
                // empty queue.
                _uiState.update { it.copy(importProgress = null, importSummary = summary) }
                return@launch
            }
            val base = currentQueue ?: PaymentQueue.create(
                queueId = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
            )
            val updated = base.appendItems(importedItems).normalized()
            persist(updated)
            // Import is finished and saved: close the progress screen so the app
            // is back on the queue, with no Continue / Done tap.
            _uiState.update { it.copy(importProgress = null, importSummary = summary) }
            sweepOrphanImages(updated)
        }
    }

    /** The picker was dismissed without a selection. */
    fun onImageSelectionCancelled() {
        _uiState.update { it.copy(importProgress = null) }
    }

    /** The user dismissed the import result banner. */
    fun onImportSummaryShown() {
        _uiState.update { it.copy(importSummary = null) }
    }

    /** Copies one picked image into app-private storage. Runs on the IO dispatcher. */
    private fun importOne(sourceUri: String, position: Int): QueueItem? {
        val itemId = QueueImport.newItemId()
        val stored = runCatching { repository.importImage(Uri.parse(sourceUri), itemId) }.getOrNull()
            ?: return null
        return QueueImport.buildItem(
            id = itemId,
            position = position,
            sourceUri = sourceUri,
            storedImagePath = stored.file.absolutePath,
            displayName = stored.displayName,
            mimeType = stored.mimeType,
            nowMillis = System.currentTimeMillis(),
        )
    }

    // ---- hand-off -----------------------------------------------------------

    /**
     * The user wants to hand this specific image to K PLUS.
     *
     * Nothing happens while a previous hand-off is still launching. An item that
     * may already have reached K PLUS ([PaymentStatus.WAITING_USER]) needs an
     * explicit double-payment confirmation first, and an item whose result is
     * unknown must be resolved before it can be handed over again.
     */
    fun onShareItemRequested(itemId: String) {
        if (_uiState.value.imageIntent != null) return
        val queue = _uiState.value.queue ?: return
        val item = queue.item(itemId) ?: return
        when {
            item.status.needsReShareWarning ->
                _uiState.update { it.copy(reShareItemId = item.id) }

            item.status == PaymentStatus.UNKNOWN -> Unit

            !queue.canStartHandoff(itemId) ->
                _uiState.update { it.copy(notice = QueueNotice.HANDOFF_IN_PROGRESS) }

            else -> requestShare(item)
        }
    }

    /** The user explicitly confirmed handing an already shared image to K PLUS again. */
    fun onReShareConfirmed() {
        val itemId = _uiState.value.reShareItemId ?: return
        _uiState.update { it.copy(reShareItemId = null, confirmationDismissedFor = null) }
        val item = _uiState.value.queue?.item(itemId) ?: return
        if (item.status != PaymentStatus.WAITING_USER) return
        requestShare(item)
    }

    fun onReShareDismissed() {
        _uiState.update { it.copy(reShareItemId = null) }
    }

    /** Open a stored image in a viewer. This is not a hand-off and changes nothing. */
    fun onViewItemRequested(itemId: String) {
        if (_uiState.value.imageIntent != null) return
        val item = _uiState.value.queue?.item(itemId) ?: return
        if (!File(item.storedImagePath).isFile) {
            failItem(item.id, MISSING_IMAGE_DETAIL, QueueNotice.IMAGE_MISSING)
            return
        }
        _uiState.update {
            it.copy(
                imageIntent = ImageIntentRequest(
                    kind = ImageIntentKind.VIEW,
                    itemId = item.id,
                    filePath = item.storedImagePath,
                    mimeType = item.mimeType,
                    fileName = item.displayName,
                ),
            )
        }
    }

    private fun requestShare(item: QueueItem) {
        if (!File(item.storedImagePath).isFile) {
            failItem(item.id, MISSING_IMAGE_DETAIL, QueueNotice.IMAGE_MISSING)
            return
        }
        if (!KPlusTarget.query(getApplication<Application>(), item.mimeType).canHandOff) {
            _uiState.update { it.copy(notice = QueueNotice.K_PLUS_UNAVAILABLE) }
            return
        }
        // Mark the item as being handed off before the intent is launched, so a
        // process death during the hand-off is restored as UNKNOWN rather than
        // QUEUED, and so nothing can share the same image in the same breath.
        updateQueue { queue -> queue.startSharing(item.id, System.currentTimeMillis()) }
        _uiState.update {
            it.copy(
                confirmationDismissedFor = null,
                imageIntent = ImageIntentRequest(
                    kind = ImageIntentKind.SHARE,
                    itemId = item.id,
                    filePath = item.storedImagePath,
                    mimeType = item.mimeType,
                    fileName = item.displayName,
                ),
            )
        }
    }

    /**
     * The screen reports whether the intent actually launched.
     *
     * A launched hand-off moves the item to "waiting for the user" — never to a
     * completed state. A hand-off that could not be started is recorded as a known
     * failure, because nothing reached K PLUS. This is also the only place the
     * pending hand-off is cleared, so a recreated composition cannot launch it
     * twice.
     */
    fun onImageIntentLaunched(kind: ImageIntentKind, launched: Boolean) {
        val request = _uiState.value.imageIntent
        _uiState.update { it.copy(imageIntent = null) }
        if (kind == ImageIntentKind.VIEW) {
            if (!launched) {
                _uiState.update { it.copy(notice = QueueNotice.VIEW_TARGET_UNAVAILABLE) }
            }
            return
        }
        val itemId = request?.itemId ?: return
        if (launched) {
            updateQueue { queue -> queue.shareLaunched(itemId, System.currentTimeMillis()) }
        } else {
            failItem(itemId, SHARE_FAILED_DETAIL, QueueNotice.SHARE_FAILED)
        }
    }

    // ---- user confirmation --------------------------------------------------

    /**
     * Explicit "this payment is done". The only path to a completed item, and it
     * also covers the user resolving an UNKNOWN result after checking K PLUS.
     */
    fun onConfirmCompleted(itemId: String) {
        val queue = _uiState.value.queue ?: return
        val item = queue.item(itemId) ?: return
        val nowMillis = System.currentTimeMillis()
        val updated = when (item.status) {
            PaymentStatus.WAITING_USER -> queue.confirmCompleted(itemId, nowMillis)
            PaymentStatus.UNKNOWN -> queue.resolveUnknownCompleted(itemId, nowMillis)
            else -> return
        }
        _uiState.update { it.copy(confirmationDismissedFor = null) }
        persist(updated)
    }

    /**
     * "ลองอีกครั้ง": the payment is not done yet. The item stays
     * [PaymentStatus.WAITING_USER] so it can be handed to K PLUS again on purpose,
     * and the queue does not move on. Nothing is shared by this call.
     */
    fun onKeepWaiting(itemId: String) {
        val queue = _uiState.value.queue ?: return
        val item = queue.item(itemId) ?: return
        if (item.status != PaymentStatus.WAITING_USER) return
        _uiState.update { it.copy(confirmationDismissedFor = itemId) }
        persist(queue.keepWaiting(itemId, System.currentTimeMillis()))
    }

    /**
     * Explicit retry of a FAILED or UNKNOWN item. The item goes back to QUEUED and
     * nothing is handed to K PLUS until the user asks for it.
     */
    fun onRetryItem(itemId: String) {
        val queue = _uiState.value.queue ?: return
        if (_uiState.value.confirmationDismissedFor == itemId) {
            _uiState.update { it.copy(confirmationDismissedFor = null) }
        }
        persist(queue.retryItem(itemId, System.currentTimeMillis()))
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
                reShareItemId = null,
                confirmationDismissedFor = null,
                imageIntent = null,
                importProgress = null,
                importSummary = null,
            )
        }
        scope.launch {
            withContext(Dispatchers.IO) { repository.clearQueue() }
            _uiState.update { it.copy(queue = null, notice = QueueNotice.QUEUE_CLEARED) }
        }
    }

    fun onNoticeShown() {
        _uiState.update { it.copy(notice = null) }
    }

    // ---- internals ----------------------------------------------------------

    /** Records a known error on one item and tells the user why. */
    private fun failItem(itemId: String, detail: String, notice: QueueNotice) {
        updateQueue { queue -> queue.failItem(itemId, detail, System.currentTimeMillis()) }
        _uiState.update { it.copy(notice = notice) }
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
        val referenced = queue.items.map { item -> item.storedImagePath }.toSet()
        scope.launch {
            withContext(Dispatchers.IO) { repository.sweepOrphanImages(referenced) }
        }
    }

    private companion object {
        const val MISSING_IMAGE_DETAIL = "The imported image file is missing from this device."
        const val SHARE_FAILED_DETAIL =
            "K PLUS did not accept the shared image (no activity matched the hand-off intent)."
        const val INTERRUPTED_DETAIL = "The app stopped while this image was being handed off."
    }
}
