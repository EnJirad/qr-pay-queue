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
import com.enjirad.qrqueue.data.AppSettingsStore
import com.enjirad.qrqueue.data.SharedPreferencesAppSettingsStore
import com.enjirad.qrqueue.domain.HandPreference
import com.enjirad.qrqueue.domain.ImportProgress
import com.enjirad.qrqueue.domain.ImportSummary
import com.enjirad.qrqueue.domain.PaymentQueue
import com.enjirad.qrqueue.domain.PaymentStatus
import com.enjirad.qrqueue.domain.QrVersion
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
    BANK_NOT_SHARE_CAPABLE,
    QR_REPLACEMENT_FAILED,
    DAILY_DATA_CLEARED,
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

/** The three primary bottom-navigation destinations, in their fixed order. */
enum class QueueTab { HOME, PROBLEMS, COMPLETED }

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
    val selectedTab: QueueTab = QueueTab.HOME,
    val importProgress: ImportProgress? = null,
    val importSummary: ImportSummary? = null,
    val clearConfirmationVisible: Boolean = false,
    /** The item whose QR the user asked to replace, while the picker is open. */
    val replaceQrItemId: String? = null,
    val imageIntent: ImageIntentRequest? = null,
    val notice: QueueNotice? = null,

    // ---- bank selection -----------------------------------------------------
    val selectedBank: BankInfo? = null,
    val bankStatus: BankTargetStatus? = null,
    val bankSelectionVisible: Boolean = false,

    // ---- settings ----------------------------------------------------------
    val handPreference: HandPreference = HandPreference.RIGHT,
    val autoDailyReset: Boolean = false,
    val settingsVisible: Boolean = false,

    // ---- home lock (V0.7) --------------------------------------------------
    /** When true, Home prevents vertical scroll so QR + actions stay fixed. */
    val homeLocked: Boolean = false,

    // ---- clear item ---------------------------------------------------------
    /** True when the clear-item confirmation dialog is visible. */
    val clearItemConfirmationVisible: Boolean = false,
    /** The item the user wants to delete from the Problem tab. */
    val itemToClearId: String? = null,
) {
    /** True while the import screen must be shown instead of the queue. */
    val importing: Boolean get() = QueueImport.isImportRunning(importProgress)

    /**
     * Upload gate: the user must select an installed bank before importing any
     * images. This is the single source of truth for whether the import button
     * is enabled.
     */
    val canImport: Boolean
        get() = selectedBank != null && (bankStatus?.canHandOff == true)

    /** True when the bank is installed and advertises image sharing. */
    val isBankReady: Boolean get() = bankStatus?.advertised == true

    /**
     * True when the user must (re)select a bank before importing or sharing:
     * either nothing is selected yet, or the selected package is not installed
     * on this device any more (V0.4.2 §12/§13). The previous selection is kept
     * so the card can still show "K PLUS — ไม่พบแอป".
     */
    val requiresBankSelection: Boolean
        get() = selectedBank == null || bankStatus?.isInstalled != true

    // ---- derived queue state ------------------------------------------------

    /** The number of items that need the user's attention right now. */
    val problemCount: Int get() = queue?.problemCount ?: 0

    /** The badge value for the problem tab; null means "no badge at all". */
    val problemBadge: Int? get() = problemCount.takeIf { count -> count > 0 }

    /** How many items the user has confirmed, shown inside the completed tab. */
    val completedCount: Int get() = queue?.completedCount ?: 0

    /** The item Home offers next, if any. */
    val nextActionItem: QueueItem? get() = queue?.nextActionItem

    /** The item waiting for the user's payment answer, if any. */
    val awaitingAnswerItem: QueueItem? get() = queue?.awaitingAnswerItem

    /** The item whose QR the user asked to replace, while the picker is open. */
    val replaceQrItem: QueueItem? get() = replaceQrItemId?.let { id -> queue?.item(id) }

    /** True while a bank hand-off is being launched; the payment action is locked. */
    val paymentInFlight: Boolean get() = imageIntent?.kind == ImageIntentKind.SHARE
}

/**
 * State holder for the whole workflow: select a bank, import images, queue them
 * as Payment Items, hand one item's QR at a time to the selected bank, and record
 * the user's own confirmation before moving on.
 *
 * The app does not read the QR code and does not decide whether a payment
 * happened.
 */
class QueueViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = QueueRepository(application)
    private val settingsStore: AppSettingsStore = SharedPreferencesAppSettingsStore(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    /**
     * Item waiting for the user to return from the banking app.
     *
     * This is transient UI state. The persisted queue remains SHARING until
     * the queue screen actually resumes after the bank hand-off.
     */
    private var pendingBankReturnItemId: String? = null

    init {
        // Load persisted settings.
        val handPref = settingsStore.loadHandPreference()
        val autoReset = settingsStore.loadAutoDailyReset()
        val homeLocked = settingsStore.loadHomeLocked()
        _uiState.update {
            it.copy(handPreference = handPref, autoDailyReset = autoReset, homeLocked = homeLocked)
        }

        // Lazy daily reset: check if a new day has started since the last reset.
        val today = todayDateString()
        val lastReset = settingsStore.loadLastResetDate()
        if (autoReset && lastReset != null && today != lastReset) {
            repository.clearDailyData()
            settingsStore.saveLastResetDate(today)
            _uiState.update { it.copy(notice = QueueNotice.DAILY_DATA_CLEARED) }
        } else if (autoReset && lastReset == null) {
            // First time auto-reset is on: record today so the next day triggers a reset.
            settingsStore.saveLastResetDate(today)
        }

        // Restore the previously selected bank and re-probe it on this device.
        val savedBank = repository.loadSelectedBank()
        if (savedBank != null) {
            val status = BankTarget.query(getApplication(), savedBank)
            _uiState.update {
                it.copy(selectedBank = savedBank, bankStatus = status)
            }
            if (!status.canHandOff) {
                _uiState.update { it.copy(notice = QueueNotice.BANK_UNINSTALLED) }
            }
        }

        // Restore the queue. Anything that was mid hand-off becomes UNKNOWN.
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

    // ---- navigation ---------------------------------------------------------

    /** Switches the visible tab. Never touches the queue itself. */
    fun onTabSelected(tab: QueueTab) {
        _uiState.update { it.copy(selectedTab = tab) }
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
            it.copy(
                importProgress = ImportProgress.of(sourceUris.size),
                importSummary = null,
            )
        }

        scope.launch {
            var progress = ImportProgress.of(sourceUris.size)
            var nextPosition = queueBeforeImport?.nextPosition ?: PaymentQueue.FIRST_POSITION
            val importedItems = mutableListOf<QueueItem>()
            var failedImports = 0
            var duplicates = 0

            val seenFingerprints = mutableSetOf<String>()

            queueBeforeImport?.items?.forEach { item ->
                item.versions.forEach { version ->
                    version.fingerprint?.let { fingerprint ->
                        seenFingerprints += fingerprint
                    }
                }
            }

            sourceUris.forEach { sourceUri ->
                val outcome = withContext(Dispatchers.IO) {
                    importOne(sourceUri, nextPosition, seenFingerprints)
                }

                when (outcome) {
                    is ImportOutcome.Imported -> {
                        importedItems += outcome.item
                        outcome.fingerprint?.let { fingerprint ->
                            seenFingerprints += fingerprint
                        }
                        nextPosition++
                    }

                    ImportOutcome.Duplicate -> duplicates++
                    ImportOutcome.Failed -> failedImports++
                }

                progress = progress.advance()
                val snapshot = progress
                _uiState.update { it.copy(importProgress = snapshot) }
            }

            val currentQueue = _uiState.value.queue

            val summary = ImportSummary(
                imported = importedItems.size,
                failed = failedImports,
                duplicates = duplicates,
            )

            if (currentQueue == null && importedItems.isEmpty()) {
                _uiState.update {
                    it.copy(
                        importProgress = null,
                        importSummary = summary,
                    )
                }
                return@launch
            }

            val base = currentQueue ?: PaymentQueue.create(
                queueId = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
            )

            val updated = base.appendItems(importedItems).normalized()
            persist(updated)

            _uiState.update {
                it.copy(
                    importProgress = null,
                    importSummary = summary,
                    selectedTab = if (currentQueue == null) {
                        QueueTab.HOME
                    } else {
                        it.selectedTab
                    },
                )
            }

            sweepOrphanImages(updated)
        }
    }

    fun onImageSelectionCancelled() {
        _uiState.update { it.copy(importProgress = null) }
    }

    fun onImportSummaryShown() {
        _uiState.update { it.copy(importSummary = null) }
    }

    private sealed interface ImportOutcome {
        data class Imported(
            val item: QueueItem,
            val fingerprint: String?,
        ) : ImportOutcome

        data object Duplicate : ImportOutcome
        data object Failed : ImportOutcome
    }

    private fun importOne(
        sourceUri: String,
        position: Int,
        seenFingerprints: Set<String>,
    ): ImportOutcome {
        val itemId = QueueImport.newItemId()

        val stored = runCatching {
            repository.importImage(Uri.parse(sourceUri), itemId)
        }.getOrNull() ?: return ImportOutcome.Failed

        val fingerprint = stored.fingerprint

        // Exact duplicate protection: the same image content is never queued twice.
        if (fingerprint != null && fingerprint in seenFingerprints) {
            runCatching { stored.file.delete() }
            return ImportOutcome.Duplicate
        }

        val item = QueueImport.buildItem(
            id = itemId,
            position = position,
            sourceUri = sourceUri,
            storedImagePath = stored.file.absolutePath,
            displayName = stored.displayName,
            mimeType = stored.mimeType,
            nowMillis = System.currentTimeMillis(),
            fingerprint = fingerprint,
        )

        return ImportOutcome.Imported(item, fingerprint)
    }

    // ---- QR replacement -----------------------------------------------------

    /**
     * The user asked to replace the QR of one Payment Item. This never creates a
     * new item: the picker result is attached to [itemId] as a new QR version.
     */
    fun onReplaceQrRequested(itemId: String) {
        val item = _uiState.value.queue?.item(itemId) ?: return
        if (!item.canReplaceQr) return
        _uiState.update { it.copy(replaceQrItemId = itemId) }
    }

    fun onReplacementCancelled() {
        _uiState.update { it.copy(replaceQrItemId = null) }
    }

    /**
     * The user picked a replacement image. The new QR becomes current, the old
     * one stays in history, and the item returns to [PaymentStatus.READY] — with
     * the same Payment Item id, position and number.
     *
     * If the replacement cannot be recorded, the copy is removed and the previous
     * current QR is left exactly as it was.
     */
    fun onReplacementImagePicked(uri: Uri) {
        val itemId = _uiState.value.replaceQrItemId ?: return

        _uiState.update {
            it.copy(replaceQrItemId = null)
        }

        val item = _uiState.value.queue?.item(itemId) ?: return
        if (!item.canReplaceQr) return

        scope.launch {
            val versionId = QueueImport.newVersionId()

            val stored = withContext(Dispatchers.IO) {
                runCatching {
                    repository.importImage(uri, versionId)
                }.getOrNull()
            }

            if (stored == null) {
                _uiState.update {
                    it.copy(notice = QueueNotice.QR_REPLACEMENT_FAILED)
                }
                return@launch
            }

            val nowMillis = System.currentTimeMillis()

            val version: QrVersion = QueueImport.buildVersion(
                id = versionId,
                paymentItemId = itemId,
                sourceUri = uri.toString(),
                storedImagePath = stored.file.absolutePath,
                displayName = stored.displayName,
                mimeType = stored.mimeType,
                nowMillis = nowMillis,
                fingerprint = stored.fingerprint,
            )

            val queue = _uiState.value.queue ?: return@launch

            val updated = queue.replaceCurrentQr(
                itemId,
                version,
                nowMillis,
            )

            if (updated.item(itemId)?.currentVersionId != versionId) {
                // Refused: the original current QR must stay intact.
                withContext(Dispatchers.IO) {
                    repository.deleteImages(
                        listOf(stored.file.absolutePath),
                    )
                }

                _uiState.update {
                    it.copy(notice = QueueNotice.QR_REPLACEMENT_FAILED)
                }

                return@launch
            }

            persist(updated)
            sweepOrphanImages(updated)
        }
    }

    // ---- hand-off -----------------------------------------------------------

    fun onShareItemRequested(itemId: String) {
        // Double-payment protection: while a hand-off is being launched, no
        // second attempt may start from this screen.
        if (_uiState.value.paymentInFlight) {
            _uiState.update {
                it.copy(notice = QueueNotice.HANDOFF_IN_PROGRESS)
            }
            return
        }

        val queue = _uiState.value.queue ?: return
        val item = queue.item(itemId) ?: return

        // V0.4.2 §9 step 1: without a selected target nothing is shared.
        val bank = _uiState.value.selectedBank

        if (bank == null) {
            _uiState.update {
                it.copy(notice = QueueNotice.BANK_UNAVAILABLE)
            }
            return
        }

        if (!queue.canStartHandoff(itemId)) {
            _uiState.update {
                it.copy(notice = QueueNotice.HANDOFF_IN_PROGRESS)
            }
            return
        }

        beginHandOff(item, bank)
    }

    /**
     * V0.4.2 §9/§11/§17: re-verify the selected bank on the device immediately
     * before the hand-off. If the package is gone or no target can be reached the
     * user is told and nothing is launched — the Android chooser is never used as
     * a fallback, and another app is never opened instead.
     */
    private fun beginHandOff(
        item: QueueItem,
        bank: BankInfo,
    ) {
        val status = BankTarget.query(
            getApplication(),
            bank,
        )

        _uiState.update {
            it.copy(bankStatus = status)
        }

        val readiness = BankTarget.preflight(
            bank,
            status.isInstalled,
        )

        val notice = BankShareFlow.noticeFor(readiness)

        if (notice != null) {
            _uiState.update {
                it.copy(notice = notice)
            }
            return
        }

        requestShare(item, bank)
    }

    fun onViewItemRequested(itemId: String) {
        if (_uiState.value.imageIntent != null) return

        val item = _uiState.value.queue?.item(itemId) ?: return
        val path = item.previewFilePath

        if (path == null || !File(path).isFile) {
            failItem(
                item.id,
                MISSING_IMAGE_DETAIL,
                QueueNotice.IMAGE_MISSING,
            )
            return
        }

        _uiState.update {
            it.copy(
                imageIntent = ImageIntentRequest(
                    kind = ImageIntentKind.VIEW,
                    itemId = item.id,
                    filePath = path,
                    mimeType = item.currentMimeType,
                    fileName = item.currentDisplayName,
                ),
            )
        }
    }

    private fun requestShare(
        item: QueueItem,
        bank: BankInfo,
    ) {
        val path = item.currentFilePath

        if (path == null || !File(path).isFile) {
            failItem(
                item.id,
                MISSING_IMAGE_DETAIL,
                QueueNotice.IMAGE_MISSING,
            )
            return
        }

        val nowMillis = System.currentTimeMillis()

        val started = (_uiState.value.queue ?: return).startSharing(
            item.id,
            nowMillis,
        )

        if (started.item(item.id)?.status != PaymentStatus.SHARING) return

        persist(started)

        _uiState.update {
            it.copy(
                imageIntent = ImageIntentRequest(
                    kind = ImageIntentKind.SHARE,
                    itemId = item.id,
                    filePath = path,
                    mimeType = item.currentMimeType,
                    fileName = item.currentDisplayName,
                    targetPackage = bank.packageName,
                ),
            )
        }
    }

    /**
     * Called after Android attempts to launch the requested external app.
     *
     * Important:
     * - launched == true means only that the bank app was opened.
     * - It does NOT mean that the payment was completed.
     * - We therefore keep the item in SHARING until QueueScreen receives
     *   ON_RESUME after the user actually returns to QR Pay Queue.
     */
    fun onImageIntentLaunched(
        kind: ImageIntentKind,
        launched: Boolean,
    ) {
        val request = _uiState.value.imageIntent

        _uiState.update {
            it.copy(imageIntent = null)
        }

        if (kind == ImageIntentKind.VIEW) {
            if (!launched) {
                _uiState.update {
                    it.copy(
                        notice = QueueNotice.VIEW_TARGET_UNAVAILABLE,
                    )
                }
            }
            return
        }

        val itemId = request?.itemId ?: return

        if (launched) {
            /*
             * The banking app has been opened successfully.
             *
             * Opening the bank is NOT the same as returning from the bank.
             * Keep the item in SHARING until QueueScreen receives ON_RESUME.
             */
            pendingBankReturnItemId = itemId
        } else {
            // The bank could not be opened: no resolving activity, or launch
            // threw. Never fall back to the Android chooser or another app
            // (V0.4.2 §17).
            BankShareFlow.noticeFor(
                BankShareReadiness.TARGET_UNRESOLVABLE,
            )?.let { notice ->
                failItem(
                    itemId,
                    SHARE_FAILED_DETAIL,
                    notice,
                )
            }
        }
    }

    /**
     * Called when QR Pay Queue returns to the foreground after opening the bank.
     *
     * Only now do we move SHARING -> AWAITING_USER_CONFIRMATION.
     * The app still does NOT assume that a payment happened.
     */
    fun onBankAppReturnedToForeground() {
        val itemId = pendingBankReturnItemId ?: return

        val queue = _uiState.value.queue ?: run {
            pendingBankReturnItemId = null
            return
        }

        val item = queue.item(itemId)

        /*
         * Only resolve the exact item that was handed to the bank, and only if
         * it is still in SHARING. This makes the callback idempotent and avoids
         * accidentally changing another queue item.
         */
        if (item?.status == PaymentStatus.SHARING) {
            updateQueue { currentQueue ->
                currentQueue.shareLaunched(
                    itemId,
                    System.currentTimeMillis(),
                )
            }
        }

        pendingBankReturnItemId = null
    }

    // ---- user confirmation --------------------------------------------------

    /**
     * The user pressed "ทำรายการเสร็จแล้ว". This is the only path to COMPLETED:
     * an unresolved item must be resolved explicitly by the user, and an item
     * that is merely awaiting confirmation must be confirmed by the user.
     */
    fun onConfirmCompleted(itemId: String) {
        val queue = _uiState.value.queue ?: return
        val item = queue.item(itemId) ?: return
        val nowMillis = System.currentTimeMillis()

        val updated = when (item.status) {
            PaymentStatus.AWAITING_USER_CONFIRMATION ->
                queue.confirmCompleted(itemId, nowMillis)

            PaymentStatus.UNKNOWN ->
                queue.resolveUnknownCompleted(itemId, nowMillis)

            else -> return
        }

        persist(updated)
    }

    /** The user is not sure yet: the item stays open and is never re-shared. */
    fun onKeepWaiting(itemId: String) {
        val queue = _uiState.value.queue ?: return

        if (
            queue.item(itemId)?.status !=
            PaymentStatus.AWAITING_USER_CONFIRMATION
        ) {
            return
        }

        persist(
            queue.keepWaiting(
                itemId,
                System.currentTimeMillis(),
            ),
        )
    }

    /** Explicit user retry of a FAILED or UNKNOWN item; nothing is shared yet. */
    fun onRetryItem(itemId: String) {
        val queue = _uiState.value.queue ?: return

        persist(
            queue.retryItem(
                itemId,
                System.currentTimeMillis(),
            ),
        )
    }

    /** The user reports that this QR cannot be used; the item waits for a new QR. */
    fun onMarkQrUnusable(itemId: String) {
        val queue = _uiState.value.queue ?: return

        persist(
            queue.markQrUnusable(
                itemId,
                QR_UNUSABLE_DETAIL,
                System.currentTimeMillis(),
            ),
        )
    }

    // ---- lock toggle --------------------------------------------------------

    fun onLockToggled() {
        val newLocked = !_uiState.value.homeLocked
        settingsStore.saveHomeLocked(newLocked)

        _uiState.update {
            it.copy(homeLocked = newLocked)
        }
    }

    // ---- settings ----------------------------------------------------------

    fun onSettingsRequested() {
        _uiState.update {
            it.copy(settingsVisible = true)
        }
    }

    fun onSettingsDismissed() {
        _uiState.update {
            it.copy(settingsVisible = false)
        }
    }

    fun onHandPreferenceChanged(pref: HandPreference) {
        settingsStore.saveHandPreference(pref)

        _uiState.update {
            it.copy(handPreference = pref)
        }
    }

    fun onAutoDailyResetChanged(enabled: Boolean) {
        settingsStore.saveAutoDailyReset(enabled)

        _uiState.update {
            it.copy(autoDailyReset = enabled)
        }

        if (enabled) {
            // Record today so the next day triggers a reset.
            settingsStore.saveLastResetDate(todayDateString())
        }
    }

    fun onManualDailyResetRequested() {
        _uiState.update {
            it.copy(clearConfirmationVisible = true)
        }
    }

    // ---- simplified problem flow (V0.7: one-tap, no reason sheet) ----------

    /**
     * The user tapped the problem icon on an item that was just shared. Marks
     * the QR as unusable and advances to the next item immediately — one tap,
     * no reason selection, no confirmation.
     */
    fun onReportProblem(itemId: String) {
        val queue = _uiState.value.queue ?: return
        val nowMillis = System.currentTimeMillis()

        persist(
            queue.markQrUnusable(
                itemId,
                QR_UNUSABLE_DETAIL,
                nowMillis,
            ),
        )
    }

    /**
     * The user tapped the unknown icon. The item moves to UNKNOWN and the next
     * QR becomes current. UNKNOWN lives in the problem tab.
     */
    fun onMarkUnknown(itemId: String) {
        val queue = _uiState.value.queue ?: return

        if (
            queue.item(itemId)?.status !=
            PaymentStatus.AWAITING_USER_CONFIRMATION
        ) {
            return
        }

        persist(
            queue.markUnknown(
                itemId,
                null,
                System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Retry-share (§11–§15): the user is in AWAITING_USER_CONFIRMATION and wants
     * to open the bank again with the same QR. The item goes back to SHARING with
     * a new attempt record, and the bank share flow is re-triggered.
     */
    fun onRetryShare(itemId: String) {
        if (_uiState.value.paymentInFlight) {
            _uiState.update {
                it.copy(notice = QueueNotice.HANDOFF_IN_PROGRESS)
            }
            return
        }

        val queue = _uiState.value.queue ?: return
        val item = queue.item(itemId) ?: return

        if (item.status != PaymentStatus.AWAITING_USER_CONFIRMATION) return

        val bank = _uiState.value.selectedBank ?: return
        val nowMillis = System.currentTimeMillis()

        val updated = queue.retryShare(
            itemId,
            nowMillis,
        )

        if (updated.item(itemId)?.status != PaymentStatus.SHARING) return

        persist(updated)

        // Re-trigger the bank share with the same QR.
        beginHandOff(item, bank)
    }

    // ---- clear item ---------------------------------------------------------

    fun onClearItemRequested(itemId: String) {
        _uiState.update {
            it.copy(
                clearItemConfirmationVisible = true,
                itemToClearId = itemId,
            )
        }
    }

    fun onClearItemDismissed() {
        _uiState.update {
            it.copy(
                clearItemConfirmationVisible = false,
                itemToClearId = null,
            )
        }
    }

    fun onClearItemConfirmed() {
        val itemId = _uiState.value.itemToClearId ?: return

        _uiState.update {
            it.copy(
                clearItemConfirmationVisible = false,
                itemToClearId = null,
            )
        }

        scope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteItem(itemId)
            }

            val restored = repository.loadQueue()

            if (restored != null) {
                persist(
                    restored.resolveInterrupted(
                        INTERRUPTED_DETAIL,
                        System.currentTimeMillis(),
                    ),
                )
            } else {
                _uiState.update {
                    it.copy(queue = null)
                }
            }
        }
    }

    // ---- housekeeping -------------------------------------------------------

    fun onClearQueueRequested() {
        _uiState.update {
            it.copy(clearConfirmationVisible = true)
        }
    }

    fun onClearQueueDismissed() {
        _uiState.update {
            it.copy(clearConfirmationVisible = false)
        }
    }

    fun onClearQueueConfirmed() {
        _uiState.update {
            it.copy(
                clearConfirmationVisible = false,
                replaceQrItemId = null,
                imageIntent = null,
                importProgress = null,
                importSummary = null,
            )
        }

        scope.launch {
            withContext(Dispatchers.IO) {
                repository.clearDailyData()
                settingsStore.saveLastResetDate(todayDateString())
            }

            _uiState.update {
                it.copy(
                    queue = null,
                    notice = QueueNotice.QUEUE_CLEARED,
                )
            }
        }
    }

    fun onNoticeShown() {
        _uiState.update {
            it.copy(notice = null)
        }
    }

    // ---- internals ----------------------------------------------------------

    private fun failItem(
        itemId: String,
        detail: String,
        notice: QueueNotice,
    ) {
        updateQueue { queue ->
            queue.failItem(
                itemId,
                detail,
                System.currentTimeMillis(),
            )
        }

        _uiState.update {
            it.copy(notice = notice)
        }
    }

    private fun updateQueue(
        transform: (PaymentQueue) -> PaymentQueue,
    ) {
        val queue = _uiState.value.queue ?: return
        persist(transform(queue))
    }

    private fun persist(
        queue: PaymentQueue,
        notice: QueueNotice? = null,
    ) {
        val stamped = queue.copy(
            updatedAtMillis = System.currentTimeMillis(),
        )

        val saved = repository.saveQueue(stamped)

        _uiState.update { state ->
            val nextNotice = when {
                !saved -> QueueNotice.QUEUE_NOT_SAVED
                notice != null -> notice
                else -> state.notice
            }

            state.copy(
                queue = stamped,
                notice = nextNotice,
            )
        }
    }

    /**
     * The images that must stay on disk: every QR version of every item, not just
     * the current one, because replaced QRs are kept as history.
     */
    private fun sweepOrphanImages(queue: PaymentQueue) {
        val referenced = queue.items
            .flatMap { item -> item.versions }
            .map { version -> version.filePath }
            .toSet()

        scope.launch {
            withContext(Dispatchers.IO) {
                repository.sweepOrphanImages(referenced)
            }
        }
    }

    private fun todayDateString(): String {
        val sdf = java.text.SimpleDateFormat(
            "yyyy-MM-dd",
            java.util.Locale.US,
        )

        return sdf.format(java.util.Date())
    }

    private companion object {
        const val MISSING_IMAGE_DETAIL =
            "The imported image file is missing from this device."

        const val SHARE_FAILED_DETAIL =
            "The selected bank did not accept the shared image (no activity matched the hand-off intent)."

        const val INTERRUPTED_DETAIL =
            "The app stopped while this image was being handed off."

        const val QR_UNUSABLE_DETAIL =
            "The user reported that this QR cannot be used."
    }
}
