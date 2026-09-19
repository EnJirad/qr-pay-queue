package com.enjirad.qrqueue.domain

/**
 * One image in the queue.
 *
 * V0.4 does not decode QR codes, so an item carries nothing about the payment:
 * it is just the app's own copy of one image the user picked, plus where it
 * stands in the queue. There is deliberately no decoded payload, recipient,
 * amount, reference, merchant or validation result on this model — K PLUS reads
 * the QR and the user verifies everything inside K PLUS.
 *
 * @param sourceUri the picker URI the image was imported from (reference only;
 *   the original gallery file is never moved, changed or deleted).
 * @param storedImagePath app-private copy of the image that is actually shared.
 * @param displayName the original file name, for display only.
 * @param position 0-based order of this item in the queue.
 * @param status where this item is in the hand-off flow.
 * @param createdAt when the image was imported (wall clock, millis).
 * @param updatedAt when this item last changed (wall clock, millis).
 * @param failureDetail a short diagnostic reason shown when [status] is
 *   [PaymentStatus.FAILED]. It is never payment data and is never read from the
 *   image; it only tells the user why the hand-off could not start.
 */
data class QueueItem(
    val id: String,
    val position: Int,
    val sourceUri: String,
    val storedImagePath: String,
    val displayName: String,
    val mimeType: String,
    val status: PaymentStatus = PaymentStatus.QUEUED,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val failureDetail: String? = null,
) {
    /** True while the item still needs work from the user. */
    val isActive: Boolean get() = status.isActive
}
