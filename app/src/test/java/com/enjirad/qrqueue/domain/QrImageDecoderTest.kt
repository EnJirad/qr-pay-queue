package com.enjirad.qrqueue.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the real decoding path: a QR code is rendered to pixels with ZXing's
 * encoder and then read back through [QrImageDecoder], the same object the app
 * uses on imported screenshots. Nothing here is mocked or hard-coded.
 */
class QrImageDecoderTest {

    private val payload =
        "00020101021229370016A0000006770101110113006681234567853037645406750.005802TH5912SOMCHAI SHOP6007Bangkok62160512INV-2026-00163049E28"

    private val otherPayload =
        "00020101021229370016A00000067701011101130066898765432153037645406450.005802TH630475AC"

    @Test
    fun decodesARenderedQrCode() {
        val size = 320
        val result = QrImageDecoder.decodeArgb(renderQr(payload, size), size, size)
        assertTrue(result is QrDecodeResult.Decoded)
        assertEquals(payload, (result as QrDecodeResult.Decoded).text)
    }

    @Test
    fun decodesAndParsesARealPromptPayQr() {
        val size = 320
        val decoded = QrImageDecoder.decodeArgb(renderQr(payload, size), size, size)
        val outcome = QrValidation.evaluate(decoded, emptySet())
        assertTrue(outcome is ItemOutcome.Accepted)
        val parsed = (outcome as ItemOutcome.Accepted).payload
        assertEquals(75_000L, parsed.amountSatang)
        assertEquals("081-234-5678", parsed.recipient)
    }

    @Test
    fun anImageWithTwoDifferentQrCodesIsReportedAsAmbiguous() {
        val size = 600
        val half = size / 2
        val pixels = IntArray(size * size) { WHITE }
        drawQr(pixels, size, 0, 0, half, payload)
        drawQr(pixels, size, half, half, half, otherPayload)

        val result = QrImageDecoder.decodeArgb(pixels, size, size)

        assertTrue(result is QrDecodeResult.Ambiguous)
        val payloads = (result as QrDecodeResult.Ambiguous).payloads
        assertEquals(2, payloads.size)
        assertTrue(payloads.contains(payload))
        assertTrue(payloads.contains(otherPayload))
    }

    @Test
    fun anImageWithOneQrCodeIsNotAmbiguous() {
        val size = 600
        val pixels = IntArray(size * size) { WHITE }
        drawQr(pixels, size, 0, 0, size / 2, payload)

        val result = QrImageDecoder.decodeArgb(pixels, size, size)

        assertTrue(result is QrDecodeResult.Decoded)
        assertEquals(payload, (result as QrDecodeResult.Decoded).text)
    }

    @Test
    fun reportsNoQrForAnImageWithoutOne() {
        val size = 200
        val blank = IntArray(size * size) { WHITE }
        val result = QrImageDecoder.decodeArgb(blank, size, size)
        assertTrue(
            "a blank image must never decode to a QR payload",
            result is QrDecodeResult.NotFound || result is QrDecodeResult.Failed,
        )
    }

    @Test
    fun reportsUnreadableForImpossiblePixelData() {
        assertEquals(QrDecodeResult.Unreadable, QrImageDecoder.decodeArgb(IntArray(4), 64, 64))
        assertEquals(QrDecodeResult.Unreadable, QrImageDecoder.decodeArgb(IntArray(0), 0, 0))
    }

    private fun renderQr(text: String, size: Int): IntArray {
        val pixels = IntArray(size * size) { WHITE }
        drawQr(pixels, size, 0, 0, size, text)
        return pixels
    }

    /** Draws one QR code into a region of an existing ARGB canvas. */
    private fun drawQr(
        canvas: IntArray,
        canvasWidth: Int,
        left: Int,
        top: Int,
        size: Int,
        text: String,
    ) {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                val canvasX = left + x
                val canvasY = top + y
                if (canvasX >= canvasWidth || canvasY >= canvasWidth) continue
                canvas[canvasY * canvasWidth + canvasX] = if (matrix.get(x, y)) BLACK else WHITE
            }
        }
    }

    private companion object {
        // 0xFF000000 and 0xFFFFFFFF written as Int literals.
        const val BLACK = -0x1000000
        const val WHITE = -0x1
    }
}
