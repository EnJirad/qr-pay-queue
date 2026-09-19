package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueImportTest {

    private val payload = QrPayload(
        raw = "00020101021229370016A0000006770101110113006681234567853037645406750.005802TH6304ABCD",
        format = QrFormat.PROMPTPAY,
        recipientKind = RecipientKind.MOBILE_NUMBER,
        recipient = "081-234-5678",
        amountSatang = 75_000L,
        reference = "INV-1",
    )

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
    fun anAcceptedPayloadBecomesAReadyItemWithOnlyRealValues() {
        val item = QueueImport.buildItem(
            id = "item-1",
            fileName = "screenshot.png",
            sourceUri = "content://media/1",
            storedImagePath = "/data/app/qrqueue/images/item-1.png",
            mimeType = "image/png",
            outcome = ItemOutcome.Accepted(payload),
        )

        assertEquals(PaymentStatus.READY, item.status)
        assertEquals(75_000L, item.amountSatang)
        assertEquals("081-234-5678", item.recipient)
        assertEquals("INV-1", item.reference)
        assertEquals("PromptPay", item.payloadLabel)
        assertEquals(payload.raw, item.rawPayload)
        assertNull(item.issue)
        assertTrue(item.isPayable)
        assertTrue(item.hasKnownAmount)
    }

    @Test
    fun aRejectedImageBecomesAVisibleItemWithItsReason() {
        val item = QueueImport.buildItem(
            id = "item-2",
            fileName = "broken.png",
            sourceUri = "content://media/2",
            storedImagePath = null,
            mimeType = null,
            outcome = ItemOutcome.Rejected(ValidationIssue.QR_NOT_FOUND, "no finder pattern"),
        )

        assertEquals(PaymentStatus.INVALID, item.status)
        assertEquals(ValidationIssue.QR_NOT_FOUND, item.issue)
        assertEquals("no finder pattern", item.issueDetail)
        assertNull(item.amountSatang)
        assertNull(item.recipient)
        assertNull(item.rawPayload)
        assertTrue(item.isPayable.not())
    }

    @Test
    fun aDuplicateImageIsExcludedFromTheQueue() {
        val item = QueueImport.buildItem(
            id = "item-3",
            fileName = "again.png",
            sourceUri = "content://media/3",
            storedImagePath = "/data/app/qrqueue/images/item-3.png",
            mimeType = "image/png",
            outcome = ItemOutcome.Rejected(ValidationIssue.DUPLICATE_PAYLOAD),
        )

        assertEquals(PaymentStatus.DUPLICATE, item.status)
        assertEquals(ValidationIssue.DUPLICATE_PAYLOAD, item.issue)
        assertTrue(item.status.isExcludedFromQueue)
    }

    @Test
    fun itemIdsAreUnique() {
        assertNotEquals(QueueImport.newItemId(), QueueImport.newItemId())
    }
}
