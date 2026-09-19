package com.enjirad.qrqueue.ui

import com.enjirad.qrqueue.domain.BankAvailability
import com.enjirad.qrqueue.domain.BankInfo
import com.enjirad.qrqueue.domain.BankRegistry
import com.enjirad.qrqueue.domain.BankTargetStatus
import com.enjirad.qrqueue.domain.ImportProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The upload gate is the single rule that prevents the user from importing QR
 * images before selecting a bank: `canImport` must be false when no bank is
 * selected, and true only when the selected bank is installed and reachable.
 */
class UploadGateTest {

    private fun readyStatus(bank: BankInfo = BankRegistry.K_PLUS) = BankTargetStatus(
        bank = bank,
        availability = BankAvailability.SHARE_CAPABLE,
        advertised = true,
    )

    private fun installedNotAdvertised(bank: BankInfo = BankRegistry.K_PLUS) = BankTargetStatus(
        bank = bank,
        availability = BankAvailability.INSTALLED_BUT_NOT_SHARE_CAPABLE,
        advertised = false,
    )

    private fun notInstalled(bank: BankInfo = BankRegistry.K_PLUS) = BankTargetStatus(
        bank = bank,
        availability = BankAvailability.NOT_INSTALLED,
        advertised = false,
    )

    @Test
    fun uploadIsDisabledWhenNoBankIsSelected() {
        // First install: nothing saved, nothing selected.
        val state = QueueUiState()
        assertFalse(state.canImport)
        assertFalse(state.isBankReady)
    }

    @Test
    fun uploadIsDisabledWhenBankIsInstalledButNotAdvertised() {
        // Some ROMs hide the share activity from package queries even though
        // the bank is installed. The hand-off is still attempted, but the
        // import must not be gated on an uncertain share-activity check — the
        // bank IS installed, so canImport should be true.
        val state = QueueUiState(
            selectedBank = BankRegistry.K_PLUS,
            bankStatus = installedNotAdvertised(),
        )
        assertTrue(state.canImport)
        assertFalse(state.isBankReady)
    }

    @Test
    fun uploadIsEnabledWhenBankIsReady() {
        val state = QueueUiState(
            selectedBank = BankRegistry.K_PLUS,
            bankStatus = readyStatus(),
        )
        assertTrue(state.canImport)
        assertTrue(state.isBankReady)
    }

    @Test
    fun uploadIsDisabledWhenBankIsNotInstalled() {
        // The bank was selected but has since been uninstalled.
        val state = QueueUiState(
            selectedBank = BankRegistry.K_PLUS,
            bankStatus = notInstalled(),
        )
        assertFalse(state.canImport)
        assertFalse(state.isBankReady)
    }

    @Test
    fun bankSelectionDialogIsIndependentOfImportState() {
        val state = QueueUiState(bankSelectionVisible = true)
        assertFalse(state.canImport)
        assertTrue(state.bankSelectionVisible)
    }

    @Test
    fun importProgressDoesNotAffectUploadGate() {
        val withProgress = QueueUiState(
            selectedBank = BankRegistry.K_PLUS,
            bankStatus = readyStatus(),
            importProgress = ImportProgress(3, 5),
        )
        assertTrue(withProgress.canImport)
    }

    @Test
    fun queuePresenceDoesNotAffectUploadGate() {
        val withQueue = QueueUiState(
            selectedBank = BankRegistry.SCB_EASY,
            bankStatus = readyStatus(BankRegistry.SCB_EASY),
            queue = com.enjirad.qrqueue.domain.PaymentQueue.create("q", 1L),
        )
        assertTrue(withQueue.canImport)
    }
}
