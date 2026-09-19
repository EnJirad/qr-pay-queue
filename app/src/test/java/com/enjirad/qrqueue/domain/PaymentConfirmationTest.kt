package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nothing the app or the Android lifecycle does can complete a payment. These
 * tests walk every non-user path — starting the hand-off, the bank app opening,
 * coming back undefined, an interrupted process, a retry — and assert that the
 * item is still unfinished until the user says otherwise.
 */
class PaymentConfirmationTest {

    private fun ready() = testQueue(testItem("a", 0), testItem("b", 1))

    @Test
    fun startingTheHandOffDoesNotCompleteAnything() {
        val sharing = ready().startSharing("a", 10L)

        assertEquals(PaymentStatus.SHARING, sharing.item("a")?.status)
        assertEquals(0, sharing.completedCount)
        assertFalse(sharing.finished)
    }

    @Test
    fun theBankAppOpeningDoesNotCompleteAnything() {
        val awaiting = ready().startSharing("a", 10L).shareLaunched("a", 20L)

        assertEquals(PaymentStatus.AWAITING_USER_CONFIRMATION, awaiting.item("a")?.status)
        assertEquals(0, awaiting.completedCount)
        assertEquals(0, awaiting.completedItems.size)
        assertFalse(awaiting.finished)
    }

    @Test
    fun comingBackWithoutAnsweringDoesNotCompleteAnything() {
        // Returning from the bank app is not a transition in this app at all: the
        // only thing that changes an awaiting item is the user's own decision.
        val stillWaiting = ready()
            .startSharing("a", 10L)
            .shareLaunched("a", 20L)
            .keepWaiting("a", 30L)

        assertEquals(PaymentStatus.AWAITING_USER_CONFIRMATION, stillWaiting.item("a")?.status)
        assertEquals(0, stillWaiting.completedCount)
        assertEquals(0, stillWaiting.completedItems.size)
        assertFalse(stillWaiting.finished)
    }

    @Test
    fun anInterruptedProcessDoesNotCompleteAnything() {
        val restored = ready()
            .startSharing("a", 10L)
            .shareLaunched("a", 20L)
            .resolveInterrupted("process death", 30L)

        assertEquals(PaymentStatus.UNKNOWN, restored.item("a")?.status)
        assertEquals(0, restored.completedCount)
        assertFalse(restored.finished)
    }

    @Test
    fun retryingDoesNotCompleteAnything() {
        val retried = ready().startSharing("a").failItem("a", "boom").retryItem("a", 40L)

        assertEquals(PaymentStatus.READY, retried.item("a")?.status)
        assertEquals(0, retried.completedCount)
    }

    @Test
    fun onlyTheUsersOwnConfirmationCompletesAnItem() {
        val resolved = ready().startSharing("a").shareLaunched("a").confirmCompleted("a", 50L)

        assertEquals(PaymentStatus.COMPLETED, resolved.item("a")?.status)
        assertEquals(50L, resolved.item("a")?.completedAt)
        assertEquals(1, resolved.completedCount)
        assertEquals(listOf("a"), resolved.completedItems.map { it.id })
    }

    @Test
    fun anUnknownResultIsOnlyCompletedByAnExplicitUserDecision() {
        val unknown = ready().startSharing("a").shareLaunched("a").markUnknown("a")

        assertEquals(0, unknown.confirmCompleted("a").completedCount)
        assertEquals(1, unknown.resolveUnknownCompleted("a", 60L).completedCount)
    }

    @Test
    fun attemptsAreRecordedAsWorkflowHistoryForTheQrThatWasUsed() {
        val queue = ready()
            .startSharing("a", 10L)
            .shareLaunched("a", 20L)
            .confirmCompleted("a", 30L)

        val item = queue.item("a")!!
        assertEquals(
            listOf(
                PaymentAttemptResult.STARTED,
                PaymentAttemptResult.LAUNCHED,
                PaymentAttemptResult.COMPLETED,
            ),
            item.attempts.map { attempt -> attempt.result },
        )
        assertTrue(item.attempts.all { attempt -> attempt.paymentItemId == "a" })
        assertTrue(item.attempts.all { attempt -> attempt.qrVersionId == "a-v1" })
        assertEquals(30L, item.lastAttempt?.startedAt)
    }

    @Test
    fun aFailedAttemptIsRecordedWithoutCompletingTheItem() {
        val queue = ready().startSharing("a", 10L).failItem("a", "no bank", 15L)

        val item = queue.item("a")!!
        assertEquals(0, queue.completedCount)
        assertEquals(
            listOf(PaymentAttemptResult.STARTED, PaymentAttemptResult.FAILED),
            item.attempts.map { attempt -> attempt.result },
        )
        assertEquals("no bank", item.lastAttempt?.reason)
    }

    @Test
    fun reportingAnUnusableQrIsRecordedButNothingIsCompleted() {
        val queue = ready()
            .startSharing("a")
            .shareLaunched("a")
            .markQrUnusable("a", "bank rejected", 25L)

        val item = queue.item("a")!!
        assertEquals(0, queue.completedCount)
        assertEquals(PaymentAttemptResult.QR_UNUSABLE, item.lastAttempt?.result)
        assertEquals(QrVersionStatus.UNUSABLE, item.versions.single().status)
    }
}
