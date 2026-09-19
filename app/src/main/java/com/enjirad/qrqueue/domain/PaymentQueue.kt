package com.enjirad.qrqueue.domain

/**
 * A payment queue: the ordered set of QR items imported in one session, plus
 * exactly where the sequential run currently stands.
 *
 * This class is the whole sequential workflow as pure data. Every transition
 * returns a new queue and refuses anything that would break the safety rules:
 *
 * - `READY` can only move to `SUBMITTED` by an explicit hand-off action.
 * - `SUCCESS` / `PAYMENT_FAILED` can only be reached from
 *   `WAITING_CONFIRMATION` or from an `UNKNOWN` item the user resolved — i.e.
 *   only from an explicit human answer. Showing or sharing a QR never settles a
 *   payment.
 * - An `UNKNOWN` item blocks the queue and is never retried automatically.
 * - An item interrupted mid-flight (app killed/backgrounded) comes back as
 *   `UNKNOWN` and must be resolved by the user.
 */
data class PaymentQueue(
    val queueId: String,
    val createdAt: Long,
    val items: List<QueueItem> = emptyList(),
    val currentIndex: Int = NO_CURRENT_ITEM,
    val started: Boolean = false,
    val finished: Boolean = false,
) {

    val currentItem: QueueItem? get() = items.getOrNull(currentIndex)

    // ---- counts -------------------------------------------------------------

    val itemCount: Int get() = items.size

    /** Items that belong to the sequential run (everything but invalid/duplicate). */
    val queueableItems: List<QueueItem> get() = items.filter { it.isPayable }
    val excludedItems: List<QueueItem> get() = items.filter { !it.isPayable }

    val readyItems: List<QueueItem> get() = items.filter { it.status == PaymentStatus.READY }
    val paidItems: List<QueueItem> get() = items.filter { it.status.isPaid }
    val failedItems: List<QueueItem> get() = items.filter { it.status == PaymentStatus.PAYMENT_FAILED }
    val unknownItems: List<QueueItem> get() = items.filter { it.status == PaymentStatus.UNKNOWN }
    val invalidItems: List<QueueItem> get() = items.filter { it.status == PaymentStatus.INVALID }
    val duplicateItems: List<QueueItem> get() = items.filter { it.status == PaymentStatus.DUPLICATE }

    val readyCount: Int get() = readyItems.size
    val queueableCount: Int get() = queueableItems.size
    val paidCount: Int get() = paidItems.size
    val failedCount: Int get() = failedItems.size
    val unknownCount: Int get() = unknownItems.size
    val invalidCount: Int get() = invalidItems.size
    val duplicateCount: Int get() = duplicateItems.size

    /** Items the user has already answered for: paid or failed. */
    val processedCount: Int get() = paidCount + failedCount
    val remainingCount: Int get() = (queueableCount - processedCount).coerceAtLeast(0)

    // ---- money --------------------------------------------------------------

    /** Everything the queue is expected to move, from the real decoded amounts. */
    val queuedSatang: Long get() = queueableItems.sumOf { it.amountSatang ?: 0L }

    /** Only what the user confirmed as paid. */
    val paidSatang: Long get() = paidItems.sumOf { it.amountSatang ?: 0L }

    val failedSatang: Long get() = failedItems.sumOf { it.amountSatang ?: 0L }

    val unknownSatang: Long get() = unknownItems.sumOf { it.amountSatang ?: 0L }

    /** Amounts still waiting for a result, never affected by failed/unknown items. */
    val remainingSatang: Long get() = queueableItems.filter { it.status.isPending }.sumOf { it.amountSatang ?: 0L }

    /** Queueable QR codes that carry no amount at all (static PromptPay QRs). */
    val amountMissingCount: Int get() = queueableItems.count { !it.hasKnownAmount }

    // ---- state --------------------------------------------------------------

    val canStart: Boolean get() = !started && readyCount > 0

    /** True while a payment result is unknown and must be resolved first. */
    val requiresResolution: Boolean get() = unknownCount > 0

    /** Fraction of the run that has a recorded result (paid + failed). */
    val progressFraction: Float get() =
        if (queueableCount == 0) 0f else processedCount.toFloat() / queueableCount.toFloat()

    /** 1-based position of the item being worked on, for "Payment 4 / 15". */
    val currentOrdinal: Int get() =
        if (currentIndex == NO_CURRENT_ITEM) processedCount else processedCount + 1

    // ---- transitions --------------------------------------------------------

    /** Adds newly imported items. Only meaningful before the run starts. */
    fun appendItems(newItems: List<QueueItem>): PaymentQueue =
        if (newItems.isEmpty()) this else copy(items = items + newItems)

    /**
     * Starts the sequential run at the first payable item.
     * A queue with nothing payable is left untouched.
     */
    fun start(): PaymentQueue {
        if (started) return this
        val first = items.indexOfFirst { it.status == PaymentStatus.READY }
        if (first < 0) return this
        return copy(started = true, finished = false, currentIndex = first)
    }

    /** Records the hand-off to the banking app, then waits for the user's answer. */
    fun handOffCurrent(): PaymentQueue = markCurrentSubmitted().awaitConfirmation()

    fun markCurrentSubmitted(): PaymentQueue =
        updateCurrent(from = PaymentStatus.READY, to = PaymentStatus.SUBMITTED)

    fun awaitConfirmation(): PaymentQueue =
        updateCurrent(from = PaymentStatus.SUBMITTED, to = PaymentStatus.WAITING_CONFIRMATION)

    /** The user could not tell what happened: unknown, and the queue waits. */
    fun reportUnknown(): PaymentQueue =
        updateCurrent(from = PaymentStatus.WAITING_CONFIRMATION, to = PaymentStatus.UNKNOWN)

    /** Explicit "payment successful" — the only way an item becomes paid. */
    fun confirmSuccess(): PaymentQueue = settleCurrent(PaymentStatus.SUCCESS)

    /** Explicit "payment failed" — recorded, then the queue moves on. */
    fun confirmFailure(): PaymentQueue = settleCurrent(PaymentStatus.PAYMENT_FAILED)

    /**
     * Resolves an [PaymentStatus.UNKNOWN] item with the real result the user
     * found in the banking app. Refused for any other status, so an unresolved
     * result can never be advanced by accident.
     */
    fun resolveUnknown(success: Boolean): PaymentQueue {
        val item = currentItem ?: return this
        if (item.status != PaymentStatus.UNKNOWN) return this
        return settleCurrent(if (success) PaymentStatus.SUCCESS else PaymentStatus.PAYMENT_FAILED)
    }

    /**
     * Applied to a queue loaded from disk: an item that was mid-hand-off when
     * the app stopped cannot be assumed successful or failed, so it becomes
     * [PaymentStatus.UNKNOWN] and needs an explicit resolution.
     */
    fun resolveInterrupted(): PaymentQueue = normalized(
        items = items.map { item ->
            if (item.status == PaymentStatus.SUBMITTED || item.status == PaymentStatus.WAITING_CONFIRMATION) {
                item.copy(status = PaymentStatus.UNKNOWN)
            } else {
                item
            }
        },
    )

    /** Recomputes the pointer and the finished flag from the items themselves. */
    fun normalized(): PaymentQueue = normalized(items)

    private fun normalized(items: List<QueueItem>): PaymentQueue {
        if (!started) return copy(items = items, currentIndex = NO_CURRENT_ITEM, finished = false)
        val current = items.getOrNull(currentIndex)
        if (current != null && current.status.isProcessable) {
            return copy(items = items, currentIndex = currentIndex, finished = false)
        }
        val next = items.indexOfFirst { it.status.isProcessable }
        return if (next >= 0) {
            copy(items = items, currentIndex = next, finished = false)
        } else {
            copy(items = items, currentIndex = NO_CURRENT_ITEM, finished = true)
        }
    }

    private fun updateCurrent(from: PaymentStatus, to: PaymentStatus): PaymentQueue {
        val index = currentIndex
        val item = items.getOrNull(index) ?: return this
        if (item.status != from) return this
        return copy(items = items.replacingAt(index, item.copy(status = to)))
    }

    /**
     * Records a final result for the current item and moves to the next payable
     * one. Only reachable from [PaymentStatus.WAITING_CONFIRMATION] or
     * [PaymentStatus.UNKNOWN].
     */
    private fun settleCurrent(result: PaymentStatus): PaymentQueue {
        val index = currentIndex
        val item = items.getOrNull(index) ?: return this
        if (item.status != PaymentStatus.WAITING_CONFIRMATION && item.status != PaymentStatus.UNKNOWN) {
            return this
        }
        val settled = items.replacingAt(index, item.copy(status = result))
        val next = settled.indexOfFirst { it.status.isProcessable }
        return if (next >= 0) {
            copy(items = settled, currentIndex = next, finished = false)
        } else {
            copy(items = settled, currentIndex = NO_CURRENT_ITEM, finished = true)
        }
    }

    private fun List<QueueItem>.replacingAt(index: Int, value: QueueItem): List<QueueItem> {
        val copy = toMutableList()
        copy[index] = value
        return copy
    }

    companion object {
        /** No item is being worked on (queue not started, or finished). */
        const val NO_CURRENT_ITEM = -1

        fun create(queueId: String, createdAt: Long, items: List<QueueItem> = emptyList()): PaymentQueue =
            PaymentQueue(queueId = queueId, createdAt = createdAt, items = items)
    }
}
