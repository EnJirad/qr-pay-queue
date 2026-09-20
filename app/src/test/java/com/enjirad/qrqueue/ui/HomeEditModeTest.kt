package com.enjirad.qrqueue.ui

import com.enjirad.qrqueue.domain.HomeElement
import com.enjirad.qrqueue.domain.HomeLayoutConfig
import com.enjirad.qrqueue.domain.PaymentStatus
import com.enjirad.qrqueue.domain.QueueItem
import com.enjirad.qrqueue.domain.testItem
import com.enjirad.qrqueue.domain.testQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home screen's own state: which QR the top area shows, what the queue list
 * below it contains, whether Edit mode is on, and which actions Edit mode can
 * place. Compose cannot be exercised by these tests, so the state and the
 * placement rules the screen renders from are asserted here.
 */
class HomeEditModeTest {

    private fun queueOf(vararg items: QueueItem) = testQueue(*items)

    @Test
    fun `edit mode is off and the layout is the app default until the user changes it`() {
        val state = QueueUiState()

        assertFalse(state.homeEditMode)
        assertTrue(state.homeLayout.isDefault)
        assertFalse(state.layoutResetConfirmationVisible)
        assertTrue(state.queuedItems.isEmpty())
    }

    @Test
    fun `the queued list is the open queue without the active QR`() {
        val state = QueueUiState(
            queue = queueOf(
                testItem("active", 0, PaymentStatus.UNKNOWN),
                testItem("ready-1", 1),
                testItem("ready-2", 2),
            ),
        )

        assertEquals("active", state.nextActionItem?.id)
        assertEquals(listOf("ready-1", "ready-2"), state.queuedItems.map { item -> item.id })
    }

    @Test
    fun `a confirmed item is never listed below the active QR`() {
        val state = QueueUiState(
            queue = queueOf(
                testItem("done", 0, PaymentStatus.COMPLETED),
                testItem("ready-1", 1),
            ),
        )

        assertEquals("ready-1", state.nextActionItem?.id)
        assertTrue(state.queuedItems.isEmpty())
    }

    @Test
    fun `an item that owns the hand-off is the one the top area shows`() {
        // The same two transitions the ViewModel performs before it launches
        // anything: hand-off starts, then the item owns the user's answer.
        val queue = queueOf(testItem("a", 0), testItem("b", 1))
            .startSharing("a", 10L)
            .shareLaunched("a", 10L)

        val state = QueueUiState(queue = queue)

        // The payment in flight owns the top area (its four actions must be
        // visible); the next ready item is only offered inside the list below.
        assertEquals("a", state.activeItem?.id)
        assertEquals("a", state.awaitingAnswerItem?.id)
        assertEquals("b", state.nextActionItem?.id)
        assertEquals(listOf("b"), state.queuedItems.map { item -> item.id })
    }

    @Test
    fun `a whole queue of one awaiting item keeps the top area busy`() {
        val queue = queueOf(testItem("only", 0))
            .startSharing("only", 10L)
            .shareLaunched("only", 10L)

        val state = QueueUiState(queue = queue)

        assertEquals("only", state.activeItem?.id)
        assertNull(state.nextActionItem)
        assertTrue(state.queuedItems.isEmpty())
    }

    @Test
    fun `the visibility of an element follows the layout the user saved`() {
        val layout = HomeLayoutConfig.DEFAULT
            .toggleVisible(HomeElement.GUIDANCE)
            .toggleVisible(HomeElement.PROGRESS)

        val state = QueueUiState(homeLayout = layout)

        assertFalse(state.isElementVisible(HomeElement.GUIDANCE))
        assertFalse(state.isElementVisible(HomeElement.PROGRESS))
        assertTrue(state.isElementVisible(HomeElement.IMPORT_HINT))
        assertTrue(state.isElementVisible(HomeElement.SCAN_ACTION))
    }

    @Test
    fun `the QR image offset comes straight from the layout`() {
        val state = QueueUiState(
            homeLayout = HomeLayoutConfig.DEFAULT.moveQrImage(6f, -10f),
        )

        assertEquals(6f, state.homeLayout.qrImageOffsetX, 0.0001f)
        assertEquals(-10f, state.homeLayout.qrImageOffsetY, 0.0001f)
    }

    // ---- both action states are placeable (V0.9.1 §5–§7) --------------------

    @Test
    fun `a ready item can place the share action and every answer`() {
        // Normal mode: the item has exactly one action, because it has not been
        // handed over yet.
        assertEquals(
            listOf(HomeElement.SCAN_ACTION),
            HomeElement.railElements(PaymentStatus.READY, editMode = false),
        )

        // Edit mode: both states at once, so nothing has to be shared and no bank
        // has to be opened before an action can be moved.
        assertEquals(
            listOf(
                HomeElement.SCAN_ACTION,
                HomeElement.CONFIRM_ACTION,
                HomeElement.WARNING_ACTION,
                HomeElement.UNKNOWN_ACTION,
                HomeElement.RETRY_ACTION,
            ),
            HomeElement.railElements(PaymentStatus.READY, editMode = true),
        )
        assertEquals(
            HomeElement.ACTION_ELEMENTS,
            HomeElement.railElements(PaymentStatus.READY, editMode = true),
        )
    }

    @Test
    fun `an awaiting item can place its four answers and the share action`() {
        val awaiting = PaymentStatus.AWAITING_USER_CONFIRMATION

        assertEquals(
            listOf(
                HomeElement.CONFIRM_ACTION,
                HomeElement.WARNING_ACTION,
                HomeElement.UNKNOWN_ACTION,
                HomeElement.RETRY_ACTION,
            ),
            HomeElement.railElements(awaiting, editMode = false),
        )
        assertEquals(
            HomeElement.ACTION_ELEMENTS,
            HomeElement.railElements(awaiting, editMode = true),
        )

        // A hand-off that is still being launched shows the same four answers, and
        // can be arranged the same way.
        assertEquals(
            HomeElement.railElements(awaiting, editMode = false),
            HomeElement.railElements(PaymentStatus.SHARING, editMode = false),
        )
        assertEquals(
            HomeElement.ACTION_ELEMENTS,
            HomeElement.railElements(PaymentStatus.SHARING, editMode = true),
        )
    }

    @Test
    fun `a problem item keeps its resolving action and can place every action`() {
        assertTrue(PaymentStatus.entries.any { status -> status.isProblem })

        PaymentStatus.entries.filter { status -> status.isProblem }.forEach { problem ->
            assertEquals(
                listOf(HomeElement.RETRY_ACTION),
                HomeElement.railElements(problem, editMode = false),
            )
            assertEquals(
                HomeElement.ACTION_ELEMENTS,
                HomeElement.railElements(problem, editMode = true),
            )
        }
    }

    @Test
    fun `a finished item has no action left but can still place every action`() {
        assertEquals(
            emptyList<HomeElement>(),
            HomeElement.railElements(PaymentStatus.COMPLETED, editMode = false),
        )
        assertEquals(
            HomeElement.ACTION_ELEMENTS,
            HomeElement.railElements(PaymentStatus.COMPLETED, editMode = true),
        )
    }

    @Test
    fun `no placeable action can ever be hidden`() {
        assertTrue(HomeElement.ACTION_ELEMENTS.none { element -> element.hideable })

        // Even a hand-written preference cannot hide one: the model refuses.
        val tampered = HomeElement.ACTION_ELEMENTS.fold(HomeLayoutConfig.DEFAULT) { layout, element ->
            layout.toggleVisible(element)
        }
        assertTrue(HomeElement.ACTION_ELEMENTS.all { element -> tampered.isVisible(element) })
    }

    @Test
    fun `a placement belongs to the action, not to the state it was made in`() {
        val layout = HomeLayoutConfig.DEFAULT.moveElement(HomeElement.CONFIRM_ACTION, 8f, -16f)

        // The same element reads the same offset whatever the item's status is, so
        // the row arranged while the item was ready is where the action sits later.
        assertEquals(8f, layout.offsetX(HomeElement.CONFIRM_ACTION), 0.0001f)
        assertEquals(-16f, layout.offsetY(HomeElement.CONFIRM_ACTION), 0.0001f)
        assertEquals(0f, layout.offsetX(HomeElement.SCAN_ACTION), 0.0001f)
    }
}
