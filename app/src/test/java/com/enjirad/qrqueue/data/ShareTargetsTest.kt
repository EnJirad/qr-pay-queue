package com.enjirad.qrqueue.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The share decision logic is pure, so the K PLUS availability reporting and the
 * MIME fallback are covered without a device.
 */
class ShareTargetsTest {

    private val kPlus = "com.kasikornbank.kplus"

    @Test
    fun sharesWithTheDeclaredTypeWhenSomethingHandlesIt() {
        val status = ShareTargets.classify(
            declaredMimeType = "image/png",
            targetsForDeclaredType = setOf(kPlus),
            targetsForAnyImage = setOf(kPlus),
            installedKPlusPackages = setOf(kPlus),
        )

        assertEquals("image/png", status.shareMimeType)
        assertEquals("image/png", status.declaredMimeType)
        assertEquals(1, status.imageTargetCount)
        assertEquals(KPlusAvailability.SHARE_TARGET, status.kPlus)
        assertTrue(status.canShare)
    }

    @Test
    fun fallsBackToAnyImageTypeWhenNothingHandlesTheDeclaredType() {
        // e.g. a HEIC screenshot where the picker app only filters image/*
        val status = ShareTargets.classify(
            declaredMimeType = "image/heic",
            targetsForDeclaredType = emptySet(),
            targetsForAnyImage = setOf("com.android.gallery"),
            installedKPlusPackages = emptySet(),
        )

        assertEquals("image/*", status.shareMimeType)
        assertEquals("image/heic", status.declaredMimeType)
        assertTrue(status.canShare)
        assertEquals(KPlusAvailability.NOT_INSTALLED, status.kPlus)
    }

    @Test
    fun reportsNoShareTargetWhenNothingCanReceiveImages() {
        val status = ShareTargets.classify(
            declaredMimeType = "image/png",
            targetsForDeclaredType = emptySet(),
            targetsForAnyImage = emptySet(),
            installedKPlusPackages = setOf(kPlus),
        )

        assertNull(status.shareMimeType)
        assertFalse(status.canShare)
        assertEquals(0, status.imageTargetCount)
        assertEquals(KPlusAvailability.INSTALLED_NOT_SHARE_TARGET, status.kPlus)
    }

    @Test
    fun countsEachReceivingAppOnce() {
        val status = ShareTargets.classify(
            declaredMimeType = "image/png",
            targetsForDeclaredType = setOf("a", "b"),
            targetsForAnyImage = setOf("b", "c"),
            installedKPlusPackages = emptySet(),
        )

        assertEquals(3, status.imageTargetCount)
    }

    @Test
    fun anUnknownDeclaredTypeIsTreatedAsAnyImage() {
        val status = ShareTargets.classify(
            declaredMimeType = null,
            targetsForDeclaredType = emptySet(),
            targetsForAnyImage = setOf("com.android.gallery"),
            installedKPlusPackages = emptySet(),
        )

        assertEquals("image/*", status.declaredMimeType)
        assertEquals("image/*", status.shareMimeType)
    }

    @Test
    fun aNonImageDeclaredTypeIsNeverShared() {
        val status = ShareTargets.classify(
            declaredMimeType = "application/pdf",
            targetsForDeclaredType = setOf("com.reader"),
            targetsForAnyImage = setOf("com.android.gallery"),
            installedKPlusPackages = emptySet(),
        )

        assertEquals("image/*", status.declaredMimeType)
        assertEquals("image/*", status.shareMimeType)
        assertEquals(1, status.imageTargetCount)
    }

    @Test
    fun aNonImageHandlerDoesNotMakeKPlusAnImageShareTarget() {
        // K PLUS may handle other types (e.g. statements); that must not be
        // mistaken for being able to receive a QR image.
        val status = ShareTargets.classify(
            declaredMimeType = "application/pdf",
            targetsForDeclaredType = setOf(kPlus),
            targetsForAnyImage = setOf("com.android.gallery"),
            installedKPlusPackages = setOf(kPlus),
        )

        assertEquals(KPlusAvailability.INSTALLED_NOT_SHARE_TARGET, status.kPlus)
        assertEquals(1, status.imageTargetCount)
    }

    @Test
    fun kPlusInstalledWithoutAShareFilterIsReportedHonestly() {
        val status = ShareTargets.classify(
            declaredMimeType = "image/png",
            targetsForDeclaredType = setOf("com.other.bank"),
            targetsForAnyImage = setOf("com.other.bank"),
            installedKPlusPackages = setOf(kPlus),
        )

        assertEquals(KPlusAvailability.INSTALLED_NOT_SHARE_TARGET, status.kPlus)
        assertTrue(status.canShare)
    }
}
