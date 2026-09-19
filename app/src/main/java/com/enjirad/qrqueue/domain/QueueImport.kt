package com.enjirad.qrqueue.domain

import java.util.UUID

/**
 * Pure helpers for the multi-image import step: selected-URI de-duplication,
 * file naming and turning a validation outcome into a queue item.
 */
object QueueImport {

    private val SUPPORTED_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "heic", "heif", "bmp")

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
        else -> "image/*"
    }

    /**
     * Builds the queue item for one imported image.
     *
     * An accepted payload becomes a [PaymentStatus.READY] item with everything
     * the QR actually contained. A rejected image becomes an item carrying the
     * specific issue and the status that issue maps to — it stays visible in the
     * review list instead of disappearing.
     */
    fun buildItem(
        id: String,
        fileName: String,
        sourceUri: String?,
        storedImagePath: String?,
        mimeType: String?,
        outcome: ItemOutcome,
        importedAtMillis: Long,
    ): QueueItem = when (outcome) {
        is ItemOutcome.Accepted -> QueueItem(
            id = id,
            fileName = fileName,
            sourceUri = sourceUri,
            storedImagePath = storedImagePath,
            mimeType = mimeType,
            amountSatang = outcome.payload.amountSatang,
            recipient = outcome.payload.recipient,
            reference = outcome.payload.reference,
            status = PaymentStatus.READY,
            issue = null,
            payloadLabel = outcome.payload.format.label,
            rawPayload = outcome.payload.raw,
            importedAtMillis = importedAtMillis,
        )
        is ItemOutcome.Rejected -> QueueItem(
            id = id,
            fileName = fileName,
            sourceUri = sourceUri,
            storedImagePath = storedImagePath,
            mimeType = mimeType,
            amountSatang = null,
            recipient = null,
            reference = null,
            status = outcome.issue.status,
            issue = outcome.issue,
            issueDetail = outcome.detail,
            payloadLabel = null,
            rawPayload = null,
            importedAtMillis = importedAtMillis,
        )
    }

    private const val DEFAULT_EXTENSION = "img"
}
