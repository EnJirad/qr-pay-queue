package com.enjirad.qrqueue.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The status model is where the product's safety promises live, so the rules are
 * asserted directly: only the user's own confirmation completes an item, only one
 * item may hold the payment hand-off, and an item that may already have reached
 * K PLUS is never shared without a warning.
 */
class PaymentStatusTest {

    @Test
    fun onlyTheUserConfirmedStateCountsAsCompleted() {
        assertTrue(PaymentStatus.COMPLETED.isCompleted)
        listOf(
            PaymentStatus.QUEUED,
            PaymentStatus.SHARING,
            PaymentStatus.WAITING_USER,
            PaymentStatus.FAILED,
            PaymentStatus.UNKNOWN,
        ).forEach { status ->
            assertFalse("${status.name} must not count as completed", status.isCompleted)
        }
    }

    @Test
    fun sharingOrWaitingIsNeverTreatedAsAPayment() {
        // Handing an image to K PLUS is not a payment.
        assertFalse(PaymentStatus.SHARING.isCompleted)
        assertFalse(PaymentStatus.WAITING_USER.isCompleted)
        assertTrue(PaymentStatus.SHARING.isActive)
        assertTrue(PaymentStatus.WAITING_USER.isActive)
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
    fun failedAndUnknownAreTheErrorStates() {
        assertTrue(PaymentStatus.FAILED.isError)
        assertTrue(PaymentStatus.UNKNOWN.isError)
        listOf(
            PaymentStatus.QUEUED,
            PaymentStatus.SHARING,
            PaymentStatus.WAITING_USER,
            PaymentStatus.COMPLETED,
        ).forEach { status ->
            assertFalse("${status.name} is not an error state", status.isError)
        }
    }

    @Test
    fun onlyOneItemCanHoldThePaymentHandOff() {
        assertTrue(PaymentStatus.SHARING.holdsHandoff)
        assertTrue(PaymentStatus.WAITING_USER.holdsHandoff)
        assertTrue(PaymentStatus.UNKNOWN.holdsHandoff)
        assertFalse(PaymentStatus.QUEUED.holdsHandoff)
        // A failed hand-off never reached K PLUS, so nothing is in flight.
        assertFalse(PaymentStatus.FAILED.holdsHandoff)
        assertFalse(PaymentStatus.COMPLETED.holdsHandoff)
    }

    @Test
    fun onlyWaitingForTheUserAsksTheUserAQuestion() {
        assertTrue(PaymentStatus.WAITING_USER.awaitsUserAnswer)
        PaymentStatus.entries
            .filter { it != PaymentStatus.WAITING_USER }
            .forEach { status ->
                assertFalse("${status.name} owes no payment answer", status.awaitsUserAnswer)
            }
    }

    @Test
    fun onlyAFailedOrUnknownResultCanBeRetriedByTheUser() {
        assertTrue(PaymentStatus.FAILED.isRetryable)
        assertTrue(PaymentStatus.UNKNOWN.isRetryable)
        listOf(
            PaymentStatus.QUEUED,
            PaymentStatus.SHARING,
            PaymentStatus.WAITING_USER,
            PaymentStatus.COMPLETED,
        ).forEach { status ->
            assertFalse("${status.name} is not a retry target", status.isRetryable)
        }
    }

    @Test
    fun kPlusCanOnlyBeAskedFromAStateThatMayBeHandedOver() {
        assertTrue(PaymentStatus.QUEUED.canShare)
        assertTrue(PaymentStatus.FAILED.canShare)
        // An already shared image may be sent again, but only after a warning.
        assertTrue(PaymentStatus.WAITING_USER.canShare)
        assertFalse(PaymentStatus.SHARING.canShare)
        // Unknown and completed results must be resolved by the user first.
        assertFalse(PaymentStatus.UNKNOWN.canShare)
        assertFalse(PaymentStatus.COMPLETED.canShare)
    }

    @Test
    fun onlyAnItemThatCannotHaveReachedKPlusIsSharedWithoutAWarning() {
        assertTrue(PaymentStatus.QUEUED.canShareWithoutWarning)
        // A failed hand-off never reached K PLUS, so retrying it is safe.
        assertTrue(PaymentStatus.FAILED.canShareWithoutWarning)
        // These may already have been handed to K PLUS: re-sharing must be explicit.
        assertFalse(PaymentStatus.WAITING_USER.canShareWithoutWarning)
        assertTrue(PaymentStatus.WAITING_USER.needsReShareWarning)
        assertFalse(PaymentStatus.UNKNOWN.canShareWithoutWarning)
        assertFalse(PaymentStatus.SHARING.canShareWithoutWarning)
        assertFalse(PaymentStatus.COMPLETED.canShareWithoutWarning)
    }

    @Test
    fun everyStatusHasALabelForTheQueueScreen() {
        PaymentStatus.entries.forEach { status ->
            assertTrue("${status.name} needs a label", status.label.isNotBlank())
        }
    }
}
