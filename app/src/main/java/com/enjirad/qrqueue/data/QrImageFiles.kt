package com.enjirad.qrqueue.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Image display helpers for the app-private image copies.
 *
 * V0.4 does not decode QR codes, so this object no longer reads pixels for a
 * decoder: it only produces downscaled bitmaps so the queue screen can show the
 * current image without loading full-resolution screenshots into memory.
 */
object QrImageFiles {

    const val PREVIEW_MAX_DIMENSION = 1080

    /** Bitmap for on-screen display, downscaled to keep the UI light. */
    fun loadPreviewBitmap(file: File, maxDimension: Int = PREVIEW_MAX_DIMENSION): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(file, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
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
