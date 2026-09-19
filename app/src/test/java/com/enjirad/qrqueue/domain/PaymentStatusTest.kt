package com.enjirad.qrqueue.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The status model is where the product's safety promises live, so the rules are
 * asserted directly: only the user's own confirmation completes an item, and an
 * item that may already have reached K PLUS is never shared without a warning.
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
        // Handing an image to the share sheet is not a payment.
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
    fun onlyAnItemThatCannotHaveReachedKPlusIsSharedWithoutAWarning() {
        assertTrue(PaymentStatus.QUEUED.canShareWithoutWarning)
        // A failed share never reached the share sheet, so retrying it is safe.
        assertTrue(PaymentStatus.FAILED.canShareWithoutWarning)
        // These may already have been handed to K PLUS: re-sharing them must be explicit.
        assertFalse(PaymentStatus.WAITING_USER.canShareWithoutWarning)
        assertFalse(PaymentStatus.UNKNOWN.canShareWithoutWarning)
        assertFalse(PaymentStatus.SHARING.canShareWithoutWarning)
        assertFalse(PaymentStatus.COMPLETED.canShareWithoutWarning)
    }
}
