package com.enjirad.qrqueue.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.enjirad.qrqueue.data.QueueRepository
import com.enjirad.qrqueue.data.ShareTargets
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
    IMAGES_NOT_IMPORTED,
    SHARE_FAILED,
    SHARE_TARGET_UNAVAILABLE,
    VIEW_TARGET_UNAVAILABLE,
    IMAGE_MISSING,
    QUEUE_NOT_SAVED,
    QUEUE_CLEARED,
}

/** Where the user is in the workflow. */
enum class QueueStage { HOME, QUEUE, FINISHED }

/** Progress of the multi-image import, in images. */
data class ImportProgress(val processed: Int, val total: Int)

/** Which external app the queue is about to hand the stored image to. */
enum class ImageIntentKind { SHARE, VIEW }

/**
 * A pending one-shot request to hand one stored image to another app.
 * It is transient UI state: it is never persisted with the queue, so an activity
 * recreation or a process restart can never trigger a second share.
 */
data class ImageIntentRequest(
    val kind: ImageIntentKind,
    val itemId: String,
    val filePath: String,
    val mimeType: String,
    val fileName: String,
)

/** Everything the queue screen renders, derived from the persisted queue. */
data class QueueUiState(
    val queue: PaymentQueue? = null,
    val importing: Boolean = false,
    val importProgress: ImportProgress? = null,
    val clearConfirmationVisible: Boolean = false,
    /** Set when the user taps share on an item that was already handed off. */
    val reShareConfirmationVisible: Boolean = false,
    /** Id of the WAITING_USER item whose confirmation the user already answered "not yet". */
    val confirmationDismissedFor: String? = null,
    val imageIntent: ImageIntentRequest? = null,
    val notice: QueueNotice? = null,
) {
    val stage: QueueStage
        get() = when {
            queue == null -> QueueStage.HOME
            queue.finished -> QueueStage.FINISHED
            else -> QueueStage.QUEUE
        }
}

/**
 * State holder for the whole workflow: import images, queue them, hand the
 * current image to K PLUS through Android's share sheet, and record the user's
 * own confirmation.
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
 * - a share never marks anything paid; only the user's explicit "done" does;
 * - an item that was mid hand-off when the app stopped comes back as UNKNOWN;
 * - sharing is only ever started by a user action, and an item that may already
 *   have been handed to K PLUS is not shared again without an explicit
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
     * does not have — and the failure is reported instead.
     */
    fun onImagesPicked(uris: List<Uri>) {
        val sourceUris = QueueImport.dedupeSourceUris(uris.map { uri -> uri.toString() })
        if (sourceUris.isEmpty()) {
            _uiState.update { it.copy(importing = false, importProgress = null) }
            return
        }
        _uiState.update {
            it.copy(
                importing = true,
                importProgress = ImportProgress(0, sourceUris.size),
            )
        }
        scope.launch {
            val existingQueue = _uiState.value.queue
            var nextPosition = existingQueue?.nextPosition ?: 0
            val importedItems = mutableListOf<QueueItem>()
            var failedImports = 0
            sourceUris.forEachIndexed { index, sourceUri ->
                val item = withContext(Dispatchers.IO) { importOne(sourceUri, nextPosition) }
                if (item == null) {
                    failedImports++
                } else {
                    importedItems += item
                    nextPosition++
                }
                _uiState.update {
                    it.copy(importProgress = ImportProgress(index + 1, sourceUris.size))
                }
            }

            val currentQueue = _uiState.value.queue
            if (currentQueue == null && existingQueue != null) {
                // The queue was cleared while these images were being imported.
                // Do not resurrect a deleted queue: drop the result and the image
                // copies that were just written.
                val copiedPaths = importedItems.map { item -> item.storedImagePath }
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
     * The user wants to hand the current image to another app.
     *
     * Sharing is gated: nothing happens while a previous hand-off is still in
     * flight, the device must actually have an app that can receive an image, and
     * an item that may already have reached K PLUS ([PaymentStatus.WAITING_USER])
     * needs an explicit confirmation first. An [PaymentStatus.UNKNOWN] item must
     * be resolved by the user before it can be shared again.
     */
    fun onShareCurrentRequested() {
        if (_uiState.value.imageIntent != null) return
        val item = _uiState.value.queue?.currentItem ?: return
        when (item.status) {
            PaymentStatus.QUEUED, PaymentStatus.FAILED -> requestShare(item)
            PaymentStatus.WAITING_USER ->
                _uiState.update { it.copy(reShareConfirmationVisible = true) }
            PaymentStatus.SHARING, PaymentStatus.UNKNOWN, PaymentStatus.COMPLETED -> Unit
        }
    }

    /** The user explicitly confirmed sharing an already handed-off image again. */
    fun onReShareConfirmed() {
        _uiState.update {
            it.copy(reShareConfirmationVisible = false, confirmationDismissedFor = null)
        }
        val item = _uiState.value.queue?.currentItem ?: return
        if (item.status != PaymentStatus.WAITING_USER) return
        requestShare(item)
    }

    fun onReShareDismissed() {
        _uiState.update { it.copy(reShareConfirmationVisible = false) }
    }

    /** Open the stored image in a viewer. This is not a hand-off and changes nothing. */
    fun onViewCurrentRequested() {
        if (_uiState.value.imageIntent != null) return
        val item = _uiState.value.queue?.currentItem ?: return
        if (!File(item.storedImagePath).isFile) {
            failCurrent(MISSING_IMAGE_DETAIL, QueueNotice.IMAGE_MISSING)
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
            failCurrent(MISSING_IMAGE_DETAIL, QueueNotice.IMAGE_MISSING)
            return
        }
        val targets = ShareTargets.query(getApplication<Application>(), item.mimeType)
        if (!targets.canShare) {
            _uiState.update { it.copy(notice = QueueNotice.SHARE_TARGET_UNAVAILABLE) }
            return
        }
        // Mark the item as sharing before the intent is launched, so a process
        // death during the hand-off is restored as UNKNOWN rather than QUEUED.
        updateQueue { queue -> queue.startSharing(System.currentTimeMillis()) }
        _uiState.update {
            it.copy(
                confirmationDismissedFor = null,
                imageIntent = ImageIntentRequest(
                    kind = ImageIntentKind.SHARE,
                    itemId = item.id,
                    filePath = item.storedImagePath,
                    mimeType = targets.shareMimeType ?: item.mimeType,
                    fileName = item.displayName,
                ),
            )
        }
    }

    /**
     * The screen reports whether the intent actually launched.
     *
     * A launched share moves the item to "waiting for the user" — never to a
     * completed state. A share that could not start is recorded as a known
     * failure, because nothing was handed off.
     */
    fun onImageIntentLaunched(kind: ImageIntentKind, launched: Boolean) {
        _uiState.update { it.copy(imageIntent = null) }
        if (kind == ImageIntentKind.VIEW) {
            if (!launched) {
                _uiState.update { it.copy(notice = QueueNotice.VIEW_TARGET_UNAVAILABLE) }
            }
            return
        }
        if (launched) {
            updateQueue { queue -> queue.shareLaunched(System.currentTimeMillis()) }
        } else {
            updateQueue { queue ->
                queue.failCurrent(SHARE_FAILED_DETAIL, System.currentTimeMillis())
            }
            _uiState.update { it.copy(notice = QueueNotice.SHARE_FAILED) }
        }
    }

    // ---- user confirmation --------------------------------------------------

    /**
     * Explicit "this payment is done". The only path to a completed item, and it
     * also covers the user resolving an UNKNOWN result after checking their bank
     * app.
     */
    fun onConfirmCompleted() {
        val queue = _uiState.value.queue ?: return
        val item = queue.currentItem ?: return
        val nowMillis = System.currentTimeMillis()
        val updated = when (item.status) {
            PaymentStatus.WAITING_USER -> queue.confirmCompleted(nowMillis)
            PaymentStatus.UNKNOWN -> queue.resolveUnknownCompleted(nowMillis)
            else -> return
        }
        _uiState.update { it.copy(confirmationDismissedFor = null) }
        persist(updated)
    }

    /**
     * "Not done yet": the queue does not advance and the item stays retryable.
     * This only changes what the screen shows; the item remains in
     * [PaymentStatus.WAITING_USER] so it can be shared again on purpose.
     */
    fun onConfirmNotCompleted() {
        val queue = _uiState.value.queue ?: return
        val item = queue.currentItem ?: return
        if (item.status != PaymentStatus.WAITING_USER) return
        _uiState.update { it.copy(confirmationDismissedFor = item.id) }
        persist(queue.confirmNotCompleted(System.currentTimeMillis()))
    }

    /**
     * Explicit retry of a FAILED or UNKNOWN item. The item goes back to the queue
     * and nothing is shared until the user asks for it.
     */
    fun onRetryCurrent() {
        val queue = _uiState.value.queue ?: return
        _uiState.update { it.copy(confirmationDismissedFor = null) }
        persist(queue.retryCurrent(System.currentTimeMillis()))
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
                reShareConfirmationVisible = false,
                confirmationDismissedFor = null,
                imageIntent = null,
                importing = false,
            )
        }
        scope.launch {
            withContext(Dispatchers.IO) { repository.clearQueue() }
            _uiState.update {
                it.copy(queue = null, importProgress = null, notice = QueueNotice.QUEUE_CLEARED)
            }
        }
    }

    fun onNoticeShown() {
        _uiState.update { it.copy(notice = null) }
    }

    // ---- internals ----------------------------------------------------------

    /** Records a known error on the current item and tells the user why. */
    private fun failCurrent(detail: String, notice: QueueNotice) {
        updateQueue { queue -> queue.failCurrent(detail, System.currentTimeMillis()) }
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
        const val SHARE_FAILED_DETAIL = "Android's share sheet could not be opened."
        const val INTERRUPTED_DETAIL = "The app stopped while this image was being handed off."
    }
}
