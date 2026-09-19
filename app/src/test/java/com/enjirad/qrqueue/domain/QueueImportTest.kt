package com.enjirad.qrqueue.domain

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The import step is pure apart from the byte copy, which needs a device. These
 * tests cover the parts that can run without one: URI de-duplication, storage
 * naming, content fingerprints, and the fact that N selected images become N
 * queued Payment Items in order, each with one current QR version.
 */
class QueueImportTest {

    /** Mirrors the ViewModel import loop for one successfully copied image. */
    private fun importAll(uris: List<String>, base: PaymentQueue?, now: Long): PaymentQueue {
        val queue = base ?: PaymentQueue.create("queue-1", now)
        var nextPosition = queue.nextPosition
        val items = QueueImport.dedupeSourceUris(uris).map { uri ->
            val itemId = QueueImport.newItemId()
            QueueImport.buildItem(
                id = itemId,
                position = nextPosition++,
                sourceUri = uri,
                storedImagePath = "/data/user/0/com.enjirad.qrqueue/files/qrqueue/images/$itemId.png",
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
    fun itemIdsAndVersionIdsAreUnique() {
        assertNotEquals(QueueImport.newItemId(), QueueImport.newItemId())
        assertNotEquals(QueueImport.newVersionId(), QueueImport.newVersionId())
    }

    @Test
    fun fingerprintsAreDeterministicAndContentSensitive() {
        val one = "qr-image-one".toByteArray(StandardCharsets.UTF_8)
        val two = "qr-image-two".toByteArray(StandardCharsets.UTF_8)

        assertEquals(QueueImport.fingerprintOf(one), QueueImport.fingerprintOf(one.copyOf()))
        assertNotEquals(QueueImport.fingerprintOf(one), QueueImport.fingerprintOf(two))
        assertEquals(64, QueueImport.fingerprintOf(one).length)
    }

    @Test
    fun aCopiedImageBecomesAReadyItemWithOneCurrentVersion() {
        val item = QueueImport.buildItem(
            id = "item-1",
            position = 2,
            sourceUri = "content://media/1",
            storedImagePath = "/data/user/0/com.enjirad.qrqueue/files/qrqueue/images/item-1.png",
            displayName = "screenshot.png",
            mimeType = "image/png",
            nowMillis = 1_700_000_000_000L,
            fingerprint = "abc123",
        )

        assertEquals("item-1", item.id)
        assertEquals(2, item.position)
        assertEquals(PaymentStatus.READY, item.status)
        assertEquals(1_700_000_000_000L, item.createdAt)
        assertEquals(1_700_000_000_000L, item.updatedAt)
        assertNull(item.failureDetail)
        assertNull(item.completedAt)
        assertTrue(item.isActive)

        // One current QR version, v1, pointing at the copied file.
        assertEquals(1, item.qrVersionCount)
        assertEquals("item-1", item.versions.single().paymentItemId)
        assertEquals(QueueImport.FIRST_VERSION_NUMBER, item.versions.single().versionNumber)
        assertTrue(item.versions.single().isCurrent)
        assertEquals("screenshot.png", item.currentDisplayName)
        assertEquals("image/png", item.currentMimeType)
        assertEquals("abc123", item.currentVersion?.fingerprint)
        assertEquals("content://media/1", item.currentVersion?.sourceUri)
        assertEquals("QR #03", item.itemLabel)
    }

    @Test
    fun oneSelectedImageBecomesOneReadyItem() {
        val queue = importAll(listOf("content://media/1"), base = null, now = 1L)

        assertEquals(1, queue.itemCount)
        assertEquals(listOf(0), queue.items.map { it.position })
        assertEquals(PaymentStatus.READY, queue.items.first().status)
        assertTrue(queue.canStartHandoff(queue.items.first().id))
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
            queue.items.map { item -> item.currentVersion?.sourceUri?.substringAfterLast('/') },
        )
    }

    @Test
    fun tenSelectedImagesBecomeTenItemsInOrder() {
        val uris = (1..10).map { index -> "content://media/$index" }
        val queue = importAll(uris, base = null, now = 1L)

        assertEquals(10, queue.itemCount)
        assertEquals((0..9).toList(), queue.items.map { it.position })
        assertEquals(10, queue.nextPosition)
        assertEquals("1", queue.items.first().currentVersion?.sourceUri?.substringAfterLast('/'))
        assertTrue(queue.items.all { item -> item.status == PaymentStatus.READY })
        // Every imported image is offered to the user, one at a time.
        assertTrue(queue.items.all { item -> queue.canStartHandoff(item.id) })
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

    @Test
    fun theQueueRecognisesAStoredFingerprint() {
        val item = QueueImport.buildItem(
            id = "item-1",
            position = 0,
            sourceUri = "content://media/1",
            storedImagePath = "/tmp/item-1.png",
            displayName = "1.png",
            mimeType = "image/png",
            nowMillis = 1L,
            fingerprint = "hash-one",
        )
        val queue = PaymentQueue.create("queue-1", 1L, listOf(item))

        assertTrue(queue.hasFingerprint("hash-one"))
        assertFalse(queue.hasFingerprint("hash-two"))
        assertFalse(queue.hasFingerprint(null))
        assertFalse(queue.hasFingerprint(""))
    }
}
