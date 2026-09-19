package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replacing a QR is a first-class feature: the user reports that a QR cannot be
 * used, picks a new image, and the *same* Payment Item carries on. These tests
 * pin that the item keeps its identity, that the old QR is kept as history, that
 * the new QR becomes the only current one, and that a replacement that cannot be
 * used leaves the previous QR exactly as it was.
 */
class QrReplacementTest {

    /** An item whose only QR the user has already reported as unusable. */
    private fun itemWithUnusableQr(): QueueItem {
        val v1 = testVersion("a-v1", "a", versionNumber = 1, status = QrVersionStatus.UNUSABLE)
        return testItem("a", 0, PaymentStatus.REQUIRES_QR_REPLACEMENT, versions = listOf(v1))
    }

    @Test
    fun reportingAQrAsUnusableKeepsTheItemInTheQueue() {
        val queue = testQueue(testItem("a", 0), testItem("b", 1))
            .startSharing("a", 10L)
            .shareLaunched("a", 20L)
            .markQrUnusable("a", "bank rejected it", 30L)

        assertEquals(2, queue.itemCount)
        assertEquals(PaymentStatus.REQUIRES_QR_REPLACEMENT, queue.item("a")?.status)
        assertEquals("bank rejected it", queue.item("a")?.failureDetail)
        assertEquals(1, queue.requiresReplacementCount)
        assertEquals(1, queue.problemCount)
        // The item no longer offers its QR to the bank, but keeps it as history.
        assertNull(queue.item("a")?.currentVersion)
        assertEquals(QrVersionStatus.UNUSABLE, queue.item("a")?.latestVersion?.status)
        assertFalse(queue.canStartHandoff("a"))
        // The other item is untouched.
        assertEquals(PaymentStatus.READY, queue.item("b")?.status)
    }

    @Test
    fun anUnresolvedItemCanAlsoBeReportedAsAnUnusableQr() {
        val fromUnknown = testQueue(testItem("a", 0))
            .startSharing("a").shareLaunched("a").markUnknown("a")
            .markQrUnusable("a", null, 5L)
        assertEquals(PaymentStatus.REQUIRES_QR_REPLACEMENT, fromUnknown.item("a")?.status)

        val fromFailed = testQueue(testItem("a", 0))
            .failItem("a", "boom", 1L)
            .markQrUnusable("a", null, 5L)
        assertEquals(PaymentStatus.REQUIRES_QR_REPLACEMENT, fromFailed.item("a")?.status)

        // A ready item has no reported problem, so nothing is marked unusable.
        val fromReady = testQueue(testItem("a", 0)).markQrUnusable("a", null, 5L)
        assertEquals(PaymentStatus.READY, fromReady.item("a")?.status)
        assertTrue(fromReady.item("a")?.currentVersion != null)
    }

    @Test
    fun replacingAQrKeepsThePaymentItemIdPositionAndNumber() {
        val before = itemWithUnusableQr()
        val stored = testQueue(before)
        val after = stored
            .replaceCurrentQr("a", testVersion("a-v2", "a", path = "/tmp/a-v2.png"), 40L)
            .item("a")!!

        assertEquals(before.id, after.id)
        assertEquals(before.position, after.position)
        assertEquals(before.itemLabel, after.itemLabel)
        assertEquals("QR #01", after.itemLabel)
        assertEquals(1, stored.replaceCurrentQr("a", testVersion("a-v2", "a")).let { it.itemCount })
    }

    @Test
    fun theNewQrBecomesCurrentAndTheOldOneStaysAsHistory() {
        val queue = testQueue(itemWithUnusableQr()).replaceCurrentQr(
            itemId = "a",
            newVersion = testVersion("a-v2", "a", path = "/tmp/a-v2.png", fingerprint = "hash-two"),
            40L,
        )

        val item = queue.item("a")!!
        assertEquals(2, item.qrVersionCount)
        assertEquals("a-v2", item.currentVersion?.id)
        assertEquals("/tmp/a-v2.png", item.currentFilePath)
        assertEquals(2, item.currentVersion?.versionNumber)
        assertEquals("hash-two", item.currentVersion?.fingerprint)
        assertEquals(listOf("a-v1"), item.historicalVersions.map { it.id })
        // The unused QR stays unusable and never becomes current again.
        assertEquals(QrVersionStatus.UNUSABLE, item.versions[0].status)
        assertEquals(1, item.versions.count { version -> version.isCurrent })
    }

    @Test
    fun aReplacedQrReturnsTheItemToReady() {
        val queue = testQueue(itemWithUnusableQr())
            .replaceCurrentQr("a", testVersion("a-v2", "a"), 40L)

        assertEquals(PaymentStatus.READY, queue.item("a")?.status)
        assertNull(queue.item("a")?.failureDetail)
        assertEquals(0, queue.problemCount)
        assertEquals(1, queue.readyCount)
        assertTrue(queue.canStartHandoff("a"))
    }

    @Test
    fun replacingAQrNeverCreatesANewPaymentItem() {
        val queue = testQueue(testItem("a", 0), testItem("b", 1))
            .failItem("a", "boom", 1L)

        val replaced = queue.replaceCurrentQr("a", testVersion("a-v2", "a"), 2L)

        assertEquals(2, replaced.itemCount)
        assertEquals(listOf("a", "b"), replaced.items.map { it.id })
        assertEquals(listOf(0, 1), replaced.items.map { it.position })
        // The other item's QR is not touched.
        assertEquals("b-v1", replaced.item("b")?.currentVersion?.id)
    }

    @Test
    fun aReplacementThatCannotBeUsedLeavesTheOriginalQrIntact() {
        val before = testQueue(itemWithUnusableQr())

        val after = before.replaceCurrentQr(
            itemId = "a",
            newVersion = testVersion("a-v2", "a", path = "   "),
            40L,
        )

        assertEquals(before, after)
        assertEquals(PaymentStatus.REQUIRES_QR_REPLACEMENT, after.item("a")?.status)
        assertEquals(1, after.item("a")?.qrVersionCount)
        assertNull(after.item("a")?.currentVersion)
        // Nothing was corrupted: the reported-unusable QR is still the history.
        assertEquals("a-v1", after.item("a")?.latestVersion?.id)
    }

    @Test
    fun aCompletedItemCannotHaveItsQrReplaced() {
        val completed = testQueue(testItem("a", 0))
            .startSharing("a").shareLaunched("a").confirmCompleted("a")

        val after = completed.replaceCurrentQr("a", testVersion("a-v2", "a"), 5L)

        assertEquals(PaymentStatus.COMPLETED, after.item("a")?.status)
        assertEquals(1, after.item("a")?.qrVersionCount)
    }

    @Test
    fun anItemThatIsStillWithTheBankCannotHaveItsQrReplaced() {
        val awaiting = testQueue(testItem("a", 0)).startSharing("a").shareLaunched("a")
        assertEquals(
            PaymentStatus.AWAITING_USER_CONFIRMATION,
            awaiting.replaceCurrentQr("a", testVersion("a-v2", "a"), 5L).item("a")?.status,
        )

        val ready = testQueue(testItem("a", 0))
        assertEquals(
            PaymentStatus.READY,
            ready.replaceCurrentQr("a", testVersion("a-v2", "a"), 5L).item("a")?.status,
        )
        assertEquals(1, ready.replaceCurrentQr("a", testVersion("a-v2", "a"), 5L).item("a")?.qrVersionCount)
    }

    @Test
    fun replacingAnUnknownQrAlsoReturnsTheItemToReady() {
        val unknown = testQueue(testItem("a", 0))
            .startSharing("a").shareLaunched("a").markUnknown("a")

        val replaced = unknown.replaceCurrentQr("a", testVersion("a-v2", "a"), 9L)

        assertEquals(PaymentStatus.READY, replaced.item("a")?.status)
        assertEquals(QrVersionStatus.SUPERSEDED, replaced.item("a")?.versions?.first()?.status)
        assertEquals(0, replaced.problemCount)
    }

    @Test
    fun theQrHistoryKeepsVersionOrderAndTheCurrentFlag() {
        val queue = testQueue(testItem("a", 0))
            .failItem("a", "boom", 1L)
            .markQrUnusable("a", null, 2L)
            .replaceCurrentQr("a", testVersion("a-v2", "a", versionNumber = 0), 3L)

        val item = queue.normalized().item("a")!!
        assertEquals(listOf(1, 2), item.versions.map { version -> version.versionNumber })
        assertEquals("a-v2", item.currentVersion?.id)
        assertTrue(item.versions.all { version -> version.paymentItemId == "a" })
    }
}
