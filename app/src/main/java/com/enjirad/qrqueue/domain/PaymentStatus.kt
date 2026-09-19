package com.enjirad.qrqueue.domain

/**
 * Lifecycle of a single payment task.
 *
 * These states are the deliberate design from the project brief. Two rules are
 * encoded here and must never be weakened:
 *
 * 1. A QR is never [PAID] merely because the app opened, shared or handed it
 *    to a banking app — only a human-verified result may mark it paid.
 * 2. Error states are never silently retried; they wait for a human decision.
 *
 * @param label short human-readable name shown in the queue UI.
 * @param isError true when the task needs user intervention.
 * @param isTerminal true when the state is a finished outcome (success or failure).
 */
enum class PaymentStatus(
    val label: String,
    val isError: Boolean = false,
    val isTerminal: Boolean = false,
) {
    DISCOVERED("Discovered"),
    DECODED("Decoded"),
    VALIDATED("Validated"),
    READY("Ready"),
    SUBMITTED("Submitted"),
    WAITING_CONFIRMATION("Waiting for confirmation"),

    SUCCESS("Success", isTerminal = true),
    RECONCILED("Reconciled", isTerminal = true),
    PAID("Paid", isTerminal = true),

    INVALID("Invalid QR", isError = true, isTerminal = true),
    RECIPIENT_MISMATCH("Recipient mismatch", isError = true, isTerminal = true),
    AMOUNT_MISMATCH("Amount mismatch", isError = true, isTerminal = true),
    ORDER_NOT_FOUND("Order not found", isError = true, isTerminal = true),
    DUPLICATE("Duplicate", isError = true, isTerminal = true),
    EXPIRED("Expired", isError = true, isTerminal = true),
    PAYMENT_FAILED("Payment failed", isError = true, isTerminal = true),
    UNKNOWN("Unknown", isError = true);

    /**
     * True when the item still needs human attention inside the queue:
     * it can be handed to a banking app, and only an explicit user answer may
     * move it to a final result.
     */
    val isProcessable: Boolean get() = this in processableStates

    /** True while the amount is neither paid, failed nor unknown. */
    val isPending: Boolean get() = this in pendingStates

    /**
     * True when the item can never be paid and must stay out of the queue
     * (a QR that could not be read, or a duplicate of another QR).
     */
    val isExcludedFromQueue: Boolean get() = this in excludedStates

    /** True when a human recorded this payment as done. */
    val isPaid: Boolean get() = this in completedStates

    companion object {
        /** States that count as a human-verified, finished payment. */
        val completedStates: Set<PaymentStatus> = setOf(SUCCESS, RECONCILED, PAID)

        /** States an item can be processed from during a queue run. */
        val processableStates: Set<PaymentStatus> =
            setOf(READY, SUBMITTED, WAITING_CONFIRMATION, UNKNOWN)

        /** States that carry an amount the queue still has to work through. */
        val pendingStates: Set<PaymentStatus> = setOf(
            DISCOVERED,
            DECODED,
            VALIDATED,
            READY,
            SUBMITTED,
            WAITING_CONFIRMATION,
        )

        /** Problem states that are excluded from the payment queue itself. */
        val excludedStates: Set<PaymentStatus> = setOf(INVALID, DUPLICATE)
    }
}
