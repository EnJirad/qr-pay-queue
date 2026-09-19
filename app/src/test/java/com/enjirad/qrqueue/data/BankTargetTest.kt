package com.enjirad.qrqueue.data

import com.enjirad.qrqueue.domain.BankAvailability
import com.enjirad.qrqueue.domain.BankInfo
import com.enjirad.qrqueue.domain.BankRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision logic that decides whether the direct hand-off to any banking app
 * can be attempted is pure, so it is covered without a device.
 */
class BankTargetTest {

    private fun bank(
        id: String = "test_bank",
        name: String = "com.test.bank",
        displayName: String = "Test Bank",
    ) = BankInfo(id = id, name = name, displayName = displayName)

    @Test
    fun readyWhenTheBankAdvertisesImageSharing() {
        val status = BankTarget.classify(
            declaredMimeType = null,
            advertisedForDeclaredType = false,
            advertisedForAnyImage = true,
            installed = true,
            bank = bank(),
        )

        assertTrue(status.advertised)
        assertEquals(BankAvailability.READY, status.availability)
        assertTrue(status.advertised)
        assertTrue(status.isInstalled)
        assertTrue(status.canHandOff)
    }

    @Test
    fun installedButNotAdvertisedIsStillOffered() {
        val status = BankTarget.classify(
            declaredMimeType = null,
            advertisedForDeclaredType = false,
            advertisedForAnyImage = false,
            installed = true,
            bank = bank(),
        )

        assertFalse(status.advertised)
        assertEquals(BankAvailability.INSTALLED_NOT_ADVERTISED, status.availability)
        assertFalse(status.advertised)
        assertTrue(status.isInstalled)
        // The hand-off button is still offered so the refusal is reported honestly.
        assertTrue(status.canHandOff)
    }

    @Test
    fun anAbsentBankCannotBeHandedOffTo() {
        val status = BankTarget.classify(
            declaredMimeType = null,
            advertisedForDeclaredType = false,
            advertisedForAnyImage = false,
            installed = false,
            bank = bank(),
        )

        assertEquals(BankAvailability.NOT_INSTALLED, status.availability)
        assertFalse(status.isInstalled)
        assertFalse(status.canHandOff)
        assertFalse(status.advertised)
    }

    @Test
    fun theBankInfoIsCarriedThroughClassification() {
        val myBank = bank(id = "my_bank", name = "com.my.bank", displayName = "My Bank")
        val status = BankTarget.classify(
            declaredMimeType = null,
            advertisedForDeclaredType = false,
            advertisedForAnyImage = true,
            installed = true,
            bank = myBank,
        )

        assertEquals("my_bank", status.bank.id)
        assertEquals("com.my.bank", status.bank.name)
        assertEquals("My Bank", status.bank.displayName)
    }

    @Test
    fun kPlusClassificationMatchesItsKnownPackageName() {
        val status = BankTarget.classify(
            declaredMimeType = null,
            advertisedForDeclaredType = false,
            advertisedForAnyImage = true,
            installed = true,
            bank = BankRegistry.K_PLUS,
        )

        assertEquals("com.kasikornbank.kplus", status.bank.name)
        assertTrue(status.canHandOff)
    }

    @Test
    fun eachKnownBankHasAValidPackageName() {
        BankRegistry.BANKS.forEach { bank ->
            assertTrue(
                "${bank.displayName} must have a non-empty package name",
                bank.name.isNotBlank(),
            )
            assertTrue(
                "${bank.displayName} must have a package name with a dot",
                bank.name.contains("."),
            )
        }
    }

    @Test
    fun findBankByIdWorks() {
        assertEquals(BankRegistry.K_PLUS, BankRegistry.findById("kplus"))
        assertEquals(BankRegistry.SCB_EASY, BankRegistry.findById("scb_easy"))
        assertEquals(null, BankRegistry.findById("nonexistent"))
    }

    @Test
    fun findBankByPackageWorks() {
        assertEquals(BankRegistry.K_PLUS, BankRegistry.findByPackage("com.kasikornbank.kplus"))
        assertEquals(null, BankRegistry.findByPackage("com.nonexistent.app"))
    }
}
