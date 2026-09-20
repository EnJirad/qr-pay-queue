package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home layout is what the V0.9 Edit mode changes: where each element sits,
 * what the user hid, and where the QR image rests inside its frame. These rules
 * are what the screen and the settings store rely on, so they are asserted here
 * without Android.
 */
class HomeLayoutTest {

    private val delta = 0.0001f

    // ---- defaults -----------------------------------------------------------

    @Test
    fun `the default layout leaves every element in place and visible`() {
        val layout = HomeLayoutConfig.DEFAULT

        HomeElement.entries.forEach { element ->
            assertEquals(0f, layout.offsetX(element), delta)
            assertEquals(0f, layout.offsetY(element), delta)
            assertTrue(layout.isVisible(element))
        }
        assertEquals(0f, layout.qrImageOffsetX, delta)
        assertEquals(0f, layout.qrImageOffsetY, delta)
        assertTrue(layout.isDefault)
    }

    @Test
    fun `an element the user never touched reads as its default`() {
        val layout = HomeLayoutConfig.DEFAULT.moveElement(HomeElement.PROGRESS, 10f, 5f)

        assertEquals(10f, layout.offsetX(HomeElement.PROGRESS), delta)
        assertEquals(5f, layout.offsetY(HomeElement.PROGRESS), delta)
        assertEquals(0f, layout.offsetX(HomeElement.GUIDANCE), delta)
        assertTrue(layout.isVisible(HomeElement.GUIDANCE))
    }

    // ---- placement ----------------------------------------------------------

    @Test
    fun `moving an element accumulates its offset`() {
        val layout = HomeLayoutConfig.DEFAULT
            .moveElement(HomeElement.ADD_QR, 12f, -8f)
            .moveElement(HomeElement.ADD_QR, 3f, 18f)

        assertEquals(15f, layout.offsetX(HomeElement.ADD_QR), delta)
        assertEquals(10f, layout.offsetY(HomeElement.ADD_QR), delta)
    }

    @Test
    fun `an element can never be dragged off the screen`() {
        val layout = HomeLayoutConfig.DEFAULT
            .moveElement(HomeElement.CONFIRM_ACTION, 10_000f, -10_000f)

        assertEquals(
            HomeLayoutConfig.MAX_OFFSET_DP,
            layout.offsetX(HomeElement.CONFIRM_ACTION),
            delta,
        )
        assertEquals(
            -HomeLayoutConfig.MAX_OFFSET_DP,
            layout.offsetY(HomeElement.CONFIRM_ACTION),
            delta,
        )
    }

    @Test
    fun `an absolute placement is clamped too`() {
        val layout = HomeLayoutConfig.DEFAULT.setElementOffset(
            HomeElement.SCAN_ACTION,
            500f,
            -500f,
        )

        assertEquals(HomeLayoutConfig.MAX_OFFSET_DP, layout.offsetX(HomeElement.SCAN_ACTION), delta)
        assertEquals(-HomeLayoutConfig.MAX_OFFSET_DP, layout.offsetY(HomeElement.SCAN_ACTION), delta)
    }

    @Test
    fun `a value the screen cannot use is refused instead of stored`() {
        val layout = HomeLayoutConfig.DEFAULT
            .setElementOffset(HomeElement.QR_IMAGE, Float.NaN, Float.POSITIVE_INFINITY)

        assertEquals(0f, layout.offsetX(HomeElement.QR_IMAGE), delta)
        assertEquals(0f, layout.offsetY(HomeElement.QR_IMAGE), delta)
    }

    // ---- visibility ---------------------------------------------------------

    @Test
    fun `a hideable element can be hidden and shown again`() {
        val hidden = HomeLayoutConfig.DEFAULT.toggleVisible(HomeElement.GUIDANCE)

        assertFalse(hidden.isVisible(HomeElement.GUIDANCE))
        assertFalse(hidden.isDefault)
        assertTrue(hidden.toggleVisible(HomeElement.GUIDANCE).isVisible(HomeElement.GUIDANCE))
    }

    @Test
    fun `the elements needed to pay can never be hidden`() {
        val protected = listOf(
            HomeElement.QR_IMAGE,
            HomeElement.SCAN_ACTION,
            HomeElement.CONFIRM_ACTION,
            HomeElement.WARNING_ACTION,
            HomeElement.UNKNOWN_ACTION,
            HomeElement.RETRY_ACTION,
            HomeElement.ADD_QR,
        )

        protected.forEach { element ->
            assertFalse("$element must not be hideable", element.hideable)
            val attempted = HomeLayoutConfig.DEFAULT.setVisible(element, false)
            assertTrue("$element must stay visible", attempted.isVisible(element))
            assertTrue(attempted.isDefault)
        }
    }

    @Test
    fun `only guidance and helper text are offered for hiding`() {
        assertEquals(
            listOf(
                HomeElement.GUIDANCE,
                HomeElement.IMPORT_HINT,
                HomeElement.PROGRESS,
            ),
            HomeElement.entries.filter { element -> element.hideable },
        )
    }

    // ---- the QR image inside its frame --------------------------------------

    @Test
    fun `the QR image moves inside its frame within a bounded range`() {
        val layout = HomeLayoutConfig.DEFAULT
            .moveQrImage(20f, -12f)
            .moveQrImage(4f, 2f)

        assertEquals(24f, layout.qrImageOffsetX, delta)
        assertEquals(-10f, layout.qrImageOffsetY, delta)

        val dragged = layout.moveQrImage(1_000f, 1_000f)
        assertEquals(
            HomeLayoutConfig.MAX_QR_IMAGE_OFFSET_DP,
            dragged.qrImageOffsetX,
            delta,
        )
        assertEquals(
            HomeLayoutConfig.MAX_QR_IMAGE_OFFSET_DP,
            dragged.qrImageOffsetY,
            delta,
        )
        assertTrue(HomeLayoutConfig.MAX_QR_IMAGE_OFFSET_DP > HomeLayoutConfig.MAX_OFFSET_DP)
    }

    @Test
    fun `moving everything back to the default place makes the layout default again`() {
        val moved = HomeLayoutConfig.DEFAULT
            .moveElement(HomeElement.PROGRESS, 30f, 30f)
            .toggleVisible(HomeElement.IMPORT_HINT)
            .moveQrImage(10f, 0f)
        assertFalse(moved.isDefault)

        val restored = moved
            .moveElement(HomeElement.PROGRESS, -30f, -30f)
            .toggleVisible(HomeElement.IMPORT_HINT)
            .moveQrImage(-10f, 0f)

        assertTrue(restored.isDefault)
    }

    // ---- the stored form ----------------------------------------------------

    @Test
    fun `a customised layout survives a save and a load`() {
        val layout = HomeLayoutConfig.DEFAULT
            .moveElement(HomeElement.ADD_QR, 24f, -16f)
            .toggleVisible(HomeElement.GUIDANCE)
            .toggleVisible(HomeElement.PROGRESS)
            .moveQrImage(-8f, 12f)

        val restored = HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout))

        assertEquals(layout.qrImageOffsetX, restored.qrImageOffsetX, delta)
        assertEquals(layout.qrImageOffsetY, restored.qrImageOffsetY, delta)
        HomeElement.entries.forEach { element ->
            assertEquals(element.name, layout.offsetX(element), restored.offsetX(element), delta)
            assertEquals(element.name, layout.offsetY(element), restored.offsetY(element), delta)
            assertEquals(element.name, layout.isVisible(element), restored.isVisible(element))
        }
        assertFalse(restored.isDefault)
    }

    @Test
    fun `the default layout survives a save and a load`() {
        val restored = HomeLayoutCodec.decode(HomeLayoutCodec.encode(HomeLayoutConfig.DEFAULT))

        assertTrue(restored.isDefault)
        HomeElement.entries.forEach { element ->
            assertEquals(0f, restored.offsetX(element), delta)
            assertEquals(0f, restored.offsetY(element), delta)
            assertTrue(restored.isVisible(element))
        }
    }

    @Test
    fun `an absent or unreadable layout falls back to the default`() {
        listOf(null, "", "   ", "not a layout", "v2|PROGRESS:1.0:2.0:0").forEach { raw ->
            assertEquals(HomeLayoutConfig.DEFAULT, HomeLayoutCodec.decode(raw))
        }
    }

    @Test
    fun `a damaged field is ignored instead of breaking the screen`() {
        val decoded = HomeLayoutCodec.decode("v1|PROGRESS:abc:def:1|GUIDANCE:5.0:5.0:0|nonsense")

        assertEquals(0f, decoded.offsetX(HomeElement.PROGRESS), delta)
        assertTrue(decoded.isVisible(HomeElement.PROGRESS))
        assertFalse(decoded.isVisible(HomeElement.GUIDANCE))
        assertEquals(5f, decoded.offsetX(HomeElement.GUIDANCE), delta)
    }

    @Test
    fun `a stored offset that is out of range is clamped on load`() {
        val decoded = HomeLayoutCodec.decode("v1|ADD_QR:9999.0:-9999.0:1|qr:9999.0:-9999.0")

        assertEquals(HomeLayoutConfig.MAX_OFFSET_DP, decoded.offsetX(HomeElement.ADD_QR), delta)
        assertEquals(-HomeLayoutConfig.MAX_OFFSET_DP, decoded.offsetY(HomeElement.ADD_QR), delta)
        assertEquals(
            HomeLayoutConfig.MAX_QR_IMAGE_OFFSET_DP,
            decoded.qrImageOffsetX,
            delta,
        )
        assertEquals(
            -HomeLayoutConfig.MAX_QR_IMAGE_OFFSET_DP,
            decoded.qrImageOffsetY,
            delta,
        )
    }

    @Test
    fun `a stored value can never hide an element that must stay visible`() {
        val decoded = HomeLayoutCodec.decode("v1|SCAN_ACTION:0.0:0.0:0|CONFIRM_ACTION:0.0:0.0:0")

        assertTrue(decoded.isVisible(HomeElement.SCAN_ACTION))
        assertTrue(decoded.isVisible(HomeElement.CONFIRM_ACTION))
        assertTrue(decoded.isDefault)
    }

    @Test
    fun `an element added in a later version keeps its default placement`() {
        // The stored layout only names elements it knows; everything else is the
        // app's own layout, so a new element never arrives hidden or misplaced.
        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(HomeLayoutConfig.DEFAULT))

        assertEquals(
            HomeElement.entries.size,
            HomeElement.entries.count { element -> decoded.isVisible(element) },
        )
    }
}
