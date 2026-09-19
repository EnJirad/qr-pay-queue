package com.enjirad.qrqueue.ui

import com.enjirad.qrqueue.data.QrShare
import com.enjirad.qrqueue.domain.BankRegistry
import com.enjirad.qrqueue.domain.BankShareReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V0.4.2 §16/§17/§22 — when the selected bank cannot be opened, the app shows an
 * error. It must never open the Android Sharesheet and never open another app.
 */
class ShareFallbackTest {

    @Test
    fun aReadyTargetNeedsNoError() {
        assertNull(BankShareFlow.noticeFor(BankShareReadiness.READY))
    }

    @Test
    fun noBankSelectedAsksTheUserToSelectOne() {
        assertEquals(
            QueueNotice.BANK_UNAVAILABLE,
            BankShareFlow.noticeFor(BankShareReadiness.NO_BANK_SELECTED),
        )
    }

    @Test
    fun anUninstalledBankAsksForANewOne() {
        assertEquals(
            QueueNotice.BANK_UNINSTALLED,
            BankShareFlow.noticeFor(BankShareReadiness.BANK_NOT_INSTALLED),
        )
    }

    @Test
    fun anUnresolvableTargetShowsTheCannotOpenError() {
        assertEquals(
            QueueNotice.SHARE_TARGET_UNAVAILABLE,
            BankShareFlow.noticeFor(BankShareReadiness.TARGET_UNRESOLVABLE),
        )
    }

    @Test
    fun everyNonReadyOutcomeIsAnErrorNotAShare() {
        // There is no readiness value that opens the chooser or another app: every
        // non-ready outcome maps to an error notice instead.
        BankShareReadiness.entries.forEach { readiness ->
            val notice = BankShareFlow.noticeFor(readiness)
            if (readiness == BankShareReadiness.READY) {
                assertNull(notice)
            } else {
                assertTrue("$readiness must produce an error notice", notice != null)
            }
        }
    }

    @Test
    fun theHandOffActionNeverBecomesAChooser() {
        val spec = QrShare.shareSpec(
            "content://com.enjirad.qrqueue.fileprovider/qrqueue/images/qr.png",
            "image/png",
            BankRegistry.K_PLUS.name,
        )

        assertEquals(QrShare.ACTION_SEND, spec?.action)
        assertNotEquals(QrShare.ACTION_CHOOSER, spec?.action)
    }
}
