package com.enjirad.qrqueue.domain

import java.util.UUID

/**
 * Progress of a multi-image import, in images.
 *
 * `processed` counts every selected image the app has finished with — copied or
 * failed — and `total` is how many the user selected, so the progress screen can
 * say "3 / 5" and reaches "5 / 5" exactly once.
 */
data class ImportProgress(val processed: Int, val total: Int) {

    /** 1 image, 3 images or 10 images: any count the picker allows. */
    val fraction: Float
        get() = if (total <= 0) 0f else (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    /** True once every selected image has been processed. */
    val isComplete: Boolean get() = processed >= total

    /** The state after one more selected image has been handled. */
    fun advance(): ImportProgress = copy(processed = (processed + 1).coerceAtMost(total))

    companion object {
        fun of(total: Int): ImportProgress = ImportProgress(processed = 0, total = total)
    }
}

/**
 * What one import run actually did, so the app can report it honestly instead of
 * only reporting success.
 */
data class ImportSummary(val imported: Int, val failed: Int) {

    /** How many images the user selected for this run. */
    val selected: Int get() = imported + failed

    /** True when nothing the user selected was left behind. */
    val allImported: Boolean get() = failed == 0

    /** True when at least one selected image could not be copied into the app. */
    val hasFailures: Boolean get() = failed > 0
}

/**
 * Pure helpers for the multi-image import step: selected-URI de-duplication,
 * storage file naming, turning one successfully copied image into a queue item,
 * and the progress/again reporting. Nothing here reads or inspects image content.
 */
object QueueImport {

    private val SUPPORTED_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "heic", "heif", "bmp")

    /**
     * Whether the import screen must still be shown.
     *
     * The progress screen exists only while images are still being processed, so
     * the app returns to the queue by itself as soon as the last selected image
     * has been handled. It must never stay on a completed "5 / 5" step and never
     * wait for a Continue, Done or Next tap.
     */
    fun isImportRunning(progress: ImportProgress?): Boolean =
        progress != null && !progress.isComplete

    /**
     * Drops blank entries and repeated URIs while keeping the user's selection
     * order. Selecting the same file twice must not create two queue items.
     */
    fun dedupeSourceUris(uris: List<String>): List<String> {
        val unique = LinkedHashSet<String>()
        uris.forEach { uri ->
            val key = uri.trim()
            if (key.isNotEmpty()) unique += key
        }
        return unique.toList()
    }

    fun newItemId(): String = UUID.randomUUID().toString()

    /** Storage extension for an imported image, from its MIME type or its name. */
    fun fileExtensionFor(mimeType: String?, displayName: String?): String {
        val fromMime = when (mimeType?.lowercase()) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/bmp" -> "bmp"
            else -> null
        }
        if (fromMime != null) return fromMime
        val fromName = displayName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() && it.length <= 4 && it.all { character -> character.isLetterOrDigit() } }
        return if (fromName != null && fromName in SUPPORTED_EXTENSIONS) fromName else DEFAULT_EXTENSION
    }

    /** MIME type to hand to another app when sharing the stored image. */
    fun mimeTypeForExtension(extension: String): String = when (extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic", "heif" -> "image/heic"
        "bmp" -> "image/bmp"
        else -> ANY_IMAGE_MIME_TYPE
    }

    /**
     * Builds the queue item for one image that was successfully copied into
     * app-private storage. The image starts as [PaymentStatus.QUEUED]; the app
     * knows nothing about what the QR contains, and that is intentional.
     */
    fun buildItem(
        id: String,
        position: Int,
        sourceUri: String,
        storedImagePath: String,
        displayName: String,
        mimeType: String,
        nowMillis: Long,
    ): QueueItem = QueueItem(
        id = id,
        position = position,
        sourceUri = sourceUri,
        storedImagePath = storedImagePath,
        displayName = displayName,
        mimeType = mimeType,
        status = PaymentStatus.QUEUED,
        createdAt = nowMillis,
        updatedAt = nowMillis,
    )

    private const val DEFAULT_EXTENSION = "img"

    /** The generic image type, used when an image has no usable declared type. */
    const val ANY_IMAGE_MIME_TYPE = "image/*"
}
