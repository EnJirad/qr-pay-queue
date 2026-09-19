package com.enjirad.qrqueue.domain

/**
 * Lifecycle of one Payment Item (one logical payment task in the queue).
 *
 * The app never reads the QR code and never decides whether a payment happened.
 * Three rules are encoded here and must never be weakened:
 *
 * 1. An item becomes [COMPLETED] only when the user says so. Sharing an image,
 *    opening the bank app, returning to this app, or the activity resuming never
 *    completes anything.
 * 2. [UNKNOWN] is never completed, retried or shared automatically, because the
 *    real result is unknown and guessing could pay the same bill twice.
 * 3. [REQUIRES_QR_REPLACEMENT] keeps the item in the queue: a QR the user says is
 *    unusable is replaced, never deleted.
 *
 * The transitions are:
 *
 * ```
 * READY -> SHARING -> AWAITING_USER_CONFIRMATION -> COMPLETED
 *            |                       |
 *            v                       v
 *          FAILED                 UNKNOWN
 *                                    |
 *   AWAITING / UNKNOWN / FAILED -> REQUIRES_QR_REPLACEMENT -> READY (new QR)
 *                    FAILED / UNKNOWN -> READY (explicit user retry)
 * ```
 *
 * Only one item at a time may [holdsHandoff], which keeps the app to one payment
 * hand-off at a time.
 *
 * @param label short human-readable name shown in the queue UI.
 */
enum class PaymentStatus(val label: String) {
    /** Imported and ready to be handed to the selected bank. */
    READY("รอชำระ"),

    /** Being handed to the selected bank right now. */
    SHARING("กำลังเปิดแอปธนาคาร"),

    /** Handed to the bank; the user must confirm the result themselves. */
    AWAITING_USER_CONFIRMATION("รอการยืนยัน"),

    /** The user pressed "ทำรายการเสร็จแล้ว". The only finished state. */
    COMPLETED("ชำระแล้ว"),

    /** A known, reported failure in this app (missing file, intent, bank gone). */
    FAILED("ผิดพลาด"),

    /** The real result could not be determined (the app stopped mid-payment). */
    UNKNOWN("ไม่ทราบผล"),

    /** The user reported that this QR cannot be used and wants to replace it. */
    REQUIRES_QR_REPLACEMENT("ต้องเปลี่ยน QR");

    /** True only for the state the user confirmed themselves. */
    val isCompleted: Boolean get() = this == COMPLETED

    /** True while the item still needs work from the user. */
    val isActive: Boolean get() = !isCompleted

    /**
     * True for the states the ปัญหา (problem) tab collects: an unresolved result,
     * a QR that must be replaced, or a reported failure. Completed items and
     * ordinary [READY] items are never problems.
     */
    val isProblem: Boolean
        get() = this == UNKNOWN || this == REQUIRES_QR_REPLACEMENT || this == FAILED

    /** Alias used by the UI when it colours a status chip as an error. */
    val isError: Boolean get() = isProblem

    /**
     * True while this item owns the single payment hand-off: it is being handed
     * to the bank, the bank already has it, or its result is not known yet.
     *
     * No second item may enter one of these states while another one holds them,
     * so a batch can never have two payments in flight at once. A [FAILED]
     * hand-off never reached the bank, so it does not hold the hand-off.
     */
    val holdsHandoff: Boolean
        get() = this == SHARING || this == AWAITING_USER_CONFIRMATION || this == UNKNOWN

    /** True when a payment hand-off to the selected bank can be started. */
    val canShare: Boolean get() = this == READY || this == FAILED

    /** True when the user owes this item an answer (it was handed to the bank). */
    val awaitsUserAnswer: Boolean get() = this == AWAITING_USER_CONFIRMATION

    /** True when the QR must be replaced before this item can be paid again. */
    val requiresQrReplacement: Boolean get() = this == REQUIRES_QR_REPLACEMENT

    /** True when only an explicit user action may move the item on. */
    val isRetryable: Boolean get() = this == FAILED || this == UNKNOWN

    /** True when the user may replace the current QR image of this item. */
    val canReplaceQr: Boolean
        get() = this == REQUIRES_QR_REPLACEMENT || this == FAILED || this == UNKNOWN
}
