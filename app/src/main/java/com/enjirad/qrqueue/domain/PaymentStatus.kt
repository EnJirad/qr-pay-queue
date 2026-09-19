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

    companion object {
        /** States that count as a human-verified, finished payment. */
        val completedStates: Set<PaymentStatus> = setOf(SUCCESS, RECONCILED, PAID)
    }
}
