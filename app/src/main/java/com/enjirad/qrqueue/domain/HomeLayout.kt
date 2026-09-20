package com.enjirad.qrqueue.domain

/**
 * One element of the home screen the user may rearrange (and, when it is not
 * critical, hide) in Edit mode.
 *
 * [hideable] is a property of the model, not of the UI: an element that is
 * needed to use the app or to keep a payment safe can never be hidden, so the
 * screen cannot be edited into a state where the user cannot pay, confirm or
 * replace a QR.
 */
enum class HomeElement(val hideable: Boolean) {
    /** The QR preview / QR frame of the current Payment Item. */
    QR_IMAGE(hideable = false),

    /** The single scan action of an item that is ready to be handed over. */
    SCAN_ACTION(hideable = false),

    /** The ✓ action: the user confirms the payment themselves. */
    CONFIRM_ACTION(hideable = false),

    /** The ⚠ action: the user reports that this QR cannot be used. */
    WARNING_ACTION(hideable = false),

    /** The ? action: the result of the hand-off is not known. */
    UNKNOWN_ACTION(hideable = false),

    /** The ↻ action: open the bank again with the same QR. */
    RETRY_ACTION(hideable = false),

    /** The add-QR action. */
    ADD_QR(hideable = false),

    /** The safety / guidance text. */
    GUIDANCE(hideable = true),

    /** The helper text under the add-QR action. */
    IMPORT_HINT(hideable = true),

    /** The "x / y confirmed" caption. */
    PROGRESS(hideable = true),
}

/**
 * Where one [HomeElement] sits and whether it is shown.
 *
 * The offsets are dp relative to the element's built-in place, so they survive
 * a different screen density, and they are clamped ([HomeLayoutConfig]), so a
 * drag can never throw an element off the screen.
 */
data class HomeElementLayout(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val visible: Boolean = true,
) {
    /** True when this element sits where the app puts it and is shown. */
    val isDefault: Boolean get() = offsetX == 0f && offsetY == 0f && visible
}

/**
 * The user's own arrangement of the home screen (V0.9 Edit mode).
 *
 * One model holds everything Edit mode can change — element placement,
 * visibility, the position of the QR image inside its frame — so the state is
 * never scattered across the screen, and the whole configuration is a single
 * value that can be persisted, reset and tested without Android.
 *
 * [DEFAULT] is what an existing user gets: every element in its built-in place
 * and visible, so an app that has no saved layout behaves exactly as before.
 */
data class HomeLayoutConfig(
    val elements: Map<HomeElement, HomeElementLayout> = emptyMap(),
    /** Horizontal position of the QR image inside its frame (dp). */
    val qrImageOffsetX: Float = 0f,
    /** Vertical position of the QR image inside its frame (dp). */
    val qrImageOffsetY: Float = 0f,
) {

    /** The layout of [element], or its default when the user never moved it. */
    fun element(element: HomeElement): HomeElementLayout =
        elements[element] ?: HomeElementLayout()

    /** True when the user currently wants [element] on screen. */
    fun isVisible(element: HomeElement): Boolean = element(element).visible

    /** Horizontal offset of [element] from its default place, in dp. */
    fun offsetX(element: HomeElement): Float = element(element).offsetX

    /** Vertical offset of [element] from its default place, in dp. */
    fun offsetY(element: HomeElement): Float = element(element).offsetY

    /** Places [element] at an absolute offset, clamped to the allowed range. */
    fun setElementOffset(element: HomeElement, offsetX: Float, offsetY: Float): HomeLayoutConfig =
        withElement(element) { current ->
            current.copy(
                offsetX = clamp(offsetX, MAX_OFFSET_DP),
                offsetY = clamp(offsetY, MAX_OFFSET_DP),
            )
        }

    /** Moves [element] by (dx, dy) dp from where it is now. */
    fun moveElement(element: HomeElement, dx: Float, dy: Float): HomeLayoutConfig {
        val current = element(element)
        return setElementOffset(element, current.offsetX + dx, current.offsetY + dy)
    }

    /** Shows or hides [element]; a non-hideable element is never hidden. */
    fun setVisible(element: HomeElement, visible: Boolean): HomeLayoutConfig =
        if (!element.hideable) {
            this
        } else {
            withElement(element) { current -> current.copy(visible = visible) }
        }

    /** Flips the visibility of [element]. */
    fun toggleVisible(element: HomeElement): HomeLayoutConfig =
        setVisible(element, !isVisible(element))

    /**
     * Moves the QR image inside its frame. The range is deliberately smaller
     * than the element range: the code must stay visible enough for the bank app
     * to read it, so it can never be dragged out of the frame and cropped away.
     */
    fun moveQrImage(dx: Float, dy: Float): HomeLayoutConfig = copy(
        qrImageOffsetX = clamp(qrImageOffsetX + dx, MAX_QR_IMAGE_OFFSET_DP),
        qrImageOffsetY = clamp(qrImageOffsetY + dy, MAX_QR_IMAGE_OFFSET_DP),
    )

    /** True when nothing has been customised: the app's own layout. */
    val isDefault: Boolean
        get() = qrImageOffsetX == 0f &&
            qrImageOffsetY == 0f &&
            elements.values.all { layout -> layout.isDefault }

    private fun withElement(
        element: HomeElement,
        transform: (HomeElementLayout) -> HomeElementLayout,
    ): HomeLayoutConfig = copy(elements = elements + (element to transform(element(element))))

    companion object {
        /** How far one element may sit from its built-in place (dp). */
        const val MAX_OFFSET_DP = 72f

        /** How far the QR image may move inside its frame (dp). */
        const val MAX_QR_IMAGE_OFFSET_DP = 96f

        /** The app's own layout: everything in place, everything shown. */
        val DEFAULT = HomeLayoutConfig()

        /** NaN / infinite values are refused so they can never reach the screen. */
        private fun clamp(value: Float, limit: Float): Float =
            if (value.isFinite()) value.coerceIn(-limit, limit) else 0f
    }
}

/**
 * The stored form of a [HomeLayoutConfig].
 *
 * Pure Kotlin on purpose: the settings store only ever sees a string, the
 * encode/decode rules are unit-tested without Android, and a value written by an
 * older or damaged version can never crash the app — anything unreadable decodes
 * to [HomeLayoutConfig.DEFAULT], which is exactly how a fresh install behaves.
 */
object HomeLayoutCodec {

    /** Bumped only if the stored grammar changes; other versions decode to default. */
    const val VERSION = "v1"

    private const val ELEMENT_SEPARATOR = "|"
    private const val FIELD_SEPARATOR = ":"
    private const val QR_KEY = "qr"
    private const val VISIBLE = "1"
    private const val HIDDEN = "0"

    fun encode(config: HomeLayoutConfig): String {
        val fields = mutableListOf(VERSION)
        HomeElement.entries.forEach { element ->
            val layout = config.element(element)
            fields += listOf(
                element.name,
                layout.offsetX.toString(),
                layout.offsetY.toString(),
                if (layout.visible) VISIBLE else HIDDEN,
            ).joinToString(FIELD_SEPARATOR)
        }
        fields += listOf(
            QR_KEY,
            config.qrImageOffsetX.toString(),
            config.qrImageOffsetY.toString(),
        ).joinToString(FIELD_SEPARATOR)
        return fields.joinToString(ELEMENT_SEPARATOR)
    }

    fun decode(raw: String?): HomeLayoutConfig {
        if (raw.isNullOrBlank()) return HomeLayoutConfig.DEFAULT
        val fields = raw.split(ELEMENT_SEPARATOR)
        if (fields.firstOrNull() != VERSION) return HomeLayoutConfig.DEFAULT
        var config = HomeLayoutConfig.DEFAULT
        fields.drop(1).forEach { field ->
            val parts = field.split(FIELD_SEPARATOR)
            if (parts.size < 3) return@forEach
            if (parts[0] == QR_KEY) {
                val dx = parts[1].toFiniteFloat() ?: return@forEach
                val dy = parts[2].toFiniteFloat() ?: return@forEach
                config = config.moveQrImage(dx, dy)
                return@forEach
            }
            val element = HomeElement.entries.firstOrNull { it.name == parts[0] } ?: return@forEach
            val dx = parts[1].toFiniteFloat() ?: 0f
            val dy = parts[2].toFiniteFloat() ?: 0f
            config = config.setElementOffset(element, dx, dy)
            // A truncated field is simply ignored, so a damaged value can never
            // throw here: an unknown layout decodes to the app default instead.
            if (parts.getOrNull(3) == HIDDEN) config = config.setVisible(element, false)
        }
        return config
    }

    /** Reads one stored number, refusing anything the screen cannot use. */
    private fun String.toFiniteFloat(): Float? = toFloatOrNull()?.takeIf { value -> value.isFinite() }
}
