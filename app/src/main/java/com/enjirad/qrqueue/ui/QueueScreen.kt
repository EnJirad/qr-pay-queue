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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enjirad.qrqueue.R
import com.enjirad.qrqueue.data.BankTarget
import com.enjirad.qrqueue.data.QrImageFiles
import com.enjirad.qrqueue.data.QrShare
import com.enjirad.qrqueue.domain.BankAvailability
import com.enjirad.qrqueue.domain.BankInfo
import com.enjirad.qrqueue.domain.BankRegistry
import com.enjirad.qrqueue.domain.BankTargetStatus
import com.enjirad.qrqueue.domain.ImportProgress
import com.enjirad.qrqueue.domain.ImportSummary
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
    val onShareItem: (String) -> Unit,
    val onViewItem: (String) -> Unit,
    val onConfirmCompleted: (String) -> Unit,
    val onKeepWaiting: (String) -> Unit,
    val onRetryItem: (String) -> Unit,
    val onReShareConfirmed: () -> Unit,
    val onReShareDismissed: () -> Unit,
    val onImportSummaryShown: () -> Unit,
    val onClearRequested: () -> Unit,
    val onClearConfirmed: () -> Unit,
    val onClearDismissed: () -> Unit,
    val onNoticeShown: () -> Unit,
    val onChangeBank: () -> Unit,
    val onBankSelected: (String) -> Unit,
    val onBankSelectionDismissed: () -> Unit,
)

/** Connects the screen to its ViewModel and to Android's photo picker. */
@Composable
fun QueueRoute(viewModel: QueueViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
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
        viewModel.onImageIntentLaunched(
            request.kind,
            launchImageIntent(context, request),
        )
    }

    QueueScreen(
        state = state,
        callbacks = QueueCallbacks(
            onImportImages = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onShareItem = viewModel::onShareItemRequested,
            onViewItem = viewModel::onViewItemRequested,
            onConfirmCompleted = viewModel::onConfirmCompleted,
            onKeepWaiting = viewModel::onKeepWaiting,
            onRetryItem = viewModel::onRetryItem,
            onReShareConfirmed = viewModel::onReShareConfirmed,
            onReShareDismissed = viewModel::onReShareDismissed,
            onImportSummaryShown = viewModel::onImportSummaryShown,
            onClearRequested = viewModel::onClearQueueRequested,
            onClearConfirmed = viewModel::onClearQueueConfirmed,
            onClearDismissed = viewModel::onClearQueueDismissed,
            onNoticeShown = viewModel::onNoticeShown,
            onChangeBank = viewModel::onChangeBankRequested,
            onBankSelected = viewModel::onBankSelected,
            onBankSelectionDismissed = viewModel::onBankSelectionDismissed,
        ),
    )
}

/**
 * Hands the stored image straight to the selected bank, or opens it in a viewer.
 * Returns false when nothing could be opened, so the item can be recorded as
 * failed.
 */
private fun launchImageIntent(context: Context, request: ImageIntentRequest): Boolean {
    val file = File(request.filePath)
    val intent = when (request.kind) {
        ImageIntentKind.SHARE -> QrShare.bankShareIntent(
            context, file, request.mimeType,
            request.targetPackage ?: return false,
        )
        ImageIntentKind.VIEW -> QrShare.viewIntent(context, file, request.mimeType)
    } ?: return false
    // V0.4.2 §9 step 4: the selected bank must be able to resolve this intent.
    // A bank-addressed intent is never widened to the Android chooser and never
    // falls back to another app (§16/§17).
    if (request.kind == ImageIntentKind.SHARE &&
        intent.resolveActivity(context.packageManager) == null
    ) {
        return false
    }
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
        QueueNotice.SHARE_TARGET_UNAVAILABLE -> stringResource(R.string.notice_share_target_unavailable)
        QueueNotice.BANK_UNAVAILABLE -> stringResource(R.string.notice_bank_unavailable)
        QueueNotice.BANK_UNINSTALLED -> stringResource(R.string.notice_bank_uninstalled)
        QueueNotice.HANDOFF_IN_PROGRESS -> stringResource(R.string.notice_handoff_in_progress)
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
            bankName = state.selectedBank?.displayName ?: stringResource(R.string.bank_generic),
            onConfirm = callbacks.onReShareConfirmed,
            onDismiss = callbacks.onReShareDismissed,
        )
    }

    if (state.bankSelectionVisible) {
        BankSelectionDialog(
            currentBank = state.selectedBank,
            onBankSelected = callbacks.onBankSelected,
            onDismiss = callbacks.onBankSelectionDismissed,
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
            state.importSummary?.let { summary ->
                ImportSummaryBanner(summary = summary, onDismiss = callbacks.onImportSummaryShown)
            }
            // Bank selection card is always visible on the home/queue screen.
            BankSelectionCard(
                selectedBank = state.selectedBank,
                bankStatus = state.bankStatus,
                onChangeBank = callbacks.onChangeBank,
            )
            val queue = state.queue
            when {
                state.importing -> ImportProgressCard(progress = state.importProgress)
                queue == null -> HomeContent(
                    canImport = state.canImport,
                    onImportImages = callbacks.onImportImages,
                )
                else -> QueueContent(
                    queue = queue,
                    selectedBank = state.selectedBank,
                    confirmationDismissedFor = state.confirmationDismissedFor,
                    callbacks = callbacks,
                )
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
            text = status.label,
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

// ---- bank selection ---------------------------------------------------------

@Composable
private fun BankIdentityRow(bank: BankInfo) {
    Column {
        Text(text = bank.displayName, style = MaterialTheme.typography.titleMedium)
        Text(
            text = bank.company,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BankStatusNote(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun BankSelectionCard(
    selectedBank: BankInfo?,
    bankStatus: BankTargetStatus?,
    onChangeBank: () -> Unit,
) {
    val alreadySelected = selectedBank != null
    val installed = bankStatus?.isInstalled == true
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (alreadySelected && !installed) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.bank_section_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(10.dp))
            val bank = selectedBank
            if (bank == null) {
                // No bank selected yet — first install or cleared.
                Text(
                    text = stringResource(R.string.bank_not_selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.bank_not_selected_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // A probe that never ran or could not complete reads as UNKNOWN.
                val availability = bankStatus?.availability ?: BankAvailability.UNKNOWN
                when (availability) {
                    BankAvailability.UNKNOWN -> {
                        BankIdentityRow(bank = bank)
                        Spacer(Modifier.height(2.dp))
                        BankStatusNote(
                            text = stringResource(R.string.bank_status_unknown),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    BankAvailability.NOT_INSTALLED -> {
                        BankIdentityRow(bank = bank)
                        Spacer(Modifier.height(2.dp))
                        BankStatusNote(
                            text = stringResource(R.string.bank_not_found),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    BankAvailability.INSTALLED_BUT_NOT_SHARE_CAPABLE -> {
                        // Installed, but no image-share activity was found. NOT "missing".
                        BankIdentityRow(bank = bank)
                        Spacer(Modifier.height(2.dp))
                        BankStatusNote(
                            text = stringResource(R.string.bank_installed_not_advertised),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    BankAvailability.SHARE_CAPABLE -> {
                        // Installed and advertises image sharing.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = bank.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = bank.company,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.bank_ready),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onChangeBank,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (selectedBank == null) {
                        stringResource(R.string.action_select_bank)
                    } else {
                        stringResource(R.string.action_change_bank)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun BankSelectionDialog(
    currentBank: BankInfo?,
    onBankSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Probe every known bank on this device.
    val bankStatuses = remember {
        BankRegistry.allBanks.map { bank -> BankTarget.query(context, bank) }
    }
    var selectedId by remember { mutableStateOf(currentBank?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.bank_dialog_title)) },
        text = {
            Column {
                bankStatuses.forEach { status ->
                    val bank = status.bank
                    val isAvailable = status.canHandOff
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedId == bank.id,
                                enabled = isAvailable,
                                onClick = { selectedId = bank.id },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedId == bank.id,
                            onClick = null,
                            enabled = isAvailable,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bank.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isAvailable) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                            )
                            Text(
                                text = when {
                                    status.availability == BankAvailability.NOT_INSTALLED ->
                                        stringResource(R.string.bank_option_not_installed)
                                    status.availability == BankAvailability.UNKNOWN ->
                                        stringResource(R.string.bank_option_unknown)
                                    status.availability == BankAvailability.INSTALLED_BUT_NOT_SHARE_CAPABLE ->
                                        stringResource(R.string.bank_option_not_advertised)
                                    else ->
                                        stringResource(R.string.bank_option_ready)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    !isAvailable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    status.availability == BankAvailability.INSTALLED_BUT_NOT_SHARE_CAPABLE ->
                                        MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedId?.let { onBankSelected(it) } },
                enabled = selectedId != null,
            ) {
                Text(text = stringResource(R.string.bank_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

// ---- home -------------------------------------------------------------------

@Composable
private fun HomeContent(canImport: Boolean, onImportImages: () -> Unit) {
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

    ImportCard(enabled = canImport, onImportImages = onImportImages)

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
private fun ImportCard(enabled: Boolean, onImportImages: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Button(
                onClick = onImportImages,
                enabled = enabled,
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
                text = if (enabled) {
                    stringResource(R.string.action_import_hint)
                } else {
                    stringResource(R.string.action_import_disabled_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                },
            )
        }
    }
}

// ---- import progress --------------------------------------------------------

@Composable
private fun ImportProgressCard(progress: ImportProgress?) {
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
                progress = { progress?.fraction ?: 0f },
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

@Composable
private fun ImportSummaryBanner(summary: ImportSummary, onDismiss: () -> Unit) {
    val failed = summary.hasFailures
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (failed) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        val contentColor = if (failed) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
        Row(modifier = Modifier.padding(20.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (failed) {
                        stringResource(
                            R.string.import_summary_partial_title,
                            summary.imported,
                            summary.selected,
                        )
                    } else {
                        stringResource(R.string.import_summary_ok_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (failed) {
                        stringResource(R.string.import_summary_partial_body, summary.failed)
                    } else {
                        stringResource(
                            R.string.import_summary_ok_body,
                            summary.imported,
                            summary.selected,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_dismiss), color = contentColor)
            }
        }
    }
}

// ---- queue ------------------------------------------------------------------

@Composable
private fun QueueContent(
    queue: PaymentQueue,
    selectedBank: BankInfo?,
    confirmationDismissedFor: String?,
    callbacks: QueueCallbacks,
) {
    SectionTitle(
        text = stringResource(R.string.queue_section_title),
        badge = stringResource(R.string.queue_badge_count, queue.itemCount),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(
            label = stringResource(R.string.stat_total),
            value = queue.itemCount.toString(),
            modifier = Modifier.weight(1f),
        )
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

    if (queue.finished) {
        FinishedBanner(queue = queue, onBackHome = callbacks.onClearRequested)
    }

    val answerItem = queue.awaitingAnswerItem
    if (answerItem != null && confirmationDismissedFor != answerItem.id) {
        ConfirmPaymentPanel(item = answerItem, selectedBank = selectedBank, callbacks = callbacks)
    }

    queue.unknownItem?.let { unknown ->
        UnknownResultPanel(item = unknown, callbacks = callbacks)
    }

    if (answerItem != null && confirmationDismissedFor == answerItem.id) {
        StillWaitingCard(
            item = answerItem,
            bankName = selectedBank?.displayName ?: stringResource(R.string.bank_generic),
        )
    }

    SectionTitle(text = stringResource(R.string.queue_list_title))

    queue.items.forEach { item ->
        QueueItemCard(item = item, queue = queue, selectedBank = selectedBank, callbacks = callbacks)
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
private fun QueueItemCard(
    item: QueueItem,
    queue: PaymentQueue,
    selectedBank: BankInfo?,
    callbacks: QueueCallbacks,
) {
    val canStart = queue.canStartHandoff(item.id)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (item.status.awaitsUserAnswer) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    if (item.status.isError && item.failureDetail != null) {
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

            when {
                item.status.isCompleted -> CompletedRow()
                item.status == PaymentStatus.SHARING ->
                    ItemHint(text = stringResource(R.string.item_sharing_hint))
                item.status == PaymentStatus.UNKNOWN ->
                    ItemHint(text = stringResource(R.string.item_unknown_hint))
                else -> ShareRow(
                    item = item,
                    enabled = canStart && selectedBank != null,
                    selectedBank = selectedBank,
                    blocked = !canStart,
                    callbacks = callbacks,
                )
            }
        }
    }
}

@Composable
private fun CompletedRow() {
    Row(
        modifier = Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.item_completed_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ItemHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun ShareRow(
    item: QueueItem,
    enabled: Boolean,
    selectedBank: BankInfo?,
    blocked: Boolean,
    callbacks: QueueCallbacks,
) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = { callbacks.onShareItem(item.id) },
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
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
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = { callbacks.onViewItem(item.id) }) {
            Text(
                text = stringResource(R.string.action_view_image),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    when {
        blocked -> ItemHint(text = stringResource(R.string.share_blocked_note))
        selectedBank == null -> ItemHint(text = stringResource(R.string.share_no_bank_note))
    }
}

@Composable
private fun ConfirmPaymentPanel(
    item: QueueItem,
    selectedBank: BankInfo?,
    callbacks: QueueCallbacks,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            QrImagePreview(path = item.storedImagePath)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.confirm_question),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.confirm_body,
                    selectedBank?.displayName ?: stringResource(R.string.bank_generic),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { callbacks.onConfirmCompleted(item.id) },
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
                onClick = { callbacks.onKeepWaiting(item.id) },
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
        }
    }
}

@Composable
private fun StillWaitingCard(item: QueueItem, bankName: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.waiting_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.waiting_body, (item.position + 1), bankName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun UnknownResultPanel(item: QueueItem, callbacks: QueueCallbacks) {
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
            Spacer(Modifier.height(10.dp))
            QrImagePreview(path = item.storedImagePath)
            Spacer(Modifier.height(12.dp))
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
                onClick = { callbacks.onConfirmCompleted(item.id) },
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
                onClick = { callbacks.onRetryItem(item.id) },
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
        }
    }
}

@Composable
private fun QrImagePreview(path: String?) {
    val imageState = remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        imageState.value = if (path.isNullOrEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                QrImageFiles.loadPreviewBitmap(File(path))?.asImageBitmap()
            }
        }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
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
            val bitmap = imageState.value
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
                        .height(220.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

// ---- finished ---------------------------------------------------------------

@Composable
private fun FinishedBanner(queue: PaymentQueue, onBackHome: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.finished_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.finished_count,
                    queue.completedCount,
                    queue.itemCount,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.finished_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onBackHome,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_back_home),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ---- shared pieces ----------------------------------------------------------

@Composable
private fun ReShareDialog(bankName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.reshare_dialog_title, bankName)) },
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

private fun previewCallbacks(): QueueCallbacks = QueueCallbacks(
    onImportImages = {},
    onShareItem = {},
    onViewItem = {},
    onConfirmCompleted = {},
    onKeepWaiting = {},
    onRetryItem = {},
    onReShareConfirmed = {},
    onReShareDismissed = {},
    onImportSummaryShown = {},
    onClearRequested = {},
    onClearConfirmed = {},
    onClearDismissed = {},
    onNoticeShown = {},
    onChangeBank = {},
    onBankSelected = {},
    onBankSelectionDismissed = {},
)
