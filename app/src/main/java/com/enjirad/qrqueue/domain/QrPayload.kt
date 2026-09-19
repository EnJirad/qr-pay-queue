package com.enjirad.qrqueue.domain

/** Payment network that produced a QR payload. */
enum class QrFormat(val label: String) {
    PROMPTPAY("PromptPay"),
    BILL_PAYMENT("Bill payment"),
}

/** What the payload itself says the money goes to. Never guessed by the app. */
enum class RecipientKind(val label: String) {
    MOBILE_NUMBER("Mobile number"),
    NATIONAL_ID("National / tax ID"),
    EWALLET("e-Wallet ID"),
    MERCHANT("Merchant"),
    BILLER("Biller"),
}

/**
 * The verified contents of one QR payload.
 *
 * Every field is either read from the payload or absent. The app never fills a
 * gap with an invented amount, recipient or reference: [amountSatang] stays null
 * when the QR does not carry an amount (a static PromptPay QR) and the user pays
 * whatever the banking app shows.
 */
data class QrPayload(
    /** The exact decoded payload text, used for duplicate detection. */
    val raw: String,
    val format: QrFormat,
    val recipientKind: RecipientKind,
    /** Human-readable destination, e.g. `081-234-5678` or a merchant name. */
    val recipient: String,
    val merchantName: String? = null,
    val merchantCity: String? = null,
    /** Amount in satang, or null when the QR carries no amount. */
    val amountSatang: Long? = null,
    val currencyCode: String? = null,
    val countryCode: String? = null,
    val reference: String? = null,
    /** EMVCo point of initiation method: "11" static, "12" dynamic. */
    val pointOfInitiationMethod: String? = null,
) {
    val isDynamic: Boolean get() = pointOfInitiationMethod == POINT_OF_INITIATION_DYNAMIC

    companion object {
        const val POINT_OF_INITIATION_DYNAMIC = "12"
    }
}
