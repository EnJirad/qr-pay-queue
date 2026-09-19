package com.enjirad.qrqueue.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.enjirad.qrqueue.domain.QrDecodeResult
import com.enjirad.qrqueue.domain.QrImageDecoder
import java.io.File

/** ARGB pixels of one image, ready for the ZXing decoder. */
data class ArgbPixels(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    /** BitmapFactory sample size used to load them; 1 means full resolution. */
    val sampleSize: Int,
)

/**
 * Loads imported image files with Android's own decoders and hands the pixels to
 * [QrImageDecoder].
 *
 * Screenshots are large, so the first decode attempt runs on a downscaled copy.
 * If no QR is found and the image really was downscaled, one full-resolution
 * retry follows — a QR that is small in a big screenshot is still readable.
 */
object QrImageFiles {

    const val PREFERRED_MAX_DIMENSION = 1600
    const val PREVIEW_MAX_DIMENSION = 1080

    /** Above this pixel count the full-resolution retry is skipped (memory). */
    private const val FULL_RESOLUTION_PIXEL_BUDGET = 8_000_000L

    /** "No downscaling" for the retry pass. */
    private const val FULL_RESOLUTION_MAX_DIMENSION = Int.MAX_VALUE

    fun decodeQr(file: File): QrDecodeResult = runCatching {
        val sampled = loadArgbPixels(file, PREFERRED_MAX_DIMENSION) ?: return@runCatching QrDecodeResult.Unreadable
        val sampledResult = QrImageDecoder.decodeArgb(sampled.pixels, sampled.width, sampled.height)
        if (sampledResult !is QrDecodeResult.NotFound || sampled.sampleSize <= 1) {
            return@runCatching sampledResult
        }
        val fullPixelCount = sampled.width.toLong() * sampled.sampleSize *
            sampled.height.toLong() * sampled.sampleSize
        if (fullPixelCount > FULL_RESOLUTION_PIXEL_BUDGET) return@runCatching sampledResult
        val full = loadArgbPixels(file, FULL_RESOLUTION_MAX_DIMENSION) ?: return@runCatching sampledResult
        QrImageDecoder.decodeArgb(full.pixels, full.width, full.height)
    }.getOrElse { error ->
        QrDecodeResult.Failed(error.message ?: "the image could not be decoded")
    }

    /** Bitmap for on-screen display, downscaled to keep the UI light. */
    fun loadPreviewBitmap(file: File, maxDimension: Int = PREVIEW_MAX_DIMENSION): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(file, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    fun loadArgbPixels(file: File, maxDimension: Int): ArgbPixels? {
        val sampleSize = sampleSizeFor(file, maxDimension)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val result = ArgbPixels(pixels, bitmap.width, bitmap.height, sampleSize)
        bitmap.recycle()
        return result
    }

    private fun sampleSizeFor(file: File, maxDimension: Int): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        val longestSide = maxOf(width, height)
        while (sampleSize < 64 && longestSide / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
