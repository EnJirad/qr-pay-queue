package com.enjirad.qrqueue.ui

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import com.enjirad.qrqueue.domain.HomeElement
import com.enjirad.qrqueue.domain.HomeLayoutConfig
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
    val onMarkUnknown: (String) -> Unit,
    val onRetryShare: (String) -> Unit,
    val onLockToggled: () -> Unit,
    val onClearItem: (String) -> Unit,
    val onClearItemConfirmed: () -> Unit,
    val onClearItemDismissed: () -> Unit,
    // ---- V0.9 home layout edit mode ----
    val onEditModeToggled: () -> Unit,
    val onHomeElementMoved: (HomeElement, Float, Float) -> Unit,
    val onQrImageMoved: (Float, Float) -> Unit,
    val onHomeElementVisibilityToggled: (HomeElement) -> Unit,
    val onResetLayoutRequested: () -> Unit,
    val onResetLayoutConfirmed: () -> Unit,
    val onResetLayoutDismissed: () -> Unit,
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

    LaunchedEffect(state.replaceQrItemId) {
        if (state.replaceQrItemId != null) {
            replacementPicker.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                ),
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
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    ),
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
            onReportProblem = viewModel::onReportProblem,
            onMarkUnknown = viewModel::onMarkUnknown,
            onRetryShare = viewModel::onRetryShare,
            onLockToggled = viewModel::onLockToggled,
            onClearItem = viewModel::onClearItemRequested,
            onClearItemConfirmed = viewModel::onClearItemConfirmed,
            onClearItemDismissed = viewModel::onClearItemDismissed,
            onEditModeToggled = viewModel::onEditModeToggled,
            onHomeElementMoved = viewModel::onHomeElementMoved,
            onQrImageMoved = viewModel::onQrImageMoved,
            onHomeElementVisibilityToggled = viewModel::onHomeElementVisibilityToggled,
            onResetLayoutRequested = viewModel::onResetLayoutRequested,
            onResetLayoutConfirmed = viewModel::onResetLayoutConfirmed,
            onResetLayoutDismissed = viewModel::onResetLayoutDismissed,
        ),
    )
}

/**
 * Hands the stored image straight to the selected bank, or opens it in a viewer.
 *
 * Returns false when nothing could be opened, so the ViewModel can record a failed
 * launch attempt — V0.9 §1 keeps the item in its four-action rail in that case; it
 * is never failed out of the payment flow.
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
        QueueNotice.LAYOUT_LOCKED -> stringResource(R.string.notice_layout_locked)
        QueueNotice.LAYOUT_RESET -> stringResource(R.string.notice_layout_reset)
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
            homeLocked = state.homeLocked,
            autoDailyReset = state.autoDailyReset,
            selectedBank = state.selectedBank,
            onHandPreferenceChanged = callbacks.onHandPreferenceChanged,
            onAutoDailyResetChanged = callbacks.onAutoDailyResetChanged,
            onLockToggled = callbacks.onLockToggled,
            onManualDailyReset = callbacks.onManualDailyResetRequested,
            onChangeBank = callbacks.onChangeBank,
            onDismiss = callbacks.onSettingsDismissed,
        )
    }

    if (state.layoutResetConfirmationVisible) {
        ResetLayoutDialog(
            onConfirm = callbacks.onResetLayoutConfirmed,
            onDismiss = callbacks.onResetLayoutDismissed,
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
        // The three tabs (HOME / PROBLEMS / COMPLETED) sit below every tab, so the
        // bottom navigation is never lost when the user switches views.
        bottomBar = {
            QueueBottomBar(
                state = state,
                onSelectTab = callbacks.onSelectTab,
            )
        },
    ) { innerPadding ->
        when (state.selectedTab) {
            QueueTab.HOME -> {
                HomeTab(
                    state = state,
                    callbacks = callbacks,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            QueueTab.PROBLEMS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AppHeader(
                        handPreference = state.handPreference,
                        homeLocked = state.homeLocked,
                        editMode = state.homeEditMode,
                        onSettingsRequested = callbacks.onSettingsRequested,
                        onLockToggled = callbacks.onLockToggled,
                        onEditModeToggled = callbacks.onEditModeToggled,
                    )
                    ProblemsTab(
                        queue = state.queue,
                        handPreference = state.handPreference,
                        callbacks = callbacks,
                    )
                    SafetyCard()
                }
            }
            QueueTab.COMPLETED -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AppHeader(
                        handPreference = state.handPreference,
                        homeLocked = state.homeLocked,
                        editMode = state.homeEditMode,
                        onSettingsRequested = callbacks.onSettingsRequested,
                        onLockToggled = callbacks.onLockToggled,
                        onEditModeToggled = callbacks.onEditModeToggled,
                    )
                    CompletedTab(queue = state.queue)
                    SafetyCard()
                }
            }
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
private fun AppHeader(
    handPreference: HandPreference,
    homeLocked: Boolean,
    editMode: Boolean,
    onSettingsRequested: () -> Unit,
    onLockToggled: () -> Unit,
    onEditModeToggled: () -> Unit,
) {
    val iconsEnd = handPreference == HandPreference.RIGHT
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!iconsEnd) {
            HeaderIcons(
                homeLocked = homeLocked,
                editMode = editMode,
                onSettingsRequested = onSettingsRequested,
                onLockToggled = onLockToggled,
                onEditModeToggled = onEditModeToggled,
            )
            Spacer(Modifier.weight(1f))
        } else {
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
        }
        if (iconsEnd) {
            HeaderIcons(
                homeLocked = homeLocked,
                editMode = editMode,
                onSettingsRequested = onSettingsRequested,
                onLockToggled = onLockToggled,
                onEditModeToggled = onEditModeToggled,
            )
        } else {
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
}

@Composable
private fun HeaderIcons(
    homeLocked: Boolean,
    editMode: Boolean,
    onSettingsRequested: () -> Unit,
    onLockToggled: () -> Unit,
    onEditModeToggled: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = onLockToggled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = stringResource(
                    if (homeLocked) R.string.action_unlock else R.string.action_lock,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                tint = if (homeLocked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        // V0.9 §3: the Edit control sits right next to Lock — locked means the
        // layout cannot be changed, edit mode means it can.
        Surface(
            onClick = onEditModeToggled,
            shape = CircleShape,
            color = if (editMode) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(
                    if (editMode) R.string.action_edit_done else R.string.action_edit,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                tint = if (editMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Surface(
            onClick = onSettingsRequested,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.action_settings),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

// ---- tab 1: home (V0.7 fixed-position control panel) -----------------------

/**
 * V0.9 Home: one active QR area with the action rail on the chosen hand's side,
 * the rest of the open queue listed below it, and an Edit mode that lets the
 * user move and hide the elements for themselves.
 *
 * Scrolling is disabled while the screen is locked (V0.7) **and** while Edit
 * mode is on, so a drag can never be read as a scroll and the elements the user
 * is repositioning stay exactly where they are put.
 */
@Composable
private fun HomeTab(
    state: QueueUiState,
    callbacks: QueueCallbacks,
    modifier: Modifier = Modifier,
) {
    val leftHanded = state.handPreference == HandPreference.LEFT
    val scrollState = rememberScrollState()
    val scrollModifier = if (state.homeLocked || state.homeEditMode) {
        Modifier
    } else {
        Modifier.verticalScroll(scrollState)
    }
    val layout = state.homeLayout
    val editMode = state.homeEditMode

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(scrollModifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader(
            handPreference = state.handPreference,
            homeLocked = state.homeLocked,
            editMode = editMode,
            onSettingsRequested = callbacks.onSettingsRequested,
            onLockToggled = callbacks.onLockToggled,
            onEditModeToggled = callbacks.onEditModeToggled,
        )
        if (editMode) {
            EditLayoutPanel(
                layout = layout,
                onToggleVisibility = callbacks.onHomeElementVisibilityToggled,
                onResetRequested = callbacks.onResetLayoutRequested,
                onDone = callbacks.onEditModeToggled,
            )
        }
        state.importSummary?.let { summary ->
            ImportSummaryBanner(summary = summary, onDismiss = callbacks.onImportSummaryShown)
        }
        BankSelectionCard(
            selectedBank = state.selectedBank,
            bankStatus = state.bankStatus,
            onChangeBank = callbacks.onChangeBank,
        )
        if (state.importing) {
            ImportProgressCard(progress = state.importProgress)
            return@Column
        }
        val queue = state.queue
        // V0.9 §1: the item that owns the hand-off is the one the top area shows,
        // so the four actions are always on screen while a payment is in flight.
        val currentItem = state.activeItem

        if (currentItem != null) {
            ControlPanel(
                item = currentItem,
                selectedBank = state.selectedBank,
                handPreference = state.handPreference,
                leftHanded = leftHanded,
                layout = layout,
                editMode = editMode,
                onElementMoved = callbacks.onHomeElementMoved,
                onQrImageMoved = callbacks.onQrImageMoved,
                callbacks = callbacks,
            )
        } else if (queue != null && queue.finished) {
            FinishedControlPanel(
                queue = queue,
                leftHanded = leftHanded,
                onImportImages = callbacks.onImportImages,
                onSelectTab = callbacks.onSelectTab,
            )
        } else if (queue == null) {
            EmptyHome(
                canImport = state.canImport,
                onImportImages = callbacks.onImportImages,
                layout = layout,
                editMode = editMode,
                onElementMoved = callbacks.onHomeElementMoved,
            )
        }
        // V0.9 §2: the active QR keeps the top area and every other open item is
        // listed below it, so a newly imported QR appears here without ever
        // duplicating or replacing the QR the user is working on.
        val queuedItems = state.queuedItems
        if (queue != null && queuedItems.isNotEmpty()) {
            SectionTitle(
                text = stringResource(R.string.home_queue_title),
                badge = queuedItems.size.toString(),
            )
            queuedItems.forEach { item ->
                CompactItemCard(item = item, queue = queue, callbacks = callbacks)
            }
        }
        if (queue != null && queue.itemCount > 0 && layout.isVisible(HomeElement.PROGRESS)) {
            val progressQueue = queue
            HomeElementBox(
                element = HomeElement.PROGRESS,
                layout = layout,
                editMode = editMode,
                onMove = callbacks.onHomeElementMoved,
            ) {
                Text(
                    text = stringResource(
                        R.string.home_progress,
                        progressQueue.completedCount,
                        progressQueue.itemCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        ImportCard(
            canImport = state.canImport,
            onImportImages = callbacks.onImportImages,
            layout = layout,
            editMode = editMode,
            onElementMoved = callbacks.onHomeElementMoved,
        )
        TextButton(onClick = callbacks.onClearRequested) {
            Text(text = stringResource(R.string.action_clear_queue))
        }
        if (layout.isVisible(HomeElement.GUIDANCE)) {
            HomeElementBox(
                element = HomeElement.GUIDANCE,
                layout = layout,
                editMode = editMode,
                onMove = callbacks.onHomeElementMoved,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SafetyCard()
            }
        }
    }
}

@Composable
private fun EmptyHome(
    canImport: Boolean,
    onImportImages: () -> Unit,
    layout: HomeLayoutConfig = HomeLayoutConfig.DEFAULT,
    editMode: Boolean = false,
    onElementMoved: (HomeElement, Float, Float) -> Unit = { _, _, _ -> },
) {
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

    ImportCard(
        canImport = canImport,
        onImportImages = onImportImages,
        layout = layout,
        editMode = editMode,
        onElementMoved = onElementMoved,
    )

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
private fun ImportCard(
    canImport: Boolean,
    onImportImages: () -> Unit,
    layout: HomeLayoutConfig = HomeLayoutConfig.DEFAULT,
    editMode: Boolean = false,
    onElementMoved: (HomeElement, Float, Float) -> Unit = { _, _, _ -> },
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // The add-QR action is one of the movable elements (V0.9 §3.6). In
            // normal mode it does exactly what it always did; in edit mode the
            // touch moves it instead of importing.
            HomeElementBox(
                element = HomeElement.ADD_QR,
                layout = layout,
                editMode = editMode,
                onMove = onElementMoved,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = { if (!editMode) onImportImages() },
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
            }
            if (layout.isVisible(HomeElement.IMPORT_HINT)) {
                Spacer(Modifier.height(10.dp))
                HomeElementBox(
                    element = HomeElement.IMPORT_HINT,
                    layout = layout,
                    editMode = editMode,
                    onMove = onElementMoved,
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
    }
}

// ---- control panel (V0.7) ---------------------------------------------------

/**
 * V0.7 §4/§5: QR preview on one side, ActionRail on the other.
 * Right-hand mode: QR left, actions right. Left-hand: mirror.
 */
@Composable
private fun ControlPanel(
    item: QueueItem,
    selectedBank: BankInfo?,
    handPreference: HandPreference,
    leftHanded: Boolean,
    layout: HomeLayoutConfig,
    editMode: Boolean,
    onElementMoved: (HomeElement, Float, Float) -> Unit,
    onQrImageMoved: (Float, Float) -> Unit,
    callbacks: QueueCallbacks,
) {
    val isReady = item.status == PaymentStatus.READY
    // V0.9 §1: a hand-off in flight shows the same four actions as one awaiting
    // the user's answer, so the rail can never collapse into an empty panel.
    val isAwaiting = item.status == PaymentStatus.AWAITING_USER_CONFIRMATION ||
        item.status == PaymentStatus.SHARING
    val isProblem = item.status.isProblem

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            when {
                isProblem -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                isAwaiting -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leftHanded) {
                ActionRail(
                    item = item,
                    selectedBank = selectedBank,
                    isReady = isReady,
                    isAwaiting = isAwaiting,
                    isProblem = isProblem,
                    layout = layout,
                    editMode = editMode,
                    onElementMoved = onElementMoved,
                    callbacks = callbacks,
                )
                QrPreviewSlot(
                    item = item,
                    layout = layout,
                    editMode = editMode,
                    onElementMoved = onElementMoved,
                    onQrImageMoved = onQrImageMoved,
                    modifier = Modifier.weight(1f).height(300.dp),
                )
            } else {
                QrPreviewSlot(
                    item = item,
                    layout = layout,
                    editMode = editMode,
                    onElementMoved = onElementMoved,
                    onQrImageMoved = onQrImageMoved,
                    modifier = Modifier.weight(1f).height(300.dp),
                )
                ActionRail(
                    item = item,
                    selectedBank = selectedBank,
                    isReady = isReady,
                    isAwaiting = isAwaiting,
                    isProblem = isProblem,
                    layout = layout,
                    editMode = editMode,
                    onElementMoved = onElementMoved,
                    callbacks = callbacks,
                )
            }
        }
    }
}

/**
 * The active QR area: the movable QR element plus, when a launch did not reach
 * the bank, the reason as a caption (V0.9 §1: the notice must not exist only as
 * a transient snackbar).
 */
@Composable
private fun QrPreviewSlot(
    item: QueueItem,
    layout: HomeLayoutConfig,
    editMode: Boolean,
    onElementMoved: (HomeElement, Float, Float) -> Unit,
    onQrImageMoved: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!layout.isVisible(HomeElement.QR_IMAGE)) return
    HomeElementBox(
        element = HomeElement.QR_IMAGE,
        layout = layout,
        editMode = editMode,
        onMove = onElementMoved,
        modifier = modifier,
    ) {
        QrPreviewArea(
            item = item,
            layout = layout,
            editMode = editMode,
            onQrImageMoved = onQrImageMoved,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun QrPreviewArea(
    item: QueueItem,
    layout: HomeLayoutConfig = HomeLayoutConfig.DEFAULT,
    editMode: Boolean = false,
    onQrImageMoved: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QrImagePreview(
            path = item.previewFilePath,
            imageOffsetX = layout.qrImageOffsetX,
            imageOffsetY = layout.qrImageOffsetY,
            editMode = editMode,
            onImageMoved = onQrImageMoved,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(8.dp))
        Text(text = item.itemLabel, style = MaterialTheme.typography.titleMedium)
        val detail = item.failureDetail
        if (detail != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ActionRail(
    item: QueueItem,
    selectedBank: BankInfo?,
    isReady: Boolean,
    isAwaiting: Boolean,
    isProblem: Boolean,
    layout: HomeLayoutConfig,
    editMode: Boolean,
    onElementMoved: (HomeElement, Float, Float) -> Unit,
    callbacks: QueueCallbacks,
) {
    Column(
        // Edit mode plots every action of both states at once, so the rail is taller
        // there: five actions must not overlap the QR area next to them.
        modifier = Modifier
            .width(80.dp)
            .height(if (editMode) 392.dp else 300.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            isReady -> {
                // V0.7 §4: single scan icon to send QR to the selected bank.
                // V0.9 §3.3: in edit mode the same touch moves the icon instead of
                // starting a hand-off.
                HomeElementBox(
                    element = HomeElement.SCAN_ACTION,
                    layout = layout,
                    editMode = editMode,
                    onMove = onElementMoved,
                ) {
                    IconButton(
                        onClick = { if (!editMode) callbacks.onShareItem(item.id) },
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.action_scan),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            isAwaiting -> {
                // V0.7 §5–§6: four action icons in fixed order.
                // §18/§20: order is always ✓, ⚠, ?, ↻ — muscle memory.
                HomeElementBox(
                    element = HomeElement.CONFIRM_ACTION,
                    layout = layout,
                    editMode = editMode,
                    onMove = onElementMoved,
                ) {
                    ActionIconButton(
                        onClick = { if (!editMode) callbacks.onConfirmCompleted(item.id) },
                        icon = Icons.Outlined.CheckCircle,
                        contentDescription = stringResource(R.string.action_confirm_completed),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                HomeElementBox(
                    element = HomeElement.WARNING_ACTION,
                    layout = layout,
                    editMode = editMode,
                    onMove = onElementMoved,
                ) {
                    ActionIconButton(
                        onClick = { if (!editMode) callbacks.onReportProblem(item.id) },
                        icon = Icons.Outlined.Warning,
                        contentDescription = stringResource(R.string.action_report_problem),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                HomeElementBox(
                    element = HomeElement.UNKNOWN_ACTION,
                    layout = layout,
                    editMode = editMode,
                    onMove = onElementMoved,
                ) {
                    ActionIconButton(
                        onClick = { if (!editMode) callbacks.onMarkUnknown(item.id) },
                        icon = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.action_unknown),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                // §11–§15: retry — opens the bank again with the same QR. The item
                // does not move and nothing is completed by it.
                HomeElementBox(
                    element = HomeElement.RETRY_ACTION,
                    layout = layout,
                    editMode = editMode,
                    onMove = onElementMoved,
                ) {
                    ActionIconButton(
                        onClick = { if (!editMode) callbacks.onRetryShare(item.id) },
                        icon = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.action_retry_share),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            isProblem -> {
                HomeElementBox(
                    element = HomeElement.RETRY_ACTION,
                    layout = layout,
                    editMode = editMode,
                    onMove = onElementMoved,
                ) {
                    ActionIconButton(
                        onClick = {
                            if (editMode) {
                                Unit
                            } else if (item.status.requiresQrReplacement) {
                                callbacks.onReplaceQr(item.id)
                            } else {
                                callbacks.onRetryItem(item.id)
                            }
                        },
                        icon = Icons.Outlined.Refresh,
                        contentDescription = stringResource(
                            if (item.status.requiresQrReplacement) R.string.action_replace_qr
                            else R.string.action_retry,
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // V0.9.1 §5–§7: Edit mode must not depend on the item's state. Every action
        // this state does not show is drawn here as a draggable placeholder, so all
        // of them can be placed without handing a QR over (or resolving a problem)
        // first. They are placeholders only: their actions are never wired up.
        if (editMode) {
            GhostRailActions(
                item = item,
                layout = layout,
                onElementMoved = onElementMoved,
            )
        }
    }
}

@Composable
private fun ActionIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = tint.copy(alpha = 0.12f),
        modifier = Modifier.size(64.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().padding(16.dp),
            tint = tint,
        )
    }
}

@Composable
private fun FinishedControlPanel(
    queue: PaymentQueue,
    leftHanded: Boolean,
    onImportImages: () -> Unit,
    onSelectTab: (QueueTab) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leftHanded) {
                ActionIconButton(
                    onClick = onImportImages,
                    icon = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.action_import),
                    tint = MaterialTheme.colorScheme.primary,
                )
                FinishedSummary(queue = queue, onSelectTab = onSelectTab)
            } else {
                FinishedSummary(queue = queue, onSelectTab = onSelectTab)
                ActionIconButton(
                    onClick = onImportImages,
                    icon = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.action_import),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * The "all done" summary. Every open item is accounted for: what still needs the
 * user is one tap away in the ปัญหา tab, what is confirmed is in ชำระแล้ว — the
 * bottom bar is always there, this just points at it.
 */
@Composable
private fun FinishedSummary(
    queue: PaymentQueue,
    onSelectTab: (QueueTab) -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.finished_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.finished_count, queue.completedCount, queue.itemCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        if (queue.problemCount > 0) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onSelectTab(QueueTab.PROBLEMS) }) {
                Text(
                    text = stringResource(R.string.finished_open_problems, queue.problemCount),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (queue.completedCount > 0) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { onSelectTab(QueueTab.COMPLETED) }) {
                Text(
                    text = stringResource(R.string.finished_open_completed, queue.completedCount),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ---- tab 2: problems --------------------------------------------------------

@Composable
private fun ColumnScope.ProblemsTab(
    queue: PaymentQueue?,
    handPreference: HandPreference,
    callbacks: QueueCallbacks,
) {
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
        ProblemItemCard(item = item, handPreference = handPreference, callbacks = callbacks)
    }
}

// ---- tab 3: completed -------------------------------------------------------

@Composable
private fun ColumnScope.CompletedTab(queue: PaymentQueue?) {
    val completed = queue?.completedItems.orEmpty()
    // Same badge pattern as the ปัญหา tab: the count comes straight from the
    // queue, and nothing here is ever removed from persistence by viewing it.
    SectionTitle(
        text = stringResource(R.string.completed_title),
        badge = if (completed.isEmpty()) null else completed.size.toString(),
    )

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
private fun ProblemItemCard(
    item: QueueItem,
    handPreference: HandPreference,
    callbacks: QueueCallbacks,
) {
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
            ProblemActions(item = item, handPreference = handPreference, callbacks = callbacks)
        }
    }
}

/**
 * The recovery actions of the current item, placed in the one-handed action area
 * (V0.6 §4–§6) so "what do I do about this?" sits under the same thumb as the pay
 * action.
 */
@Composable
private fun ProblemActions(
    item: QueueItem,
    handPreference: HandPreference,
    callbacks: QueueCallbacks,
) {
    OneHandActionBar(handPreference = handPreference) {
        ProblemActionButtons(item = item, callbacks = callbacks)
    }
}

/**
 * The buttons themselves, full width inside the action band. Every one of them is
 * an explicit user decision: nothing is retried, completed or re-shared by the app
 * itself.
 */
@Composable
private fun ProblemActionButtons(item: QueueItem, callbacks: QueueCallbacks) {
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
private fun ReadyItemCard(
    item: QueueItem,
    selectedBank: BankInfo?,
    handPreference: HandPreference,
    callbacks: QueueCallbacks,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ItemHeader(item = item)
            Spacer(Modifier.height(12.dp))
            OneHandActionBar(handPreference = handPreference) {
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
}

/**
 * How much of the card width a [OneHandActionBar] band occupies. It is a fraction
 * rather than a fixed size so the primary action stays inside the thumb zone on a
 * narrow phone and never overflows it.
 */
private const val oneHandActionBand = 0.85f

/**
 * The one-handed action area (V0.6 §4–§6): the primary actions sit together in a
 * band on the thumb side — bottom-right when the user chose ถนัดขวา, mirrored for
 * ถนัดซ้าย — instead of being stretched across the whole card where a thumb has to
 * reach for them.
 *
 * There is exactly one layout: the hand preference only decides which side the
 * band is anchored to, so no second UI tree has to be kept in sync. The actions
 * themselves are laid out by [content] exactly as before, full width inside the
 * band, which keeps their touch targets large.
 */
@Composable
private fun OneHandActionBar(
    handPreference: HandPreference,
    content: @Composable ColumnScope.() -> Unit,
) {
    val leftHanded = handPreference == HandPreference.LEFT
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (leftHanded) Arrangement.Start else Arrangement.End,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(oneHandActionBand),
            horizontalAlignment = if (leftHanded) Alignment.Start else Alignment.End,
            content = content,
        )
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
    handPreference: HandPreference,
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
            OneHandActionBar(handPreference = handPreference) {
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
private fun QrImagePreview(
    path: String?,
    modifier: Modifier = Modifier,
    imageOffsetX: Float = 0f,
    imageOffsetY: Float = 0f,
    editMode: Boolean = false,
    onImageMoved: (Float, Float) -> Unit = { _, _ -> },
) {
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
                // V0.9 3.5: the QR image can be nudged inside its frame, and only
                // inside it - the frame clips, ContentScale.Fit keeps the aspect
                // ratio and HomeLayoutConfig clamps the offset, so the code can
                // never be dragged out of view.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .offset(x = imageOffsetX.dp, y = imageOffsetY.dp)
                    .then(
                        if (!editMode) {
                            Modifier
                        } else {
                            Modifier.pointerInput(editMode) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onImageMoved(
                                            dragAmount.x.toDp().value,
                                            dragAmount.y.toDp().value,
                                        )
                                    },
                                )
                            }
                        },
                    ),
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
    homeLocked: Boolean,
    onManualDailyReset: () -> Unit,
    onChangeBank: () -> Unit,
    onDismiss: () -> Unit,
    onLockToggled: () -> Unit,
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
                // Lock toggle (V0.7 §11–§14)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.settings_lock_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = homeLocked,
                        onCheckedChange = { onLockToggled() },
                    )
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
    onMarkUnknown = {},
    onRetryShare = {},
    onLockToggled = {},
    onClearItem = {},
    onClearItemConfirmed = {},
    onClearItemDismissed = {},
    onEditModeToggled = {},
    onHomeElementMoved = { _, _, _ -> },
    onQrImageMoved = { _, _ -> },
    onHomeElementVisibilityToggled = {},
    onResetLayoutRequested = {},
    onResetLayoutConfirmed = {},
    onResetLayoutDismissed = {},
)
