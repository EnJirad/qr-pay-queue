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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.enjirad.qrqueue.domain.HandPreference
import androidx.compose.material3.ExperimentalMaterial3Api
import com.enjirad.qrqueue.ui.theme.QrQueueTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val onMarkQrUnusable: (String) -> Unit,
    val onReplaceQr: (String) -> Unit,
    val onImportSummaryShown: () -> Unit,
    val onClearRequested: () -> Unit,
    val onClearConfirmed: () -> Unit,
    val onClearDismissed: () -> Unit,
    val onNoticeShown: () -> Unit,
    val onChangeBank: () -> Unit,
    val onBankSelected: (String) -> Unit,
    val onBankSelectionDismissed: () -> Unit,
    val onSelectTab: (QueueTab) -> Unit,
    val onSettingsRequested: () -> Unit,
    val onSettingsDismissed: () -> Unit,
    val onHandPreferenceChanged: (HandPreference) -> Unit,
    val onAutoDailyResetChanged: (Boolean) -> Unit,
    val onManualDailyResetRequested: () -> Unit,
    val onReportProblem: (String) -> Unit,
    val onProblemReasonSelected: (String) -> Unit,
    val onProblemReasonDismissed: () -> Unit,
    val onClearItem: (String) -> Unit,
    val onClearItemConfirmed: () -> Unit,
    val onClearItemDismissed: () -> Unit,
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
    val replacementPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            viewModel.onReplacementCancelled()
        } else {
            viewModel.onReplacementImagePicked(uri)
        }
    }

    // Replacing a QR opens the picker for exactly one image, and never creates a
    // new Payment Item: the result is attached to the item that asked for it.
    LaunchedEffect(state.replaceQrItemId) {
        if (state.replaceQrItemId != null) {
            replacementPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
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
            onMarkQrUnusable = viewModel::onMarkQrUnusable,
            onReplaceQr = viewModel::onReplaceQrRequested,
            onImportSummaryShown = viewModel::onImportSummaryShown,
            onClearRequested = viewModel::onClearQueueRequested,
            onClearConfirmed = viewModel::onClearQueueConfirmed,
            onClearDismissed = viewModel::onClearQueueDismissed,
            onNoticeShown = viewModel::onNoticeShown,
            onChangeBank = viewModel::onChangeBankRequested,
            onBankSelected = viewModel::onBankSelected,
            onBankSelectionDismissed = viewModel::onBankSelectionDismissed,
            onSelectTab = viewModel::onTabSelected,
            onSettingsRequested = viewModel::onSettingsRequested,
            onSettingsDismissed = viewModel::onSettingsDismissed,
            onHandPreferenceChanged = viewModel::onHandPreferenceChanged,
            onAutoDailyResetChanged = viewModel::onAutoDailyResetChanged,
            onManualDailyResetRequested = viewModel::onManualDailyResetRequested,
            onReportProblem = viewModel::onReportProblemRequested,
            onProblemReasonSelected = viewModel::onProblemReasonSelected,
            onProblemReasonDismissed = viewModel::onProblemReasonDismissed,
            onClearItem = viewModel::onClearItemRequested,
            onClearItemConfirmed = viewModel::onClearItemConfirmed,
            onClearItemDismissed = viewModel::onClearItemDismissed,
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
        QueueNotice.BANK_NOT_SHARE_CAPABLE -> stringResource(R.string.notice_bank_not_share_capable)
        QueueNotice.HANDOFF_IN_PROGRESS -> stringResource(R.string.notice_handoff_in_progress)
        QueueNotice.VIEW_TARGET_UNAVAILABLE -> stringResource(R.string.notice_view_target_unavailable)
        QueueNotice.IMAGE_MISSING -> stringResource(R.string.notice_image_missing)
        QueueNotice.QUEUE_NOT_SAVED -> stringResource(R.string.notice_queue_not_saved)
        QueueNotice.QUEUE_CLEARED -> stringResource(R.string.notice_queue_cleared)
        QueueNotice.QR_REPLACEMENT_FAILED -> stringResource(R.string.notice_qr_replacement_failed)
        QueueNotice.DAILY_DATA_CLEARED -> stringResource(R.string.notice_daily_data_cleared)
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

    if (state.bankSelectionVisible) {
        BankSelectionDialog(
            currentBank = state.selectedBank,
            onBankSelected = callbacks.onBankSelected,
            onDismiss = callbacks.onBankSelectionDismissed,
        )
    }

    if (state.settingsVisible) {
        SettingsDialog(
            handPreference = state.handPreference,
            autoDailyReset = state.autoDailyReset,
            selectedBank = state.selectedBank,
            onHandPreferenceChanged = callbacks.onHandPreferenceChanged,
            onAutoDailyResetChanged = callbacks.onAutoDailyResetChanged,
            onManualDailyReset = callbacks.onManualDailyResetRequested,
            onChangeBank = callbacks.onChangeBank,
            onDismiss = callbacks.onSettingsDismissed,
        )
    }

    if (state.problemReasonSheetVisible) {
        ProblemReasonsSheet(
            onReasonSelected = callbacks.onProblemReasonSelected,
            onDismiss = callbacks.onProblemReasonDismissed,
        )
    }

    if (state.clearItemConfirmationVisible) {
        val itemLabel = state.itemToClearId?.let { id ->
            state.queue?.item(id)?.itemLabel ?: id
        } ?: ""
        ClearItemDialog(
            itemLabel = itemLabel,
            onConfirm = callbacks.onClearItemConfirmed,
            onDismiss = callbacks.onClearItemDismissed,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { QueueBottomBar(state = state, onSelectTab = callbacks.onSelectTab) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppHeader(onSettingsRequested = callbacks.onSettingsRequested)
            when (state.selectedTab) {
                QueueTab.HOME -> HomeTab(state = state, callbacks = callbacks)
                QueueTab.PROBLEMS -> ProblemsTab(queue = state.queue, callbacks = callbacks)
                QueueTab.COMPLETED -> CompletedTab(queue = state.queue)
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

// ---- navigation -------------------------------------------------------------

@Composable
private fun QueueBottomBar(state: QueueUiState, onSelectTab: (QueueTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = state.selectedTab == QueueTab.HOME,
            onClick = { onSelectTab(QueueTab.HOME) },
            icon = { Icon(imageVector = Icons.Outlined.Home, contentDescription = null) },
            label = { Text(text = stringResource(R.string.tab_home)) },
        )
        NavigationBarItem(
            selected = state.selectedTab == QueueTab.PROBLEMS,
            onClick = { onSelectTab(QueueTab.PROBLEMS) },
            icon = {
                BadgedBox(
                    badge = {
                        val badge = state.problemBadge
                        if (badge != null) {
                            Badge { Text(text = badge.toString()) }
                        }
                    },
                ) {
                    Icon(imageVector = Icons.Outlined.Warning, contentDescription = null)
                }
            },
            label = { Text(text = stringResource(R.string.tab_problems)) },
        )
        NavigationBarItem(
            selected = state.selectedTab == QueueTab.COMPLETED,
            onClick = { onSelectTab(QueueTab.COMPLETED) },
            icon = { Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null) },
            label = { Text(text = stringResource(R.string.tab_completed)) },
        )
    }
}

// ---- shell ------------------------------------------------------------------

@Composable
private fun AppHeader(onSettingsRequested: () -> Unit) {
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
            modifier = Modifier.weight(1f),
        )
        Surface(
            onClick = onSettingsRequested,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(36.dp),
        ) {
            Text(
                text = stringResource(R.string.action_settings),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize().wrapContentSize(),
            )
        }
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
private fun StatusChip(status: PaymentStatus) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        status.isCompleted -> colors.primaryContainer
        status.isProblem -> colors.errorContainer
        status == PaymentStatus.AWAITING_USER_CONFIRMATION -> colors.tertiaryContainer
        else -> colors.secondaryContainer
    }
    val content = when {
        status.isCompleted -> colors.onPrimaryContainer
        status.isProblem -> colors.onErrorContainer
        status == PaymentStatus.AWAITING_USER_CONFIRMATION -> colors.onTertiaryContainer
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

// ---- tab 1: home ------------------------------------------------------------

@Composable
private fun ColumnScope.HomeTab(state: QueueUiState, callbacks: QueueCallbacks) {
    state.importSummary?.let { summary ->
        ImportSummaryBanner(summary = summary, onDismiss = callbacks.onImportSummaryShown)
    }

    // Bank selection card is always visible on Home.
    BankSelectionCard(
        selectedBank = state.selectedBank,
        bankStatus = state.bankStatus,
        onChangeBank = callbacks.onChangeBank,
    )

    if (state.importing) {
        ImportProgressCard(progress = state.importProgress)
        return
    }

    val queue = state.queue
    if (queue == null) {
        EmptyHome(canImport = state.canImport, onImportImages = callbacks.onImportImages)
        return
    }

    val problem = state.nextActionItem?.takeIf { item -> item.status.isProblem }
    val awaiting = state.awaitingAnswerItem
    val ready = state.nextActionItem?.takeIf { item -> item.status == PaymentStatus.READY }
    val highlighted = setOfNotNull(problem?.id, awaiting?.id, ready?.id)

    if (problem != null) {
        SectionTitle(text = stringResource(R.string.home_attention_title))
        ProblemItemCard(item = problem, callbacks = callbacks)
    }

    if (awaiting != null) {
        SectionTitle(text = stringResource(R.string.confirm_section_title))
        ConfirmPaymentPanel(
            item = awaiting,
            selectedBank = state.selectedBank,
            callbacks = callbacks,
        )
    } else if (awaiting == null && problem == null && ready != null) {
        SectionTitle(text = stringResource(R.string.home_ready_title))
        ReadyItemCard(item = ready, selectedBank = state.selectedBank, callbacks = callbacks)
    }

    if (awaiting == null && problem == null && ready == null && queue.finished) {
        FinishedBanner(queue = queue, onImportImages = callbacks.onImportImages)
    }

    val remaining = queue.items
        .filter { item -> item.isActive && item.id !in highlighted }
        .sortedBy { item -> item.position }
    if (remaining.isNotEmpty()) {
        SectionTitle(
            text = stringResource(R.string.home_remaining_title),
            badge = stringResource(R.string.queue_badge_count, remaining.size),
        )
        remaining.forEach { item ->
            CompactItemCard(item = item, queue = queue, callbacks = callbacks)
        }
    }

    ImportCard(canImport = state.canImport, onImportImages = callbacks.onImportImages)

    TextButton(onClick = callbacks.onClearRequested) {
        Text(text = stringResource(R.string.action_clear_queue))
    }
}

@Composable
private fun EmptyHome(canImport: Boolean, onImportImages: () -> Unit) {
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

    ImportCard(canImport = canImport, onImportImages = onImportImages)

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
private fun ImportCard(canImport: Boolean, onImportImages: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Button(
                onClick = onImportImages,
                enabled = canImport,
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
                text = if (canImport) {
                    stringResource(R.string.action_import_hint)
                } else {
                    stringResource(R.string.action_import_disabled_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (canImport) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                },
            )
        }
    }
}

// ---- tab 2: problems --------------------------------------------------------

@Composable
private fun ColumnScope.ProblemsTab(queue: PaymentQueue?, callbacks: QueueCallbacks) {
    val problems = queue?.problemItems.orEmpty()
    SectionTitle(
        text = stringResource(R.string.problems_title),
        badge = if (problems.isEmpty()) null else problems.size.toString(),
    )

    if (problems.isEmpty()) {
        EmptyStateCard(
            title = stringResource(R.string.problems_empty_title),
            body = stringResource(R.string.problems_empty_body),
        )
        return
    }

    problems.forEach { item ->
        ProblemItemCard(item = item, callbacks = callbacks)
    }
}

// ---- tab 3: completed -------------------------------------------------------

@Composable
private fun ColumnScope.CompletedTab(queue: PaymentQueue?) {
    val completed = queue?.completedItems.orEmpty()
    SectionTitle(text = stringResource(R.string.completed_title))

    Text(
        text = stringResource(R.string.completed_count, completed.size),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (completed.isEmpty()) {
        EmptyStateCard(
            title = stringResource(R.string.completed_empty_title),
            body = stringResource(R.string.completed_empty_body),
        )
        return
    }

    completed.forEach { item ->
        CompletedItemCard(item = item)
    }
}

@Composable
private fun EmptyStateCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- item cards -------------------------------------------------------------

/** The card Home uses for the item that needs attention right now. */
@Composable
private fun ProblemItemCard(item: QueueItem, callbacks: QueueCallbacks) {
    val status = item.status
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ItemHeader(item = item)
            if (item.failureDetail != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.failureDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (status) {
                    PaymentStatus.UNKNOWN -> stringResource(R.string.problem_unknown_note)
                    PaymentStatus.REQUIRES_QR_REPLACEMENT ->
                        stringResource(R.string.problem_qr_unusable_note)

                    else -> stringResource(R.string.problem_failed_note)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ProblemActions(item = item, callbacks = callbacks)
        }
    }
}

/**
 * The actions a problem item offers. Every one of them is an explicit user
 * decision: nothing is retried, completed or re-shared by the app itself.
 */
@Composable
private fun ProblemActions(item: QueueItem, callbacks: QueueCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (item.status) {
            PaymentStatus.UNKNOWN -> {
                Button(
                    onClick = { callbacks.onConfirmCompleted(item.id) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_confirm_completed))
                }
                OutlinedButton(
                    onClick = { callbacks.onRetryItem(item.id) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_retry))
                }
                OutlinedButton(
                    onClick = { callbacks.onMarkQrUnusable(item.id) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(text = stringResource(R.string.action_mark_qr_unusable))
                }
            }

            PaymentStatus.REQUIRES_QR_REPLACEMENT -> {
                Button(
                    onClick = { callbacks.onReplaceQr(item.id) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(text = stringResource(R.string.action_replace_qr))
                }
            }

            else -> {
                Button(
                    onClick = { callbacks.onShareItem(item.id) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_retry))
                }
                OutlinedButton(
                    onClick = { callbacks.onReplaceQr(item.id) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(text = stringResource(R.string.action_replace_qr))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { callbacks.onViewItem(item.id) }) {
                Text(
                    text = stringResource(R.string.action_view_image),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { callbacks.onClearItem(item.id) }) {
                Text(
                    text = stringResource(R.string.action_clear_item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** The card Home uses for the next QR that is ready to pay. */
@Composable
private fun ReadyItemCard(item: QueueItem, selectedBank: BankInfo?, callbacks: QueueCallbacks) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ItemHeader(item = item)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { callbacks.onShareItem(item.id) },
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
                    text = stringResource(
                        R.string.action_pay,
                        selectedBank?.displayName ?: stringResource(R.string.bank_generic),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { callbacks.onViewItem(item.id) }) {
                    Text(
                        text = stringResource(R.string.action_view_image),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { callbacks.onReportProblem(item.id) }) {
                    Text(
                        text = stringResource(R.string.action_report_problem),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** A compact row for an item that is simply still in the queue. */
@Composable
private fun CompactItemCard(
    item: QueueItem,
    queue: PaymentQueue,
    callbacks: QueueCallbacks,
) {
    val canStart = queue.canStartHandoff(item.id)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.itemLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            StatusChip(status = item.status)
            Spacer(Modifier.weight(1f))
            if (item.status == PaymentStatus.READY) {
                TextButton(
                    onClick = { callbacks.onShareItem(item.id) },
                    enabled = canStart,
                ) {
                    Text(text = stringResource(R.string.action_pay_short))
                }
            } else if (item.status.requiresQrReplacement) {
                TextButton(onClick = { callbacks.onReplaceQr(item.id) }) {
                    Text(text = stringResource(R.string.action_replace_qr))
                }
            }
        }
    }
}

@Composable
private fun ItemHeader(item: QueueItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        QrImagePreview(
            path = item.previewFilePath,
            modifier = Modifier.size(width = 72.dp, height = 72.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.itemLabel, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            StatusChip(status = item.status)
            if (item.qrVersionCount > 1) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.item_qr_version, item.qrVersionCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CompletedItemCard(item: QueueItem) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.itemLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.completed_by_user),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val time = rememberTime(item.completedAt)
            if (time.isNotEmpty()) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun rememberTime(millis: Long?): String = remember(millis) {
    if (millis == null || millis <= 0L) {
        ""
    } else {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }
}

// ---- import ----------------------------------------------------------------

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
    val warning = summary.needsReport
    val contentColor = if (warning) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (warning) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(20.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (summary.imported > 0) {
                        stringResource(R.string.import_summary_ok_title)
                    } else {
                        stringResource(R.string.import_summary_none_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.import_summary_line, summary.imported),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
                if (summary.hasDuplicates) {
                    Text(
                        text = stringResource(R.string.import_summary_duplicates, summary.duplicates),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                    )
                }
                if (summary.hasFailures) {
                    Text(
                        text = stringResource(R.string.import_summary_failed, summary.failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                    )
                }
            }
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_dismiss), color = contentColor)
            }
        }
    }
}

// ---- payment confirmation ---------------------------------------------------

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
            Text(
                text = item.itemLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            QrImagePreview(
                path = item.previewFilePath,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
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
                onClick = { callbacks.onMarkQrUnusable(item.id) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_mark_qr_unusable),
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
                    text = stringResource(R.string.action_keep_waiting),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ---- finished ---------------------------------------------------------------

@Composable
private fun FinishedBanner(queue: PaymentQueue, onImportImages: () -> Unit) {
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
                onClick = onImportImages,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_import),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ---- shared pieces ----------------------------------------------------------

@Composable
private fun ClearQueueDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.clear_queue_title)) },
        text = { Text(text = stringResource(R.string.clear_queue_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.clear_queue_confirm))
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
private fun QrImagePreview(path: String?, modifier: Modifier = Modifier) {
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
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        val bitmap = imageState.value
        if (bitmap == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.image_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.image_description),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
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

// ---- settings dialog --------------------------------------------------------

@Composable
private fun SettingsDialog(
    handPreference: HandPreference,
    autoDailyReset: Boolean,
    selectedBank: BankInfo?,
    onHandPreferenceChanged: (HandPreference) -> Unit,
    onAutoDailyResetChanged: (Boolean) -> Unit,
    onManualDailyReset: () -> Unit,
    onChangeBank: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Hand preference
                Text(
                    text = stringResource(R.string.settings_hand_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HandPreference.entries.forEach { pref ->
                        val label = when (pref) {
                            HandPreference.RIGHT -> stringResource(R.string.settings_hand_right)
                            HandPreference.LEFT -> stringResource(R.string.settings_hand_left)
                        }
                        OutlinedButton(
                            onClick = { onHandPreferenceChanged(pref) },
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = if (handPreference == pref) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = label)
                        }
                    }
                }
                HorizontalDivider()
                // Daily reset
                Text(
                    text = stringResource(R.string.settings_daily_reset_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (autoDailyReset) stringResource(R.string.settings_daily_reset_on) else stringResource(R.string.settings_daily_reset_off),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = autoDailyReset,
                        onCheckedChange = onAutoDailyResetChanged,
                    )
                }
                if (autoDailyReset) {
                    Text(
                        text = stringResource(R.string.settings_daily_reset_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                OutlinedButton(
                    onClick = onManualDailyReset,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.settings_manual_reset))
                }
                HorizontalDivider()
                // Bank
                Text(
                    text = stringResource(R.string.settings_bank_section),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = selectedBank?.displayName ?: stringResource(R.string.bank_not_selected),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onChangeBank) {
                        Text(text = stringResource(R.string.settings_bank_change))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_dismiss))
            }
        },
    )
}

// ---- problem reasons bottom sheet --------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProblemReasonsSheet(
    onReasonSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val reasons = listOf(
        R.string.problem_reason_qr_unusable,
        R.string.problem_reason_bank_rejected,
        R.string.problem_reason_expired,
        R.string.problem_reason_cannot_pay,
        R.string.problem_reason_image_issue,
        R.string.problem_reason_other,
    )
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.problem_reason_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            reasons.forEach { reasonRes ->
                val label = stringResource(reasonRes)
                Surface(
                    onClick = { onReasonSelected(label) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---- clear item confirmation ------------------------------------------------

@Composable
private fun ClearItemDialog(
    itemLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.clear_item_title)) },
        text = { Text(text = stringResource(R.string.clear_item_body, itemLabel)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.clear_item_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
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
    onMarkQrUnusable = {},
    onReplaceQr = {},
    onImportSummaryShown = {},
    onClearRequested = {},
    onClearConfirmed = {},
    onClearDismissed = {},
    onNoticeShown = {},
    onChangeBank = {},
    onBankSelected = {},
    onBankSelectionDismissed = {},
    onSelectTab = {},
    onSettingsRequested = {},
    onSettingsDismissed = {},
    onHandPreferenceChanged = {},
    onAutoDailyResetChanged = {},
    onManualDailyResetRequested = {},
    onReportProblem = {},
    onProblemReasonSelected = {},
    onProblemReasonDismissed = {},
    onClearItem = {},
    onClearItemConfirmed = {},
    onClearItemDismissed = {},
)
