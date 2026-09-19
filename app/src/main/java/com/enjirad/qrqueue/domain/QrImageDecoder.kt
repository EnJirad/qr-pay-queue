package com.enjirad.qrqueue.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/** What came back from trying to read a QR code out of one image. */
sealed interface QrDecodeResult {
    /** A QR code was found and decoded to [text]. */
    data class Decoded(val text: String) : QrDecodeResult

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
 * (see `QrImageDecoderTest`), which encodes a QR and reads it back.
 *
 * Only the QR_CODE format is configured: this product handles payment QRs, and
 * one decoder with one responsibility keeps the behaviour predictable.
 */
object QrImageDecoder {

    fun decodeArgb(pixels: IntArray, width: Int, height: Int): QrDecodeResult {
        if (width <= 0 || height <= 0 || pixels.size < width * height) {
            return QrDecodeResult.Unreadable
        }
        val reader = MultiFormatReader()
        return try {
            val source = RGBLuminanceSource(width, height, pixels)
            val image = BinaryBitmap(HybridBinarizer(source))
            reader.setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )
            val text = reader.decodeWithState(image).text
            if (text.isNullOrBlank()) QrDecodeResult.NotFound else QrDecodeResult.Decoded(text)
        } catch (notFound: NotFoundException) {
            QrDecodeResult.NotFound
        } catch (readerException: ReaderException) {
            QrDecodeResult.Failed(readerException.message ?: "the decoder rejected this image")
        } catch (illegalArgument: IllegalArgumentException) {
            QrDecodeResult.Failed(illegalArgument.message ?: "the image data could not be interpreted")
        } finally {
            reader.reset()
        }
    }
}
