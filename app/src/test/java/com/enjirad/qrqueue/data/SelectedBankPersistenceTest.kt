package com.enjirad.qrqueue.data

import com.enjirad.qrqueue.domain.BankRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * V0.4.2 §5/§6/§22 — the selected bank survives an app restart.
 *
 * One "device" is a shared backing map; each new [InMemoryBankSelectionStore]
 * over that map is a new process after a restart, so the second store must
 * restore exactly what the first one saved.
 */
class SelectedBankPersistenceTest {

    private val device: MutableMap<String, String> = mutableMapOf()

    /** A fresh store over the same device = the app process after a restart. */
    private fun process() = InMemoryBankSelectionStore(device)

    @Test
    fun noBankSavedMeansNothingSelected() {
        assertNull(process().load())
    }

    @Test
    fun selectingKPlusIsRestoredAfterRestart() {
        process().save(BankRegistry.K_PLUS)

        val afterRestart = process()
        assertEquals(BankRegistry.K_PLUS, afterRestart.load())
        assertEquals("com.kasikornbank.kplus", afterRestart.load()?.name)
    }

    @Test
    fun changingTheBankPersistsTheNewOne() {
        process().save(BankRegistry.K_PLUS)
        process().save(BankRegistry.SCB_EASY)

        val afterRestart = process()
        assertEquals(BankRegistry.SCB_EASY, afterRestart.load())
        assertEquals("com.scb.BankApp", afterRestart.load()?.name)
    }

    @Test
    fun clearingForgetsTheSelectionAcrossRestart() {
        process().save(BankRegistry.K_PLUS)
        process().clear()

        assertNull(process().load())
    }

    @Test
    fun anUnknownStoredPackageDecodesToNothing() {
        device[BankSelectionCodec.KEY_BANK_PACKAGE] = "com.unknown.bank"
        device[BankSelectionCodec.KEY_BANK_ID] = "unknown"

        assertNull(process().load())
    }

    @Test
    fun aMismatchedIdAndPackageDecodesToNothing() {
        // The id says K PLUS, the package says SCB EASY. The app never guesses.
        device[BankSelectionCodec.KEY_BANK_ID] = BankRegistry.K_PLUS.id
        device[BankSelectionCodec.KEY_BANK_PACKAGE] = BankRegistry.SCB_EASY.name

        assertNull(process().load())
    }

    @Test
    fun anOlderValueWithoutAnIdStillResolvesByPackage() {
        device[BankSelectionCodec.KEY_BANK_PACKAGE] = BankRegistry.K_PLUS.name

        assertEquals(BankRegistry.K_PLUS, process().load())
    }
}
