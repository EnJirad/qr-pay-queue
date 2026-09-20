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
 * completes on its own, and that only one item can be in the bank app at a time.
 */
class PaymentQueueTest {

    private fun twoItems(): PaymentQueue = testQueue(testItem("a", 0), testItem("b", 1))

    private fun oneItem(): PaymentQueue = testQueue(testItem("a", 0))

    // ---- order --------------------------------------------------------------

    @Test
    fun importedImagesKeepSelectionOrderAndPositions() {
        val queue = testQueue(testItem("a", 0), testItem("b", 1), testItem("c", 2))

        assertEquals(listOf("a", "b", "c"), queue.items.map { it.id })
        assertEquals(listOf(0, 1, 2), queue.items.map { it.position })
        assertEquals(3, queue.itemCount)
        assertEquals(3, queue.nextPosition)
        assertEquals(PaymentQueue.FIRST_POSITION, 0)
    }

    @Test
    fun appendedImagesTakeTheNextPositionsInOrder() {
        val extended = testQueue(testItem("a", 0))
            .appendItems(listOf(testItem("c", 2), testItem("b", 1)))

        assertEquals(listOf("a", "b", "c"), extended.items.map { it.id })
        assertEquals(3, extended.nextPosition)
    }

    @Test
    fun outOfOrderPositionsAreRepairedOnRestore() {
        val messy = PaymentQueue(
            queueId = "queue-1",
            createdAt = 1_000L,
            items = listOf(testItem("b", 2), testItem("a", 0)),
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
        assertNull(empty.nextActionItem)
    }

    // ---- happy path ---------------------------------------------------------

    @Test
    fun theHappyPathReadySharingAwaitingThenCompleted() {
        val sharing = twoItems().startSharing("a", 10L)
        assertEquals(PaymentStatus.SHARING, sharing.item("a")?.status)
        assertEquals("a", sharing.handoffItem?.id)

        val awaiting = sharing.shareLaunched("a", 20L)
        assertEquals(PaymentStatus.AWAITING_USER_CONFIRMATION, awaiting.item("a")?.status)
        assertEquals(0, awaiting.completedCount)
        assertEquals("a", awaiting.awaitingAnswerItem?.id)

        val completed = awaiting.confirmCompleted("a", 30L)
        assertEquals(PaymentStatus.COMPLETED, completed.item("a")?.status)
        assertEquals(30L, completed.item("a")?.updatedAt)
        assertEquals(30L, completed.item("a")?.completedAt)
        assertEquals(1, completed.completedCount)
        assertEquals(1, completed.remainingCount)
        assertNull(completed.awaitingAnswerItem)
        // The next item is available, and nothing is shared by itself.
        assertTrue(completed.canStartHandoff("b"))
        assertEquals(PaymentStatus.READY, completed.item("b")?.status)
        assertFalse(completed.finished)
    }

    @Test
    fun anItemIsCompletedOnlyAfterTheUserIsAsked() {
        assertEquals(0, twoItems().confirmCompleted("a").completedCount)
        assertEquals(0, twoItems().startSharing("a").confirmCompleted("a").completedCount)
        // Only an awaiting item answers the user's confirmation prompt.
        assertEquals(
            1,
            twoItems().startSharing("a").shareLaunched("a").confirmCompleted("a").completedCount,
        )
    }

    @Test
    fun theUserCanWorkTheQueueInAnyOrder() {
        // Pay the second item first: the user picks the item, not the app.
        val awaiting = twoItems().startSharing("b", 10L).shareLaunched("b", 20L)

        assertEquals("b", awaiting.handoffItem?.id)
        assertEquals(PaymentStatus.AWAITING_USER_CONFIRMATION, awaiting.item("b")?.status)
        assertEquals(PaymentStatus.READY, awaiting.item("a")?.status)
        assertFalse(awaiting.canStartHandoff("a"))

        val answered = awaiting.confirmCompleted("b", 30L)
        assertEquals(PaymentStatus.COMPLETED, answered.item("b")?.status)
        assertEquals(PaymentStatus.READY, answered.item("a")?.status)
        assertTrue(answered.canStartHandoff("a"))
    }

    @Test
    fun theUserSayingNotSureYetKeepsTheItemOpenAndDoesNotAdvance() {
        val awaiting = twoItems().startSharing("a", 10L).shareLaunched("a", 20L)

        val kept = awaiting.keepWaiting("a", 40L)

        assertEquals("a", kept.handoffItem?.id)
        assertEquals(PaymentStatus.AWAITING_USER_CONFIRMATION, kept.item("a")?.status)
        assertEquals(40L, kept.item("a")?.updatedAt)
        assertEquals(0, kept.completedCount)
        assertFalse(kept.canStartHandoff("b"))
        assertFalse(kept.finished)

        // Trying to keep an item waiting that was never handed off does nothing.
        assertEquals(0L, twoItems().keepWaiting("a").item("a")?.updatedAt)
    }

    // ---- failure path -------------------------------------------------------

    @Test
    fun theFailurePathReadySharingThenFailed() {
        val failed = twoItems().startSharing("a", 10L).failItem("a", "the file is missing", 15L)

        assertEquals(PaymentStatus.FAILED, failed.item("a")?.status)
        assertEquals("the file is missing", failed.item("a")?.failureDetail)
        assertEquals(15L, failed.item("a")?.updatedAt)
        assertEquals(1, failed.failedCount)
        assertEquals(0, failed.completedCount)
        assertFalse(failed.finished)
    }

    @Test
    fun aHandOffThatNeverReachedTheBankDoesNotBlockTheRestOfTheQueue() {
        val failed = twoItems().startSharing("a", 10L).failItem("a", "missing", 15L)

        // Nothing was paid, so the user may hand another item over right away.
        assertTrue(failed.canStartHandoff("b"))
        val nextShared = failed.startSharing("b", 20L)
        assertEquals(PaymentStatus.SHARING, nextShared.item("b")?.status)
        assertEquals(PaymentStatus.FAILED, nextShared.item("a")?.status)

        // And the failed item can be retried by the user, going back to READY.
        val retried = failed.retryItem("a", 25L)
        assertEquals(PaymentStatus.READY, retried.item("a")?.status)
        assertNull(retried.item("a")?.failureDetail)
        assertEquals(0, retried.failedCount)
    }

    @Test
    fun aReadyItemCanFailBeforeAnyHandOff() {
        val failed = twoItems().failItem("a", "no bank selected", 12L)

        assertEquals(PaymentStatus.FAILED, failed.item("a")?.status)
        assertEquals("no bank selected", failed.item("a")?.failureDetail)
        assertEquals(0, failed.unknownCount)
    }

    // ---- launch failure (V0.9 §1) -------------------------------------------

    @Test
    fun aLaunchThatNeverReachedTheBankKeepsTheItemAndItsFourActions() {
        val awaiting = twoItems().startSharing("a", 10L).shareLaunched("a", 20L)

        val failedLaunch = awaiting.recordLaunchFailed("a", "the bank app is not installed", 30L)

        // The rail and its retry action must survive a launch the bank never
        // received: the item stays the one the user owes an answer for.
        assertEquals(
            PaymentStatus.AWAITING_USER_CONFIRMATION,
            failedLaunch.item("a")?.status,
        )
        assertEquals("a", failedLaunch.awaitingAnswerItem?.id)
        assertEquals("a", failedLaunch.handoffItem?.id)
        assertEquals("the bank app is not installed", failedLaunch.item("a")?.failureDetail)
        assertEquals(30L, failedLaunch.item("a")?.updatedAt)
        assertEquals(
            PaymentAttemptResult.FAILED,
            failedLaunch.item("a")?.attempts?.last()?.result,
        )

        // Nothing was paid, nothing became a problem, and the queue still waits on
        // this item instead of moving on by itself.
        assertEquals(0, failedLaunch.completedCount)
        assertEquals(0, failedLaunch.failedCount)
        assertEquals(0, failedLaunch.problemCount)
        assertEquals(PaymentStatus.READY, failedLaunch.item("b")?.status)
        assertFalse(failedLaunch.canStartHandoff("b"))
        assertFalse(failedLaunch.finished)

        // ↻ retries the same Payment Item with the same QR version: the hand-off
        // starts again (a fresh attempt) and the item owns the user's answer once
        // more — the same two transitions the ViewModel runs on a tap.
        val retrying = failedLaunch.retryShare("a", 40L)
        assertEquals(PaymentStatus.SHARING, retrying.item("a")?.status)

        val awaitingAgain = retrying.shareLaunched("a", 41L)
        assertEquals(
            PaymentStatus.AWAITING_USER_CONFIRMATION,
            awaitingAgain.item("a")?.status,
        )
        assertEquals("a", awaitingAgain.item("a")?.id)
        assertEquals(1, awaitingAgain.item("a")!!.versions.size)
        assertEquals(0, awaitingAgain.completedCount)
        assertEquals(0, awaitingAgain.problemCount)
        assertFalse(awaitingAgain.finished)
    }

    @Test
    fun aLaunchFailureIsOnlyRecordedWhileTheItemWaitsForAnAnswer() {
        // A READY item never launched, so a stale callback is refused outright.
        assertEquals(0, twoItems().recordLaunchFailed("a", "stale", 10L).item("a")!!.attempts.size)

        // A completed item is resolved: a late failure cannot reopen it.
        val completed = twoItems()
            .startSharing("a", 10L)
            .shareLaunched("a", 20L)
            .confirmCompleted("a", 30L)

        val afterLateCallback = completed.recordLaunchFailed("a", "stale", 40L)

        assertEquals(PaymentStatus.COMPLETED, afterLateCallback.item("a")?.status)
        assertEquals(1, afterLateCallback.completedCount)
        assertNull(afterLateCallback.item("a")?.failureDetail)
    }

    // ---- unknown path -------------------------------------------------------

    @Test
    fun theUnknownPathReadySharingAwaitingThenUnknown() {
        val unknown = twoItems()
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
        val unknown = twoItems().startSharing("a").shareLaunched("a").markUnknown("a")

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
        val unknown = twoItems().startSharing("a").shareLaunched("a").markUnknown("a")
        assertEquals(PaymentStatus.UNKNOWN, unknown.startSharing("a").item("a")?.status)

        val done = oneItem().startSharing("a").shareLaunched("a").confirmCompleted("a")
        assertTrue(done.finished)
        assertTrue(done.startSharing("a").finished)
        assertEquals(PaymentStatus.COMPLETED, done.startSharing("a").item("a")?.status)
    }

    @Test
    fun aFailedOrUnknownItemIsRetriedOnlyOnRequest() {
        val failed = twoItems().startSharing("a").failItem("a", "boom")
        val retried = failed.retryItem("a", 50L)
        assertEquals(PaymentStatus.READY, retried.item("a")?.status)
        assertNull(retried.item("a")?.failureDetail)
        assertEquals(50L, retried.item("a")?.updatedAt)

        val unknown = twoItems().startSharing("a").shareLaunched("a").markUnknown("a")
        assertEquals(PaymentStatus.READY, unknown.retryItem("a").item("a")?.status)

        // Retry is refused for an item the user has not seen fail or go unknown.
        assertEquals(PaymentStatus.READY, twoItems().retryItem("a").item("a")?.status)
        assertEquals(0L, twoItems().retryItem("a").item("a")?.updatedAt)
        val awaiting = twoItems().startSharing("a").shareLaunched("a")
        assertEquals(
            PaymentStatus.AWAITING_USER_CONFIRMATION,
            awaiting.retryItem("a").item("a")?.status,
        )
    }

    // ---- one hand-off at a time ---------------------------------------------

    @Test
    fun onlyOneItemCanBeInTheBankAppAtATime() {
        val awaiting = twoItems().startSharing("a", 10L).shareLaunched("a", 20L)

        assertFalse(awaiting.canStartHandoff("b"))
        val blocked = awaiting.startSharing("b", 30L)
        assertEquals(PaymentStatus.READY, blocked.item("b")?.status)
        assertEquals(PaymentStatus.AWAITING_USER_CONFIRMATION, blocked.item("a")?.status)

        // An unresolved result blocks just as firmly: resolving it is the user's job.
        val unknown = awaiting.markUnknown("a", "stopped", 30L)
        assertFalse(unknown.canStartHandoff("b"))
        val blockedByUnknown = unknown.startSharing("b", 40L)
        assertEquals(PaymentStatus.READY, blockedByUnknown.item("b")?.status)
        assertEquals(PaymentStatus.UNKNOWN, blockedByUnknown.item("a")?.status)

        // Resolving it frees the queue again.
        assertTrue(unknown.resolveUnknownCompleted("a", 50L).canStartHandoff("b"))
        assertTrue(unknown.retryItem("a", 50L).canStartHandoff("b"))
    }

    // ---- process death ------------------------------------------------------

    @Test
    fun anInterruptedHandOffBecomesUnknownOnRestore() {
        val restored = twoItems().startSharing("a", 10L).resolveInterrupted("app stopped", 60L)

        assertEquals(PaymentStatus.UNKNOWN, restored.item("a")?.status)
        assertEquals("app stopped", restored.item("a")?.failureDetail)
        assertEquals(60L, restored.item("a")?.updatedAt)
        assertEquals(0, restored.completedCount)
        assertFalse(restored.canStartHandoff("b"))
    }

    @Test
    fun anInterruptedAwaitingItemAlsoComesBackAsUnknown() {
        val restored = twoItems().startSharing("a").shareLaunched("a").resolveInterrupted()

        assertEquals(PaymentStatus.UNKNOWN, restored.item("a")?.status)
        assertEquals(0, restored.completedCount)
        assertFalse(restored.finished)
    }

    @Test
    fun anInterruptedQueueKeepsEarlierConfirmedResults() {
        val afterFirst = twoItems().startSharing("a").shareLaunched("a").confirmCompleted("a")
        assertEquals(1, afterFirst.completedCount)

        val restored = afterFirst.startSharing("b").shareLaunched("b").resolveInterrupted()

        assertEquals(1, restored.completedCount)
        assertEquals("b", restored.unknownItem?.id)
        assertFalse(restored.canStartHandoff("b"))
        assertEquals(PaymentStatus.COMPLETED, restored.item("a")?.status)
    }

    // ---- completion and counts ---------------------------------------------

    @Test
    fun theQueueFinishesOnlyWhenEveryItemHasBeenConfirmed() {
        val finished = twoItems()
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
        val queue = testQueue(
            testItem("a", 0, PaymentStatus.COMPLETED),
            testItem("b", 1, PaymentStatus.AWAITING_USER_CONFIRMATION),
            testItem("c", 2, PaymentStatus.FAILED),
            testItem("d", 3, PaymentStatus.UNKNOWN),
            testItem("e", 4, PaymentStatus.READY),
            testItem("f", 5, PaymentStatus.REQUIRES_QR_REPLACEMENT),
        )

        assertEquals(1, queue.completedCount)
        assertEquals(1, queue.awaitingCount)
        assertEquals(1, queue.failedCount)
        assertEquals(1, queue.unknownCount)
        assertEquals(1, queue.readyCount)
        assertEquals(1, queue.requiresReplacementCount)
        assertEquals(5, queue.remainingCount)
        assertEquals(3, queue.problemCount)
        assertEquals(1f / 6f, queue.progressFraction, 0.0001f)
        assertEquals("b", queue.handoffItem?.id)
        assertEquals("b", queue.awaitingAnswerItem?.id)
        assertEquals("d", queue.unknownItem?.id)
        assertFalse(queue.finished)
    }

    @Test
    fun theProblemItemsAreOnlyTheUnresolvedOnesInQueueOrder() {
        val queue = testQueue(
            testItem("a", 0, PaymentStatus.COMPLETED),
            testItem("b", 1, PaymentStatus.UNKNOWN),
            testItem("c", 2, PaymentStatus.READY),
            testItem("d", 3, PaymentStatus.REQUIRES_QR_REPLACEMENT),
            testItem("e", 4, PaymentStatus.FAILED),
        )

        assertEquals(listOf("b", "d", "e"), queue.problemItems.map { it.id })
        assertEquals(listOf("a"), queue.completedItems.map { it.id })
    }

    // ---- home priority ------------------------------------------------------

    @Test
    fun homeOffersUnresolvedResultsBeforeAnythingElse() {
        val queue = testQueue(
            testItem("ready-1", 0),
            testItem("failed-1", 1, PaymentStatus.FAILED),
            testItem("qr-1", 2, PaymentStatus.REQUIRES_QR_REPLACEMENT),
            testItem("unknown-1", 3, PaymentStatus.UNKNOWN),
        )

        // UNKNOWN first, then the QR that must be replaced, then FAILED, then READY.
        assertEquals("unknown-1", queue.nextActionItem?.id)

        // Retrying the unknown item hands the priority back to the next problem,
        // still in queue order — the app never silently reorders the queue.
        val retried = queue.retryItem("unknown-1")
        assertEquals(PaymentStatus.READY, retried.item("unknown-1")?.status)
        assertEquals("qr-1", retried.nextActionItem?.id)

        // Replacing that QR resolves it too, so the failed item becomes next.
        val replaced = retried.replaceCurrentQr("qr-1", testVersion("qr-1-v2", "qr-1", 2), 2L)
        assertEquals(PaymentStatus.READY, replaced.item("qr-1")?.status)
        assertEquals("failed-1", replaced.nextActionItem?.id)
        assertEquals("ready-1", replaced.retryItem("failed-1").nextActionItem?.id)
    }

    @Test
    fun homeFallsBackToTheNextReadyItemInQueueOrder() {
        val queue = testQueue(
            testItem("completed-1", 0, PaymentStatus.COMPLETED),
            testItem("ready-1", 1),
            testItem("ready-2", 2),
        )

        assertEquals("ready-1", queue.nextActionItem?.id)
        assertEquals(
            "ready-2",
            queue.startSharing("ready-1").shareLaunched("ready-1").confirmCompleted("ready-1")
                .nextActionItem?.id,
        )
    }

    @Test
    fun anItemAwaitingConfirmationIsNotOfferedAsTheNextNewAction() {
        val queue = testQueue(testItem("a", 0)).startSharing("a").shareLaunched("a")

        assertNull(queue.nextActionItem)
        assertEquals("a", queue.awaitingAnswerItem?.id)
    }


    // ---- retryShare (V0.7 §11–§15) ----

    @Test
    fun `retryShare moves AWAITING to SHARING with same QR`() {
        val item = testItem("a", 0)
        val q = PaymentQueue.create("q", 100, listOf(item))
            .startSharing("a", 200)
            .shareLaunched("a", 300)
        assertEquals(PaymentStatus.AWAITING_USER_CONFIRMATION, q.item("a")!!.status)
        val retried = q.retryShare("a", 400)
        assertEquals(PaymentStatus.SHARING, retried.item("a")!!.status)
        assertEquals(item.currentVersionId, retried.item("a")!!.currentVersionId)
    }

    @Test
    fun `retryShare creates new PaymentAttempt`() {
        val item = testItem("a", 0)
        val q = PaymentQueue.create("q", 100, listOf(item))
            .startSharing("a", 200)
            .shareLaunched("a", 300)
        val before = q.item("a")!!.attempts.size
        val retried = q.retryShare("a", 400)
        assertEquals(before + 1, retried.item("a")!!.attempts.size)
    }

    @Test
    fun `retryShare does not create new PaymentItem`() {
        val item = testItem("a", 0)
        val q = PaymentQueue.create("q", 100, listOf(item))
            .startSharing("a", 200)
            .shareLaunched("a", 300)
        val retried = q.retryShare("a", 400)
        assertEquals(1, retried.itemCount)
        assertEquals("a", retried.items[0].id)
    }

    @Test
    fun `retryShare does not create new QR Version`() {
        val item = testItem("a", 0)
        val q = PaymentQueue.create("q", 100, listOf(item))
            .startSharing("a", 200)
            .shareLaunched("a", 300)
        val versionCount = q.item("a")!!.versions.size
        val retried = q.retryShare("a", 400)
        assertEquals(versionCount, retried.item("a")!!.versions.size)
    }

    @Test
    fun `retryShare is no-op for READY item`() {
        val item = testItem("a", 0)
        val q = PaymentQueue.create("q", 100, listOf(item))
        val result = q.retryShare("a", 200)
        assertEquals(PaymentStatus.READY, result.item("a")!!.status)
    }

    @Test
    fun `retryShare is no-op for COMPLETED item`() {
        val item = testItem("a", 0)
        val q = PaymentQueue.create("q", 100, listOf(item))
            .startSharing("a", 200)
            .shareLaunched("a", 300)
            .confirmCompleted("a", 400)
        assertEquals(PaymentStatus.COMPLETED, q.item("a")!!.status)
        val result = q.retryShare("a", 500)
        assertEquals(PaymentStatus.COMPLETED, result.item("a")!!.status)
    }

    @Test
    fun `multiple retries create separate attempts`() {
        val item = testItem("a", 0)
        var q = PaymentQueue.create("q", 100, listOf(item))
            .startSharing("a", 200)
            .shareLaunched("a", 300)
        q = q.retryShare("a", 400)  // attempt 2
        q = q.shareLaunched("a", 500)
        q = q.retryShare("a", 600)  // attempt 3
        q = q.shareLaunched("a", 700)
        // 3 attempts: initial STARTED, initial LAUNCHED, retry STARTED, retry LAUNCHED
        assertTrue(q.item("a")!!.attempts.size >= 3)
    }
}
