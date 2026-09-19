package com.enjirad.qrqueue.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The status model is where the product's safety promises live, so the rules are
 * asserted directly: only the user's own confirmation completes an item, only one
 * item may hold the payment hand-off, and an item that may already have reached
 * the bank is never shared again without the user asking for it.
 */
class PaymentStatusTest {

    @Test
    fun onlyTheUserConfirmedStateCountsAsCompleted() {
        assertTrue(PaymentStatus.COMPLETED.isCompleted)
        PaymentStatus.entries
            .filter { it != PaymentStatus.COMPLETED }
            .forEach { status ->
                assertFalse("${status.name} must not count as completed", status.isCompleted)
            }
    }

    @Test
    fun handingTheImageToTheBankIsNeverAPayment() {
        assertFalse(PaymentStatus.SHARING.isCompleted)
        assertFalse(PaymentStatus.AWAITING_USER_CONFIRMATION.isCompleted)
        assertTrue(PaymentStatus.SHARING.isActive)
        assertTrue(PaymentStatus.AWAITING_USER_CONFIRMATION.isActive)
    }

    @Test
    fun onlyCompletedIsInactive() {
        assertFalse(PaymentStatus.COMPLETED.isActive)
        PaymentStatus.entries
            .filter { it != PaymentStatus.COMPLETED }
            .forEach { status ->
                assertTrue("${status.name} must still need the user", status.isActive)
            }
    }

    @Test
    fun onlyTheThreeRecoverableStatesAreProblems() {
        assertTrue(PaymentStatus.UNKNOWN.isProblem)
        assertTrue(PaymentStatus.REQUIRES_QR_REPLACEMENT.isProblem)
        assertTrue(PaymentStatus.FAILED.isProblem)
        listOf(
            PaymentStatus.READY,
            PaymentStatus.SHARING,
            PaymentStatus.AWAITING_USER_CONFIRMATION,
            PaymentStatus.COMPLETED,
        ).forEach { status ->
            assertFalse("${status.name} is not a problem for the badge", status.isProblem)
        }
    }

    @Test
    fun onlyOneItemCanHoldThePaymentHandOff() {
        assertTrue(PaymentStatus.SHARING.holdsHandoff)
        assertTrue(PaymentStatus.AWAITING_USER_CONFIRMATION.holdsHandoff)
        assertTrue(PaymentStatus.UNKNOWN.holdsHandoff)
        assertFalse(PaymentStatus.READY.holdsHandoff)
        assertFalse(PaymentStatus.REQUIRES_QR_REPLACEMENT.holdsHandoff)
        // A failed hand-off never reached the bank, so nothing is in flight.
        assertFalse(PaymentStatus.FAILED.holdsHandoff)
        assertFalse(PaymentStatus.COMPLETED.holdsHandoff)
    }

    @Test
    fun onlyTheAwaitingStateAsksTheUserAQuestion() {
        assertTrue(PaymentStatus.AWAITING_USER_CONFIRMATION.awaitsUserAnswer)
        PaymentStatus.entries
            .filter { it != PaymentStatus.AWAITING_USER_CONFIRMATION }
            .forEach { status ->
                assertFalse("${status.name} owes no payment answer", status.awaitsUserAnswer)
            }
    }

    @Test
    fun onlyAFailedOrUnknownResultCanBeRetriedByTheUser() {
        assertTrue(PaymentStatus.FAILED.isRetryable)
        assertTrue(PaymentStatus.UNKNOWN.isRetryable)
        listOf(
            PaymentStatus.READY,
            PaymentStatus.SHARING,
            PaymentStatus.AWAITING_USER_CONFIRMATION,
            PaymentStatus.REQUIRES_QR_REPLACEMENT,
            PaymentStatus.COMPLETED,
        ).forEach { status ->
            assertFalse("${status.name} is not a retry target", status.isRetryable)
        }
    }

    @Test
    fun theBankCanOnlyBeAskedFromAStateThatMayBeHandedOver() {
        assertTrue(PaymentStatus.READY.canShare)
        assertTrue(PaymentStatus.FAILED.canShare)
        assertFalse(PaymentStatus.SHARING.canShare)
        // Unknown, awaiting and unusable-QR results must be resolved by the user first.
        assertFalse(PaymentStatus.UNKNOWN.canShare)
        assertFalse(PaymentStatus.AWAITING_USER_CONFIRMATION.canShare)
        assertFalse(PaymentStatus.REQUIRES_QR_REPLACEMENT.canShare)
        assertFalse(PaymentStatus.COMPLETED.canShare)
    }

    @Test
    fun theQrCanBeReplacedExactlyWhereTheUserReportsAProblem() {
        assertTrue(PaymentStatus.REQUIRES_QR_REPLACEMENT.canReplaceQr)
        assertTrue(PaymentStatus.FAILED.canReplaceQr)
        assertTrue(PaymentStatus.UNKNOWN.canReplaceQr)
        listOf(
            PaymentStatus.READY,
            PaymentStatus.SHARING,
            PaymentStatus.AWAITING_USER_CONFIRMATION,
            PaymentStatus.COMPLETED,
        ).forEach { status ->
            assertFalse("${status.name} has nothing to replace", status.canReplaceQr)
        }
    }

    @Test
    fun onlyRequiresQrReplacementIsFlaggedForTheQueue() {
        assertTrue(PaymentStatus.REQUIRES_QR_REPLACEMENT.requiresQrReplacement)
        PaymentStatus.entries
            .filter { it != PaymentStatus.REQUIRES_QR_REPLACEMENT }
            .forEach { status ->
                assertFalse("${status.name} does not need a new QR", status.requiresQrReplacement)
            }
    }

    @Test
    fun everyStatusHasALabelForTheQueueScreen() {
        PaymentStatus.entries.forEach { status ->
            assertTrue("${status.name} needs a label", status.label.isNotBlank())
        }
    }
}
