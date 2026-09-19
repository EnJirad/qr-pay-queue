package com.enjirad.qrqueue.domain

/**
 * One payment task in the queue.
 *
 * A queue item always comes from one image the user selected. The image is
 * copied into app-private storage ([storedImagePath]); the original gallery
 * image is never moved or deleted. Every payload field is either read from the
 * QR or left null — the app never invents an amount, recipient or reference.
 *
 * @param sourceUri the picker URI this item was imported from.
 * @param storedImagePath app-private copy of the image that is actually used.
 * @param amountSatang amount in satang (1 THB = 100 satang), or null when the
 *   QR does not carry an amount.
 * @param issue set when the item was rejected during validation.
 */
data class QueueItem(
    val id: String,
    val fileName: String,
    val sourceUri: String? = null,
    val storedImagePath: String? = null,
    val mimeType: String? = null,
    val amountSatang: Long? = null,
    val recipient: String? = null,
    val reference: String? = null,
    val status: PaymentStatus = PaymentStatus.DISCOVERED,
    /** Why this item is not payable (invalid QR, duplicate, …). */
    val issue: ValidationIssue? = null,
    /** Diagnostic detail for [issue]; never shown as a payment value. */
    val issueDetail: String? = null,
    /** Which QR format produced this item, e.g. `PromptPay`. */
    val payloadLabel: String? = null,
    /** The decoded payload text, kept for duplicate detection and debugging. */
    val rawPayload: String? = null,
) {
    /** True when the queue knows how much this item is for. */
    val hasKnownAmount: Boolean get() = amountSatang != null

    /** True when this item takes part in the sequential payment run. */
    val isPayable: Boolean get() = !status.isExcludedFromQueue
}
