package com.enjirad.qrqueue.domain

/** Where one QR image version stands in the history of its Payment Item. */
enum class QrVersionStatus {
    /** The version currently associated with the Payment Item. */
    CURRENT,

    /** The user reported that this QR cannot be used. */
    UNUSABLE,

    /** Replaced while it was still usable (kept only as history). */
    SUPERSEDED,
}

/**
 * One QR image version of a Payment Item.
 *
 * Replacing a QR never creates a new Payment Item: the item id stays stable and
 * a new [QrVersion] is appended, so `Payment Item #08` keeps its identity while
 * its QR moves from v1 to v2.
 *
 * Exactly one version of an item is [QrVersionStatus.CURRENT] at a time, and an
 * old version never becomes current again by itself.
 *
 * @param id stable id of this version (used by payment attempts).
 * @param paymentItemId the Payment Item (`QueueItem.id`) this version belongs to.
 * @param filePath the app-private copy of the image that is actually shared.
 * @param createdAt when this version was imported (wall clock, millis).
 * @param versionNumber 1-based version number inside the item.
 * @param status the lifecycle state of this version.
 * @param mimeType the image MIME type handed to the bank.
 * @param displayName the original file name, for display only.
 * @param sourceUri the picker URI this version was imported from (reference only).
 * @param fingerprint deterministic content hash used to reject exact duplicates.
 *   It is never a perceptual or QR-content comparison.
 */
data class QrVersion(
    val id: String,
    val paymentItemId: String,
    val filePath: String,
    val createdAt: Long,
    val versionNumber: Int,
    val status: QrVersionStatus = QrVersionStatus.CURRENT,
    val mimeType: String = "image/*",
    val displayName: String = "QR image",
    val sourceUri: String = "",
    val fingerprint: String? = null,
) {
    /** True when this is the version currently used for the Payment Item. */
    val isCurrent: Boolean get() = status == QrVersionStatus.CURRENT
}

/**
 * One payment attempt of a Payment Item, recorded in this app only.
 *
 * These are the app's own workflow records ("the user started a hand-off with QR
 * v2 at 19:35"), never bank transaction records, and they are never used to
 * decide whether the payment succeeded.
 */
enum class PaymentAttemptResult {
    /** The hand-off was started (the item entered SHARING). */
    STARTED,

    /** The bank app was launched with the image; the result is unknown. */
    LAUNCHED,

    /** This app could not complete the hand-off. Nothing reached the bank. */
    FAILED,

    /** The user reported that this QR cannot be used. */
    QR_UNUSABLE,

    /** The user explicitly confirmed that the payment was completed. */
    COMPLETED,

    /** The result could not be determined. */
    UNKNOWN,
}

/**
 * One recorded payment attempt for a Payment Item.
 *
 * @param id stable id of the attempt.
 * @param paymentItemId the Payment Item this attempt belongs to.
 * @param qrVersionId the QR version that was used for this attempt.
 * @param startedAt when the attempt started (wall clock, millis).
 * @param result the workflow outcome recorded by this app.
 * @param reason a short human-readable diagnostic, when there is one.
 */
data class PaymentAttempt(
    val id: String,
    val paymentItemId: String,
    val qrVersionId: String,
    val startedAt: Long,
    val result: PaymentAttemptResult,
    val reason: String? = null,
)
