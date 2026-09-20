package com.enjirad.qrqueue.ui

import com.enjirad.qrqueue.domain.PaymentQueue
import com.enjirad.qrqueue.domain.PaymentStatus
import com.enjirad.qrqueue.domain.ProblemAction
import com.enjirad.qrqueue.domain.ProblemActions
import com.enjirad.qrqueue.domain.testItem
import com.enjirad.qrqueue.domain.testQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ปัญหา tab as an action area, and the Home/PROBLEMS separation it depends on.
 *
 * Compose cannot be rendered here, so this asserts the state the screen draws
 * from: which recovery actions each problem item is offered, that ล้างรายการ always
 * asks for confirmation before it deletes anything, and that reporting a problem
 * takes the item off Home while leaving it in the queue.
 */
class ProblemTabActionsTest {

    private val problemStatuses = PaymentStatus.entries.filter { status -> status.isProblem }

    private fun queueOf(vararg statuses: PaymentStatus): PaymentQueue = testQueue(
        *statuses
            .mapIndexed { index, status -> testItem("item-$index", index, status = status) }
            .toTypedArray(),
    )

    // ---- A / B: a reported problem leaves Home ------------------------------

    @Test
    fun `reporting a problem takes the item off Home without deleting it`() {
        val queue = testQueue(testItem("a", 0), testItem("b", 1), testItem("c", 2))
        val before = QueueUiState(queue = queue)

        assertEquals("a", before.activeItem?.id)
        assertEquals(listOf("b", "c"), before.queuedItems.map { item -> item.id })

        val after = before.copy(queue = queue.reportProblem("a", "QR ใช้งานไม่ได้", 10L))

        // Home continues with B and never offers the reported QR again: it is not
        // the active QR and it is not in the list below it either.
        assertEquals("b", after.activeItem?.id)
        assertEquals("b", after.nextActionItem?.id)
        assertEquals(listOf("c"), after.queuedItems.map { item -> item.id })
        assertTrue(after.queuedItems.none { item -> item.id == "a" })

        // It is still in the queue (persistence is untouched) and it is now a
        // problem: the ปัญหา tab lists it and the badge counts it.
        assertEquals(3, after.queue?.itemCount)
        assertEquals(listOf("a"), after.queue?.problemItems?.map { item -> item.id })
        assertEquals(1, after.problemCount)
        assertEquals(1, after.problemBadge)
        assertEquals(0, after.completedCount)
    }

    @Test
    fun `work already reported is not offered back on Home`() {
        // FAILED stays a problem status: it belongs to the ปัญหา tab, not to the
        // Home queue list.
        val queue = testQueue(
            testItem("failed", 0, PaymentStatus.FAILED),
            testItem("unknown", 1, PaymentStatus.UNKNOWN),
            testItem("replace", 2, PaymentStatus.REQUIRES_QR_REPLACEMENT),
            testItem("ready", 3),
        )
        val state = QueueUiState(queue = queue)

        assertEquals("ready", state.activeItem?.id)
        assertTrue(state.queuedItems.isEmpty())
        assertEquals(3, state.problemCount)
        assertEquals(4, state.queue?.itemCount)
    }

    @Test
    fun `a queue of nothing but problems has no QR left for Home`() {
        val queue = testQueue(
            testItem("unknown", 0, PaymentStatus.UNKNOWN),
            testItem("replace", 1, PaymentStatus.REQUIRES_QR_REPLACEMENT),
        )
        val state = QueueUiState(queue = queue)

        // Nothing to hand the bank, so Home shows the pointer at the ปัญหา tab
        // instead of an empty panel: no active QR and no queue list.
        assertNull(state.activeItem)
        assertNull(state.nextActionItem)
        assertTrue(state.queuedItems.isEmpty())
        assertFalse(queue.finished)
        assertEquals(2, state.problemCount)
        // Nothing was deleted and nothing was completed by any of this.
        assertEquals(2, state.queue?.itemCount)
        assertEquals(0, state.completedCount)
    }

    // ---- D: the actions come from the domain, and never be empty ------------

    @Test
    fun `every problem item has a real action area`() {
        problemStatuses.forEach { status ->
            val actions = ProblemActions.availableFor(status)

            assertTrue(status.toString(), actions.isNotEmpty())
            // The three ways out of a problem are always there.
            assertTrue(status.toString(), ProblemAction.REPLACE_QR in actions)
            assertTrue(status.toString(), ProblemAction.CLEAR_ITEM in actions)
        }
    }

    @Test
    fun `the actions of a problem item match what its status can do`() {
        // UNKNOWN: it may not be re-sent by the app, so its re-send is the
        // domain's explicit retry; it is the only status that can be confirmed.
        assertEquals(
            listOf(
                ProblemAction.RESCAN,
                ProblemAction.REPLACE_QR,
                ProblemAction.CONFIRM_COMPLETED,
                ProblemAction.CLEAR_ITEM,
            ),
            ProblemActions.availableFor(PaymentStatus.UNKNOWN),
        )

        // FAILED: nothing reached the bank, so the same QR may go again, and the
        // user may report the image itself as unusable.
        assertEquals(
            listOf(
                ProblemAction.RESCAN,
                ProblemAction.REPLACE_QR,
                ProblemAction.MARK_QR_UNUSABLE,
                ProblemAction.CLEAR_ITEM,
            ),
            ProblemActions.availableFor(PaymentStatus.FAILED),
        )

        // REQUIRES_QR_REPLACEMENT: both a re-send and the unusable verdict are
        // refused by the domain, so neither is drawn.
        assertEquals(
            listOf(ProblemAction.REPLACE_QR, ProblemAction.CLEAR_ITEM),
            ProblemActions.availableFor(PaymentStatus.REQUIRES_QR_REPLACEMENT),
        )
    }

    // ---- E: clearing always asks first --------------------------------------

    @Test
    fun `clearing an item asks first and deletes nothing on the ask alone`() {
        val state = QueueUiState(queue = queueOf(PaymentStatus.UNKNOWN))

        assertFalse(state.awaitsClearItemConfirmation)

        val asking = state.clearingItem("item-0")

        // The confirmation is on screen and the item is still in the queue: the
        // ask is not a delete.
        assertTrue(asking.awaitsClearItemConfirmation)
        assertEquals("item-0", asking.itemToClearId)
        assertEquals(1, asking.queue?.itemCount)
        assertNotNull(asking.queue?.item("item-0"))
        // The queue object itself is the same one, so what Home renders cannot
        // change before the user answers.
        assertSame(state.queue, asking.queue)
    }

    @Test
    fun `dismissing the confirmation keeps the item exactly as it was`() {
        val state = QueueUiState(queue = queueOf(PaymentStatus.FAILED))
            .clearingItem("item-0")

        val dismissed = state.clearItemDismissed()

        assertFalse(dismissed.awaitsClearItemConfirmation)
        assertNull(dismissed.itemToClearId)
        assertSame(state.queue, dismissed.queue)
        assertEquals(1, dismissed.queue?.itemCount)
    }

    @Test
    fun `a string-only confirmation state is not a confirmation`() {
        // The id and the flag must belong together: a half-set state (which no
        // code path produces) never reads as "the user is being asked".
        assertFalse(QueueUiState(clearItemConfirmationVisible = true).awaitsClearItemConfirmation)
        assertFalse(QueueUiState(itemToClearId = "item-0").awaitsClearItemConfirmation)
    }

    @Test
    fun `the confirmed delete is the only thing that removes the item`() {
        // The domain deletion the confirmation guards, driven directly: this is
        // what onClearItemConfirmed reaches after the user says yes.
        val queue = queueOf(PaymentStatus.UNKNOWN, PaymentStatus.READY)

        val cleared = queue.clearItem("item-0")

        assertNull(cleared.item("item-0"))
        assertEquals(1, cleared.itemCount)
        assertEquals("item-1", cleared.item("item-1")?.id)
    }
}
