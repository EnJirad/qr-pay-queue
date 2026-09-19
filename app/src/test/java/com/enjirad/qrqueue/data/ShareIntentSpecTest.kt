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

    private val kPlus = BankRegistry.K_PLUS.packageName
    private val scb = BankRegistry.SCB_EASY.packageName

    @Test
    fun aContentUriBecomesAStandardImageShare() {
        val spec = QrShare.shareSpec(contentUri, "image/png", kPlus)

        assertEquals(QrShare.ACTION_SEND, spec?.action)
        assertEquals("android.intent.action.SEND", spec?.action)
        assertEquals("image/png", spec?.mimeType)
        assertEquals(contentUri, spec?.streamUri)
        assertTrue(spec?.grantReadUriPermission == true)
    }

    @Test
    fun theHandOffIsAddressedToTheSelectedBank() {
        val spec = QrShare.shareSpec(contentUri, "image/png", kPlus)

        assertEquals("com.kasikorn.retail.mbanking.wap", spec?.targetPackage)
        assertEquals(kPlus, spec?.targetPackage)
    }

    @Test
    fun theTargetPackageComesFromTheBankRegistry() {
        val spec = QrShare.shareSpec(contentUri, "image/png", scb)

        assertEquals("com.scb.phone", spec?.targetPackage)
        assertEquals(scb, spec?.targetPackage)
    }

    @Test
    fun noChooserIsEverBuilt() {
        assertEquals("android.intent.action.CHOOSER", QrShare.ACTION_CHOOSER)
        val spec = QrShare.shareSpec(contentUri, "image/png", kPlus)
        assertNotEquals(QrShare.ACTION_CHOOSER, spec?.action)
    }

    @Test
    fun theStreamUriIsAlwaysAContentUri() {
        val spec = QrShare.shareSpec(contentUri, "image/jpeg", kPlus)

        assertTrue(spec?.streamUri?.startsWith(QrShare.CONTENT_URI_PREFIX) == true)
    }

    @Test
    fun aFileUriIsNeverShared() {
        assertNull(QrShare.shareSpec("file:///data/user/0/app/files/qrqueue/images/a.png", "image/png", kPlus))
        assertNull(QrShare.shareSpec(null, "image/png", kPlus))
        assertNull(QrShare.shareSpec("", "image/png", kPlus))
        assertNull(QrShare.shareSpec("   ", "image/png", kPlus))
    }

    @Test
    fun aMissingOrNonImageTypeFallsBackToAnyImage() {
        assertEquals("image/*", QrShare.shareSpec(contentUri, null, kPlus)?.mimeType)
        assertEquals("image/*", QrShare.shareSpec(contentUri, "", kPlus)?.mimeType)
        assertEquals("image/*", QrShare.shareSpec(contentUri, "application/pdf", kPlus)?.mimeType)
    }

    @Test
    fun theReadGrantIsAlwaysRequested() {
        assertTrue(QrShare.shareSpec(contentUri, "image/png", kPlus)?.grantReadUriPermission == true)
        assertTrue(QrShare.shareSpec(contentUri, null, kPlus)?.grantReadUriPermission == true)
    }
}
