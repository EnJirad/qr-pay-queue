package com.enjirad.qrqueue.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.enjirad.qrqueue.data.BankTarget
import com.enjirad.qrqueue.data.QueueRepository
import com.enjirad.qrqueue.domain.BankInfo
import com.enjirad.qrqueue.domain.BankRegistry
import com.enjirad.qrqueue.domain.BankShareReadiness
import com.enjirad.qrqueue.domain.BankTargetStatus
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
    SHARE_TARGET_UNAVAILABLE,
    BANK_UNAVAILABLE,
    HANDOFF_IN_PROGRESS,
    VIEW_TARGET_UNAVAILABLE,
    IMAGE_MISSING,
    QUEUE_NOT_SAVED,
    QUEUE_CLEARED,
    BANK_UNINSTALLED,
}

/**
 * Maps the pre-flight/share outcome onto the notice the screen must show.
 *
 * Pure and unit-tested so it is explicit that a failed hand-off never turns into
 * the Android chooser or another app: every non-[BankShareReadiness.READY]
 * outcome becomes an error message instead.
 */
object BankShareFlow {

    fun noticeFor(readiness: BankShareReadiness): QueueNotice? = when (readiness) {
        BankShareReadiness.NO_BANK_SELECTED -> QueueNotice.BANK_UNAVAILABLE
        BankShareReadiness.BANK_NOT_INSTALLED -> QueueNotice.BANK_UNINSTALLED
        BankShareReadiness.TARGET_UNRESOLVABLE -> QueueNotice.SHARE_TARGET_UNAVAILABLE
        BankShareReadiness.READY -> null
    }
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
    /** The banking app this hand-off is addressed to; null for VIEW intents. */
    val targetPackage: String? = null,
)

/**
 * Everything the queue screen renders, derived from the persisted queue and the
 * selected bank.
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

    // ---- bank selection -----------------------------------------------------
    /** The bank the user chose, or null on first install. */
    val selectedBank: BankInfo? = null,
    /** The real runtime status of the selected bank (installed, advertised, etc). */
    val bankStatus: BankTargetStatus? = null,
    /** True when the bank-selection dialog is visible. */
    val bankSelectionVisible: Boolean = false,
) {
    /** True while the import screen must be shown instead of the queue. */
    val importing: Boolean get() = QueueImport.isImportRunning(importProgress)

    /** True when the double-payment warning dialog is up. */
    val reShareConfirmationVisible: Boolean get() = reShareItemId != null

    /**
     * Upload gate: the user must select an installed bank before importing any
     * images. This is the single source of truth for whether the import button
     * is enabled.
     */
    val canImport: Boolean
        get() = selectedBank != null && (bankStatus?.canHandOff == true)

    /** True when the bank is installed and advertises image sharing. */
    val isBankReady: Boolean
        get() = bankStatus?.advertised == true

    /**
     * True when the user must (re)select a bank before importing or sharing:
     * either nothing is selected yet, or the selected package is not installed
     * on this device any more (V0.4.2 §12/§13). The previous selection is kept
     * so the card can still show "K PLUS — ไม่พบแอป".
     */
    val requiresBankSelection: Boolean
        get() = selectedBank == null || bankStatus?.isInstalled != true
}

/**
 * State holder for the whole workflow: select a bank, import images, queue them,
 * hand one image at a time to the selected bank, and record the user's own
 * confirmation before moving on.
 *
 * The app does not read the QR code and does not decide whether a payment
 * happened.
 */
class QueueViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = QueueRepository(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    init {
        // Restore the previously selected bank and re-probe it on this device.
        val savedBank = repository.loadSelectedBank()
        if (savedBank != null) {
            val status = BankTarget.query(getApplication(), savedBank)
            _uiState.update {
                it.copy(selectedBank = savedBank, bankStatus = status)
            }
            // If the bank was uninstalled since the last session, notify the user
            // immediately so the upload gate is disabled before they try to import.
            if (!status.canHandOff) {
                _uiState.update { it.copy(notice = QueueNotice.BANK_UNINSTALLED) }
            }
        }
        // Restore the queue.
        val restored = repository.loadQueue()
        if (restored != null) {
            val resolved = restored.resolveInterrupted(
                INTERRUPTED_DETAIL,
                System.currentTimeMillis(),
            )
            persist(resolved)
            sweepOrphanImages(resolved)
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    // ---- bank selection -----------------------------------------------------

    /** Shows the bank-selection dialog. */
    fun onChangeBankRequested() {
        _uiState.update { it.copy(bankSelectionVisible = true) }
    }

    fun onBankSelectionDismissed() {
        _uiState.update { it.copy(bankSelectionVisible = false) }
    }

    /**
     * The user picked a bank from the list. Persist it immediately so it
     * survives every form of process death, re-probe the target, and close
     * the dialog.
     */
    fun onBankSelected(bankId: String) {
        val bank = BankRegistry.findById(bankId) ?: return
        val status = BankTarget.query(getApplication(), bank)
        repository.saveSelectedBank(bank)
        _uiState.update {
            it.copy(
                selectedBank = bank,
                bankStatus = status,
                bankSelectionVisible = false,
            )
        }
    }

    // ---- import -------------------------------------------------------------

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
                val copiedPaths = importedItems.map { item -> item.storedImagePath }
                withContext(Dispatchers.IO) { repository.deleteImages(copiedPaths) }
                _uiState.update { it.copy(importProgress = null) }
                return@launch
            }

            val summary = ImportSummary(imported = importedItems.size, failed = failedImports)
            if (currentQueue == null && importedItems.isEmpty()) {
                _uiState.update { it.copy(importProgress = null, importSummary = summary) }
                return@launch
            }
            val base = currentQueue ?: PaymentQueue.create(
                queueId = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
            )
            val updated = base.appendItems(importedItems).normalized()
            persist(updated)
            _uiState.update { it.copy(importProgress = null, importSummary = summary) }
            sweepOrphanImages(updated)
        }
    }

    fun onImageSelectionCancelled() {
        _uiState.update { it.copy(importProgress = null) }
    }

    fun onImportSummaryShown() {
        _uiState.update { it.copy(importSummary = null) }
    }

    private fun importOne(sourceUri: String, position: Int): QueueItem? {
        val itemId = QueueImport.newItemId()
        val stored = runCatching {
            repository.importImage(Uri.parse(sourceUri), itemId)
        }.getOrNull() ?: return null
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

    fun onShareItemRequested(itemId: String) {
        if (_uiState.value.imageIntent != null) return
        val queue = _uiState.value.queue ?: return
        val item = queue.item(itemId) ?: return
        // V0.4.2 §9 step 1: without a selected target nothing is shared.
        val bank = _uiState.value.selectedBank
        if (bank == null) {
            _uiState.update { it.copy(notice = QueueNotice.BANK_UNAVAILABLE) }
            return
        }
        when {
            item.status.needsReShareWarning ->
                _uiState.update { it.copy(reShareItemId = item.id) }

            item.status == PaymentStatus.UNKNOWN -> Unit

            !queue.canStartHandoff(itemId) ->
                _uiState.update { it.copy(notice = QueueNotice.HANDOFF_IN_PROGRESS) }

            else -> beginHandOff(item, bank)
        }
    }

    fun onReShareConfirmed() {
        val itemId = _uiState.value.reShareItemId ?: return
        _uiState.update { it.copy(reShareItemId = null, confirmationDismissedFor = null) }
        val item = _uiState.value.queue?.item(itemId) ?: return
        if (item.status != PaymentStatus.WAITING_USER) return
        val bank = _uiState.value.selectedBank ?: return
        beginHandOff(item, bank)
    }

    /**
     * V0.4.2 §9/§11/§17: re-verify the selected bank on the device immediately
     * before the hand-off. If the package is gone or no target can be reached the
     * user is told and nothing is launched — the Android chooser is never used as
     * a fallback, and another app is never opened instead.
     */
    private fun beginHandOff(item: QueueItem, bank: BankInfo) {
        val status = BankTarget.query(getApplication(), bank)
        _uiState.update { it.copy(bankStatus = status) }
        val readiness = BankTarget.preflight(bank, status.isInstalled)
        val notice = BankShareFlow.noticeFor(readiness)
        if (notice != null) {
            _uiState.update { it.copy(notice = notice) }
            return
        }
        requestShare(item, bank)
    }

    fun onReShareDismissed() {
        _uiState.update { it.copy(reShareItemId = null) }
    }

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

    private fun requestShare(item: QueueItem, bank: BankInfo) {
        if (!File(item.storedImagePath).isFile) {
            failItem(item.id, MISSING_IMAGE_DETAIL, QueueNotice.IMAGE_MISSING)
            return
        }
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
                    targetPackage = bank.packageName,
                ),
            )
        }
    }

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
            // The bank could not be opened: no resolving activity, or launch
            // threw. Never fall back to the Android chooser or another app
            // (V0.4.2 §17).
            BankShareFlow.noticeFor(BankShareReadiness.TARGET_UNRESOLVABLE)?.let { notice ->
                failItem(itemId, SHARE_FAILED_DETAIL, notice)
            }
        }
    }

    // ---- user confirmation --------------------------------------------------

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

    fun onKeepWaiting(itemId: String) {
        val queue = _uiState.value.queue ?: return
        val item = queue.item(itemId) ?: return
        if (item.status != PaymentStatus.WAITING_USER) return
        _uiState.update { it.copy(confirmationDismissedFor = itemId) }
        persist(queue.keepWaiting(itemId, System.currentTimeMillis()))
    }

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

    private fun failItem(itemId: String, detail: String, notice: QueueNotice) {
        updateQueue { queue -> queue.failItem(itemId, detail, System.currentTimeMillis()) }
        _uiState.update { it.copy(notice = notice) }
    }

    private fun updateQueue(transform: (PaymentQueue) -> PaymentQueue) {
        val queue = _uiState.value.queue ?: return
        persist(transform(queue))
    }

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

    private fun sweepOrphanImages(queue: PaymentQueue) {
        val referenced = queue.items.map { item -> item.storedImagePath }.toSet()
        scope.launch {
            withContext(Dispatchers.IO) { repository.sweepOrphanImages(referenced) }
        }
    }

    private companion object {
        const val MISSING_IMAGE_DETAIL = "The imported image file is missing from this device."
        const val SHARE_FAILED_DETAIL =
            "The selected bank did not accept the shared image (no activity matched the hand-off intent)."
        const val INTERRUPTED_DETAIL = "The app stopped while this image was being handed off."
    }
}
