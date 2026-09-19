package com.enjirad.qrqueue.domain

/**
 * The status of a banking app on this device, relevant to the image hand-off.
 *
 * There are three meaningful outcomes when the app probes a banking package:
 *
 * - [READY] — the app is installed and advertises an activity that can receive
 *   the image share intent.
 * - [INSTALLED_NOT_ADVERTISED] — the app is installed but does not advertise an
 *   image share target. The hand-off may still work (the probe can be incomplete
 *   on some builds), so the app tries it and reports the failure honestly instead
 *   of hiding it.
 * - [NOT_INSTALLED] — the package was not found on this device at all.
 */
enum class BankAvailability {
    READY,
    INSTALLED_NOT_ADVERTISED,
    NOT_INSTALLED,
}

/**
 * Whether a direct bank hand-off may be attempted, decided from the facts the app
 * already knows before it builds the intent.
 *
 * This is the pure part of the V0.4.2 share gate:
 *
 * - [NO_BANK_SELECTED] — the user has not chosen a bank; ask them to select one.
 * - [BANK_NOT_INSTALLED] — the selected bank's package is gone; ask for a new one.
 * - [TARGET_UNRESOLVABLE] — the package is installed but no activity accepts the
 *   image share right now (checked on the device just before launching).
 * - [READY] — the hand-off may be launched.
 *
 * A bank that is installed but does not advertise an image share is **not**
 * reported as missing: the hand-off is attempted and a failure is reported
 * honestly (see AI_HANDOFF.md). "Installed" and "can receive this intent" are
 * deliberately separate states.
 */
enum class BankShareReadiness {
    READY,
    NO_BANK_SELECTED,
    BANK_NOT_INSTALLED,
    TARGET_UNRESOLVABLE,
}

/**
 * One banking app the user may choose as the payment hand-off destination.
 *
 * @param id a short, stable key for this bank (never shown to the user; used in
 *   SharedPreferences to persist the selection).
 * @param name the package name of the Android app on Google Play. This is the
 *   verified value — the runtime probe checks whether this package is actually
 *   installed and whether it can receive the image share intent.
 * @param displayName the localised label shown in the bank-selection UI (e.g.
 *   "K PLUS").
 * @param displayNameDetail a short secondary line shown below the name when
 *   the status needs explaining (e.g. "KBank").
 */
data class BankInfo(
    val id: String,
    val name: String,
    val displayName: String,
    val displayNameDetail: String = "",
)

/**
 * The runtime result of probing one [BankInfo] on a real device.
 *
 * @param bank the bank that was probed.
 * @param availability what was actually found.
 * @param advertised true when the bank's package advertises an activity for the
 *   exact image share intent the app builds.
 */
data class BankTargetStatus(
    val bank: BankInfo,
    val availability: BankAvailability,
    val advertised: Boolean,
) {
    /** The share button should be available whenever the app is installed. */
    val canHandOff: Boolean get() = availability != BankAvailability.NOT_INSTALLED

    val isInstalled: Boolean get() = availability != BankAvailability.NOT_INSTALLED
}

/**
 * The verified list of Thai banking apps that the app can hand QR images to.
 *
 * Package names below come from each app's published Google Play listing. The
 * runtime probe (via [com.enjirad.qrqueue.data.BankTarget]) still checks the
 * real device — this list is the *candidate* set, not a claim that every bank
 * on the list is installed on every device.
 *
 * The list may be extended in future versions. Only apps that accept
 * `ACTION_SEND` with an image MIME type through the Android share system belong
 * here; apps that require a proprietary SDK, an accessibility service or any
 * form of automation are excluded by design.
 */
object BankRegistry {

    /** K PLUS — Kasikorn Bank (KBank). */
    val K_PLUS = BankInfo(
        id = "kplus",
        name = "com.kasikornbank.kplus",
        displayName = "K PLUS",
        displayNameDetail = "KBank",
    )

    /** SCB EASY — Siam Commercial Bank. */
    val SCB_EASY = BankInfo(
        id = "scb_easy",
        name = "com.scb.BankApp",
        displayName = "SCB EASY",
        displayNameDetail = "Siam Commercial Bank",
    )

    /** Krungthai NEXT — Bank of Ayudhya. */
    val KRUNGTHAI_NEXT = BankInfo(
        id = "krungthai_next",
        name = "com.krungthai.nextbanking",
        displayName = "Krungthai NEXT",
        displayNameDetail = "Bank of Ayudhya",
    )

    /** Bualuang mBanking — Bangkok Bank. */
    val BUALUANG_MBANKING = BankInfo(
        id = "bualuang_mbanking",
        name = "com.bblmobilebanking",
        displayName = "Bualuang mBanking",
        displayNameDetail = "Bangkok Bank",
    )

    /**
     * All banks the app knows about, in the order they appear in the
     * bank-selection UI. The runtime probe checks each one; only the banks
     * actually found on the device show as available.
     */
    val BANKS: List<BankInfo> = listOf(
        K_PLUS,
        SCB_EASY,
        KRUNGTHAI_NEXT,
        BUALUANG_MBANKING,
    )

    /** Find a bank by its stable [BankInfo.id]. */
    fun findById(id: String): BankInfo? = BANKS.firstOrNull { it.id == id }

    /** Find a bank by its Android package name. */
    fun findByPackage(packageName: String): BankInfo? =
        BANKS.firstOrNull { it.name == packageName }
}
