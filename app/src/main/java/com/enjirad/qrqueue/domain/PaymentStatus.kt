package com.enjirad.qrqueue.domain

/**
 * Lifecycle of one image in the queue.
 *
 * The app never reads the QR code and never decides whether a payment happened.
 * Two rules are encoded here and must never be weakened:
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
 * Only one item at a time may [holdsHandoff], which is what keeps the app to one
 * payment hand-off at a time.
 *
 * @param label short human-readable name shown in the queue UI.
 */
enum class PaymentStatus(val label: String) {
    /** Imported into the queue, not handed to K PLUS yet. */
    QUEUED("รอทำรายการ"),

    /** Being handed to K PLUS right now. */
    SHARING("กำลังเปิด K PLUS"),

    /** Handed to K PLUS; waiting for the user to pay and confirm. */
    WAITING_USER("รอการยืนยัน"),

    /** The user pressed "ทำรายการเสร็จแล้ว". The only finished state. */
    COMPLETED("เสร็จแล้ว"),

    /** A known, reported error, e.g. a missing image file or K PLUS not accepting the image. */
    FAILED("ผิดพลาด"),

    /** The payment result could not be determined (the app stopped mid-payment). */
    UNKNOWN("ไม่ทราบผล");

    /** True only for the state the user confirmed themselves. */
    val isCompleted: Boolean get() = this == COMPLETED

    /** True while the item still needs work from the user. */
    val isActive: Boolean get() = !isCompleted

    /** True for the states that need the user's attention (shown as error chips). */
    val isError: Boolean get() = this == FAILED || this == UNKNOWN

    /**
     * True while this item owns the single payment hand-off: it is being handed
     * to K PLUS, K PLUS already has it, or its result is not known yet.
     *
     * No second item may enter one of these states while another one holds them,
     * so a batch can never have two payments in flight at once.
     */
    val holdsHandoff: Boolean
        get() = this == SHARING || this == WAITING_USER || this == UNKNOWN

    /** True when a payment hand-off to K PLUS can be started for this item. */
    val canShare: Boolean
        get() = this == QUEUED || this == FAILED || this == WAITING_USER

    /** True when the user owes this item an answer (it was handed to K PLUS). */
    val awaitsUserAnswer: Boolean get() = this == WAITING_USER

    /** True when only an explicit user action may move the item on. */
    val isRetryable: Boolean get() = this == FAILED || this == UNKNOWN

    /**
     * True when sharing this item must be preceded by the double-payment warning:
     * a [WAITING_USER] image may already have been paid inside K PLUS.
     */
    val needsReShareWarning: Boolean get() = this == WAITING_USER

    /**
     * True when sharing this item is safe without an extra double-payment
     * warning: a [QUEUED] item was never handed off, and a [FAILED] hand-off never
     * reached K PLUS at all.
     */
    val canShareWithoutWarning: Boolean get() = canShare && !needsReShareWarning
}
