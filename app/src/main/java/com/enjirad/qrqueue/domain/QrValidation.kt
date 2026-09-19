package com.enjirad.qrqueue.domain

/** Outcome of validating one imported image. */
sealed interface ItemOutcome {
    data class Accepted(val payload: QrPayload) : ItemOutcome
    data class Rejected(val issue: ValidationIssue, val detail: String? = null) : ItemOutcome
}

/**
 * Validation layer between "we read pixels" and "this is a payable queue item".
 *
 * Order matters and mirrors the promise made to the user:
 * an image that cannot be read is rejected as unreadable, an image without a QR
 * is rejected, a QR that repeats one already in the queue is rejected as a
 * duplicate, and only then is the payload parsed. Nothing is silently skipped
 * and nothing is assumed.
 */
object QrValidation {

    /** Normalised identity of a decoded payload, used for duplicate detection. */
    fun payloadKey(rawPayload: String): String = rawPayload.trim()

    /**
     * @param decode what the image decoder returned for this image.
     * @param acceptedPayloadKeys keys of payloads already accepted in the queue.
     */
    fun evaluate(decode: QrDecodeResult, acceptedPayloadKeys: Set<String>): ItemOutcome {
        val text = when (decode) {
            is QrDecodeResult.Decoded -> decode.text
            QrDecodeResult.Unreadable -> return ItemOutcome.Rejected(ValidationIssue.UNREADABLE_IMAGE)
            QrDecodeResult.NotFound -> return ItemOutcome.Rejected(ValidationIssue.QR_NOT_FOUND)
            is QrDecodeResult.Failed -> return ItemOutcome.Rejected(ValidationIssue.QR_NOT_FOUND, decode.message)
        }
        if (payloadKey(text) in acceptedPayloadKeys) {
            return ItemOutcome.Rejected(ValidationIssue.DUPLICATE_PAYLOAD)
        }
        return when (val parsed = EmvCoQrParser.parse(text)) {
            is ParseOutcome.Accepted -> ItemOutcome.Accepted(parsed.payload)
            is ParseOutcome.Rejected -> ItemOutcome.Rejected(parsed.issue, parsed.detail)
        }
    }

    /** Keys of the payloads already accepted in a queue, for duplicate checks. */
    fun acceptedPayloadKeys(items: List<QueueItem>): Set<String> =
        items.mapNotNull { it.rawPayload }.map(::payloadKey).toSet()
}
