package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the problem-reporting and item-clearing workflows (V0.6).
 *
 * reportProblem moves an active item to REQUIRES_QR_REPLACEMENT with a reason.
 * clearItem removes it from the queue entirely.
 */
class ProblemFlowTest {

    private fun queueWithItems(count: Int): PaymentQueue {
        val items = (1..count).map { i ->
            testItem(id = "item-$i", position = i - 1)
        }
        return testQueue(*items.toTypedArray())
    }

    @Test
    fun `reportProblem moves active item to REQUIRES_QR_REPLACEMENT`() {
        val q = queueWithItems(3)
        val item = q.items[1]
        assertEquals(PaymentStatus.READY, item.status)

        val updated = q.reportProblem(item.id, "QR ใช้งานไม่ได้", 1000L)
        val changed = updated.item(item.id)!!

        assertEquals(PaymentStatus.REQUIRES_QR_REPLACEMENT, changed.status)
        assertEquals("QR ใช้งานไม่ได้", changed.problemReason)
        assertNotNull(changed.attempts.lastOrNull { it.result == PaymentAttemptResult.QR_UNUSABLE })
    }

    @Test
    fun `reportProblem marks current QR as UNUSABLE`() {
        val q = queueWithItems(2)
        val item = q.items[0]
        val oldCurrent = item.currentVersion!!

        val updated = q.reportProblem(item.id, "หมดอายุ", 2000L)
        val changed = updated.item(item.id)!!
        val newCurrent = changed.currentVersion!!

        assertEquals(oldCurrent.id, newCurrent.id)
        assertEquals(QrVersionStatus.UNUSABLE, newCurrent.status)
    }

    @Test
    fun `reportProblem is no-op for already completed item`() {
        val q = queueWithItems(1)
        val completed = q.confirmCompleted(q.items[0].id, 1000L)
        val result = completed.reportProblem(completed.items[0].id, "test", 2000L)

        assertEquals(PaymentStatus.COMPLETED, result.items[0].status)
        assertNull(result.items[0].problemReason)
    }

    @Test
    fun `reportProblem is no-op for non-existent item`() {
        val q = queueWithItems(1)
        val result = q.reportProblem("nonexistent", "test", 1000L)

        assertEquals(q.items.size, result.items.size)
    }

    @Test
    fun `clearItem removes item from queue`() {
        val q = queueWithItems(3)
        val targetId = q.items[1].id

        val updated = q.clearItem(targetId)

        assertEquals(2, updated.items.size)
        assertNull(updated.item(targetId))
    }

    @Test
    fun `clearItem is no-op for non-existent item`() {
        val q = queueWithItems(2)
        val updated = q.clearItem("nonexistent")

        assertEquals(2, updated.items.size)
    }

    @Test
    fun `clearItem preserves other items`() {
        val q = queueWithItems(4)
        val firstId = q.items[0].id
        val thirdId = q.items[2].id
        val fourthId = q.items[3].id

        val updated = q.clearItem(q.items[1].id)

        assertNotNull(updated.item(firstId))
        assertNull(updated.item(q.items[1].id))
        assertNotNull(updated.item(thirdId))
        assertNotNull(updated.item(fourthId))
    }

    @Test
    fun `problemReason persists across queue normalization`() {
        val q = queueWithItems(2)
        val item = q.items[0]
        val updated = q.reportProblem(item.id, "ธนาคารแจ้งว่า QR ไม่ถูกต้อง", 1000L)
        val normalized = updated.normalized()
        val normalizedItem = normalized.item(item.id)!!

        assertEquals("ธนาคารแจ้งว่า QR ไม่ถูกต้อง", normalizedItem.problemReason)
        assertEquals(PaymentStatus.REQUIRES_QR_REPLACEMENT, normalizedItem.status)
    }

    @Test
    fun `reportProblem then replaceQR returns item to READY`() {
        val q = queueWithItems(1)
        val item = q.items[0]
        val afterProblem = q.reportProblem(item.id, "QR ใช้งานไม่ได้", 1000L)

        val replacement = testVersion(id = "item-1-v2", paymentItemId = item.id)
        val afterReplace = afterProblem.replaceCurrentQr(item.id, replacement, 2000L)
        val restored = afterReplace.item(item.id)!!

        assertEquals(PaymentStatus.READY, restored.status)
        assertEquals(2, restored.versions.size)
        assertEquals("QR ใช้งานไม่ได้", restored.problemReason)
    }

    @Test
    fun `reportProblem records attempt`() {
        val q = queueWithItems(1)
        val item = q.items[0]
        assertTrue(item.attempts.isEmpty())

        val updated = q.reportProblem(item.id, "จ่ายไม่ได้", 500L)
        val changed = updated.item(item.id)!!

        assertEquals(1, changed.attempts.size)
        assertEquals(PaymentAttemptResult.QR_UNUSABLE, changed.attempts[0].result)
        assertEquals("จ่ายไม่ได้", changed.attempts[0].reason)
    }
}
