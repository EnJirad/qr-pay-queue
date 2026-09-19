package com.enjirad.qrqueue.domain

/**
 * The ordered set of images the user imported, and where each one stands.
 *
 * This class is the whole workflow as pure data:
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
 *   Handing an image to K PLUS settles nothing.
 * - Every transition names the item it acts on, so the user may work the queue in
 *   any order; there is no hidden "current" pointer that could drift.
 * - At most one item may own the payment hand-off ([PaymentStatus.holdsHandoff]).
 *   Starting a hand-off for another item is refused while one is in flight or
 *   unresolved, which is what prevents two payments at once and an accidental
 *   second payment for an image that is still waiting for its answer.
 * - A [PaymentStatus.FAILED] hand-off never reached K PLUS, so it does not block
 *   the rest of the queue and may be retried by the user.
 * - A [PaymentStatus.UNKNOWN] result can never be shared again or completed
 *   without an explicit user action.
 */
data class PaymentQueue(
    val queueId: String,
    val createdAt: Long,
    val items: List<QueueItem> = emptyList(),
    /** Last time anything in this queue changed (wall clock, millis). */
    val updatedAtMillis: Long = 0L,
) {

    // ---- counts -------------------------------------------------------------

    val itemCount: Int get() = items.size
    val completedCount: Int get() = items.count { it.status.isCompleted }
    val remainingCount: Int get() = items.count { it.status.isActive }
    val queuedCount: Int get() = items.count { it.status == PaymentStatus.QUEUED }
    val waitingCount: Int get() = items.count { it.status == PaymentStatus.WAITING_USER }
    val failedCount: Int get() = items.count { it.status == PaymentStatus.FAILED }
    val unknownCount: Int get() = items.count { it.status == PaymentStatus.UNKNOWN }

    /** Fraction of the run the user has confirmed. */
    val progressFraction: Float
        get() = if (itemCount == 0) 0f else completedCount.toFloat() / itemCount.toFloat()

    /** The next free position for an imported item. */
    val nextPosition: Int get() = (items.maxOfOrNull { it.position } ?: -1) + 1

    /** True once every item has been confirmed by the user. */
    val finished: Boolean get() = items.isNotEmpty() && items.none { it.status.isActive }

    // ---- lookups ------------------------------------------------------------

    fun item(itemId: String): QueueItem? = items.firstOrNull { it.id == itemId }

    /**
     * The item that owns the single payment hand-off, if any: it is being handed
     * to K PLUS, K PLUS already has it, or its result is not known.
     */
    val handoffItem: QueueItem? get() = items.firstOrNull { it.status.holdsHandoff }

    /** The item whose payment answer the user still owes, if any. */
    val awaitingAnswerItem: QueueItem? get() = items.firstOrNull { it.status.awaitsUserAnswer }

    /** The item whose result is not known yet, if any. */
    val unknownItem: QueueItem? get() = items.firstOrNull { it.status == PaymentStatus.UNKNOWN }

    /**
     * True when a payment hand-off may be started for [itemId] right now: the item
     * itself is shareable and no other item holds the hand-off.
     */
    fun canStartHandoff(itemId: String): Boolean {
        val target = item(itemId) ?: return false
        if (!target.status.canShare) return false
        return items.none { other -> other.id != itemId && other.status.holdsHandoff }
    }

    // ---- transitions --------------------------------------------------------

    /** Adds newly imported items, keeping the queue in position order. */
    fun appendItems(newItems: List<QueueItem>): PaymentQueue =
        if (newItems.isEmpty()) this else copy(items = (items + newItems).sortedBy { it.position })

    /**
     * Records that this image is being handed to K PLUS.
     *
     * A [PaymentStatus.QUEUED] item was never handed off, a [PaymentStatus.FAILED]
     * hand-off never reached K PLUS, and a [PaymentStatus.WAITING_USER] item is
     * only re-shared after the caller obtained the user's explicit "send again"
     * confirmation. [PaymentStatus.UNKNOWN] and [PaymentStatus.COMPLETED] are
     * refused outright.
     */
    fun startSharing(itemId: String, nowMillis: Long = 0L): PaymentQueue = updateItem(
        itemId = itemId,
        from = setOf(
            PaymentStatus.QUEUED,
            PaymentStatus.FAILED,
            PaymentStatus.WAITING_USER,
        ),
        to = PaymentStatus.SHARING,
        nowMillis = nowMillis,
        requireFreeHandoff = true,
    )

    /** The hand-off actually launched, so K PLUS received the image. */
    fun shareLaunched(itemId: String, nowMillis: Long = 0L): PaymentQueue = updateItem(
        itemId = itemId,
        from = setOf(PaymentStatus.SHARING),
        to = PaymentStatus.WAITING_USER,
        nowMillis = nowMillis,
    )

    /**
     * Records a known, reported error for one item: a missing stored image, an
     * intent that could not be built, or K PLUS refusing the targeted image
     * intent. Nothing is assumed about the payment, and nothing was paid.
     */
    fun failItem(itemId: String, detail: String?, nowMillis: Long = 0L): PaymentQueue = updateItem(
        itemId = itemId,
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
     * [PaymentStatus.WAITING_USER], so an item can never be completed just because
     * its image was handed to K PLUS.
     */
    fun confirmCompleted(itemId: String, nowMillis: Long = 0L): PaymentQueue =
        completeItem(itemId, setOf(PaymentStatus.WAITING_USER), nowMillis)

    /** The user checked K PLUS and confirmed an [PaymentStatus.UNKNOWN] result. */
    fun resolveUnknownCompleted(itemId: String, nowMillis: Long = 0L): PaymentQueue =
        completeItem(itemId, setOf(PaymentStatus.UNKNOWN), nowMillis)

    /**
     * The user says this payment is not done yet. Nothing changes except the
     * timestamp: the item stays in [PaymentStatus.WAITING_USER], which is what
     * keeps it retryable and stops the queue from moving on. It is never shared
     * again by this call.
     */
    fun keepWaiting(itemId: String, nowMillis: Long = 0L): PaymentQueue = updateItem(
        itemId = itemId,
        from = setOf(PaymentStatus.WAITING_USER),
        to = PaymentStatus.WAITING_USER,
        nowMillis = nowMillis,
    )

    /**
     * Marks one item [PaymentStatus.UNKNOWN] because the real result is not known
     * (the app was stopped while a payment was in flight).
     */
    fun markUnknown(itemId: String, detail: String? = null, nowMillis: Long = 0L): PaymentQueue =
        updateItem(
            itemId = itemId,
            from = setOf(PaymentStatus.SHARING, PaymentStatus.WAITING_USER),
            to = PaymentStatus.UNKNOWN,
            nowMillis = nowMillis,
            detail = detail,
        )

    /**
     * Explicit user retry of a [PaymentStatus.FAILED] or [PaymentStatus.UNKNOWN]
     * item. It goes back to [PaymentStatus.QUEUED] so the user can hand it to
     * K PLUS again on purpose; nothing is shared by this call.
     */
    fun retryItem(itemId: String, nowMillis: Long = 0L): PaymentQueue = updateItem(
        itemId = itemId,
        from = setOf(PaymentStatus.FAILED, PaymentStatus.UNKNOWN),
        to = PaymentStatus.QUEUED,
        nowMillis = nowMillis,
    )

    /**
     * Applied to a queue loaded from disk: an item that was mid hand-off when the
     * app stopped cannot be assumed successful or failed, so it becomes
     * [PaymentStatus.UNKNOWN] and waits for the user. Nothing is retried, and
     * nothing is marked paid.
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

    // ---- internals ----------------------------------------------------------

    private fun updateItem(
        itemId: String,
        from: Set<PaymentStatus>,
        to: PaymentStatus,
        nowMillis: Long = 0L,
        detail: String? = null,
        requireFreeHandoff: Boolean = false,
    ): PaymentQueue {
        val index = items.indexOfFirst { item -> item.id == itemId }
        val item = items.getOrNull(index) ?: return this
        if (item.status !in from) return this
        if (requireFreeHandoff && !canStartHandoff(itemId)) return this
        return copy(
            items = items.replacingAt(
                index,
                item.copy(
                    status = to,
                    failureDetail = detailFor(to, item.failureDetail, detail),
                    updatedAt = nowMillis,
                ),
            ),
        )
    }

    /**
     * Only a failure reason is carried forward; a fresh hand-off or a user retry
     * starts clean, and a waiting item keeps whatever the last hand-off recorded.
     */
    private fun detailFor(to: PaymentStatus, current: String?, provided: String?): String? =
        when (to) {
            PaymentStatus.FAILED, PaymentStatus.UNKNOWN -> provided
            PaymentStatus.SHARING, PaymentStatus.QUEUED -> null
            else -> current
        }

    private fun completeItem(
        itemId: String,
        from: Set<PaymentStatus>,
        nowMillis: Long,
    ): PaymentQueue {
        val index = items.indexOfFirst { item -> item.id == itemId }
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
        /**
         * The next position for an imported item when there is no queue yet.
         */
        const val FIRST_POSITION = 0

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
