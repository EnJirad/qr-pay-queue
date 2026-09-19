package com.enjirad.qrqueue.ui

import com.enjirad.qrqueue.domain.PaymentQueue
import com.enjirad.qrqueue.domain.PaymentStatus
import com.enjirad.qrqueue.domain.testItem
import com.enjirad.qrqueue.domain.testQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three primary tabs and the problem badge are the app's way of telling the
 * user what needs attention, so the counts and the tab contents are asserted
 * directly against the queue state.
 */
class NavigationBadgeTest {

    private fun queueOf(vararg items: com.enjirad.qrqueue.domain.QueueItem): PaymentQueue =
        testQueue(*items)

    @Test
    fun thereAreExactlyThreeTabsInTheirFixedOrder() {
        assertEquals(
            listOf("HOME", "PROBLEMS", "COMPLETED"),
            QueueTab.entries.map { tab -> tab.name },
        )
    }

    @Test
    fun homeIsTheDefaultTab() {
        assertEquals(QueueTab.HOME, QueueUiState().selectedTab)
        assertEquals(QueueTab.PROBLEMS, QueueUiState(selectedTab = QueueTab.PROBLEMS).selectedTab)
    }

    @Test
    fun changingTheQueueNeverResetsTheSelectedTab() {
        val state = QueueUiState(selectedTab = QueueTab.PROBLEMS, queue = queueOf(testItem("a", 0)))
        val updated = state.copy(queue = state.queue?.startSharing("a"))

        assertEquals(QueueTab.PROBLEMS, updated.selectedTab)
        assertEquals("a", updated.queue?.handoffItem?.id)
    }

    @Test
    fun theBadgeCountsOnlyItemsThatNeedAttention() {
        val state = QueueUiState(
            queue = queueOf(
                testItem("a", 0, PaymentStatus.UNKNOWN),
                testItem("b", 1, PaymentStatus.REQUIRES_QR_REPLACEMENT),
                testItem("c", 2, PaymentStatus.FAILED),
                testItem("d", 3, PaymentStatus.READY),
                testItem("e", 4, PaymentStatus.COMPLETED),
                testItem("f", 5, PaymentStatus.AWAITING_USER_CONFIRMATION),
            ),
        )

        assertEquals(3, state.problemCount)
        assertEquals(3, state.problemBadge)
    }

    @Test
    fun completedAndReadyItemsAreNeverCountedAsProblems() {
        val state = QueueUiState(
            queue = queueOf(
                testItem("a", 0, PaymentStatus.READY),
                testItem("b", 1, PaymentStatus.COMPLETED),
            ),
        )

        assertEquals(0, state.problemCount)
        // A zero count shows no badge at all, not a "0".
        assertNull(state.problemBadge)
    }

    @Test
    fun anEmptyAppHasNoBadge() {
        assertNull(QueueUiState().problemBadge)
        assertEquals(0, QueueUiState().problemCount)
    }

    @Test
    fun resolvingAProblemUpdatesTheBadgeImmediately() {
        val queue = queueOf(testItem("a", 0, PaymentStatus.UNKNOWN), testItem("b", 1))
        val before = QueueUiState(queue = queue)
        assertEquals(1, before.problemCount)

        val resolved = queue.resolveUnknownCompleted("a", 10L)
        val after = before.copy(queue = resolved)

        assertEquals(0, after.problemCount)
        assertNull(after.problemBadge)
        assertEquals(1, after.completedCount)
    }

    @Test
    fun replacingAnUnusableQrClearsItsProblem() {
        val queue = queueOf(testItem("a", 0, PaymentStatus.REQUIRES_QR_REPLACEMENT))
        val before = QueueUiState(queue = queue)
        assertEquals(1, before.problemCount)

        val replaced = queue.replaceCurrentQr(
            itemId = "a",
            newVersion = com.enjirad.qrqueue.domain.testVersion("a-v2", "a", versionNumber = 2),
            nowMillis = 5L,
        )

        assertEquals(0, QueueUiState(queue = replaced).problemCount)
        assertEquals(PaymentStatus.READY, replaced.item("a")?.status)
    }

    @Test
    fun theProblemTabListsEveryUnresolvedItemInQueueOrder() {
        val state = QueueUiState(
            queue = queueOf(
                testItem("a", 0, PaymentStatus.FAILED),
                testItem("b", 1, PaymentStatus.READY),
                testItem("c", 2, PaymentStatus.UNKNOWN),
                testItem("d", 3, PaymentStatus.REQUIRES_QR_REPLACEMENT),
            ),
        )

        assertEquals(
            listOf("a", "c", "d"),
            state.queue?.problemItems?.map { item -> item.id },
        )
    }

    @Test
    fun theCompletedTabListsOnlyUserConfirmedItems() {
        val queue = queueOf(testItem("a", 0), testItem("b", 1))
            .startSharing("a", 10L)
            .shareLaunched("a", 20L)
            .confirmCompleted("a", 30L)

        val state = QueueUiState(queue = queue)

        assertEquals(listOf("a"), state.queue?.completedItems?.map { item -> item.id })
        assertEquals(1, state.completedCount)
        assertEquals(1, state.queue?.remainingCount)
    }

    @Test
    fun completedItemsKeepTheOrderTheUserFinishedThem() {
        val queue = queueOf(testItem("a", 0), testItem("b", 1), testItem("c", 2))
            .startSharing("c", 10L)
            .shareLaunched("c", 20L)
            .confirmCompleted("c", 30L)
            .startSharing("a", 40L)
            .shareLaunched("a", 50L)
            .confirmCompleted("a", 60L)

        assertEquals(listOf("c", "a"), queue.completedItems.map { item -> item.id })
        assertEquals(60L, queue.completedItems.last().completedAt)
    }

    @Test
    fun homeOffersTheNextActionInsteadOfOnlyShowingNumbers() {
        val state = QueueUiState(
            queue = queueOf(testItem("a", 0, PaymentStatus.READY), testItem("b", 1)),
        )

        assertEquals("a", state.nextActionItem?.id)
        assertEquals(PaymentStatus.READY, state.nextActionItem?.status)
    }

    @Test
    fun anItemWaitingForTheUserIsNotOfferedAsANewAction() {
        val queue = queueOf(testItem("a", 0)).startSharing("a").shareLaunched("a")
        val state = QueueUiState(queue = queue, selectedTab = QueueTab.HOME)

        assertNull(state.nextActionItem)
        assertEquals("a", state.awaitingAnswerItem?.id)
    }

    @Test
    fun theUploadGateIsUnchangedByTheTabs() {
        assertTrue(!QueueUiState().canImport)
        assertTrue(QueueUiState().requiresBankSelection)
    }
}
