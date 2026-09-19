package com.enjirad.qrqueue.data

import com.enjirad.qrqueue.domain.BankRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V0.4.2 §2/§9/§22 — the direct-bank hand-off contract.
 *
 * The real [android.content.Intent] is assembled from the pure [ShareIntentSpec]
 * inside [QrShare.bankShareIntent], so asserting the spec asserts the intent that
 * will be built: a standard ACTION_SEND, an image MIME type, the EXTRA_STREAM
 * `content://` URI, the temporary read grant, and setPackage() addressed to the
 * selected bank only — never a chooser.
 */
class DirectBankIntentTest {

    private val qrUri =
        "content://com.enjirad.qrqueue.fileprovider/qrqueue/images/qr-1.png"

    @Test
    fun theIntentIsAStandardSendWithTheImageMimeType() {
        val spec = QrShare.shareSpec(qrUri, "image/png", BankRegistry.K_PLUS.packageName)

        assertEquals("android.intent.action.SEND", spec?.action)
        assertEquals(QrShare.ACTION_SEND, spec?.action)
        assertEquals("image/png", spec?.mimeType)
    }

    @Test
    fun theQrUsesTheExtraStreamContentUri() {
        val spec = QrShare.shareSpec(qrUri, "image/png", BankRegistry.K_PLUS.packageName)

        assertEquals("android.intent.extra.STREAM", QrShare.EXTRA_STREAM)
        assertEquals(qrUri, spec?.streamUri)
        assertTrue(spec?.streamUri?.startsWith(QrShare.CONTENT_URI_PREFIX) == true)
        assertFalse(spec?.streamUri?.startsWith("file://") == true)
    }

    @Test
    fun theReadPermissionIsAlwaysGranted() {
        assertTrue(
            QrShare.shareSpec(qrUri, "image/png", BankRegistry.K_PLUS.packageName)
                ?.grantReadUriPermission == true,
        )
        assertTrue(
            QrShare.shareSpec(qrUri, null, BankRegistry.K_PLUS.packageName)
                ?.grantReadUriPermission == true,
        )
    }

    @Test
    fun theTargetIsTheSelectedBankPackage() {
        assertEquals(
            "com.kasikorn.retail.mbanking.wap",
            QrShare.shareSpec(qrUri, "image/png", BankRegistry.K_PLUS.packageName)?.targetPackage,
        )
        assertEquals(
            "com.scb.phone",
            QrShare.shareSpec(qrUri, "image/png", BankRegistry.SCB_EASY.packageName)?.targetPackage,
        )
        // A different selection must produce a different addressee.
        assertNotEquals(
            BankRegistry.K_PLUS.packageName,
            QrShare.shareSpec(qrUri, "image/png", BankRegistry.SCB_EASY.packageName)?.targetPackage,
        )
    }

    @Test
    fun everyRegisteredBankResolvesToItsOwnTargetPackage() {
        BankRegistry.allBanks.forEach { bank ->
            val spec = QrShare.shareSpec(qrUri, "image/png", bank.packageName)
            assertEquals(bank.packageName, spec?.targetPackage)
        }
    }

    @Test
    fun theMimeTypeFallsBackToAnyImage() {
        assertEquals(
            "image/*",
            QrShare.shareSpec(qrUri, null, BankRegistry.K_PLUS.packageName)?.mimeType,
        )
        assertEquals(
            "image/*",
            QrShare.shareSpec(qrUri, "application/pdf", BankRegistry.K_PLUS.packageName)?.mimeType,
        )
    }

    @Test
    fun aNonContentUriIsNeverShared() {
        assertNull(QrShare.shareSpec("file:///data/app/qr.png", "image/png", BankRegistry.K_PLUS.packageName))
        assertNull(QrShare.shareSpec(null, "image/png", BankRegistry.K_PLUS.packageName))
    }

    @Test
    fun noChooserIsEverBuilt() {
        assertEquals("android.intent.action.CHOOSER", QrShare.ACTION_CHOOSER)
        assertNotEquals(
            QrShare.ACTION_CHOOSER,
            QrShare.shareSpec(qrUri, "image/png", BankRegistry.K_PLUS.packageName)?.action,
        )
    }
}
