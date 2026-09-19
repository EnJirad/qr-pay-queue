package com.enjirad.qrqueue.ui

import com.enjirad.qrqueue.data.BankTarget
import com.enjirad.qrqueue.domain.BankAvailability
import com.enjirad.qrqueue.domain.BankRegistry
import com.enjirad.qrqueue.domain.BankShareReadiness
import com.enjirad.qrqueue.domain.BankTargetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V0.4.2 §12/§13/§14/§22 — a selected bank whose package is gone must be detected
 * on restart and must disable the upload gate, without the app pretending the
 * bank was never selected.
 */
class UninstalledBankTest {

    private fun status(availability: BankAvailability) = BankTargetStatus(
        bank = BankRegistry.K_PLUS,
        availability = availability,
        advertised = availability == BankAvailability.READY,
    )

    @Test
    fun aSavedButUninstalledBankIsDetectedOnRestart() {
        val state = QueueUiState(
            selectedBank = BankRegistry.K_PLUS,
            bankStatus = status(BankAvailability.NOT_INSTALLED),
        )

        assertFalse(state.bankStatus?.isInstalled == true) // isInstalled = false
        assertFalse(state.canImport)                       // canUpload = false
        assertTrue(state.requiresBankSelection)            // requiresBankSelection = true
    }

    @Test
    fun theOldSelectionIsKeptSoTheUiCanShowIt() {
        val state = QueueUiState(
            selectedBank = BankRegistry.K_PLUS,
            bankStatus = status(BankAvailability.NOT_INSTALLED),
        )

        // V0.4.2 §13: keep the value to show "K PLUS — ไม่พบแอป"; only disable use.
        assertEquals(BankRegistry.K_PLUS, state.selectedBank)
    }

    @Test
    fun noBankSelectedAlsoRequiresSelection() {
        val state = QueueUiState()

        assertFalse(state.canImport)
        assertTrue(state.requiresBankSelection)
    }

    @Test
    fun aReadyBankDoesNotRequireSelection() {
        val state = QueueUiState(
            selectedBank = BankRegistry.K_PLUS,
            bankStatus = status(BankAvailability.READY),
        )

        assertTrue(state.canImport)
        assertFalse(state.requiresBankSelection)
    }

    @Test
    fun preflightSeparatesNotInstalledFromNoSelection() {
        assertEquals(
            BankShareReadiness.BANK_NOT_INSTALLED,
            BankTarget.preflight(BankRegistry.K_PLUS, installed = false),
        )
        assertEquals(
            BankShareReadiness.NO_BANK_SELECTED,
            BankTarget.preflight(null, installed = false),
        )
        assertEquals(
            BankShareReadiness.READY,
            BankTarget.preflight(BankRegistry.K_PLUS, installed = true),
        )
    }
}
