package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state machine is where the product's safety promises live, so every rule
 * from the brief is asserted here: sequential order, explicit confirmation only,
 * UNKNOWN blocking and never retrying, and totals computed from real amounts.
 */
class PaymentQueueTest {

    private fun payable(id: String, amountSatang: Long?): QueueItem = QueueItem(
        id = id,
        fileName = "$id.png",
        amountSatang = amountSatang,
        recipient = "081-234-5678",
        status = PaymentStatus.READY,
    )

    private fun unpayable(id: String, issue: ValidationIssue): QueueItem = QueueItem(
        id = id,
        fileName = "$id.png",
        status = issue.status,
        issue = issue,
    )

    private fun mixedQueue(): PaymentQueue = PaymentQueue.create(
        queueId = "queue-1",
        createdAt = 1_000L,
        items = listOf(
            payable("a", 50_000L),
            unpayable("bad", ValidationIssue.QR_NOT_FOUND),
            unpayable("dup", ValidationIssue.DUPLICATE_PAYLOAD),
            payable("b", 75_000L),
        ),
    )

    @Test
    fun startBeginsAtTheFirstPayableItemAndSkipsUnpayableOnes() {
        val started = mixedQueue().start()

        assertTrue(started.started)
        assertEquals("a", started.currentItem?.id)
        assertEquals(2, started.queueableCount)
        assertEquals(1, started.invalidCount)
        assertEquals(1, started.duplicateCount)
        assertEquals(2, started.readyCount)
        assertEquals(125_000L, started.queuedSatang)
        assertEquals(1, started.currentOrdinal)
    }

    @Test
    fun startIsRefusedWhenNothingCanBePaid() {
        val queue = PaymentQueue.create(
            queueId = "queue-1",
            createdAt = 1_000L,
            items = listOf(unpayable("bad", ValidationIssue.QR_NOT_FOUND)),
        )
        val started = queue.start()

        assertFalse(started.canStart)
        assertFalse(started.started)
        assertFalse(started.finished)
        assertNull(started.currentItem)
    }

    @Test
    fun handingOffAQRNeverMarksItPaid() {
        val handedOff = PaymentQueue.create("queue-1", 1_000L, listOf(payable("a", 50_000L)))
            .start()
            .handOffCurrent()

        assertEquals(PaymentStatus.WAITING_CONFIRMATION, handedOff.currentItem?.status)
        assertEquals(0, handedOff.paidCount)
        assertEquals(0L, handedOff.paidSatang)
        assertFalse(handedOff.currentItem!!.status.isPaid)
        assertEquals(0f, handedOff.progressFraction, 0.0001f)
    }

    @Test
    fun repeatingTheHandOffStillNeverMarksItPaid() {
        val once = mixedQueue().start().handOffCurrent()
        val twice = once.handOffCurrent()

        assertEquals(PaymentStatus.WAITING_CONFIRMATION, twice.currentItem?.status)
        assertEquals(0, twice.paidCount)
    }

    @Test
    fun successRequiresAnExplicitConfirmationState() {
        val started = mixedQueue().start()
        val submitted = started.markCurrentSubmitted()

        assertEquals(PaymentStatus.SUBMITTED, submitted.currentItem?.status)
        // Sharing is not a result: a confirmed payment is not possible yet.
        assertEquals(0, submitted.confirmSuccess().paidCount)
        // Only the explicit confirmation reached through WAITING_CONFIRMATION pays.
        assertEquals(1, submitted.awaitConfirmation().confirmSuccess().paidCount)
    }

    @Test
    fun confirmedSuccessAdvancesToTheNextPayableItemAndRecordsMoney() {
        val afterFirst = mixedQueue().start().handOffCurrent().confirmSuccess()

        assertEquals(1, afterFirst.paidCount)
        assertEquals(50_000L, afterFirst.paidSatang)
        assertEquals("b", afterFirst.currentItem?.id)
        assertEquals(PaymentStatus.READY, afterFirst.currentItem?.status)
        assertEquals(75_000L, afterFirst.remainingSatang)
        assertEquals(1, afterFirst.remainingCount)
        assertEquals(0.5f, afterFirst.progressFraction, 0.0001f)
        assertEquals(2, afterFirst.currentOrdinal)
    }

    @Test
    fun queueFinishesOnlyAfterEveryPayableItemHasAResult() {
        val finished = mixedQueue()
            .start()
            .handOffCurrent()
            .confirmSuccess()
            .handOffCurrent()
            .confirmFailure()

        assertTrue(finished.finished)
        assertEquals(PaymentQueue.NO_CURRENT_ITEM, finished.currentIndex)
        assertNull(finished.currentItem)
        assertEquals(2, finished.processedCount)
        assertEquals(1, finished.paidCount)
        assertEquals(1, finished.failedCount)
        assertEquals(125_000L, finished.paidSatang + finished.failedSatang)
        assertEquals(0L, finished.remainingSatang)
        assertEquals(1f, finished.progressFraction, 0.0001f)
    }

    @Test
    fun failedPaymentIsRecordedAndTheQueueContinues() {
        val afterFailure = mixedQueue().start().handOffCurrent().confirmFailure()

        assertEquals(1, afterFailure.failedCount)
        assertEquals(0, afterFailure.paidCount)
        assertEquals(0L, afterFailure.paidSatang)
        assertEquals(50_000L, afterFailure.failedSatang)
        assertEquals(75_000L, afterFailure.remainingSatang)
        assertEquals("b", afterFailure.currentItem?.id)
        assertEquals(1, afterFailure.processedCount)
    }

    @Test
    fun unknownResultBlocksTheQueueUntilTheUserResolvesIt() {
        val unknown = mixedQueue().start().handOffCurrent().reportUnknown()

        assertTrue(unknown.requiresResolution)
        assertFalse(unknown.finished)
        assertEquals("a", unknown.currentItem?.id)
        assertEquals(PaymentStatus.UNKNOWN, unknown.currentItem?.status)
        assertEquals(0, unknown.processedCount)
        assertEquals(0, unknown.paidCount)
        assertEquals(0, unknown.failedCount)

        val resolvedAsPaid = unknown.resolveUnknown(true)
        assertEquals(1, resolvedAsPaid.paidCount)
        assertEquals("b", resolvedAsPaid.currentItem?.id)

        val resolvedAsFailed = unknown.resolveUnknown(false)
        assertEquals(1, resolvedAsFailed.failedCount)
        assertEquals(0, resolvedAsFailed.paidCount)
        assertEquals("b", resolvedAsFailed.currentItem?.id)
    }

    @Test
    fun anUnknownItemIsNeverFinishedOnItsOwn() {
        val queue = PaymentQueue.create("queue-1", 1_000L, listOf(payable("a", 10_000L), payable("b", 20_000L)))
        val unknown = queue.start().handOffCurrent().reportUnknown()
        // Finishing the other item may not close the queue while one result is unknown.
        val resolved = unknown.resolveUnknown(true).handOffCurrent().confirmSuccess()

        assertTrue(resolved.finished)
        assertEquals(0, resolved.unknownCount)
    }

    @Test
    fun resolvingIsRefusedForAnItemThatIsNotUnknown() {
        val started = mixedQueue().start()
        val attempted = started.resolveUnknown(true)

        assertEquals(PaymentStatus.READY, attempted.currentItem?.status)
        assertEquals(0, attempted.paidCount)
    }

    @Test
    fun anInterruptedHandOffComesBackAsUnknown() {
        val restored = mixedQueue().start().handOffCurrent().resolveInterrupted()

        assertEquals(PaymentStatus.UNKNOWN, restored.currentItem?.status)
        assertTrue(restored.requiresResolution)
        assertEquals(0, restored.paidCount)
        assertEquals(0L, restored.paidSatang)
    }

    @Test
    fun anInterruptedQueueKeepsEarlierConfirmedResults() {
        val afterFirstPaid = mixedQueue().start().handOffCurrent().confirmSuccess()
        val restored = afterFirstPaid.handOffCurrent().resolveInterrupted()

        assertEquals(1, restored.paidCount)
        assertEquals(50_000L, restored.paidSatang)
        assertEquals("b", restored.currentItem?.id)
        assertEquals(PaymentStatus.UNKNOWN, restored.currentItem?.status)
    }

    @Test
    fun aRestoredQueueRepairsItsPointerFromTheItemStatuses() {
        val stale = PaymentQueue(
            queueId = "queue-1",
            createdAt = 1_000L,
            items = listOf(
                QueueItem(id = "a", fileName = "a.png", status = PaymentStatus.SUCCESS),
                payable("b", 10_000L),
            ),
            currentIndex = 0,
            started = true,
        )
        val normalized = stale.normalized()

        assertEquals("b", normalized.currentItem?.id)
        assertFalse(normalized.finished)
    }

    @Test
    fun aQueueThatWasNeverStartedHasNoCurrentItem() {
        val normalized = mixedQueue().normalized()

        assertFalse(normalized.started)
        assertFalse(normalized.finished)
        assertEquals(PaymentQueue.NO_CURRENT_ITEM, normalized.currentIndex)
    }

    @Test
    fun qrCodesWithoutAnAmountAreCountedAndNotInvented() {
        val queue = PaymentQueue.create(
            queueId = "queue-1",
            createdAt = 1_000L,
            items = listOf(payable("a", 50_000L), payable("b", null)),
        )

        assertEquals(1, queue.amountMissingCount)
        assertEquals(50_000L, queue.queuedSatang)
        assertEquals(50_000L, queue.remainingSatang)
        assertFalse(queue.items[1].hasKnownAmount)
        assertTrue(queue.items[0].hasKnownAmount)
    }

    @Test
    fun importedItemsAreAppendedInSelectionOrder() {
        val base = PaymentQueue.create("queue-1", 1_000L, listOf(payable("a", 100L)))
        val extended = base.appendItems(
            listOf(payable("b", 200L), unpayable("bad", ValidationIssue.CRC_MISMATCH)),
        )

        assertEquals(3, extended.itemCount)
        assertEquals(listOf("a", "b", "bad"), extended.items.map { it.id })
        assertEquals(300L, extended.queuedSatang)
        assertEquals(1, extended.invalidCount)
    }

    @Test
    fun humanVerifiedStatusesCountAsPaidAmounts() {
        val queue = PaymentQueue.create(
            queueId = "queue-1",
            createdAt = 1_000L,
            items = listOf(
                QueueItem(id = "a", fileName = "a.png", amountSatang = 1_000L, status = PaymentStatus.RECONCILED),
                QueueItem(id = "b", fileName = "b.png", amountSatang = 2_000L, status = PaymentStatus.PAID),
            ),
        )

        assertEquals(2, queue.paidCount)
        assertEquals(3_000L, queue.paidSatang)
        assertEquals(0L, queue.remainingSatang)
        assertEquals(2, queue.processedCount)
    }
}
