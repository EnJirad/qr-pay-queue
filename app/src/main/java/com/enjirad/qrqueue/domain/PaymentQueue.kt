package com.enjirad.qrqueue.domain

import java.util.UUID

/**
 * The ordered set of Payment Items the user imported, and where each one stands.
 *
 * This class is the whole workflow as pure data. Safety rules encoded here and
 * never to be weakened:
 *
 * - Only the user's explicit "done" moves an item to [PaymentStatus.COMPLETED].
 *   Handing an image to the bank app settles nothing.
 * - Every transition names the item it acts on, so the user may work the queue in
 *   any order; there is no hidden "current" pointer that could drift.
 * - At most one item may own the payment hand-off ([PaymentStatus.holdsHandoff]).
 *   Starting a hand-off for another item is refused while one is in flight or
 *   unresolved, which prevents two payments at once and a second payment for an
 *   image that is still waiting for its answer.
 * - A [PaymentStatus.FAILED] hand-off never reached the bank, so it does not
 *   block the rest of the queue and may be retried by the user.
 * - A [PaymentStatus.UNKNOWN] result can never be shared again or completed
 *   without an explicit user action.
 * - Replacing a QR keeps the Payment Item id and position, appends a version and
 *   returns the item to [PaymentStatus.READY] — it never deletes the item.
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
    val completedCount: Int get() = items.count { item -> item.status.isCompleted }
    val remainingCount: Int get() = items.count { item -> item.status.isActive }
    val readyCount: Int get() = items.count { item -> item.status == PaymentStatus.READY }
    val awaitingCount: Int
        get() = items.count { item -> item.status == PaymentStatus.AWAITING_USER_CONFIRMATION }
    val failedCount: Int get() = items.count { item -> item.status == PaymentStatus.FAILED }
    val unknownCount: Int get() = items.count { item -> item.status == PaymentStatus.UNKNOWN }
    val requiresReplacementCount: Int
        get() = items.count { item -> item.status == PaymentStatus.REQUIRES_QR_REPLACEMENT }

    /**
     * The problem badge count: how many items need the user's attention right
     * now. Completed items, ordinary READY items and items merely in flight are
     * never counted.
     */
    val problemCount: Int get() = items.count { item -> item.status.isProblem }

    /** Fraction of the run the user has confirmed. */
    val progressFraction: Float
        get() = if (itemCount == 0) 0f else completedCount.toFloat() / itemCount.toFloat()

    /** The next free position for an imported item. */
    val nextPosition: Int get() = (items.maxOfOrNull { item -> item.position } ?: -1) + 1

    /** True once every item has been confirmed by the user. */
    val finished: Boolean get() = items.isNotEmpty() && items.none { item -> item.status.isActive }

    // ---- lookups ------------------------------------------------------------

    fun item(itemId: String): QueueItem? = items.firstOrNull { item -> item.id == itemId }

    /**
     * The item that owns the single payment hand-off, if any: it is being handed
     * to the bank, the bank already has it, or its result is not known.
     */
    val handoffItem: QueueItem? get() = items.firstOrNull { item -> item.status.holdsHandoff }

    /** The item whose payment answer the user still owes, if any. */
    val awaitingAnswerItem: QueueItem?
        get() = items.firstOrNull { item -> item.status.awaitsUserAnswer }

    /** The item whose result is not known yet, if any. */
    val unknownItem: QueueItem? get() = items.firstOrNull { item -> item.status == PaymentStatus.UNKNOWN }

    /**
     * Every item that needs the user's attention, in queue order. This is what
     * the problem tab shows and what the badge counts.
     */
    val problemItems: List<QueueItem>
        get() = items.filter { item -> item.status.isProblem }.sortedBy { item -> item.position }

    /** Explicitly user-confirmed items, in the order they were completed. */
    val completedItems: List<QueueItem>
        get() = items
            .filter { item -> item.status.isCompleted }
            .sortedWith(compareBy({ it.completedAt ?: Long.MAX_VALUE }, { it.position }))

    /**
     * The item Home should offer next, following the product's priority order:
     * unresolved result, QR that must be replaced, reported failure, then the
     * next READY item — each within the original queue order.
     *
     * Problem items are offered on purpose. The one-handed Home screen must say
     * what to do next without the user hunting for it (V0.5 §5/§6), and it
     * renders the problem card from this same item; the ปัญหา tab lists every
     * problem for the cases where the user goes looking instead.
     */
    val nextActionItem: QueueItem?
        get() = items
            .filter { item -> priorityRank(item.status) < IN_FLIGHT_RANK }
            .minWithOrNull(compareBy({ item -> priorityRank(item.status) }, { item -> item.position }))

    /** True when no QR image with this content hash is already in the queue. */
    fun hasFingerprint(fingerprint: String?): Boolean {
        if (fingerprint.isNullOrBlank()) return false
        return items.any { item -> item.versions.any { version -> version.fingerprint == fingerprint } }
    }

    /**
     * True when a payment hand-off may be started for [itemId] right now: the item
     * itself is shareable and no other item holds the hand-off.
     */
    fun canStartHandoff(itemId: String): Boolean {
        val target = item(itemId) ?: return false
        if (!target.status.canShare) return false
        if (target.currentVersion == null) return false
        return items.none { other -> other.id != itemId && other.status.holdsHandoff }
    }

    // ---- transitions --------------------------------------------------------

    /** Adds newly imported items, keeping the queue in position order. */
    fun appendItems(newItems: List<QueueItem>): PaymentQueue =
        if (newItems.isEmpty()) this else copy(items = (items + newItems).sortedBy { it.position })

    /**
     * Records that this item's QR is being handed to the selected bank.
     *
     * A [PaymentStatus.READY] item was never handed off and a
     * [PaymentStatus.FAILED] hand-off never reached the bank, so both may start
     * again. [PaymentStatus.UNKNOWN] and [PaymentStatus.COMPLETED] are refused
     * outright, and the single-hand-off rule blocks a second item.
     */
    fun startSharing(itemId: String, nowMillis: Long = 0L): PaymentQueue {
        val index = items.indexOfFirst { item -> item.id == itemId }
        val item = items.getOrNull(index) ?: return this
        if (!item.status.canShare) return this
        if (!canStartHandoff(itemId)) return this
        val version = item.currentVersion ?: return this
        val attempt = paymentAttempt(item, version, PaymentAttemptResult.STARTED, null, nowMillis)
        return copy(
            items = items.replacingAt(
                index,
                item.copy(
                    status = PaymentStatus.SHARING,
                    failureDetail = null,
                    lastAttemptAt = nowMillis,
                    updatedAt = nowMillis,
                    attempts = item.attempts + attempt,
                ),
            ),
        )
    }

    /** The hand-off actually launched, so the bank received the image. */
    fun shareLaunched(itemId: String, nowMillis: Long = 0L): PaymentQueue = updateItem(
        itemId = itemId,
        from = setOf(PaymentStatus.SHARING),
        to = PaymentStatus.AWAITING_USER_CONFIRMATION,
        nowMillis = nowMillis,
        attemptResult = PaymentAttemptResult.LAUNCHED,
    )

    /**
     * Records a known failure of this app: a missing stored image, an intent that
     * could not be built, or a bank that could not be opened. Nothing was paid.
     */
    fun failItem(itemId: String, detail: String?, nowMillis: Long = 0L): PaymentQueue = updateItem(
        itemId = itemId,
        from = setOf(PaymentStatus.READY, PaymentStatus.SHARING),
        to = PaymentStatus.FAILED,
        nowMillis = nowMillis,
        detail = detail,
        attemptResult = PaymentAttemptResult.FAILED,
    )

    /**
     * The user confirmed this payment is done. Only reachable from
     * [PaymentStatus.AWAITING_USER_CONFIRMATION], so an item can never be
     * completed just because its image was handed to the bank.
     */
    fun confirmCompleted(itemId: String, nowMillis: Long = 0L): PaymentQueue = completeItem(
        itemId = itemId,
        from = setOf(PaymentStatus.AWAITING_USER_CONFIRMATION),
        nowMillis = nowMillis,
    )

    /** The user checked the bank app and confirmed an [PaymentStatus.UNKNOWN] result. */
    fun resolveUnknownCompleted(itemId: String, nowMillis: Long = 0L): PaymentQueue = completeItem(
        itemId = itemId,
        from = setOf(PaymentStatus.UNKNOWN),
        nowMillis = nowMillis,
    )

    /**
     * The user says this payment is not done yet ("ยังไม่แน่ใจ"). Nothing changes
     * except the timestamp: the item stays
     * [PaymentStatus.AWAITING_USER_CONFIRMATION], which keeps it open and stops
     * the queue from moving on. It is never shared again by this call.
     */
    fun keepWaiting(itemId: String, nowMillis: Long = 0L): PaymentQueue = updateItem(
        itemId = itemId,
        from = setOf(PaymentStatus.AWAITING_USER_CONFIRMATION),
        to = PaymentStatus.AWAITING_USER_CONFIRMATION,
        nowMillis = nowMillis,
    )

    /**
     * Marks one item [PaymentStatus.UNKNOWN] because the real result is not known
     * (the app was stopped while a payment was in flight).
     */
    fun markUnknown(itemId: String, detail: String? = null, nowMillis: Long = 0L): PaymentQueue =
        updateItem(
            itemId = itemId,
            from = setOf(PaymentStatus.SHARING, PaymentStatus.AWAITING_USER_CONFIRMATION),
            to = PaymentStatus.UNKNOWN,
            nowMillis = nowMillis,
            detail = detail,
            attemptResult = PaymentAttemptResult.UNKNOWN,
        )

    /**
     * The user reports that the current QR cannot be used (the bank rejected it,
     * it expired, or the user knows it must be regenerated). The item stays in
     * the queue as [PaymentStatus.REQUIRES_QR_REPLACEMENT]; it is never deleted,
     * and its current version is marked [QrVersionStatus.UNUSABLE].
     */
    fun markQrUnusable(itemId: String, detail: String? = null, nowMillis: Long = 0L): PaymentQueue {
        val index = items.indexOfFirst { item -> item.id == itemId }
        val item = items.getOrNull(index) ?: return this
        val from = setOf(
            PaymentStatus.AWAITING_USER_CONFIRMATION,
            PaymentStatus.UNKNOWN,
            PaymentStatus.FAILED,
        )
        if (item.status !in from) return this
        val version = item.currentVersion
        return copy(
            items = items.replacingAt(
                index,
                item.copy(
                    status = PaymentStatus.REQUIRES_QR_REPLACEMENT,
                    failureDetail = detail,
                    updatedAt = nowMillis,
                    versions = item.versions.map { existing ->
                        if (version != null && existing.id == version.id) {
                            existing.copy(status = QrVersionStatus.UNUSABLE)
                        } else {
                            existing
                        }
                    },
                    attempts = item.attempts +
                        paymentAttempt(item, version, PaymentAttemptResult.QR_UNUSABLE, detail, nowMillis),
                ),
            ),
        )
    }

    /**
     * Explicit user retry of a [PaymentStatus.FAILED] or [PaymentStatus.UNKNOWN]
     * item. It goes back to [PaymentStatus.READY] so the user can hand it to the
     * bank again on purpose; nothing is shared by this call.
     */
    fun retryItem(itemId: String, nowMillis: Long = 0L): PaymentQueue = updateItem(
        itemId = itemId,
        from = setOf(PaymentStatus.FAILED, PaymentStatus.UNKNOWN),
        to = PaymentStatus.READY,
        nowMillis = nowMillis,
    )

    /**
     * Retry-share (§11–§14): the user is in [PaymentStatus.AWAITING_USER_CONFIRMATION]
     * and wants to open the bank app again with the same QR — e.g. they opened the
     * bank but did not pay. The item goes back to [PaymentStatus.SHARING] with a
     * new [PaymentAttempt] (for audit trail), and the same QR version is used.
     * No new Payment Item and no new QR Version are created.
     */
    fun retryShare(itemId: String, nowMillis: Long = 0L): PaymentQueue {
        val index = items.indexOfFirst { item -> item.id == itemId }
        val item = items.getOrNull(index) ?: return this
        if (item.status != PaymentStatus.AWAITING_USER_CONFIRMATION) return this
        val version = item.currentVersion ?: return this
        val attempt = paymentAttempt(item, version, PaymentAttemptResult.STARTED, null, nowMillis)
        return copy(
            items = items.replacingAt(
                index,
                item.copy(
                    status = PaymentStatus.SHARING,
                    failureDetail = null,
                    lastAttemptAt = nowMillis,
                    updatedAt = nowMillis,
                    attempts = item.attempts + attempt,
                ),
            ),
        )
    }

    /**
     * Replaces the current QR of an item with a new image, keeping the Payment
     * Item (its id, position and number) unchanged.
     *
     * The previous current version becomes [QrVersionStatus.UNUSABLE] when the
     * user had reported it unusable, otherwise [QrVersionStatus.SUPERSEDED], and
     * the new version becomes the only current one. A replacement that cannot be
     * used (blank path, completed item, wrong state) is refused and leaves the
     * existing QR untouched.
     */
    fun replaceCurrentQr(
        itemId: String,
        newVersion: QrVersion,
        nowMillis: Long = 0L,
    ): PaymentQueue {
        if (newVersion.filePath.isBlank()) return this
        val index = items.indexOfFirst { item -> item.id == itemId }
        val item = items.getOrNull(index) ?: return this
        if (item.status.isCompleted) return this
        val from = setOf(
            PaymentStatus.REQUIRES_QR_REPLACEMENT,
            PaymentStatus.FAILED,
            PaymentStatus.UNKNOWN,
        )
        if (item.status !in from) return this
        val previous = item.currentVersion
        val previousWasUnusable = item.status == PaymentStatus.REQUIRES_QR_REPLACEMENT
        val nextNumber = (item.versions.maxOfOrNull { version -> version.versionNumber } ?: 0) + 1
        val appended = newVersion.copy(
            paymentItemId = item.id,
            versionNumber = nextNumber,
            createdAt = if (newVersion.createdAt > 0L) newVersion.createdAt else nowMillis,
            status = QrVersionStatus.CURRENT,
        )
        val versions = item.versions.map { version ->
            when {
                previous != null && version.id == previous.id && previousWasUnusable ->
                    version.copy(status = QrVersionStatus.UNUSABLE)

                previous != null && version.id == previous.id ->
                    version.copy(status = QrVersionStatus.SUPERSEDED)

                else -> version
            }
        } + appended
        return copy(
            items = items.replacingAt(
                index,
                item.copy(
                    status = PaymentStatus.READY,
                    failureDetail = null,
                    versions = versions,
                    updatedAt = nowMillis,
                ),
            ),
        )
    }

    /**
     * Applied to a queue loaded from disk: an item that was mid hand-off when the
     * app stopped cannot be assumed successful or failed, so it becomes
     * [PaymentStatus.UNKNOWN] and waits for the user. Nothing is retried, and
     * nothing is marked paid.
     */
    fun resolveInterrupted(detail: String? = null, nowMillis: Long = 0L): PaymentQueue {
        val resolved = items.map { item ->
            if (item.status.holdsHandoff) {
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

    /**
     * Reports a problem with the current item and moves it to the Problem tab.
     * The item leaves the active queue; its status becomes
     * [PaymentStatus.REQUIRES_QR_REPLACEMENT] and its [QueueItem.problemReason]
     * is set so the Problem tab can display it.
     */
    fun reportProblem(
        itemId: String,
        reason: String,
        nowMillis: Long = 0L,
    ): PaymentQueue {
        val index = items.indexOfFirst { item -> item.id == itemId }
        val item = items.getOrNull(index) ?: return this
        if (item.status.isCompleted) return this
        // Mark the current QR as unusable and set the problem reason.
        val version = item.currentVersion
        return copy(
            items = items.replacingAt(
                index,
                item.copy(
                    status = PaymentStatus.REQUIRES_QR_REPLACEMENT,
                    problemReason = reason,
                    failureDetail = reason,
                    updatedAt = nowMillis,
                    versions = item.versions.map { existing ->
                        if (version != null && existing.id == version.id) {
                            existing.copy(status = QrVersionStatus.UNUSABLE)
                        } else {
                            existing
                        }
                    },
                    attempts = item.attempts +
                        paymentAttempt(item, version, PaymentAttemptResult.QR_UNUSABLE, reason, nowMillis),
                ),
            ),
        )
    }

    /**
     * Removes a Payment Item entirely: deletes it from the queue.
     * The caller is responsible for deleting the item's QR image files.
     */
    fun clearItem(itemId: String): PaymentQueue {
        val filtered = items.filter { item -> item.id != itemId }
        return if (filtered.size == items.size) this else copy(items = filtered)
    }

    /** Items in queue order, with positions and QR versions repaired. */
    fun normalized(): PaymentQueue = copy(
        items = items
            .sortedBy { item -> item.position }
            .map { item -> item.withNormalizedVersions() },
    )

    // ---- internals ----------------------------------------------------------

    private fun updateItem(
        itemId: String,
        from: Set<PaymentStatus>,
        to: PaymentStatus,
        nowMillis: Long = 0L,
        detail: String? = null,
        attemptResult: PaymentAttemptResult? = null,
    ): PaymentQueue {
        val index = items.indexOfFirst { item -> item.id == itemId }
        val item = items.getOrNull(index) ?: return this
        if (item.status !in from) return this
        val attempt = if (attemptResult == null) {
            null
        } else {
            paymentAttempt(item, item.currentVersion, attemptResult, detail, nowMillis)
        }
        return copy(
            items = items.replacingAt(
                index,
                item.copy(
                    status = to,
                    failureDetail = detailFor(to, item.failureDetail, detail),
                    updatedAt = nowMillis,
                    attempts = if (attempt == null) item.attempts else item.attempts + attempt,
                ),
            ),
        )
    }

    /**
     * Only a failure or unknown reason is carried forward; a fresh hand-off or a
     * user retry starts clean, and an open item keeps whatever the last hand-off
     * recorded.
     */
    private fun detailFor(to: PaymentStatus, current: String?, provided: String?): String? =
        when (to) {
            PaymentStatus.FAILED,
            PaymentStatus.UNKNOWN,
            PaymentStatus.REQUIRES_QR_REPLACEMENT,
            -> provided

            PaymentStatus.SHARING,
            PaymentStatus.READY,
            -> null

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
        val attempt = paymentAttempt(
            item,
            item.currentVersion,
            PaymentAttemptResult.COMPLETED,
            null,
            nowMillis,
        )
        return copy(
            items = items.replacingAt(
                index,
                item.copy(
                    status = PaymentStatus.COMPLETED,
                    failureDetail = null,
                    completedAt = nowMillis,
                    updatedAt = nowMillis,
                    attempts = item.attempts + attempt,
                ),
            ),
        )
    }

    private fun paymentAttempt(
        item: QueueItem,
        version: QrVersion?,
        result: PaymentAttemptResult,
        reason: String?,
        nowMillis: Long,
    ): PaymentAttempt = PaymentAttempt(
        id = UUID.randomUUID().toString(),
        paymentItemId = item.id,
        qrVersionId = version?.id.orEmpty(),
        startedAt = nowMillis,
        result = result,
        reason = reason,
    )

    private fun List<QueueItem>.replacingAt(index: Int, value: QueueItem): List<QueueItem> {
        val copy = toMutableList()
        copy[index] = value
        return copy
    }

    companion object {
        /** The next position for an imported item when there is no queue yet. */
        const val FIRST_POSITION = 0

        /** Sort rank inside [nextActionItem]; anything not listed is in flight. */
        private fun priorityRank(status: PaymentStatus): Int = when (status) {
            PaymentStatus.UNKNOWN -> 0
            PaymentStatus.REQUIRES_QR_REPLACEMENT -> 1
            PaymentStatus.FAILED -> 2
            PaymentStatus.READY -> 3
            else -> IN_FLIGHT_RANK
        }

        /** Rank given to items that Home does not offer as "the next action". */
        private const val IN_FLIGHT_RANK = 9

        fun create(
            queueId: String,
            createdAt: Long,
            items: List<QueueItem> = emptyList(),
        ): PaymentQueue = PaymentQueue(
            queueId = queueId,
            createdAt = createdAt,
            items = items.sortedBy { item -> item.position },
        )
    }
}
