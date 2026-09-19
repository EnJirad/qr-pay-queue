package com.enjirad.qrqueue.domain

/** What kind of financial app a registry entry is. */
enum class BankCategory {
    /** A full commercial bank's banking app. */
    COMMERCIAL_BANK,

    /** A state/specialised bank (GSB, BAAC, GHB, IBank, EXIM). */
    SPECIALIZED_BANK,

    /** A digital bank / savings or investment app from a bank. */
    DIGITAL_BANK,

    /** A non-bank payment app or e-wallet. */
    PAYMENT_WALLET,
}

/**
 * How strongly we can claim the app accepts a shared QR image.
 *
 * This is deliberately separate from whether the package id is correct: a
 * verified package tells us the app exists, not that it accepts `ACTION_SEND`
 * images. Only a real-device test (or official documentation) can promote an
 * entry to [SUPPORTED]; until then the runtime probe decides.
 */
enum class QrShareSupport {
    SUPPORTED,
    UNKNOWN,
    NOT_SUPPORTED,
}

/**
 * The runtime state of one banking app on this device.
 *
 * "Package installed" and "can receive this share intent" are different
 * questions and are never collapsed into one (Android 11+ package visibility and
 * per-build share activities make the difference real):
 *
 * - [SHARE_CAPABLE] — installed and an activity advertises the generic image
 *   share.
 * - [INSTALLED_BUT_NOT_SHARE_CAPABLE] — installed, but no matching share
 *   activity was found. The app is NOT reported as missing.
 * - [NOT_INSTALLED] — the package is not present.
 * - [UNKNOWN] — the probe could not be completed (e.g. a visibility/security
 *   error). Never treated as installed.
 */
enum class BankAvailability {
    SHARE_CAPABLE,
    INSTALLED_BUT_NOT_SHARE_CAPABLE,
    NOT_INSTALLED,
    UNKNOWN,
}

/**
 * Whether a direct bank hand-off may be attempted, decided from the facts the app
 * already knows before it builds the intent.
 *
 * - [NO_BANK_SELECTED] — the user has not chosen a bank; ask them to select one.
 * - [BANK_NOT_INSTALLED] — the selected bank's package is gone; ask for a new one.
 * - [TARGET_UNRESOLVABLE] — the package is installed but no activity accepts the
 *   image share right now (checked on the device just before launching).
 * - [READY] — the hand-off may be launched.
 */
enum class BankShareReadiness {
    READY,
    NO_BANK_SELECTED,
    BANK_NOT_INSTALLED,
    TARGET_UNRESOLVABLE,
}

/**
 * One financial app the user may choose as the payment hand-off destination.
 *
 * Every `packageName` here is taken from the app's **current Google Play
 * listing**; `verificationDate` is when that listing was checked and
 * [verified] records the outcome. The runtime probe (via
 * [com.enjirad.qrqueue.data.BankTarget]) still checks the real device — the
 * registry is the *candidate* set, not a claim that a bank is installed.
 *
 * @param id a short, stable key (never shown to the user; used in
 *   SharedPreferences to persist the selection).
 * @param displayName the localised label shown in the UI (e.g. "K PLUS").
 * @param packageName the Android application id, verified on Google Play.
 * @param company the bank/company that publishes the app.
 * @param category what kind of app it is.
 * @param googlePlayUrl the exact listing the package id was read from.
 * @param verificationDate ISO date (`yyyy-MM-dd`) of the Google Play check.
 * @param qrShareSupport how strongly image-share support is known; [QrShareSupport.UNKNOWN]
 *   unless a real-device test proved it.
 * @param verified whether the package id was verified against Google Play.
 */
data class BankInfo(
    val id: String,
    val displayName: String,
    val packageName: String,
    val company: String,
    val category: BankCategory,
    val googlePlayUrl: String,
    val verificationDate: String,
    val qrShareSupport: QrShareSupport = QrShareSupport.UNKNOWN,
    val verified: Boolean = true,
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
    /** True whenever the package is present — including "installed, not advertising". */
    val isInstalled: Boolean
        get() = availability == BankAvailability.SHARE_CAPABLE ||
            availability == BankAvailability.INSTALLED_BUT_NOT_SHARE_CAPABLE

    /** The hand-off may be attempted whenever the app is installed. */
    val canHandOff: Boolean get() = isInstalled

    /** True only when the installed app advertises the image share. */
    val shareCapable: Boolean get() = availability == BankAvailability.SHARE_CAPABLE
}

/**
 * The single source of truth for every financial app the queue can hand QR
 * images to.
 *
 * Package ids below were read from each app's live Google Play listing on
 * [VERIFIED_ON]. Do not add an app whose package id has not been verified, and
 * do not scatter package strings anywhere else in the codebase — everything
 * reads from [allBanks].
 *
 * Only apps that receive `ACTION_SEND` with an image MIME type through the
 * Android share system belong here. Apps that require a proprietary SDK, an
 * accessibility service or any form of automation are excluded by design.
 */
object BankRegistry {

    /** The date (ISO `yyyy-MM-dd`) on which every package id below was verified. */
    const val VERIFIED_ON = "2026-09-19"

    private const val PLAY_LISTING = "https://play.google.com/store/apps/details?id="

    // ---- commercial banks ---------------------------------------------------

    /** K PLUS — Kasikornbank (KBank). Repackaged by KBank; the old id is dead. */
    val K_PLUS = BankInfo(
        id = "kplus",
        displayName = "K PLUS",
        packageName = "com.kasikorn.retail.mbanking.wap",
        company = "Kasikornbank (KBank)",
        category = BankCategory.COMMERCIAL_BANK,
        googlePlayUrl = PLAY_LISTING + "com.kasikorn.retail.mbanking.wap",
        verificationDate = VERIFIED_ON,
    )

    /** SCB EASY — Siam Commercial Bank. */
    val SCB_EASY = BankInfo(
        id = "scb_easy",
        displayName = "SCB EASY",
        packageName = "com.scb.phone",
        company = "Siam Commercial Bank (SCB)",
        category = BankCategory.COMMERCIAL_BANK,
        googlePlayUrl = PLAY_LISTING + "com.scb.phone",
        verificationDate = VERIFIED_ON,
    )

    /** Krungthai NEXT — Krungthai Bank. */
    val KRUNGTHAI_NEXT = BankInfo(
        id = "krungthai_next",
        displayName = "Krungthai NEXT",
        packageName = "ktbcs.netbank",
        company = "Krungthai Bank",
        category = BankCategory.COMMERCIAL_BANK,
        googlePlayUrl = PLAY_LISTING + "ktbcs.netbank",
        verificationDate = VERIFIED_ON,
    )

    /** Bangkok Bank Mobile Banking — Bangkok Bank. */
    val BANGKOK_BANK = BankInfo(
        id = "bangkok_bank",
        displayName = "Bangkok Bank Mobile Banking",
        packageName = "com.bbl.mobilebanking",
        company = "Bangkok Bank",
        category = BankCategory.COMMERCIAL_BANK,
        googlePlayUrl = PLAY_LISTING + "com.bbl.mobilebanking",
        verificationDate = VERIFIED_ON,
    )

    /** krungsri — Bank of Ayudhya (Krungsri). */
    val KRUNGSRI = BankInfo(
        id = "krungsri",
        displayName = "krungsri",
        packageName = "com.krungsri.kma",
        company = "Bank of Ayudhya (Krungsri)",
        category = BankCategory.COMMERCIAL_BANK,
        googlePlayUrl = PLAY_LISTING + "com.krungsri.kma",
        verificationDate = VERIFIED_ON,
    )

    /** ttb touch — TMBThanachart Bank. */
    val TTB_TOUCH = BankInfo(
        id = "ttb_touch",
        displayName = "ttb touch",
        packageName = "com.TMBTOUCH.PRODUCTION",
        company = "TMBThanachart Bank (ttb)",
        category = BankCategory.COMMERCIAL_BANK,
        googlePlayUrl = PLAY_LISTING + "com.TMBTOUCH.PRODUCTION",
        verificationDate = VERIFIED_ON,
    )

    /** CIMB THAI — CIMB Thai Bank. */
    val CIMB_THAI = BankInfo(
        id = "cimb_thai",
        displayName = "CIMB THAI",
        packageName = "com.cimbthai.digital.mycimb",
        company = "CIMB Thai Bank",
        category = BankCategory.COMMERCIAL_BANK,
        googlePlayUrl = PLAY_LISTING + "com.cimbthai.digital.mycimb",
        verificationDate = VERIFIED_ON,
    )

    /** UOB TMRW Thailand — United Overseas Bank (Thai). */
    val UOB_TMRW = BankInfo(
        id = "uob_tmrw",
        displayName = "UOB TMRW Thailand",
        packageName = "com.uob.mightyth2",
        company = "United Overseas Bank (Thai)",
        category = BankCategory.COMMERCIAL_BANK,
        googlePlayUrl = PLAY_LISTING + "com.uob.mightyth2",
        verificationDate = VERIFIED_ON,
    )

    // ---- specialised / government banks -------------------------------------

    /** MyMo by GSB — Government Savings Bank. */
    val MYMO_GSB = BankInfo(
        id = "mymo_gsb",
        displayName = "MyMo by GSB",
        packageName = "com.mobilife.gsb.mymo",
        company = "Government Savings Bank (GSB)",
        category = BankCategory.SPECIALIZED_BANK,
        googlePlayUrl = PLAY_LISTING + "com.mobilife.gsb.mymo",
        verificationDate = VERIFIED_ON,
    )

    // ---- digital banks ------------------------------------------------------

    /** Dime! — KKP Dime (Kiatnakin Phatra). */
    val DIME = BankInfo(
        id = "dime",
        displayName = "Dime!",
        packageName = "com.dimekkp.dimeapp",
        company = "KKP Dime (Kiatnakin Phatra)",
        category = BankCategory.DIGITAL_BANK,
        googlePlayUrl = PLAY_LISTING + "com.dimekkp.dimeapp",
        verificationDate = VERIFIED_ON,
    )

    /** MAKE by KBank — Kasikornbank's money-management app. */
    val MAKE_BY_KBANK = BankInfo(
        id = "make_by_kbank",
        displayName = "MAKE by KBank",
        packageName = "com.kasikornbank.makebykbank",
        company = "Kasikornbank (KBank)",
        category = BankCategory.DIGITAL_BANK,
        googlePlayUrl = PLAY_LISTING + "com.kasikornbank.makebykbank",
        verificationDate = VERIFIED_ON,
    )

    /** Kept — Bank of Ayudhya's savings app. */
    val KEPT = BankInfo(
        id = "kept",
        displayName = "Kept",
        packageName = "com.krungsri.kept",
        company = "Bank of Ayudhya (Krungsri)",
        category = BankCategory.DIGITAL_BANK,
        googlePlayUrl = PLAY_LISTING + "com.krungsri.kept",
        verificationDate = VERIFIED_ON,
    )

    // ---- payment wallets ----------------------------------------------------

    /** TrueMoney — True Money Co. Ltd. */
    val TRUEMONEY = BankInfo(
        id = "truemoney",
        displayName = "TrueMoney",
        packageName = "th.co.truemoney.wallet",
        company = "True Money Co. Ltd.",
        category = BankCategory.PAYMENT_WALLET,
        googlePlayUrl = PLAY_LISTING + "th.co.truemoney.wallet",
        verificationDate = VERIFIED_ON,
    )

    /**
     * Every bank the app knows about, in the order they appear in the
     * bank-selection UI. The runtime probe checks each one; only the banks
     * actually found on the device are selectable.
     */
    val allBanks: List<BankInfo> = listOf(
        K_PLUS,
        SCB_EASY,
        KRUNGTHAI_NEXT,
        BANGKOK_BANK,
        KRUNGSRI,
        TTB_TOUCH,
        MYMO_GSB,
        CIMB_THAI,
        UOB_TMRW,
        DIME,
        MAKE_BY_KBANK,
        KEPT,
        TRUEMONEY,
    )

    /** Find a bank by its stable [BankInfo.id]. */
    fun findById(id: String): BankInfo? = allBanks.firstOrNull { it.id == id }

    /** Find a bank by its Android package name. */
    fun findByPackage(packageName: String): BankInfo? =
        allBanks.firstOrNull { it.packageName == packageName }
}
