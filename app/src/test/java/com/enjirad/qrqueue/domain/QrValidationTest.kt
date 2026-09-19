package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrValidationTest {

    private val validPayload =
        "00020101021229370016A0000006770101110113006681234567853037645406750.005802TH5912SOMCHAI SHOP6007Bangkok62160512INV-2026-00163049E28"

    @Test
    fun acceptsAValidPromptPayQr() {
        val outcome = QrValidation.evaluate(QrDecodeResult.Decoded(validPayload), emptySet())
        assertTrue(outcome is ItemOutcome.Accepted)
        val payload = (outcome as ItemOutcome.Accepted).payload
        assertEquals(75_000L, payload.amountSatang)
        assertEquals("081-234-5678", payload.recipient)
    }

    @Test
    fun unreadableImageIsRejectedAsUnreadable() {
        val outcome = QrValidation.evaluate(QrDecodeResult.Unreadable, emptySet())
        assertEquals(ValidationIssue.UNREADABLE_IMAGE, (outcome as ItemOutcome.Rejected).issue)
        assertEquals(PaymentStatus.INVALID, outcome.issue.status)
    }

    @Test
    fun imageWithoutQrIsRejected() {
        val outcome = QrValidation.evaluate(QrDecodeResult.NotFound, emptySet())
        assertEquals(ValidationIssue.QR_NOT_FOUND, (outcome as ItemOutcome.Rejected).issue)
    }

    @Test
    fun decoderFailureIsRejectedWithItsDetail() {
        val outcome = QrValidation.evaluate(QrDecodeResult.Failed("reader gave up"), emptySet())
        val rejected = outcome as ItemOutcome.Rejected
        assertEquals(ValidationIssue.QR_NOT_FOUND, rejected.issue)
        assertEquals("reader gave up", rejected.detail)
    }

    @Test
    fun anImageWithSeveralDifferentQrCodesIsRejectedInsteadOfGuessed() {
        val outcome = QrValidation.evaluate(
            QrDecodeResult.Ambiguous(listOf(validPayload, "00020101021153037645403300.00")),
            emptySet(),
        )
        val rejected = outcome as ItemOutcome.Rejected
        assertEquals(ValidationIssue.MULTIPLE_QR_CODES, rejected.issue)
        assertEquals(PaymentStatus.INVALID, rejected.issue.status)
        assertTrue(rejected.detail?.contains("2") == true)
    }

    @Test
    fun duplicatePayloadIsRejectedEvenWhenTheQrIsValid() {
        val accepted = setOf(QrValidation.payloadKey(validPayload))
        val outcome = QrValidation.evaluate(QrDecodeResult.Decoded(validPayload), accepted)
        val rejected = outcome as ItemOutcome.Rejected
        assertEquals(ValidationIssue.DUPLICATE_PAYLOAD, rejected.issue)
        assertEquals(PaymentStatus.DUPLICATE, rejected.issue.status)
    }

    @Test
    fun duplicateDetectionIgnoresSurroundingWhitespace() {
        val accepted = setOf(QrValidation.payloadKey("  $validPayload  "))
        val outcome = QrValidation.evaluate(QrDecodeResult.Decoded(validPayload), accepted)
        assertEquals(ValidationIssue.DUPLICATE_PAYLOAD, (outcome as ItemOutcome.Rejected).issue)
    }

    @Test
    fun aNonPaymentQrIsRejectedAsMalformed() {
        val outcome = QrValidation.evaluate(QrDecodeResult.Decoded("https://example.com/pay"), emptySet())
        assertEquals(ValidationIssue.MALFORMED_PAYLOAD, (outcome as ItemOutcome.Rejected).issue)
    }

    @Test
    fun acceptedPayloadKeysCollectOnlyRealPayloads() {
        val items = listOf(
            QueueItem(id = "1", fileName = "a.png", rawPayload = validPayload, status = PaymentStatus.READY),
            QueueItem(id = "2", fileName = "b.png", status = PaymentStatus.INVALID),
        )
        val keys = QrValidation.acceptedPayloadKeys(items)
        assertEquals(setOf(QrValidation.payloadKey(validPayload)), keys)
    }
}
