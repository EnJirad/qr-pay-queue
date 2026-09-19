package com.enjirad.qrqueue.domain

/**
 * One Payment Item: a single logical payment task in the queue, identified by a
 * stable id and a stable queue number, and carrying one or more QR image
 * versions.
 *
 * V0.5 does not decode QR codes, so an item carries nothing about the payment:
 * it is the app's own copy of the user's image(s), where it stands in the queue,
 * and where it stands in the hand-off flow. There is deliberately no decoded
 * payload, recipient, amount, reference, merchant or validation result here —
 * the bank app reads the QR and the user verifies everything inside it.
 *
 * Replacing the QR of an item appends a new [QrVersion]; it never changes
 * [id], [position] or the item's number.
 *
 * @param id stable id of the Payment Item (never changes when its QR changes).
 * @param position 0-based queue order of this item.
 * @param status where this item is in the hand-off flow.
 * @param createdAt when the item was first imported (wall clock, millis).
 * @param updatedAt when this item last changed (wall clock, millis).
 * @param completedAt when the user confirmed the payment, or null.
 * @param lastAttemptAt when a payment attempt last started, or null.
 * @param failureDetail a short diagnostic shown for problem states. It is never
 *   payment data and is never read from the image.
 * @param problemReason the user-selected reason when reporting a problem
 *   (e.g. "QR ใช้งานไม่ได้", "QR หมดอายุ"). Shown on the Problem tab.
 * @param versions the QR image versions of this item, oldest first. Exactly one
 *   is CURRENT.
 * @param attempts this app's own payment-attempt records, oldest first.
 */
data class QueueItem(
    val id: String,
    val position: Int,
    val status: PaymentStatus = PaymentStatus.READY,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val completedAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val failureDetail: String? = null,
    val problemReason: String? = null,
    val versions: List<QrVersion> = emptyList(),
    val attempts: List<PaymentAttempt> = emptyList(),
) {
    /** True while the item still needs work from the user. */
    val isActive: Boolean get() = status.isActive

    /** The QR version currently associated with this item, if any. */
    val currentVersion: QrVersion? get() = versions.lastOrNull { it.isCurrent }

    /** The newest version, current or not (used only to show a thumbnail). */
    val latestVersion: QrVersion? get() = versions.lastOrNull()

    /** Every version that is no longer current, oldest first. */
    val historicalVersions: List<QrVersion> get() = versions.filter { !it.isCurrent }

    /** Number of QR versions this item has had (v1, v2, ...). */
    val qrVersionCount: Int get() = versions.size

    /** The app-private path that may be shared for this item (current only). */
    val currentFilePath: String? get() = currentVersion?.filePath

    /**
     * The image to show in the card: the current QR, or the last known one while
     * the item waits for a replacement. Never used for sharing.
     */
    val previewFilePath: String? get() = currentVersion?.filePath ?: latestVersion?.filePath

    /** Object id of the current QR version, used for replacement targeting. */
    val currentVersionId: String? get() = currentVersion?.id

    /** MIME type handed to the bank for the current QR. */
    val currentMimeType: String
        get() = currentVersion?.mimeType ?: latestVersion?.mimeType ?: DEFAULT_MIME_TYPE

    /** Original file name of the current QR, for display only. */
    val currentDisplayName: String
        get() = currentVersion?.displayName ?: latestVersion?.displayName ?: DEFAULT_DISPLAY_NAME

    /** 1-based item number shown to the user ("QR #08"). */
    val itemNumber: Int get() = position + 1

    /** The stable, user-facing label of this Payment Item. */
    val itemLabel: String get() = "QR #" + itemNumber.toString().padStart(2, '0')

    /**
     * True when another QR image version may be appended: the item is in a state
     * where the user reported a problem that a new QR can fix.
     */
    val canReplaceQr: Boolean get() = status.canReplaceQr

    /** The most recent attempt recorded for this item, if any. */
    val lastAttempt: PaymentAttempt? get() = attempts.lastOrNull()

    /** When the user confirmed the payment, or null while it is unfinished. */
    val isCompletedAt: Long? get() = completedAt

    /**
     * Guarantees that exactly one version is current: the newest CURRENT version
     * wins, and if none is marked the highest version number becomes current.
     * Old versions never become current again.
     */
    fun withNormalizedVersions(): QueueItem {
        if (versions.isEmpty()) return this
        val ordered = versions.sortedBy { version -> version.versionNumber }
        val currentIndex = ordered.indexOfLast { version -> version.isCurrent }
        val keeper = if (currentIndex >= 0) currentIndex else ordered.lastIndex
        val normalized = ordered.mapIndexed { index, version ->
            if (index == keeper) {
                version.copy(status = QrVersionStatus.CURRENT)
            } else if (version.isCurrent) {
                version.copy(status = QrVersionStatus.SUPERSEDED)
            } else {
                version
            }
        }
        return if (normalized == versions) this else copy(versions = normalized)
    }

    private companion object {
        const val DEFAULT_MIME_TYPE = "image/*"
        const val DEFAULT_DISPLAY_NAME = "QR image"
    }
}
