package com.enjirad.qrqueue.data

import com.enjirad.qrqueue.domain.BankAvailability
import com.enjirad.qrqueue.domain.BankCategory
import com.enjirad.qrqueue.domain.BankInfo
import com.enjirad.qrqueue.domain.BankRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Banks, packages, classes and categories are verified against Google Play
 * listings. [BankTarget] is the decision logic that decides whether a direct
 * hand-off to any banking app can be attempted.
 */
class BankTargetTest {

    private fun bank(
        id: String = "test_bank",
        packageName: String = "com.test.bank",
        displayName: String = "Test Bank",
    ) = BankInfo(
        id = id,
        displayName = displayName,
        packageName = packageName,
        company = "Test Co.",
        category = BankCategory.COMMERCIAL_BANK,
        googlePlayUrl = "https://play.google.com/store/apps/details?id=$packageName",
        verificationDate = BankRegistry.VERIFIED_ON,
    )

    @Test
    fun shareCapableWhenTheBankAdvertisesImageSharing() {
        val status = BankTarget.classify(null, false, true, true, bank())

        assertEquals(BankAvailability.SHARE_CAPABLE, status.availability)
        assertTrue(status.advertised)
        assertTrue(status.isInstalled)
        assertTrue(status.canHandOff)
        assertTrue(status.shareCapable)
    }

    @Test
    fun installedButNotAdvertisedIsStillInstalled() {
        val status = BankTarget.classify(null, false, false, true, bank())

        assertFalse(status.advertised)
        assertEquals(BankAvailability.INSTALLED_BUT_NOT_SHARE_CAPABLE, status.availability)
        assertTrue(status.isInstalled)
        assertTrue(status.canHandOff)
        assertFalse(status.shareCapable)
    }

    @Test
    fun anAbsentBankCannotBeHandedOffTo() {
        val status = BankTarget.classify(null, false, false, false, bank())

        assertEquals(BankAvailability.NOT_INSTALLED, status.availability)
        assertFalse(status.isInstalled)
        assertFalse(status.canHandOff)
        assertFalse(status.advertised)
    }

    @Test
    fun anUnknownProbeIsNotTreatedAsInstalled() {
        // installed == null means the probe could not be completed.
        val status = BankTarget.classify(null, false, false, null, bank())

        assertEquals(BankAvailability.UNKNOWN, status.availability)
        assertFalse(status.isInstalled)
        assertFalse(status.canHandOff)
        assertFalse(status.advertised)
    }

    @Test
    fun theBankInfoIsCarriedThroughClassification() {
        val myBank = bank(id = "my_bank", packageName = "com.my.bank", displayName = "My Bank")
        val status = BankTarget.classify(null, false, true, true, myBank)

        assertEquals("my_bank", status.bank.id)
        assertEquals("com.my.bank", status.bank.packageName)
        assertEquals("My Bank", status.bank.displayName)
    }

    // ---- registry -----------------------------------------------------------

    @Test
    fun everyBankHasAUniqueId() {
        val ids = BankRegistry.allBanks.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyBankHasAUniquePackage() {
        val packages = BankRegistry.allBanks.map { it.packageName }
        assertEquals(packages.size, packages.toSet().size)
    }

    @Test
    fun everyBankHasAValidPackageName() {
        BankRegistry.allBanks.forEach { bank ->
            assertTrue("${bank.displayName} must have a package name", bank.packageName.isNotBlank())
            assertFalse("${bank.displayName} package must not contain spaces", bank.packageName.contains(" "))
            assertTrue("${bank.displayName} package must contain a dot", bank.packageName.contains("."))
            assertTrue(
                "${bank.displayName} package must start with a plausible segment",
                bank.packageName.substringBefore(".").length >= 2,
            )
        }
    }

    @Test
    fun everyVerifiedBankHasAGooglePlayUrlAndVerificationDate() {
        BankRegistry.allBanks.forEach { bank ->
            assertTrue("${bank.displayName} must be verified", bank.verified)
            assertTrue(
                "${bank.displayName} play url must point at its own package",
                bank.googlePlayUrl.endsWith("id=${bank.packageName}"),
            )
            assertTrue(
                "${bank.displayName} must record a verification date",
                bank.verificationDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")),
            )
            assertTrue("${bank.displayName} must name its publisher", bank.company.isNotBlank())
        }
    }

    @Test
    fun kPlusUsesTheCurrentGooglePlayPackage() {
        // The former package "com.kasikornbank.kplus" no longer exists on Play.
        assertEquals("com.kasikorn.retail.mbanking.wap", BankRegistry.K_PLUS.packageName)
        assertNotEquals("com.kasikornbank.kplus", BankRegistry.K_PLUS.packageName)
    }

    @Test
    fun theObsoletePackagesAreNoLongerInTheRegistry() {
        listOf(
            "com.kasikornbank.kplus",
            "com.scb.BankApp",
            "com.krungthai.nextbanking",
            "com.bblmobilebanking",
        ).forEach { obsolete ->
            assertEquals("obsolete package still present: $obsolete", null, BankRegistry.findByPackage(obsolete))
        }
    }

    @Test
    fun theMajorThaiBanksAreRegistered() {
        listOf(
            "kplus",
            "scb_easy",
            "krungthai_next",
            "bangkok_bank",
            "krungsri",
            "ttb_touch",
            "mymo_gsb",
            "cimb_thai",
            "uob_tmrw",
            "dime",
            "make_by_kbank",
            "kept",
            "truemoney",
        ).forEach { id ->
            assertTrue("missing registry entry: $id", BankRegistry.findById(id) != null)
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
        assertEquals(BankRegistry.K_PLUS, BankRegistry.findByPackage("com.kasikorn.retail.mbanking.wap"))
        assertEquals(null, BankRegistry.findByPackage("com.nonexistent.app"))
    }
}
