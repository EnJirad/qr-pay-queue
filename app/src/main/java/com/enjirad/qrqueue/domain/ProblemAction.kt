package com.enjirad.qrqueue.domain

/**
 * One recovery action the ปัญหา (problem) tab offers for a problem item.
 *
 * The tab is not a read-only list, so each of these is a real way out of the
 * problem. Every one of them maps onto a transition that already exists in
 * [PaymentQueue]; the model exists so the screen can never offer an action the
 * state machine refuses.
 *
 * @see ProblemActions.availableFor
 */
enum class ProblemAction {
    /**
     * ลองส่ง QR ใหม่. Hands the item back to the payment flow on purpose: a
     * [PaymentStatus.FAILED] item goes straight to the bank again (nothing ever
     * reached it), an [PaymentStatus.UNKNOWN] item goes through the domain's
     * explicit retry (`PaymentQueue.retryItem`, UNKNOWN → READY) and is **not**
     * re-sent by this tap.
     */
    RESCAN,

    /**
     * เปลี่ยนรูป QR. Attaches a newly picked image to the same Payment Item
     * (`PaymentQueue.replaceCurrentQr`); the old QR stays as history and the item
     * becomes [PaymentStatus.READY] again.
     */
    REPLACE_QR,

    /**
     * ยืนยันว่าชำระแล้ว. The only way an unresolved result becomes
     * [PaymentStatus.COMPLETED], and only for [PaymentStatus.UNKNOWN]: the user
     * checked their bank and says the money went out.
     */
    CONFIRM_COMPLETED,

    /**
     * QR ใช้งานไม่ได้. The user's verdict on the image itself: the QR is marked
     * unusable and the item waits for a replacement as
     * [PaymentStatus.REQUIRES_QR_REPLACEMENT] (`PaymentQueue.markQrUnusable`).
     * Offered for a [PaymentStatus.FAILED] item, whose QR the queue still holds as
     * current and which the user may know is the bad one.
     */
    MARK_QR_UNUSABLE,

    /**
     * ล้างรายการ. Removes the Payment Item from the queue
     * (`PaymentQueue.clearItem`). The screen must ask for confirmation first, so
     * this is never one tap away from deletion.
     */
    CLEAR_ITEM,
}

/**
 * Which recovery actions a problem status may be offered, in the order the tab
 * shows them, with [ProblemAction.CLEAR_ITEM] always last because it is the only
 * destructive one.
 *
 * The sets are deliberately **not** identical (see the request's rule that a
 * status must never be given a meaningless action):
 *
 * | Status | Offered |
 * | --- | --- |
 * | `UNKNOWN` | ลองส่ง QR ใหม่ · เปลี่ยนรูป QR · ยืนยันว่าชำระแล้ว · ล้างรายการ |
 * | `FAILED` | ลองส่ง QR ใหม่ · เปลี่ยนรูป QR · QR ใช้งานไม่ได้ · ล้างรายการ |
 * | `REQUIRES_QR_REPLACEMENT` | เปลี่ยนรูป QR · ล้างรายการ |
 *
 * The first three are what every problem item needs a way out through (re-send,
 * replace, clear); the fourth is the one resolution only that status can offer,
 * which is why the sets are deliberately **not** identical:
 *
 * - Only [PaymentStatus.UNKNOWN] may be confirmed as paid, so only it is offered
 *   การยืนยันว่าชำระแล้ว.
 * - A [PaymentStatus.FAILED] item is **not** offered that confirmation: a failure
 *   means nothing reached the bank, so there is nothing to confirm. It is offered
 *   QR ใช้งานไม่ได้ instead, the user's verdict on the image itself.
 * - An item whose QR the user already reported unusable
 *   ([PaymentStatus.REQUIRES_QR_REPLACEMENT]) is **not** offered a re-send.
 *   `reportProblem` marks that version `UNUSABLE` and both `canStartHandoff` and
 *   `retryItem` refuse the status, so an offer to re-send it would be an action
 *   that silently does nothing — or worse, a way to hand a known-bad QR to the
 *   bank. Its QR is already marked unusable, so it is not offered that either.
 *
 * Non-problem statuses get no problem action at all: Home owns those.
 */
object ProblemActions {

    /** The recovery actions [status] may be offered; empty for a non-problem status. */
    fun availableFor(status: PaymentStatus): List<ProblemAction> = when (status) {
        PaymentStatus.UNKNOWN -> listOf(
            ProblemAction.RESCAN,
            ProblemAction.REPLACE_QR,
            ProblemAction.CONFIRM_COMPLETED,
            ProblemAction.CLEAR_ITEM,
        )

        PaymentStatus.FAILED -> listOf(
            ProblemAction.RESCAN,
            ProblemAction.REPLACE_QR,
            ProblemAction.MARK_QR_UNUSABLE,
            ProblemAction.CLEAR_ITEM,
        )

        PaymentStatus.REQUIRES_QR_REPLACEMENT -> listOf(
            ProblemAction.REPLACE_QR,
            ProblemAction.CLEAR_ITEM,
        )

        PaymentStatus.READY,
        PaymentStatus.SHARING,
        PaymentStatus.AWAITING_USER_CONFIRMATION,
        PaymentStatus.COMPLETED,
        -> emptyList()
    }

    /**
     * True when [action] is offered for [status]. Used by the screen instead of
     * scattering `status == …` checks across the UI.
     */
    fun offers(status: PaymentStatus, action: ProblemAction): Boolean =
        action in availableFor(status)
}
