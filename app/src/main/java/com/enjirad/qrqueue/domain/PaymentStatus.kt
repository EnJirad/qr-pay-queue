package com.enjirad.qrqueue.domain

/**
 * Lifecycle of one image in the queue.
 *
 * V0.4 is an image queue plus a hand-off helper: the app never reads the QR code
 * and never decides whether a payment happened. Two rules are encoded here and
 * must never be weakened:
 *
 * 1. An item becomes [COMPLETED] only when the user says so. Sharing an image,
 *    opening K PLUS, or returning from K PLUS never completes anything.
 * 2. [UNKNOWN] is never completed or retried automatically, because the real
 *    payment result is unknown and guessing could pay the same bill twice.
 *
 * The transitions are:
 *
 * ```
 * QUEUED → SHARING → WAITING_USER → COMPLETED
 *                ↘ FAILED              (a known, reported error)
 * WAITING_USER → UNKNOWN               (the app stopped mid-payment)
 * FAILED / UNKNOWN → QUEUED            (only by an explicit user retry)
 * ```
 *
 * @param label short human-readable name shown in the queue UI.
 */
enum class PaymentStatus(val label: String) {
    /** Imported into the queue, not shared yet. */
    QUEUED("Queued"),

    /** Being handed to Android's share system right now. */
    SHARING("Sharing"),

    /** Handed to the share sheet; waiting for the user to pay and confirm. */
    WAITING_USER("Waiting for you"),

    /** The user confirmed the payment is done. The only finished state. */
    COMPLETED("Completed"),

    /** A known, reported error, e.g. a missing image file or a share that could not start. */
    FAILED("Failed"),

    /** The payment result could not be determined (the app stopped mid-payment). */
    UNKNOWN("Unknown");

    /** True only for the state the user confirmed themselves. */
    val isCompleted: Boolean get() = this == COMPLETED

    /** True while the item still needs work from the user. */
    val isActive: Boolean get() = !isCompleted

    /** True for the states that need the user's attention (shown as error chips). */
    val isError: Boolean get() = this == FAILED || this == UNKNOWN

    /**
     * True when sharing this item is safe without an extra double-payment
     * warning: a [QUEUED] item was never shared, and a [FAILED] share never
     * reached the share sheet. [WAITING_USER] and [UNKNOWN] may already have been
     * handed to K PLUS, so re-sharing them always needs an explicit confirmation.
     */
    val canShareWithoutWarning: Boolean get() = this == QUEUED || this == FAILED
}
