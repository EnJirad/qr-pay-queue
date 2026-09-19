package com.enjirad.qrqueue.ui

import androidx.lifecycle.ViewModel
import com.enjirad.qrqueue.domain.QueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One-off notices the screen should surface to the user. */
enum class QueueNotice { IMPORT_NOT_IMPLEMENTED }

/**
 * Everything the queue screen renders. It is a plain immutable snapshot: the
 * UI derives counts and the total from it and never mutates it.
 */
data class QueueUiState(
    val items: List<QueueItem> = emptyList(),
    val notice: QueueNotice? = null,
) {
    val itemCount: Int get() = items.size
    val totalSatang: Long get() = items.sumOf { it.amountSatang ?: 0L }
}

/**
 * State holder for the queue screen.
 *
 * V0.1 has no importer yet, so the queue stays empty and nothing here creates
 * fake transactions. When V0.2 lands, the Storage Access Framework picker
 * plugs into [onImportRequested] and decoding fills the queue.
 */
class QueueViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    /**
     * The Import button must not pretend to work: until the importer exists it
     * raises an honest "not implemented yet" notice.
     */
    fun onImportRequested() {
        _uiState.update { it.copy(notice = QueueNotice.IMPORT_NOT_IMPLEMENTED) }
    }

    fun onNoticeShown() {
        _uiState.update { it.copy(notice = null) }
    }
}
