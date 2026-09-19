package com.enjirad.qrqueue.domain

/**
 * The ordered set of images the user imported, and where the run currently is.
 *
 * This class is the whole V0.4 workflow as pure data:
 *
 * ```
 * QUEUED → SHARING → WAITING_USER → COMPLETED
 *                ↘ FAILED
 * WAITING_USER → UNKNOWN
 * ```
 *
 * Safety rules encoded here and never to be weakened:
 *
 * - Only the user's explicit "done" moves an item to [PaymentStatus.COMPLETED].
 *   Sharing an image, opening K PLUS, or coming back from K PLUS settles
 *   nothing.
 * - The current item is always the first item the user has not completed, so a
 *   [PaymentStatus.FAILED] or [PaymentStatus.UNKNOWN] item stops the queue
 *   instead of being skipped.
 * - A failed share (nothing was handed off) can be shared again; an
 *   [PaymentStatus.UNKNOWN] result can never be shared again or completed
 *   without an explicit user action.
 *
 * The current item is derived from the item statuses instead of being stored, so
 * a restored queue can never point at the wrong image.
 */
data class PaymentQueue(
    val queueId: String,
    val createdAt: Long,
    val items: List<QueueItem> = emptyList(),
    /** Last time anything in this queue changed (wall clock, millis). */
    val updatedAtMillis: Long = 0L,
) {

    /** First item the user has not completed — the one being worked on. */
    val currentItem: QueueItem? get() = items.firstOrNull { it.status.isActive }

    /** Index of [currentItem], or [NO_CURRENT_ITEM]. */
    val currentIndex: Int get() = items.indexOfFirst { it.status.isActive }

    /** True once every item has been confirmed by the user. */
    val finished: Boolean get() = items.isNotEmpty() && items.none { it.status.isActive }

    /** 1-based position of the current item, for "Image 4 / 15". */
    val currentOrdinal: Int get() = currentIndex + 1

    // ---- counts -------------------------------------------------------------

    val itemCount: Int get() = items.size
    val completedCount: Int get() = items.count { it.status.isCompleted }
    val remainingCount: Int get() = items.count { it.status.isActive }
    val queuedCount: Int get() = items.count { it.status == PaymentStatus.QUEUED }
    val waitingCount: Int get() = items.count { it.status == PaymentStatus.WAITING_USER }
    val failedCount: Int get() = items.count { it.status == PaymentStatus.FAILED }
    val unknownCount: Int get() = items.count { it.status == PaymentStatus.UNKNOWN }

    /** Fraction of the run the user has confirmed. */
    val progressFraction: Float get() =
        if (itemCount == 0) 0f else completedCount.toFloat() / itemCount.toFloat()

    /** The next free position for an imported item. */
    val nextPosition: Int get() = (items.maxOfOrNull { it.position } ?: -1) + 1

    // ---- transitions --------------------------------------------------------

    /** Adds newly imported items, keeping the queue in position order. */
    fun appendItems(newItems: List<QueueItem>): PaymentQueue =
        if (newItems.isEmpty()) this else copy(items = (items + newItems).sortedBy { it.position })

    /**
     * Records that the current image is being handed to Android's share system.
     *
     * A [PaymentStatus.QUEUED] item was never shared, and a [PaymentStatus.FAILED]
     * share never reached the share sheet, so those are safe to share.
     * [PaymentStatus.WAITING_USER] is only reachable when the caller already has
     * the user's explicit "share again" confirmation; [PaymentStatus.UNKNOWN] and
     * [PaymentStatus.COMPLETED] are refused outright.
     */
    fun startSharing(nowMillis: Long = 0L): PaymentQueue = updateCurrent(
        from = setOf(
            PaymentStatus.QUEUED,
            PaymentStatus.FAILED,
            PaymentStatus.WAITING_USER,
        ),
        to = PaymentStatus.SHARING,
        nowMillis = nowMillis,
    )

    /** The share was launched, so the image reached the share sheet / K PLUS. */
    fun shareLaunched(nowMillis: Long = 0L): PaymentQueue = updateCurrent(
        from = setOf(PaymentStatus.SHARING),
        to = PaymentStatus.WAITING_USER,
        nowMillis = nowMillis,
    )

    /**
     * Records a known, reported error for the current item: a missing stored
     * image, a share intent that could not be built, or a share flow that could
     * not be opened. Nothing is assumed about the payment.
     */
    fun failCurrent(detail: String?, nowMillis: Long = 0L): PaymentQueue = updateCurrent(
        from = setOf(
            PaymentStatus.QUEUED,
            PaymentStatus.SHARING,
            PaymentStatus.WAITING_USER,
        ),
        to = PaymentStatus.FAILED,
        nowMillis = nowMillis,
        detail = detail,
    )

    /**
     * The user confirmed this payment is done. Only reachable from
     * [PaymentStatus.WAITING_USER] or from the user resolving an
     * [PaymentStatus.UNKNOWN] result, so a payment can never be completed just
     * because an image was shared.
     */
    fun confirmCompleted(nowMillis: Long = 0L): PaymentQueue =
        completeCurrent(setOf(PaymentStatus.WAITING_USER), nowMillis)

    /** The user checked the bank app and confirmed an [PaymentStatus.UNKNOWN] result. */
    fun resolveUnknownCompleted(nowMillis: Long = 0L): PaymentQueue =
        completeCurrent(setOf(PaymentStatus.UNKNOWN), nowMillis)

    /**
     * The user says the payment is not done yet. The item stays in
     * [PaymentStatus.WAITING_USER], which is what makes it retryable: the queue
     * does not advance and the image can be shared again on purpose.
     */
    fun confirmNotCompleted(nowMillis: Long = 0L): PaymentQueue = updateCurrent(
        from = setOf(PaymentStatus.WAITING_USER),
        to = PaymentStatus.WAITING_USER,
        nowMillis = nowMillis,
    )

    /**
     * Marks the current item [PaymentStatus.UNKNOWN] because the real result is
     * not known (the app was stopped while a payment was in flight).
     */
    fun markUnknown(detail: String? = null, nowMillis: Long = 0L): PaymentQueue = updateCurrent(
        from = setOf(PaymentStatus.SHARING, PaymentStatus.WAITING_USER),
        to = PaymentStatus.UNKNOWN,
        nowMillis = nowMillis,
        detail = detail,
    )

    /**
     * Explicit user retry of a [PaymentStatus.FAILED] or [PaymentStatus.UNKNOWN]
     * item. It goes back to [PaymentStatus.QUEUED] so the user can share it
     * again on purpose; nothing is shared by this call.
     */
    fun retryCurrent(nowMillis: Long = 0L): PaymentQueue {
        val index = currentIndex
        val item = items.getOrNull(index) ?: return this
        if (item.status != PaymentStatus.FAILED && item.status != PaymentStatus.UNKNOWN) return this
        return copy(
            items = items.replacingAt(
                index,
                item.copy(
                    status = PaymentStatus.QUEUED,
                    failureDetail = null,
                    updatedAt = nowMillis,
                ),
            ),
        )
    }

    /**
     * Applied to a queue loaded from disk: an item that was mid hand-off when the
     * app stopped cannot be assumed successful or failed, so it becomes
     * [PaymentStatus.UNKNOWN] and the queue waits for the user. Nothing is
     * retried, and nothing is marked paid.
     */
    fun resolveInterrupted(detail: String? = null, nowMillis: Long = 0L): PaymentQueue {
        val resolved = items.map { item ->
            if (item.status == PaymentStatus.SHARING || item.status == PaymentStatus.WAITING_USER) {
                item.copy(
                    status = PaymentStatus.UNKNOWN,
                    failureDetail = detail,
                    updatedAt = nowMillis,
                )
            } else {
                item
            }
        }
        return copy(items = resolved)
    }

    /** Items in queue order, with any out-of-order positions repaired. */
    fun normalized(): PaymentQueue = copy(items = items.sortedBy { it.position })

    private fun updateCurrent(
        from: Set<PaymentStatus>,
        to: PaymentStatus,
        nowMillis: Long = 0L,
        detail: String? = null,
    ): PaymentQueue {
        val index = currentIndex
        val item = items.getOrNull(index) ?: return this
        if (item.status !in from) return this
        return copy(
            items = items.replacingAt(
                index,
                item.copy(
                    status = to,
                    failureDetail = when (to) {
                        PaymentStatus.FAILED, PaymentStatus.UNKNOWN -> detail
                        // Retrying a failed item starts a fresh hand-off.
                        PaymentStatus.SHARING -> null
                        else -> item.failureDetail
                    },
                    updatedAt = nowMillis,
                ),
            ),
        )
    }

    private fun completeCurrent(from: Set<PaymentStatus>, nowMillis: Long): PaymentQueue {
        val index = currentIndex
        val item = items.getOrNull(index) ?: return this
        if (item.status !in from) return this
        return copy(
            items = items.replacingAt(
                index,
                item.copy(
                    status = PaymentStatus.COMPLETED,
                    failureDetail = null,
                    updatedAt = nowMillis,
                ),
            ),
        )
    }

    private fun List<QueueItem>.replacingAt(index: Int, value: QueueItem): List<QueueItem> {
        val copy = toMutableList()
        copy[index] = value
        return copy
    }

    companion object {
        /** No item is being worked on (the queue is empty or finished). */
        const val NO_CURRENT_ITEM = -1

        fun create(
            queueId: String,
            createdAt: Long,
            items: List<QueueItem> = emptyList(),
        ): PaymentQueue = PaymentQueue(
            queueId = queueId,
            createdAt = createdAt,
            items = items.sortedBy { it.position },
        )
    }
}
