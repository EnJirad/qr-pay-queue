package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The import step is pure apart from the byte copy, which needs a device. These
 * tests cover the parts that can run without one: URI de-duplication, storage
 * naming, and the fact that N selected images become N queued items in order.
 */
class QueueImportTest {

    /** Mirrors the ViewModel import loop for one successfully copied image. */
    private fun importAll(uris: List<String>, base: PaymentQueue?, now: Long): PaymentQueue {
        val queue = base ?: PaymentQueue.create("queue-1", now)
        var nextPosition = queue.nextPosition
        val items = QueueImport.dedupeSourceUris(uris).map { uri ->
            QueueImport.buildItem(
                id = QueueImport.newItemId(),
                position = nextPosition++,
                sourceUri = uri,
                storedImagePath = "/data/user/0/com.enjirad.qrqueue/files/qrqueue/images/${QueueImport.newItemId()}.png",
                displayName = uri.substringAfterLast('/'),
                mimeType = "image/png",
                nowMillis = now,
            )
        }
        return queue.appendItems(items)
    }

    @Test
    fun duplicateSelectedUrisAreDroppedInSelectionOrder() {
        val selection = listOf(
            "content://media/1",
            "content://media/2",
            "content://media/1",
            "   ",
            "content://media/2 ",
            "content://media/3",
        )

        assertEquals(
            listOf("content://media/1", "content://media/2", "content://media/3"),
            QueueImport.dedupeSourceUris(selection),
        )
    }

    @Test
    fun emptySelectionProducesNoUris() {
        assertTrue(QueueImport.dedupeSourceUris(emptyList()).isEmpty())
        assertTrue(QueueImport.dedupeSourceUris(listOf("", "  ")).isEmpty())
    }

    @Test
    fun storageExtensionFollowsTheMimeTypeOrTheFileName() {
        assertEquals("png", QueueImport.fileExtensionFor("image/png", null))
        assertEquals("jpg", QueueImport.fileExtensionFor("image/jpeg", "ignored.png"))
        assertEquals("webp", QueueImport.fileExtensionFor("image/webp", null))
        assertEquals("jpeg", QueueImport.fileExtensionFor(null, "screenshot.JPEG"))
        assertEquals("heic", QueueImport.fileExtensionFor(null, "IMG_0001.HEIC"))
        assertEquals("img", QueueImport.fileExtensionFor(null, null))
        assertEquals("img", QueueImport.fileExtensionFor(null, "no-extension"))
        assertEquals("img", QueueImport.fileExtensionFor("application/pdf", "invoice.pdf"))
    }

    @Test
    fun mimeTypeFollowsTheStoredExtension() {
        assertEquals("image/png", QueueImport.mimeTypeForExtension("png"))
        assertEquals("image/jpeg", QueueImport.mimeTypeForExtension("jpg"))
        assertEquals("image/jpeg", QueueImport.mimeTypeForExtension("jpeg"))
        assertEquals("image/webp", QueueImport.mimeTypeForExtension("webp"))
        assertEquals("image/*", QueueImport.mimeTypeForExtension("img"))
    }

    @Test
    fun itemIdsAreUnique() {
        assertNotEquals(QueueImport.newItemId(), QueueImport.newItemId())
    }

    @Test
    fun aCopiedImageBecomesAQueuedItemWithNoQrData() {
        val item = QueueImport.buildItem(
            id = "item-1",
            position = 2,
            sourceUri = "content://media/1",
            storedImagePath = "/data/user/0/com.enjirad.qrqueue/files/qrqueue/images/item-1.png",
            displayName = "screenshot.png",
            mimeType = "image/png",
            nowMillis = 1_700_000_000_000L,
        )

        assertEquals("item-1", item.id)
        assertEquals(2, item.position)
        assertEquals("screenshot.png", item.displayName)
        assertEquals("image/png", item.mimeType)
        assertEquals(PaymentStatus.QUEUED, item.status)
        assertEquals(1_700_000_000_000L, item.createdAt)
        assertEquals(1_700_000_000_000L, item.updatedAt)
        assertNull(item.failureDetail)
        assertTrue(item.isActive)
    }

    @Test
    fun oneSelectedImageBecomesOneQueuedItem() {
        val queue = importAll(listOf("content://media/1"), base = null, now = 1L)

        assertEquals(1, queue.itemCount)
        assertEquals(listOf(0), queue.items.map { it.position })
        assertEquals(PaymentStatus.QUEUED, queue.currentItem?.status)
    }

    @Test
    fun threeSelectedImagesBecomeThreeItemsInOrder() {
        val queue = importAll(
            listOf("content://media/1", "content://media/2", "content://media/3"),
            base = null,
            now = 1L,
        )

        assertEquals(3, queue.itemCount)
        assertEquals(listOf(0, 1, 2), queue.items.map { it.position })
        assertEquals(
            listOf("1", "2", "3"),
            queue.items.map { it.sourceUri.substringAfterLast('/') },
        )
    }

    @Test
    fun tenSelectedImagesBecomeTenItemsInOrder() {
        val uris = (1..10).map { index -> "content://media/$index" }
        val queue = importAll(uris, base = null, now = 1L)

        assertEquals(10, queue.itemCount)
        assertEquals((0..9).toList(), queue.items.map { it.position })
        assertEquals(10, queue.nextPosition)
        assertEquals("1", queue.currentItem?.sourceUri?.substringAfterLast('/'))
        assertTrue(queue.items.all { it.status == PaymentStatus.QUEUED })
    }

    @Test
    fun importingIntoAnExistingQueueKeepsPositionsContinuous() {
        val first = importAll(listOf("content://media/1", "content://media/2"), null, 1L)
        val second = importAll(listOf("content://media/3"), first, 2L)

        assertEquals(3, second.itemCount)
        assertEquals(listOf(0, 1, 2), second.items.map { it.position })
    }

    @Test
    fun aDuplicateSelectionAddsNothingTwice() {
        val queue = importAll(
            listOf("content://media/1", "content://media/1", "content://media/1"),
            base = null,
            now = 1L,
        )

        assertEquals(1, queue.itemCount)
    }

    @Test
    fun anEmptySelectionLeavesTheQueueUntouched() {
        val base = importAll(listOf("content://media/1"), null, 1L)
        val unchanged = importAll(listOf("", "   "), base, 2L)

        assertEquals(1, unchanged.itemCount)
        assertEquals(base.items.map { it.id }, unchanged.items.map { it.id })
    }
}
