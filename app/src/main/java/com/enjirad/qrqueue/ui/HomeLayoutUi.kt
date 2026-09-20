package com.enjirad.qrqueue.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enjirad.qrqueue.R
import com.enjirad.qrqueue.domain.HomeElement
import com.enjirad.qrqueue.domain.HomeLayoutConfig

/**
 * The home screen's Edit mode (V0.9 §3), kept in its own file so the editing UI
 * stays separable from the payment queue screen it edits.
 *
 * Edit mode never changes what an action means outside itself: in normal mode
 * [HomeElementBox] adds nothing at all, and every action behind it behaves
 * exactly as before.
 */

/**
 * Wraps one movable home element.
 *
 * In normal mode this is a transparent wrapper: the element keeps its built-in
 * place, keeps its own size and behaves as it always did.
 *
 * In edit mode the element can be dragged with one finger and gets a thin
 * outline so it is obvious what can be moved. The drag is consumed while it
 * happens, so it can never be delivered to the action underneath as a tap, and
 * the offset itself is clamped by [HomeLayoutConfig] so no element can be
 * dragged off the screen.
 *
 * @param onMove receives the element and the drag delta in dp.
 */
@Composable
internal fun HomeElementBox(
    element: HomeElement,
    layout: HomeLayoutConfig,
    editMode: Boolean,
    onMove: (HomeElement, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val placement = layout.element(element)
    Box(
        modifier = modifier
            .offset(x = placement.offsetX.dp, y = placement.offsetY.dp)
            .then(
                if (!editMode) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(element, editMode) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onMove(
                                        element,
                                        dragAmount.x.toDp().value,
                                        dragAmount.y.toDp().value,
                                    )
                                },
                            )
                        }
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(16.dp),
                        )
                },
            ),
        content = content,
    )
}

/**
 * The Edit-mode panel.
 *
 * It holds the visibility control of every element that may be hidden, so an
 * element that is currently hidden can always be brought back: the control lives
 * in the panel, not only on the element itself. Elements the model marks as
 * needed for paying or confirming are not offered here at all — they cannot be
 * hidden in the first place.
 */
@Composable
internal fun EditLayoutPanel(
    layout: HomeLayoutConfig,
    onToggleVisibility: (HomeElement) -> Unit,
    onResetRequested: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.edit_mode_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.edit_mode_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(14.dp))
            HomeElement.entries.filter { element -> element.hideable }.forEach { element ->
                val visible = layout.isVisible(element)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(homeElementLabel(element)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    VisibilityToggle(
                        visible = visible,
                        element = element,
                        onToggle = onToggleVisibility,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onResetRequested,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.edit_reset_layout))
                }
                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.action_edit_done))
                }
            }
        }
    }
}

/**
 * The eye control: a check means the element is shown, a cross means it is
 * hidden. Both states are reachable from here, so hiding is never a one-way
 * action.
 */
@Composable
private fun VisibilityToggle(
    visible: Boolean,
    element: HomeElement,
    onToggle: (HomeElement) -> Unit,
) {
    Surface(
        onClick = { onToggle(element) },
        shape = CircleShape,
        color = if (visible) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Icon(
            imageVector = if (visible) Icons.Outlined.Check else Icons.Outlined.Clear,
            contentDescription = stringResource(
                if (visible) R.string.edit_hide_element else R.string.edit_show_element,
            ),
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .size(16.dp),
            tint = if (visible) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** The confirmation shown before the whole layout is thrown away. */
@Composable
internal fun ResetLayoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.reset_layout_title)) },
        text = { Text(text = stringResource(R.string.reset_layout_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.reset_layout_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** The name of one element in the Edit panel. */
private fun homeElementLabel(element: HomeElement): Int = when (element) {
    HomeElement.QR_IMAGE -> R.string.edit_element_qr_image
    HomeElement.SCAN_ACTION -> R.string.edit_element_scan
    HomeElement.CONFIRM_ACTION -> R.string.edit_element_confirm
    HomeElement.WARNING_ACTION -> R.string.edit_element_warning
    HomeElement.UNKNOWN_ACTION -> R.string.edit_element_unknown
    HomeElement.RETRY_ACTION -> R.string.edit_element_retry
    HomeElement.ADD_QR -> R.string.edit_element_add_qr
    HomeElement.GUIDANCE -> R.string.edit_element_guidance
    HomeElement.IMPORT_HINT -> R.string.edit_element_import_hint
    HomeElement.PROGRESS -> R.string.edit_element_progress
}
