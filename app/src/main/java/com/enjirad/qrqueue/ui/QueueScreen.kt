package com.enjirad.qrqueue.ui

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enjirad.qrqueue.R
import com.enjirad.qrqueue.data.KPlusAvailability
import com.enjirad.qrqueue.data.QrImageFiles
import com.enjirad.qrqueue.data.QrShare
import com.enjirad.qrqueue.data.ShareTargets
import com.enjirad.qrqueue.domain.PaymentQueue
import com.enjirad.qrqueue.domain.PaymentStatus
import com.enjirad.qrqueue.domain.QueueItem
import com.enjirad.qrqueue.ui.theme.QrQueueTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every action the queue screen can raise; the route wires them to the ViewModel. */
data class QueueCallbacks(
    val onImportImages: () -> Unit,
    val onShareCurrent: () -> Unit,
    val onViewCurrent: () -> Unit,
    val onReShareConfirmed: () -> Unit,
    val onReShareDismissed: () -> Unit,
    val onConfirmCompleted: () -> Unit,
    val onConfirmNotCompleted: () -> Unit,
    val onRetryCurrent: () -> Unit,
    val onClearRequested: () -> Unit,
    val onClearConfirmed: () -> Unit,
    val onClearDismissed: () -> Unit,
    val onNoticeShown: () -> Unit,
)

/** Connects the screen to its ViewModel and to Android's photo picker. */
@Composable
fun QueueRoute(viewModel: QueueViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        // No fixed item limit: the contract uses the picker limit reported by the
        // device itself, so a device with a lower limit can never reject the launch.
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isEmpty()) {
            viewModel.onImageSelectionCancelled()
        } else {
            viewModel.onImagesPicked(uris)
        }
    }

    LaunchedEffect(state.imageIntent) {
        val request = state.imageIntent ?: return@LaunchedEffect
        viewModel.onImageIntentLaunched(request.kind, launchImageIntent(context, request))
    }

    QueueScreen(
        state = state,
        callbacks = QueueCallbacks(
            onImportImages = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onShareCurrent = viewModel::onShareCurrentRequested,
            onViewCurrent = viewModel::onViewCurrentRequested,
            onReShareConfirmed = viewModel::onReShareConfirmed,
            onReShareDismissed = viewModel::onReShareDismissed,
            onConfirmCompleted = viewModel::onConfirmCompleted,
            onConfirmNotCompleted = viewModel::onConfirmNotCompleted,
            onRetryCurrent = viewModel::onRetryCurrent,
            onClearRequested = viewModel::onClearQueueRequested,
            onClearConfirmed = viewModel::onClearQueueConfirmed,
            onClearDismissed = viewModel::onClearQueueDismissed,
            onNoticeShown = viewModel::onNoticeShown,
        ),
    )
}

/**
 * Hands the stored image to another app (share chooser) or opens it in a viewer.
 * Returns false when nothing could be opened.
 */
private fun launchImageIntent(context: Context, request: ImageIntentRequest): Boolean {
    val file = File(request.filePath)
    val intent = when (request.kind) {
        ImageIntentKind.SHARE -> QrShare.shareIntent(context, file, request.mimeType, request.fileName)
        ImageIntentKind.VIEW -> QrShare.viewIntent(context, file, request.mimeType)
    } ?: return false
    return try {
        context.startActivity(intent)
        true
    } catch (notFound: ActivityNotFoundException) {
        false
    } catch (denied: SecurityException) {
        false
    }
}

@Composable
fun QueueScreen(state: QueueUiState, callbacks: QueueCallbacks) {
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeMessage = when (state.notice) {
        QueueNotice.IMAGES_NOT_IMPORTED -> stringResource(R.string.notice_images_not_imported)
        QueueNotice.SHARE_FAILED -> stringResource(R.string.notice_share_failed)
        QueueNotice.SHARE_TARGET_UNAVAILABLE -> stringResource(R.string.notice_share_target_unavailable)
        QueueNotice.VIEW_TARGET_UNAVAILABLE -> stringResource(R.string.notice_view_target_unavailable)
        QueueNotice.IMAGE_MISSING -> stringResource(R.string.notice_image_missing)
        QueueNotice.QUEUE_NOT_SAVED -> stringResource(R.string.notice_queue_not_saved)
        QueueNotice.QUEUE_CLEARED -> stringResource(R.string.notice_queue_cleared)
        null -> null
    }

    LaunchedEffect(state.notice) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            callbacks.onNoticeShown()
        }
    }

    if (state.clearConfirmationVisible) {
        ClearQueueDialog(
            onConfirm = callbacks.onClearConfirmed,
            onDismiss = callbacks.onClearDismissed,
        )
    }

    if (state.reShareConfirmationVisible) {
        ReShareDialog(
            onConfirm = callbacks.onReShareConfirmed,
            onDismiss = callbacks.onReShareDismissed,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppHeader()
            val queue = state.queue
            when {
                state.importing -> ImportProgressCard(progress = state.importProgress)
                queue == null -> HomeContent(onImportImages = callbacks.onImportImages)
                state.stage == QueueStage.FINISHED -> FinishedContent(queue, callbacks)
                else -> QueueContent(queue, state.confirmationDismissedFor, callbacks)
            }
            SafetyCard()
            Text(
                text = stringResource(R.string.status_pipeline_caption),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

// ---- shell ------------------------------------------------------------------

@Composable
private fun AppHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary) {
            QrGlyph(
                modifier = Modifier
                    .padding(8.dp)
                    .size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.screen_title),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun SectionTitle(text: String, badge: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
        if (badge != null) {
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StatusChip(status: PaymentStatus) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        status.isCompleted -> colors.primaryContainer
        status.isError -> colors.errorContainer
        else -> colors.secondaryContainer
    }
    val content = when {
        status.isCompleted -> colors.onPrimaryContainer
        status.isError -> colors.onErrorContainer
        else -> colors.onSecondaryContainer
    }
    Surface(shape = CircleShape, color = background) {
        Text(
            text = status.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SafetyCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(20.dp)) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.safety_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.safety_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- home -------------------------------------------------------------------

@Composable
private fun HomeContent(onImportImages: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.screen_subtitle),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.screen_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
            ) {
                Text(
                    text = stringResource(R.string.version_chip),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }

    ImportCard(onImportImages = onImportImages)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QrGlyph(
                modifier = Modifier.size(72.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ImportCard(onImportImages: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Button(
                onClick = onImportImages,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_import),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.action_import_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportProgressCard(progress: ImportProgress?) {
    val fraction = if (progress == null || progress.total <= 0) {
        0f
    } else {
        progress.processed.toFloat() / progress.total.toFloat()
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.import_progress_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    R.string.import_progress_text,
                    progress?.processed ?: 0,
                    progress?.total ?: 0,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.import_progress_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- queue ------------------------------------------------------------------

@Composable
private fun QueueContent(
    queue: PaymentQueue,
    confirmationDismissedFor: String?,
    callbacks: QueueCallbacks,
) {
    SectionTitle(
        text = stringResource(R.string.queue_section_title),
        badge = stringResource(R.string.queue_badge_count, queue.itemCount),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            label = stringResource(R.string.stat_completed),
            value = queue.completedCount.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.stat_remaining),
            value = queue.remainingCount.toString(),
            modifier = Modifier.weight(1f),
        )
    }

    Text(
        text = stringResource(R.string.processing_title, queue.currentOrdinal, queue.itemCount),
        style = MaterialTheme.typography.titleMedium,
    )
    LinearProgressIndicator(
        progress = { queue.progressFraction },
        modifier = Modifier.fillMaxWidth(),
    )

    queue.items.forEach { item ->
        QueueItemRow(item = item, isCurrent = item.id == queue.currentItem?.id)
    }

    val current = queue.currentItem ?: return
    CurrentItemCard(item = current)
    when (current.status) {
        PaymentStatus.QUEUED -> QueuedActions(item = current, callbacks = callbacks)
        PaymentStatus.SHARING -> SharingCard()
        PaymentStatus.WAITING_USER -> WaitingActions(
            confirmationDismissed = confirmationDismissedFor == current.id,
            callbacks = callbacks,
        )
        PaymentStatus.FAILED -> FailedActions(item = current, callbacks = callbacks)
        PaymentStatus.UNKNOWN -> UnknownActions(callbacks = callbacks)
        PaymentStatus.COMPLETED -> Unit
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = callbacks.onImportImages, modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.action_import_more))
        }
        TextButton(onClick = callbacks.onClearRequested, modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.action_clear_queue))
        }
    }

    Text(
        text = stringResource(R.string.processing_safety_caption),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun QueueItemRow(item: QueueItem, isCurrent: Boolean) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (item.position + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isCurrent) {
                    Text(
                        text = stringResource(R.string.item_current_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (item.status == PaymentStatus.FAILED && item.failureDetail != null) {
                    Text(
                        text = item.failureDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            StatusChip(status = item.status)
        }
    }
}

@Composable
private fun CurrentItemCard(item: QueueItem) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            QrImagePreview(path = item.storedImagePath)
            Spacer(Modifier.height(16.dp))
            LabelValueRow(label = stringResource(R.string.label_image), value = item.displayName)
            LabelValueRow(label = stringResource(R.string.label_position), value = (item.position + 1).toString())
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.label_status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                StatusChip(status = item.status)
            }
        }
    }
}

@Composable
private fun QrImagePreview(path: String?) {
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = if (path.isNullOrEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                QrImageFiles.loadPreviewBitmap(File(path))?.asImageBitmap()
            }
        }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        // QR images are rendered on white so the code stays scannable in dark mode.
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val bitmap = image
            if (bitmap == null) {
                Text(
                    text = stringResource(R.string.image_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 100.dp),
                )
            } else {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun QueuedActions(item: QueueItem, callbacks: QueueCallbacks) {
    val context = LocalContext.current
    // Describes the real share sheet on this device; never forces a target.
    val targets = remember(item.id, item.mimeType) {
        ShareTargets.query(context, item.mimeType)
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.queued_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.queued_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!targets.canShare) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.share_no_target_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                val kPlusNote = when (targets.kPlus) {
                    KPlusAvailability.NOT_INSTALLED -> R.string.share_kplus_not_installed_note
                    KPlusAvailability.INSTALLED_NOT_SHARE_TARGET -> R.string.share_kplus_not_share_target_note
                    KPlusAvailability.SHARE_TARGET -> null
                }
                if (kPlusNote != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(kPlusNote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = callbacks.onShareCurrent,
                enabled = targets.canShare,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_share),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = callbacks.onViewCurrent, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.action_view_image),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SharingCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.sharing_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.sharing_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * Shown when the image was handed to the share sheet / K PLUS. The user — not the
 * app — decides whether the payment is done.
 */
@Composable
private fun WaitingActions(confirmationDismissed: Boolean, callbacks: QueueCallbacks) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (confirmationDismissed) {
                    stringResource(R.string.waiting_title)
                } else {
                    stringResource(R.string.confirm_question)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (confirmationDismissed) {
                    stringResource(R.string.waiting_body)
                } else {
                    stringResource(R.string.confirm_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = callbacks.onConfirmCompleted,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_confirm_completed),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = if (confirmationDismissed) {
                    callbacks.onShareCurrent
                } else {
                    callbacks.onConfirmNotCompleted
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    text = if (confirmationDismissed) {
                        stringResource(R.string.action_reshare)
                    } else {
                        stringResource(R.string.not_now)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun FailedActions(item: QueueItem, callbacks: QueueCallbacks) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.failed_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.failureDetail ?: stringResource(R.string.failed_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = callbacks.onShareCurrent,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_try_again),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = callbacks.onViewCurrent, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.action_view_image),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun UnknownActions(callbacks: QueueCallbacks) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.unknown_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.unknown_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.unknown_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = callbacks.onConfirmCompleted,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_confirm_completed),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = callbacks.onRetryCurrent,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_retry),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ---- finished ---------------------------------------------------------------

@Composable
private fun FinishedContent(queue: PaymentQueue, callbacks: QueueCallbacks) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.summary_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.summary_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            LabelValueRow(stringResource(R.string.summary_images), queue.itemCount.toString())
            LabelValueRow(stringResource(R.string.summary_completed), queue.completedCount.toString())
        }
    }

    OutlinedButton(
        onClick = callbacks.onClearRequested,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            text = stringResource(R.string.action_new_queue),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

// ---- shared pieces ----------------------------------------------------------

@Composable
private fun LabelValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

/**
 * Shown before sharing an item that was already handed off: repeating a share is
 * how the same bill gets paid twice.
 */
@Composable
private fun ReShareDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.reshare_dialog_title)) },
        text = { Text(text = stringResource(R.string.reshare_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.reshare_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ClearQueueDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.clear_dialog_title)) },
        text = { Text(text = stringResource(R.string.clear_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.clear_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Small decorative QR glyph drawn in code — no image assets, no dependencies.
 * It is a fixed pattern, used purely as a visual motif.
 */
@Composable
private fun QrGlyph(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier) {
        val cell = size.minDimension / 11f
        val block = cell * 0.84f
        qrGlyphModules().forEach { (col, row) ->
            drawRect(
                color = color,
                topLeft = Offset(col * cell, row * cell),
                size = Size(block, block),
            )
        }
    }
}

private fun qrGlyphModules(): List<Pair<Int, Int>> {
    fun ring(col: Int, row: Int): List<Pair<Int, Int>> = buildList {
        for (c in 0..2) {
            for (r in 0..2) {
                if (c != 1 || r != 1) add((col + c) to (row + r))
            }
        }
    }
    return ring(0, 0) + ring(8, 0) + ring(0, 8) + listOf(
        5 to 4, 4 to 5, 6 to 6, 7 to 5, 5 to 7, 8 to 8, 9 to 9, 7 to 9, 9 to 7, 4 to 8,
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun QueueScreenEmptyPreview() {
    QrQueueTheme {
        QueueScreen(
            state = QueueUiState(),
            callbacks = previewCallbacks(),
        )
    }
}

/** Preview-only callback set: no behavior, no data. */
private fun previewCallbacks(): QueueCallbacks = QueueCallbacks(
    onImportImages = {},
    onShareCurrent = {},
    onViewCurrent = {},
    onReShareConfirmed = {},
    onReShareDismissed = {},
    onConfirmCompleted = {},
    onConfirmNotCompleted = {},
    onRetryCurrent = {},
    onClearRequested = {},
    onClearConfirmed = {},
    onClearDismissed = {},
    onNoticeShown = {},
)
