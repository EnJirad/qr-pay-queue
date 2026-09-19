package com.enjirad.qrqueue.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentStatusTest {

    @Test
    fun submittedOrWaitingIsNeverCountedAsPaid() {
        // The app must never mark a payment paid just because it was opened,
        // shared or handed to a banking app.
        assertFalse(PaymentStatus.SUBMITTED.isTerminal)
        assertFalse(PaymentStatus.WAITING_CONFIRMATION.isTerminal)
        assertTrue(PaymentStatus.SUBMITTED !in PaymentStatus.completedStates)
        assertTrue(PaymentStatus.WAITING_CONFIRMATION !in PaymentStatus.completedStates)
    }

    @Test
    fun onlyHumanVerifiedStatesCountAsCompleted() {
        assertTrue(PaymentStatus.completedStates.containsAll(
            setOf(PaymentStatus.SUCCESS, PaymentStatus.RECONCILED, PaymentStatus.PAID),
        ))
        assertTrue(PaymentStatus.completedStates.none { it.isError })
    }

    @Test
    fun everyTerminalStateIsEitherSuccessOrError() {
        PaymentStatus.entries
            .filter { it.isTerminal }
            .forEach { status ->
                val isCompleted = status in PaymentStatus.completedStates
                assertTrue(
                    "Terminal state ${status.name} must be a completed or an error state",
                    isCompleted || status.isError,
                )
            }
    }

    @Test
    fun documentedErrorStatesAreFlagged() {
        val expectedErrors = listOf(
            PaymentStatus.INVALID,
            PaymentStatus.RECIPIENT_MISMATCH,
            PaymentStatus.AMOUNT_MISMATCH,
            PaymentStatus.ORDER_NOT_FOUND,
            PaymentStatus.DUPLICATE,
            PaymentStatus.EXPIRED,
            PaymentStatus.PAYMENT_FAILED,
            PaymentStatus.UNKNOWN,
        )
        expectedErrors.forEach { status ->
            assertTrue("${status.name} must be flagged as an error", status.isError)
        }
        assertFalse(PaymentStatus.VALIDATED.isError)
        assertFalse(PaymentStatus.READY.isError)
    }

    @Test
    fun onlyUnpayableProblemStatesAreExcludedFromTheQueue() {
        assertTrue(PaymentStatus.INVALID.isExcludedFromQueue)
        assertTrue(PaymentStatus.DUPLICATE.isExcludedFromQueue)
        assertFalse(PaymentStatus.READY.isExcludedFromQueue)
        assertFalse(PaymentStatus.UNKNOWN.isExcludedFromQueue)
        assertFalse(PaymentStatus.PAYMENT_FAILED.isExcludedFromQueue)
        assertFalse(PaymentStatus.SUCCESS.isExcludedFromQueue)
    }

    @Test
    fun unknownIsProcessableSoTheQueueWaitsForTheUser() {
        assertTrue(PaymentStatus.UNKNOWN.isProcessable)
        assertTrue(PaymentStatus.READY.isProcessable)
        assertTrue(PaymentStatus.SUBMITTED.isProcessable)
        assertTrue(PaymentStatus.WAITING_CONFIRMATION.isProcessable)
        assertFalse(PaymentStatus.SUCCESS.isProcessable)
        assertFalse(PaymentStatus.PAYMENT_FAILED.isProcessable)
        assertFalse(PaymentStatus.INVALID.isProcessable)
    }

    @Test
    fun pendingStatesCoverEveryUnfinishedAmount() {
        assertTrue(PaymentStatus.READY.isPending)
        assertTrue(PaymentStatus.WAITING_CONFIRMATION.isPending)
        assertFalse(PaymentStatus.SUCCESS.isPending)
        assertFalse(PaymentStatus.PAYMENT_FAILED.isPending)
        assertFalse(PaymentStatus.UNKNOWN.isPending)
    }

    @Test
    fun noErrorStateIsEverCountedAsPaid() {
        PaymentStatus.entries.filter { it.isError }.forEach { status ->
            assertFalse("${status.name} must never count as paid", status.isPaid)
        }
    }
}
