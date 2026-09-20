package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ปัญหา tab is an action area, not a read-only list, so this asserts what each
 * problem status is offered — and, just as important, what it must **not** be
 * offered.
 *
 * Every offered action is then driven through the domain transition it claims to
 * use, so an action can never be an offer that silently does nothing, and the
 * actions the domain refuses are exactly the ones the tab does not draw.
 */
class ProblemActionTest {

    private fun queueWith(status: PaymentStatus): PaymentQueue = testQueue(
        testItem("problem", 0, status = status),
        testItem("next", 1),
    )

    private fun newVersion(): QrVersion = testVersion(
        id = "problem-v2",
        paymentItemId = "problem",
        versionNumber = 2,
    )

    // ---- which statuses the tab collects ------------------------------------

    @Test
    fun `the problem tab collects exactly the three problem statuses`() {
        assertEquals(
            listOf(
                PaymentStatus.FAILED,
                PaymentStatus.UNKNOWN,
                PaymentStatus.REQUIRES_QR_REPLACEMENT,
            ),
            PaymentStatus.entries.filter { status -> status.isProblem },
        )
    }

    @Test
    fun `a non-problem status is never offered a problem action`() {
        PaymentStatus.entries
            .filter { status -> !status.isProblem }
            .forEach { status ->
                assertEquals(
                    status.toString(),
                    emptyList<ProblemAction>(),
                    ProblemActions.availableFor(status),
                )
            }
    }

    // ---- what every problem item is offered ---------------------------------

    @Test
    fun `every problem item can replace its QR and be cleared`() {
        PaymentStatus.entries.filter { it.isProblem }.forEach { status ->
            assertTrue(
                status.toString(),
                ProblemActions.offers(status, ProblemAction.REPLACE_QR),
            )
            assertTrue(
                status.toString(),
                ProblemActions.offers(status, ProblemAction.CLEAR_ITEM),
            )
        }
    }

    @Test
    fun `the destructive clear action is always last and never the only one`() {
        PaymentStatus.entries.filter { it.isProblem }.forEach { status ->
            val actions = ProblemActions.availableFor(status)

            // ล้างรายการ removes the item, so it must never be the first thing
            // under the user's thumb: it is offered last, after the ways out.
            assertEquals(status.toString(), ProblemAction.CLEAR_ITEM, actions.last())
            assertTrue(status.toString(), actions.size >= 2)
            assertEquals(status.toString(), 1, actions.count { it == ProblemAction.CLEAR_ITEM })
        }
    }

    @Test
    fun `an unknown result is offered the confirmation and the explicit retry`() {
        assertEquals(
            listOf(
                ProblemAction.RESCAN,
                ProblemAction.REPLACE_QR,
                ProblemAction.CONFIRM_COMPLETED,
                ProblemAction.CLEAR_ITEM,
            ),
            ProblemActions.availableFor(PaymentStatus.UNKNOWN),
        )
    }

    @Test
    fun `a failed item is offered a re-send and the unusable-QR verdict`() {
        assertEquals(
            listOf(
                ProblemAction.RESCAN,
                ProblemAction.REPLACE_QR,
                ProblemAction.MARK_QR_UNUSABLE,
                ProblemAction.CLEAR_ITEM,
            ),
            ProblemActions.availableFor(PaymentStatus.FAILED),
        )
    }

    @Test
    fun `an item that needs a new QR is never offered a re-send`() {
        assertEquals(
            listOf(ProblemAction.REPLACE_QR, ProblemAction.CLEAR_ITEM),
            ProblemActions.availableFor(PaymentStatus.REQUIRES_QR_REPLACEMENT),
        )

        // The domain refuses both ways back to the bank for a QR the user reported
        // unusable, which is why the offer must not exist: it would be a button
        // that does nothing, or a way to hand a known-bad QR to the bank.
        val queue = queueWith(PaymentStatus.REQUIRES_QR_REPLACEMENT)

        assertFalse(queue.canStartHandoff("problem"))
        assertEquals(queue, queue.startSharing("problem", 10L))
        assertEquals(queue, queue.retryItem("problem", 10L))
    }

    // ---- every action really works on the status it is offered for ----------

    @Test
    fun `a failed re-send hands the same intact QR to the bank`() {
        val queue = queueWith(PaymentStatus.FAILED)

        // A failure means nothing reached the bank, so the same QR may go again.
        assertTrue(queue.canStartHandoff("problem"))

        val sharing = queue.startSharing("problem", 10L)

        assertEquals(PaymentStatus.SHARING, sharing.item("problem")?.status)
        // Same Payment Item, same QR: a re-send is not a replacement.
        assertEquals(
            queue.item("problem")?.currentVersionId,
            sharing.item("problem")?.currentVersionId,
        )
        assertEquals(queue.itemCount, sharing.itemCount)
    }

    @Test
    fun `an unknown re-send is the explicit retry and sends nothing`() {
        val queue = queueWith(PaymentStatus.UNKNOWN)

        // The domain refuses a direct hand-off of a result we do not know ...
        assertFalse(queue.canStartHandoff("problem"))

        val refused = queue.startSharing("problem", 10L)

        assertEquals(PaymentStatus.UNKNOWN, refused.item("problem")?.status)
        assertEquals(
            queue.item("problem")?.attempts?.size,
            refused.item("problem")?.attempts?.size,
        )

        // ... so the offer runs the explicit user retry instead: READY, and no
        // bank was opened by it (the item is back in the queue, not in flight).
        val retried = queue.retryItem("problem", 10L)

        assertEquals(PaymentStatus.READY, retried.item("problem")?.status)
        assertNull(retried.handoffItem)
        assertTrue(retried.canStartHandoff("problem"))
    }

    @Test
    fun `only an unknown result is offered the confirmation`() {
        assertTrue(
            ProblemActions.offers(PaymentStatus.UNKNOWN, ProblemAction.CONFIRM_COMPLETED),
        )
        assertFalse(
            ProblemActions.offers(PaymentStatus.FAILED, ProblemAction.CONFIRM_COMPLETED),
        )

        // A FAILED item is refused by both completion paths: nothing reached the
        // bank, so there is nothing the user could have confirmed.
        val queue = queueWith(PaymentStatus.FAILED)

        assertEquals(queue, queue.confirmCompleted("problem", 10L))
        assertEquals(queue, queue.resolveUnknownCompleted("problem", 10L))
    }

    @Test
    fun `an unknown result is completed only by an explicit user confirmation`() {
        val queue = queueWith(PaymentStatus.UNKNOWN)

        assertNull(queue.item("problem")?.completedAt)

        val completed = queue.resolveUnknownCompleted("problem", 40L)

        assertEquals(PaymentStatus.COMPLETED, completed.item("problem")?.status)
        assertEquals(40L, completed.item("problem")?.completedAt)
        // The other item is untouched, and nothing was deleted.
        assertEquals(PaymentStatus.READY, completed.item("next")?.status)
        assertEquals(2, completed.itemCount)
    }

    @Test
    fun `the replace action returns every problem item to READY on a new version`() {
        PaymentStatus.entries.filter { it.isProblem }.forEach { status ->
            val queue = queueWith(status)

            val replaced = queue.replaceCurrentQr("problem", newVersion(), 50L)
            val item = replaced.item("problem")

            assertEquals(status.toString(), PaymentStatus.READY, item?.status)
            assertEquals(status.toString(), "problem-v2", item?.currentVersionId)
            assertEquals(status.toString(), 2, item?.versions?.size)
            // Same Payment Item: replacing a QR never creates a second one.
            assertEquals(status.toString(), 2, replaced.itemCount)
        }
    }

    @Test
    fun `the unusable-QR action turns a failed item into a QR that must be replaced`() {
        val queue = queueWith(PaymentStatus.FAILED)

        val marked = queue.markQrUnusable("problem", "user verdict", 60L)

        assertEquals(PaymentStatus.REQUIRES_QR_REPLACEMENT, marked.item("problem")?.status)
        assertFalse(marked.canStartHandoff("problem"))
        assertEquals(2, marked.itemCount)
    }

    @Test
    fun `the clear action removes exactly the one item`() {
        val queue = queueWith(PaymentStatus.UNKNOWN)

        val cleared = queue.clearItem("problem")

        assertNull(cleared.item("problem"))
        assertEquals(1, cleared.itemCount)
        assertEquals(PaymentStatus.READY, cleared.item("next")?.status)
    }
}
