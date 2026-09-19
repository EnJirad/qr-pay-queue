package com.enjirad.qrqueue.data

import com.enjirad.qrqueue.domain.BankRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hand-off contract (a standard ACTION_SEND, a `content://` URI, an image MIME
 * type, a temporary read grant, and the selected bank as the addressee) is built
 * by the pure [QrShare.shareSpec], so it is asserted here without a device. The
 * Android [android.content.Intent] is only assembled from this spec.
 */
class ShareIntentSpecTest {

    private val contentUri =
        "content://com.enjirad.qrqueue.fileprovider/qrqueue/images/item-1.png"

    @Test
    fun aContentUriBecomesAStandardImageShare() {
        val spec = QrShare.shareSpec(contentUri, "image/png", BankRegistry.K_PLUS.name)

        assertEquals(QrShare.ACTION_SEND, spec?.action)
        assertEquals("android.intent.action.SEND", spec?.action)
        assertEquals("image/png", spec?.mimeType)
        assertEquals(contentUri, spec?.streamUri)
        assertTrue(spec?.grantReadUriPermission == true)
    }

    @Test
    fun theHandOffIsAddressedToTheSelectedBank() {
        val spec = QrShare.shareSpec(contentUri, "image/png", BankRegistry.K_PLUS.name)

        assertEquals("com.kasikornbank.kplus", spec?.targetPackage)
        assertEquals(BankRegistry.K_PLUS.name, spec?.targetPackage)
    }

    @Test
    fun theTargetPackageComesFromTheBankRegistry() {
        val spec = QrShare.shareSpec(contentUri, "image/png", BankRegistry.SCB_EASY.name)

        assertEquals("com.scb.BankApp", spec?.targetPackage)
        assertEquals(BankRegistry.SCB_EASY.name, spec?.targetPackage)
    }

    @Test
    fun noChooserIsEverBuilt() {
        assertEquals("android.intent.action.CHOOSER", QrShare.ACTION_CHOOSER)
        val spec = QrShare.shareSpec(contentUri, "image/png", BankRegistry.K_PLUS.name)
        assertNotEquals(QrShare.ACTION_CHOOSER, spec?.action)
    }

    @Test
    fun theStreamUriIsAlwaysAContentUri() {
        val spec = QrShare.shareSpec(contentUri, "image/jpeg", BankRegistry.K_PLUS.name)

        assertTrue(spec?.streamUri?.startsWith(QrShare.CONTENT_URI_PREFIX) == true)
    }

    @Test
    fun aFileUriIsNeverShared() {
        assertNull(QrShare.shareSpec("file:///data/user/0/app/files/qrqueue/images/a.png", "image/png", BankRegistry.K_PLUS.name))
        assertNull(QrShare.shareSpec(null, "image/png", BankRegistry.K_PLUS.name))
        assertNull(QrShare.shareSpec("", "image/png", BankRegistry.K_PLUS.name))
        assertNull(QrShare.shareSpec("   ", "image/png", BankRegistry.K_PLUS.name))
    }

    @Test
    fun aMissingOrNonImageTypeFallsBackToAnyImage() {
        assertEquals("image/*", QrShare.shareSpec(contentUri, null, BankRegistry.K_PLUS.name)?.mimeType)
        assertEquals("image/*", QrShare.shareSpec(contentUri, "", BankRegistry.K_PLUS.name)?.mimeType)
        assertEquals("image/*", QrShare.shareSpec(contentUri, "application/pdf", BankRegistry.K_PLUS.name)?.mimeType)
    }

    @Test
    fun theReadGrantIsAlwaysRequested() {
        assertTrue(QrShare.shareSpec(contentUri, "image/png", BankRegistry.K_PLUS.name)?.grantReadUriPermission == true)
        assertTrue(QrShare.shareSpec(contentUri, null, BankRegistry.K_PLUS.name)?.grantReadUriPermission == true)
    }
}
