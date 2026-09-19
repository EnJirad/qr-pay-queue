package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue state machine is the heart of the app, so every rule from the brief
 * is asserted here: the happy path, the failure path, the unknown path, that only
 * the user's own confirmation completes an item, that UNKNOWN never advances or
 * completes on its own, and that only one image can be in K PLUS at a time.
 */
class PaymentQueueTest {

    private fun item(
        id: String,
        position: Int,
        status: PaymentStatus = PaymentStatus.QUEUED,
    ): QueueItem = QueueItem(
        id = id,
        position = position,
        sourceUri = "content://media/$id",
        storedImagePath = "/data/user/0/com.enjirad.qrqueue/files/qrqueue/images/$id.png",
        displayName = "$id.png",
        mimeType = "image/png",
        status = status,
    )

    private fun twoImages(): PaymentQueue = PaymentQueue.create(
        queueId = "queue-1",
        createdAt = 1_000L,
        items = listOf(item("a", 0), item("b", 1)),
    )

    private fun oneImage(): PaymentQueue = PaymentQueue.create(
        queueId = "queue-1",
        createdAt = 1_000L,
        items = listOf(item("a", 0)),
    )

    // ---- order --------------------------------------------------------------

    @Test
    fun importedImagesKeepSelectionOrderAndPositions() {
        val queue = PaymentQueue.create(
            queueId = "queue-1",
            createdAt = 1_000L,
            items = listOf(item("a", 0), item("b", 1), item("c", 2)),
        )

        assertEquals(listOf("a", "b", "c"), queue.items.map { it.id })
        assertEquals(listOf(0, 1, 2), queue.items.map { it.position })
        assertEquals(3, queue.itemCount)
        assertEquals(3, queue.nextPosition)
        assertEquals(PaymentQueue.FIRST_POSITION, 0)
    }

    @Test
    fun appendedImagesTakeTheNextPositionsInOrder() {
        val extended = PaymentQueue.create("queue-1", 1_000L, listOf(item("a", 0)))
            .appendItems(listOf(item("c", 2), item("b", 1)))

        assertEquals(listOf("a", "b", "c"), extended.items.map { it.id })
        assertEquals(3, extended.nextPosition)
    }

    @Test
    fun outOfOrderPositionsAreRepairedOnRestore() {
        val messy = PaymentQueue(
            queueId = "queue-1",
            createdAt = 1_000L,
            items = listOf(item("b", 2), item("a", 0)),
        )

        assertEquals(listOf("a", "b"), messy.normalized().items.map { it.id })
    }

    @Test
    fun anEmptyQueueIsNeverFinished() {
        val empty = PaymentQueue.create("queue-1", 1_000L)

        assertNull(empty.handoffItem)
        assertNull(empty.awaitingAnswerItem)
        assertFalse(empty.finished)
        assertFalse(empty.normalized().finished)
    }

    // ---- happy path ---------------------------------------------------------

    @Test
    fun theHappyPathQueuedSharingWaitingThenCompleted() {
        val sharing = twoImages().startSharing("a", 10L)
        assertEquals(PaymentStatus.SHARING, sharing.item("a")?.status)
        assertEquals("a", sharing.handoffItem?.id)

        val waiting = sharing.shareLaunched("a", 20L)
        assertEquals(PaymentStatus.WAITING_USER, waiting.item("a")?.status)
        assertEquals(0, waiting.completedCount)
        assertEquals("a", waiting.awaitingAnswerItem?.id)

        val completed = waiting.confirmCompleted("a", 30L)
        assertEquals(PaymentStatus.COMPLETED, completed.item("a")?.status)
        assertEquals(30L, completed.item("a")?.updatedAt)
        assertEquals(1, completed.completedCount)
        assertEquals(1, completed.remainingCount)
        assertNull(completed.awaitingAnswerItem)
        // The next image is available, and nothing is shared by itself.
        assertTrue(completed.canStartHandoff("b"))
        assertEquals(PaymentStatus.QUEUED, completed.item("b")?.status)
        assertFalse(completed.finished)
    }

    @Test
    fun anItemIsCompletedOnlyAfterTheUserIsAsked() {
        assertEquals(0, twoImages().confirmCompleted("a").completedCount)
        assertEquals(0, twoImages().startSharing("a").confirmCompleted("a").completedCount)
        // Only a WAITING_USER item answers the user's confirmation prompt.
        assertEquals(
            1,
            twoImages().startSharing("a").shareLaunched("a").confirmCompleted("a").completedCount,
        )
    }

    @Test
    fun theUserCanWorkTheQueueInAnyOrder() {
        // Pay the second image first: the user picks the image, not the app.
        val waiting = twoImages().startSharing("b", 10L).shareLaunched("b", 20L)

        assertEquals("b", waiting.handoffItem?.id)
        assertEquals(PaymentStatus.WAITING_USER, waiting.item("b")?.status)
        assertEquals(PaymentStatus.QUEUED, waiting.item("a")?.status)
        assertFalse(waiting.canStartHandoff("a"))

        val answered = waiting.confirmCompleted("b", 30L)
        assertEquals(PaymentStatus.COMPLETED, answered.item("b")?.status)
        assertEquals(PaymentStatus.QUEUED, answered.item("a")?.status)
        assertTrue(answered.canStartHandoff("a"))
    }

    @Test
    fun theUserSayingTryAgainKeepsTheItemAndDoesNotAdvance() {
        val waiting = twoImages().startSharing("a", 10L).shareLaunched("a", 20L)

        val kept = waiting.keepWaiting("a", 40L)

        assertEquals("a", kept.handoffItem?.id)
        assertEquals(PaymentStatus.WAITING_USER, kept.item("a")?.status)
        assertEquals(40L, kept.item("a")?.updatedAt)
        assertEquals(0, kept.completedCount)
        assertFalse(kept.canStartHandoff("b"))
        assertFalse(kept.finished)

        // Trying to "try again" an item that was never handed off does nothing.
        assertEquals(0L, twoImages().keepWaiting("a").item("a")?.updatedAt)
    }

    // ---- failure path -------------------------------------------------------

    @Test
    fun theFailurePathQueuedSharingThenFailed() {
        val failed = twoImages().startSharing("a", 10L).failItem("a", "the file is missing", 15L)

        assertEquals(PaymentStatus.FAILED, failed.item("a")?.status)
        assertEquals("the file is missing", failed.item("a")?.failureDetail)
        assertEquals(15L, failed.item("a")?.updatedAt)
        assertEquals(1, failed.failedCount)
        assertEquals(0, failed.completedCount)
        assertFalse(failed.finished)
    }

    @Test
    fun aHandOffThatNeverReachedKPlusDoesNotBlockTheRestOfTheQueue() {
        val failed = twoImages().startSharing("a", 10L).failItem("a", "missing", 15L)

        // Nothing was paid, so the user may hand another image over right away.
        assertTrue(failed.canStartHandoff("b"))
        val nextShared = failed.startSharing("b", 20L)
        assertEquals(PaymentStatus.SHARING, nextShared.item("b")?.status)
        assertEquals(PaymentStatus.FAILED, nextShared.item("a")?.status)

        // And the failed image can be retried by the user, going back to QUEUED.
        val retried = failed.retryItem("a", 25L)
        assertEquals(PaymentStatus.QUEUED, retried.item("a")?.status)
        assertNull(retried.item("a")?.failureDetail)
        assertEquals(0, retried.failedCount)
    }

    // ---- unknown path -------------------------------------------------------

    @Test
    fun theUnknownPathQueuedSharingWaitingThenUnknown() {
        val unknown = twoImages()
            .startSharing("a", 10L)
            .shareLaunched("a", 20L)
            .markUnknown("a", "the app stopped", 25L)

        assertEquals(PaymentStatus.UNKNOWN, unknown.item("a")?.status)
        assertEquals("the app stopped", unknown.item("a")?.failureDetail)
        assertEquals(1, unknown.unknownCount)
        assertEquals(0, unknown.completedCount)
        assertEquals("a", unknown.unknownItem?.id)
        assertFalse(unknown.finished)
    }

    @Test
    fun anUnknownResultIsNeverCompletedAutomatically() {
        val unknown = twoImages().startSharing("a").shareLaunched("a").markUnknown("a")

        // The plain confirmation transition is refused while the result is unknown.
        assertEquals(PaymentStatus.UNKNOWN, unknown.confirmCompleted("a").item("a")?.status)
        assertEquals(0, unknown.confirmCompleted("a").completedCount)
        assertFalse(unknown.finished)

        // Only an explicit resolution by the user completes it.
        val resolved = unknown.resolveUnknownCompleted("a", 99L)
        assertEquals(PaymentStatus.COMPLETED, resolved.item("a")?.status)
        assertEquals(1, resolved.completedCount)
    }

    @Test
    fun sharingCannotStartFromUnknownOrCompleted() {
        val unknown = twoImages().startSharing("a").shareLaunched("a").markUnknown("a")
        assertEquals(PaymentStatus.UNKNOWN, unknown.startSharing("a").item("a")?.status)

        val done = oneImage().startSharing("a").shareLaunched("a").confirmCompleted("a")
        assertTrue(done.finished)
        assertTrue(done.startSharing("a").finished)
        assertEquals(PaymentStatus.COMPLETED, done.startSharing("a").item("a")?.status)
    }

    @Test
    fun aFailedOrUnknownItemIsRetriedOnlyOnRequest() {
        val failed = twoImages().startSharing("a").failItem("a", "boom")
        val retried = failed.retryItem("a", 50L)
        assertEquals(PaymentStatus.QUEUED, retried.item("a")?.status)
        assertNull(retried.item("a")?.failureDetail)
        assertEquals(50L, retried.item("a")?.updatedAt)

        val unknown = twoImages().startSharing("a").shareLaunched("a").markUnknown("a")
        assertEquals(PaymentStatus.QUEUED, unknown.retryItem("a").item("a")?.status)

        // Retry is refused for an item the user has not seen fail or go unknown.
        assertEquals(PaymentStatus.QUEUED, twoImages().retryItem("a").item("a")?.status)
        assertEquals(0L, twoImages().retryItem("a").item("a")?.updatedAt)
        val waiting = twoImages().startSharing("a").shareLaunched("a")
        assertEquals(PaymentStatus.WAITING_USER, waiting.retryItem("a").item("a")?.status)
    }

    // ---- one hand-off at a time ---------------------------------------------

    @Test
    fun onlyOneImageCanBeInKPlusAtATime() {
        val waiting = twoImages().startSharing("a", 10L).shareLaunched("a", 20L)

        assertFalse(waiting.canStartHandoff("b"))
        val blocked = waiting.startSharing("b", 30L)
        assertEquals(PaymentStatus.QUEUED, blocked.item("b")?.status)
        assertEquals(PaymentStatus.WAITING_USER, blocked.item("a")?.status)

        // An unresolved result blocks just as firmly: resolving it is the user's job.
        val unknown = waiting.markUnknown("a", "stopped", 30L)
        assertFalse(unknown.canStartHandoff("b"))
        val blockedByUnknown = unknown.startSharing("b", 40L)
        assertEquals(PaymentStatus.QUEUED, blockedByUnknown.item("b")?.status)
        assertEquals(PaymentStatus.UNKNOWN, blockedByUnknown.item("a")?.status)

        // Resolving it frees the queue again.
        assertTrue(unknown.resolveUnknownCompleted("a", 50L).canStartHandoff("b"))
        assertTrue(unknown.retryItem("a", 50L).canStartHandoff("b"))
    }

    // ---- process death ------------------------------------------------------

    @Test
    fun anInterruptedHandOffBecomesUnknownOnRestore() {
        val restored = twoImages().startSharing("a", 10L).resolveInterrupted("app stopped", 60L)

        assertEquals(PaymentStatus.UNKNOWN, restored.item("a")?.status)
        assertEquals("app stopped", restored.item("a")?.failureDetail)
        assertEquals(60L, restored.item("a")?.updatedAt)
        assertEquals(0, restored.completedCount)
        assertFalse(restored.canStartHandoff("b"))
    }

    @Test
    fun anInterruptedWaitingItemAlsoComesBackAsUnknown() {
        val restored = twoImages().startSharing("a").shareLaunched("a").resolveInterrupted()

        assertEquals(PaymentStatus.UNKNOWN, restored.item("a")?.status)
        assertEquals(0, restored.completedCount)
        assertFalse(restored.finished)
    }

    @Test
    fun anInterruptedQueueKeepsEarlierConfirmedResults() {
        val afterFirst = twoImages().startSharing("a").shareLaunched("a").confirmCompleted("a")
        assertEquals(1, afterFirst.completedCount)

        val restored = afterFirst.startSharing("b").shareLaunched("b").resolveInterrupted()

        assertEquals(1, restored.completedCount)
        assertEquals("b", restored.unknownItem?.id)
        assertFalse(restored.canStartHandoff("b"))
        assertEquals(PaymentStatus.COMPLETED, restored.item("a")?.status)
    }

    // ---- completion and counts ---------------------------------------------

    @Test
    fun theQueueFinishesOnlyWhenEveryImageHasBeenConfirmed() {
        val finished = twoImages()
            .startSharing("a").shareLaunched("a").confirmCompleted("a")
            .startSharing("b").shareLaunched("b").confirmCompleted("b")

        assertTrue(finished.finished)
        assertNull(finished.handoffItem)
        assertNull(finished.awaitingAnswerItem)
        assertEquals(2, finished.completedCount)
        assertEquals(0, finished.remainingCount)
        assertEquals(1f, finished.progressFraction, 0.0001f)
    }

    @Test
    fun countsFollowTheItemStatuses() {
        val queue = PaymentQueue.create(
            queueId = "queue-1",
            createdAt = 1_000L,
            items = listOf(
                item("a", 0, PaymentStatus.COMPLETED),
                item("b", 1, PaymentStatus.WAITING_USER),
                item("c", 2, PaymentStatus.FAILED),
                item("d", 3, PaymentStatus.UNKNOWN),
                item("e", 4, PaymentStatus.QUEUED),
            ),
        )

        assertEquals(1, queue.completedCount)
        assertEquals(1, queue.waitingCount)
        assertEquals(1, queue.failedCount)
        assertEquals(1, queue.unknownCount)
        assertEquals(1, queue.queuedCount)
        assertEquals(4, queue.remainingCount)
        assertEquals(0.2f, queue.progressFraction, 0.0001f)
        assertEquals("b", queue.handoffItem?.id)
        assertEquals("b", queue.awaitingAnswerItem?.id)
        assertEquals("d", queue.unknownItem?.id)
        assertFalse(queue.finished)
    }
}
