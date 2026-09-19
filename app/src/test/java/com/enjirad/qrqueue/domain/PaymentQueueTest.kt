package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue state machine is the heart of V0.4, so every rule from the brief is
 * asserted here: the happy path, the failure path, the unknown path, that only
 * the user's own confirmation completes an item, and that UNKNOWN never advances
 * or completes on its own.
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
        assertEquals("a", queue.currentItem?.id)
        assertEquals(3, queue.itemCount)
        assertEquals(3, queue.nextPosition)
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

        assertNull(empty.currentItem)
        assertFalse(empty.finished)
        assertFalse(empty.normalized().finished)
    }

    // ---- happy path ---------------------------------------------------------

    @Test
    fun theHappyPathQueuedSharingWaitingThenCompleted() {
        val queue = twoImages()
            .startSharing(10L)
        assertEquals(PaymentStatus.SHARING, queue.currentItem?.status)

        val waiting = queue.shareLaunched(20L)
        assertEquals(PaymentStatus.WAITING_USER, waiting.currentItem?.status)
        assertEquals(0, waiting.completedCount)

        val completed = waiting.confirmCompleted(30L)
        assertEquals(PaymentStatus.COMPLETED, completed.items[0].status)
        assertEquals(30L, completed.items[0].updatedAt)
        assertEquals(1, completed.completedCount)
        // The next image becomes the current one, ready to be shared.
        assertEquals("b", completed.currentItem?.id)
        assertEquals(PaymentStatus.QUEUED, completed.currentItem?.status)
        assertEquals(1, completed.remainingCount)
        assertFalse(completed.finished)
    }

    @Test
    fun anItemIsCompletedOnlyAfterTheUserIsAsked() {
        assertEquals(0, twoImages().confirmCompleted().completedCount)
        assertEquals(0, twoImages().startSharing().confirmCompleted().completedCount)
        // Only WAITING_USER answers the user's confirmation prompt.
        assertEquals(
            1,
            twoImages().startSharing().shareLaunched().confirmCompleted().completedCount,
        )
    }

    @Test
    fun theUserSayingNotYetKeepsTheItemAndDoesNotAdvance() {
        val waiting = twoImages().startSharing(10L).shareLaunched(20L)

        val notYet = waiting.confirmNotCompleted(40L)

        assertEquals("a", notYet.currentItem?.id)
        assertEquals(PaymentStatus.WAITING_USER, notYet.currentItem?.status)
        assertEquals(0, notYet.completedCount)
        assertEquals(40L, notYet.currentItem?.updatedAt)
        assertFalse(notYet.finished)
    }

    // ---- failure path -------------------------------------------------------

    @Test
    fun theFailurePathQueuedSharingThenFailed() {
        val failed = twoImages().startSharing(10L).failCurrent("the file is missing", 15L)

        assertEquals(PaymentStatus.FAILED, failed.currentItem?.status)
        assertEquals("the file is missing", failed.currentItem?.failureDetail)
        assertEquals(1, failed.failedCount)
        assertEquals(0, failed.completedCount)
        assertFalse(failed.finished)
    }

    // ---- unknown path -------------------------------------------------------

    @Test
    fun theUnknownPathQueuedSharingWaitingThenUnknown() {
        val unknown = twoImages()
            .startSharing(10L)
            .shareLaunched(20L)
            .markUnknown("the app stopped", 25L)

        assertEquals(PaymentStatus.UNKNOWN, unknown.currentItem?.status)
        assertEquals(1, unknown.unknownCount)
        assertEquals(0, unknown.completedCount)
        assertFalse(unknown.finished)
    }

    @Test
    fun anUnknownResultIsNeverCompletedAutomatically() {
        val unknown = twoImages().startSharing().shareLaunched().markUnknown()

        // The plain confirmation transition is refused while the result is unknown.
        assertEquals(PaymentStatus.UNKNOWN, unknown.confirmCompleted().currentItem?.status)
        assertEquals(0, unknown.confirmCompleted().completedCount)
        assertFalse(unknown.finished)

        // Only an explicit resolution by the user completes it.
        val resolved = unknown.resolveUnknownCompleted(99L)
        assertEquals(PaymentStatus.COMPLETED, resolved.items[0].status)
        assertEquals(1, resolved.completedCount)
    }

    @Test
    fun sharingCannotStartFromUnknownOrCompleted() {
        val unknown = twoImages().startSharing().shareLaunched().markUnknown()
        assertEquals(PaymentStatus.UNKNOWN, unknown.startSharing().currentItem?.status)

        val single = PaymentQueue.create("queue-1", 1_000L, listOf(item("a", 0)))
        val done = single.startSharing().shareLaunched().confirmCompleted()
        assertTrue(done.finished)
        assertNull(done.currentItem)
        assertTrue(done.startSharing().finished)
    }

    @Test
    fun aFailedOrUnknownItemIsRetriedOnlyOnRequest() {
        val failed = twoImages().startSharing().failCurrent("boom")
        val retried = failed.retryCurrent(50L)
        assertEquals(PaymentStatus.QUEUED, retried.currentItem?.status)
        assertNull(retried.currentItem?.failureDetail)
        assertEquals(0, retried.failedCount)

        val unknown = twoImages().startSharing().shareLaunched().markUnknown()
        assertEquals(PaymentStatus.QUEUED, unknown.retryCurrent().currentItem?.status)

        // Retry is refused for an item the user has not seen fail.
        assertEquals(PaymentStatus.QUEUED, twoImages().retryCurrent().currentItem?.status)
        val waiting = twoImages().startSharing().shareLaunched()
        assertEquals(PaymentStatus.WAITING_USER, waiting.retryCurrent().currentItem?.status)
    }

    // ---- process death ------------------------------------------------------

    @Test
    fun anInterruptedHandOffBecomesUnknownOnRestore() {
        val restored = twoImages().startSharing(10L).resolveInterrupted("app stopped", 60L)

        assertEquals(PaymentStatus.UNKNOWN, restored.currentItem?.status)
        assertEquals("app stopped", restored.currentItem?.failureDetail)
        assertEquals(0, restored.completedCount)
    }

    @Test
    fun anInterruptedWaitingItemAlsoComesBackAsUnknown() {
        val restored = twoImages().startSharing().shareLaunched().resolveInterrupted()

        assertEquals(PaymentStatus.UNKNOWN, restored.currentItem?.status)
        assertEquals(0, restored.completedCount)
        assertFalse(restored.finished)
    }

    @Test
    fun anInterruptedQueueKeepsEarlierConfirmedResults() {
        val afterFirst = twoImages().startSharing().shareLaunched().confirmCompleted()
        val restored = afterFirst.startSharing().shareLaunched().resolveInterrupted()

        assertEquals(1, restored.completedCount)
        assertEquals("b", restored.currentItem?.id)
        assertEquals(PaymentStatus.UNKNOWN, restored.currentItem?.status)
    }

    // ---- blocking and completion --------------------------------------------

    @Test
    fun aBlockedItemStopsTheQueueInsteadOfBeingSkipped() {
        val queue = PaymentQueue.create(
            queueId = "queue-1",
            createdAt = 1_000L,
            items = listOf(
                item("a", 0, PaymentStatus.COMPLETED),
                item("b", 1, PaymentStatus.UNKNOWN),
                item("c", 2, PaymentStatus.QUEUED),
            ),
        )

        assertEquals("b", queue.currentItem?.id)
        assertFalse(queue.finished)
        assertEquals(2, queue.currentOrdinal)

        val resolved = queue.resolveUnknownCompleted()
        assertEquals("c", resolved.currentItem?.id)
        assertEquals(1, resolved.unknownCount)
    }

    @Test
    fun theQueueFinishesOnlyWhenEveryImageHasBeenConfirmed() {
        val finished = twoImages()
            .startSharing().shareLaunched().confirmCompleted()
            .startSharing().shareLaunched().confirmCompleted()

        assertTrue(finished.finished)
        assertNull(finished.currentItem)
        assertEquals(PaymentQueue.NO_CURRENT_ITEM, finished.currentIndex)
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
    }
}
