package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Double-payment protection lives in the domain, not only in the button's
 * `enabled` state: hammering "ชำระเงิน" must produce exactly one payment attempt,
 * one hand-off and one Intent launch.
 */
class DoublePaymentTest {

    private fun ready() = testQueue(testItem("a", 0), testItem("b", 1))

    @Test
    fun tappingPayRepeatedlyStartsExactlyOneAttempt() {
        val once = ready().startSharing("a", 10L)
        val hammered = once
            .startSharing("a", 11L)
            .startSharing("a", 12L)
            .startSharing("a", 13L)

        assertEquals(PaymentStatus.SHARING, hammered.item("a")?.status)
        assertEquals(1, hammered.item("a")?.attempts?.size)
        assertEquals(10L, hammered.item("a")?.lastAttemptAt)
        assertEquals(10L, hammered.item("a")?.updatedAt)
    }

    @Test
    fun oneHandOffAtATimeBlocksEveryOtherItem() {
        val sharing = ready().startSharing("a", 10L)

        assertFalse(sharing.canStartHandoff("a"))
        assertFalse(sharing.canStartHandoff("b"))
        assertEquals(PaymentStatus.READY, sharing.startSharing("b", 11L).item("b")?.status)
        assertEquals("a", sharing.handoffItem?.id)
    }

    @Test
    fun theLaunchIsRecordedOnlyOnce() {
        val launched = ready()
            .startSharing("a", 10L)
            .shareLaunched("a", 20L)
            .shareLaunched("a", 21L)

        assertEquals(PaymentStatus.AWAITING_USER_CONFIRMATION, launched.item("a")?.status)
        assertEquals(
            listOf(PaymentAttemptResult.STARTED, PaymentAttemptResult.LAUNCHED),
            launched.item("a")?.attempts?.map { attempt -> attempt.result },
        )
    }

    @Test
    fun confirmingTwiceRecordsOneCompletion() {
        val completed = ready()
            .startSharing("a", 10L)
            .shareLaunched("a", 20L)
            .confirmCompleted("a", 30L)
            .confirmCompleted("a", 31L)

        assertEquals(1, completed.completedCount)
        assertEquals(30L, completed.item("a")?.completedAt)
        assertEquals(
            1,
            completed.item("a")?.attempts?.count { attempt ->
                attempt.result == PaymentAttemptResult.COMPLETED
            },
        )
    }

    @Test
    fun anItemCannotBePaidWhileItsResultIsUnknown() {
        val unknown = ready().startSharing("a").shareLaunched("a").markUnknown("a")

        assertFalse(unknown.canStartHandoff("a"))
        assertFalse(unknown.canStartHandoff("b"))
        assertEquals(PaymentStatus.READY, unknown.startSharing("b").item("b")?.status)
    }

    @Test
    fun anItemCannotBePaidAgainWhileItIsWithTheBank() {
        val awaiting = ready().startSharing("a").shareLaunched("a")

        // Re-sending a QR that may already have been paid is only possible after
        // the user resolves the item, never automatically.
        val retried = awaiting.startSharing("a", 30L)
        assertEquals(PaymentStatus.AWAITING_USER_CONFIRMATION, retried.item("a")?.status)
        assertEquals(
            1,
            retried.item("a")?.attempts?.count { attempt ->
                attempt.result == PaymentAttemptResult.STARTED
            },
        )
    }

    @Test
    fun aFailedHandOffDoesNotConsumeTheOnlyAttempt() {
        val failed = ready().startSharing("a", 10L).failItem("a", "no bank", 15L)

        assertTrue(failed.canStartHandoff("a"))
        val retried = failed.startSharing("a", 20L)

        assertEquals(PaymentStatus.SHARING, retried.item("a")?.status)
        assertEquals(2, retried.item("a")?.attempts?.size)
    }

    @Test
    fun aCompletedItemCanNeverBePaidAgain() {
        val completed = ready()
            .startSharing("a").shareLaunched("a").confirmCompleted("a")

        assertFalse(completed.canStartHandoff("a"))
        assertEquals(PaymentStatus.COMPLETED, completed.startSharing("a", 99L).item("a")?.status)
        assertEquals(
            1,
            completed.item("a")?.attempts?.count { attempt ->
                attempt.result == PaymentAttemptResult.STARTED
            },
        )
    }
}
