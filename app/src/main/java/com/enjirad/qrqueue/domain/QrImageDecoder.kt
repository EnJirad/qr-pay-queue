package com.enjirad.qrqueue.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader

/** What came back from trying to read a QR code out of one image. */
sealed interface QrDecodeResult {
    /** Exactly one QR code was found and decoded to [text]. */
    data class Decoded(val text: String) : QrDecodeResult

    /**
     * The image contains more than one distinct QR code.
     *
     * Real screenshots often carry a payment QR plus an unrelated code. Picking
     * one of them would risk paying the wrong code, so the image is reported as
     * ambiguous and never becomes payable.
     */
    data class Ambiguous(val payloads: List<String>) : QrDecodeResult

    /** The image was readable, but it contains no QR code. */
    data object NotFound : QrDecodeResult

    /** The image itself could not be read (missing file, unsupported format). */
    data object Unreadable : QrDecodeResult

    /** The decoder failed for another reason; [message] is diagnostic only. */
    data class Failed(val message: String) : QrDecodeResult
}

/**
 * Real QR decoding with ZXing core.
 *
 * This object works on plain ARGB pixels instead of Android bitmaps, so the
 * exact code path the app runs on device is also exercised by JVM unit tests
 * (see `QrImageDecoderTest`), which encodes QR codes and reads them back.
 *
 * Only the QR_CODE format is configured: this product handles payment QRs, and
 * one decoder with one responsibility keeps the behaviour predictable.
 *
 * After a successful decode the decoder scans the rest of the image for further
 * QR codes (ZXing's multi-barcode reader). One distinct payload means a normal
 * result; two or more mean the image is ambiguous and must not be paid
 * automatically.
 */
object QrImageDecoder {

    fun decodeArgb(pixels: IntArray, width: Int, height: Int): QrDecodeResult {
        if (width <= 0 || height <= 0 || pixels.size < width * height) {
            return QrDecodeResult.Unreadable
        }
        return try {
            val image = binaryBitmap(pixels, width, height)
            val reader = MultiFormatReader()
            reader.setHints(decodeHints())
            val text = reader.decodeWithState(image).text
            if (text.isNullOrBlank()) {
                QrDecodeResult.NotFound
            } else {
                val payloads = distinctPayloads(image)
                if (payloads.size > 1) QrDecodeResult.Ambiguous(payloads) else QrDecodeResult.Decoded(text)
            }
        } catch (notFound: NotFoundException) {
            QrDecodeResult.NotFound
        } catch (readerException: ReaderException) {
            QrDecodeResult.Failed(readerException.message ?: "the decoder rejected this image")
        } catch (illegalArgument: IllegalArgumentException) {
            QrDecodeResult.Failed(illegalArgument.message ?: "the image data could not be interpreted")
        }
    }

    private fun binaryBitmap(pixels: IntArray, width: Int, height: Int): BinaryBitmap =
        BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))

    /**
     * Extra pass that looks for further QR codes in the same image.
     *
     * This is a safety check on top of a successful decode, so if the check
     * itself cannot run the single decoded payload is still used: an internal
     * helper failure must not throw away a QR the user can see and verify.
     */
    private fun distinctPayloads(image: BinaryBitmap): List<String> = try {
        GenericMultipleBarcodeReader(MultiFormatReader())
            .decodeMultiple(image, decodeHints())
            .mapNotNull { result -> result.text }
            .distinct()
    } catch (notFound: NotFoundException) {
        emptyList()
    } catch (readerException: ReaderException) {
        emptyList()
    } catch (runtimeException: RuntimeException) {
        emptyList()
    }

    private fun decodeHints(): Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )
}
