package com.enjirad.qrqueue.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hand-off contract (a standard ACTION_SEND, a `content://` URI, an image MIME
 * type, a temporary read grant, and K PLUS as the only addressee) is built by the
 * pure [QrShare.shareSpec], so it is asserted here without a device. The Android
 * [android.content.Intent] is only assembled from this spec.
 */
class ShareIntentSpecTest {

    private val contentUri =
        "content://com.enjirad.qrqueue.fileprovider/qrqueue/images/item-1.png"

    @Test
    fun aContentUriBecomesAStandardImageShare() {
        val spec = QrShare.shareSpec(contentUri, "image/png")

        assertEquals(QrShare.ACTION_SEND, spec?.action)
        assertEquals("android.intent.action.SEND", spec?.action)
        assertEquals("image/png", spec?.mimeType)
        assertEquals(contentUri, spec?.streamUri)
        assertTrue(spec?.grantReadUriPermission == true)
    }

    @Test
    fun theHandOffIsAddressedToKPlusOnly() {
        val spec = QrShare.shareSpec(contentUri, "image/png")

        assertEquals("com.kasikornbank.kplus", spec?.targetPackage)
        assertEquals(QrShare.K_PLUS_PACKAGE, spec?.targetPackage)
        assertEquals(KPlusTarget.K_PLUS_PACKAGE, spec?.targetPackage)
        // Addressed to one package, so Android opens K PLUS directly.
        assertEquals(QrShare.ACTION_SEND, spec?.action)
    }

    @Test
    fun noChooserIsEverBuilt() {
        assertEquals("android.intent.action.CHOOSER", QrShare.ACTION_CHOOSER)
        assertNotEquals(QrShare.ACTION_CHOOSER, QrShare.shareSpec(contentUri, "image/png")?.action)
        assertNotEquals(QrShare.ACTION_CHOOSER, QrShare.shareSpec(contentUri, null)?.action)
    }

    @Test
    fun theStreamUriIsAlwaysAContentUri() {
        val spec = QrShare.shareSpec(contentUri, "image/jpeg")

        assertTrue(spec?.streamUri?.startsWith(QrShare.CONTENT_URI_PREFIX) == true)
    }

    @Test
    fun aFileUriIsNeverShared() {
        // A file:// URI cannot be read by the receiving banking app.
        assertNull(QrShare.shareSpec("file:///data/user/0/app/files/qrqueue/images/a.png", "image/png"))
        assertNull(QrShare.shareSpec(null, "image/png"))
        assertNull(QrShare.shareSpec("", "image/png"))
        assertNull(QrShare.shareSpec("   ", "image/png"))
    }

    @Test
    fun aMissingOrNonImageTypeFallsBackToAnyImage() {
        assertEquals("image/*", QrShare.shareSpec(contentUri, null)?.mimeType)
        assertEquals("image/*", QrShare.shareSpec(contentUri, "")?.mimeType)
        assertEquals("image/*", QrShare.shareSpec(contentUri, "application/pdf")?.mimeType)
    }

    @Test
    fun theReadGrantIsAlwaysRequested() {
        assertTrue(QrShare.shareSpec(contentUri, "image/png")?.grantReadUriPermission == true)
        assertTrue(QrShare.shareSpec(contentUri, null)?.grantReadUriPermission == true)
    }
}
