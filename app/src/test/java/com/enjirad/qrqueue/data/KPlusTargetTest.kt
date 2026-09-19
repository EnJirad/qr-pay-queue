package com.enjirad.qrqueue.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision logic that decides whether the direct hand-off to K PLUS can be
 * attempted is pure, so it is covered without a device.
 */
class KPlusTargetTest {

    @Test
    fun theHandOffTargetIsTheKPlusPackage() {
        assertEquals("com.kasikornbank.kplus", KPlusTarget.K_PLUS_PACKAGE)
        assertEquals(QrShare.K_PLUS_PACKAGE, KPlusTarget.K_PLUS_PACKAGE)
    }

    @Test
    fun readyWhenKPlusAdvertisesTheDeclaredType() {
        val status = KPlusTarget.classify(
            declaredMimeType = "image/png",
            advertisedForDeclaredType = true,
            advertisedForAnyImage = true,
            installed = true,
        )

        assertEquals("image/png", status.mimeType)
        assertTrue(status.advertised)
        assertEquals(KPlusAvailability.READY, status.kPlus)
        assertTrue(status.isAdvertisedShareTarget)
        assertTrue(status.isInstalled)
        assertTrue(status.canHandOff)
    }

    @Test
    fun fallsBackToAnyImageWhenOnlyTheWildcardTypeIsAdvertised() {
        // e.g. a HEIC screenshot where K PLUS only filters the generic image type.
        val status = KPlusTarget.classify(
            declaredMimeType = "image/heic",
            advertisedForDeclaredType = false,
            advertisedForAnyImage = true,
            installed = true,
        )

        assertEquals("image/*", status.mimeType)
        assertTrue(status.advertised)
        assertEquals(KPlusAvailability.READY, status.kPlus)
    }

    @Test
    fun installedButNotAdvertisedIsStillOffered() {
        // A package query can be incomplete on some builds, so the hand-off is
        // still attempted and reported honestly if K PLUS refuses it.
        val status = KPlusTarget.classify(
            declaredMimeType = "image/png",
            advertisedForDeclaredType = false,
            advertisedForAnyImage = false,
            installed = true,
        )

        assertEquals("image/png", status.mimeType)
        assertFalse(status.advertised)
        assertEquals(KPlusAvailability.INSTALLED_NOT_ADVERTISED, status.kPlus)
        assertFalse(status.isAdvertisedShareTarget)
        assertTrue(status.isInstalled)
        assertTrue(status.canHandOff)
    }

    @Test
    fun anAbsentKPlusCannotBeHandedOffTo() {
        val status = KPlusTarget.classify(
            declaredMimeType = "image/png",
            advertisedForDeclaredType = false,
            advertisedForAnyImage = false,
            installed = false,
        )

        assertEquals(KPlusAvailability.NOT_INSTALLED, status.kPlus)
        assertFalse(status.isInstalled)
        assertFalse(status.canHandOff)
        assertFalse(status.isAdvertisedShareTarget)
    }

    @Test
    fun aNonImageDeclaredTypeBecomesTheAnyImageType() {
        val status = KPlusTarget.classify(
            declaredMimeType = "application/pdf",
            advertisedForDeclaredType = false,
            advertisedForAnyImage = true,
            installed = true,
        )

        assertEquals("image/*", status.mimeType)
        assertEquals(KPlusAvailability.READY, status.kPlus)
    }

    @Test
    fun anUnknownDeclaredTypeBecomesTheAnyImageType() {
        val status = KPlusTarget.classify(
            declaredMimeType = null,
            advertisedForDeclaredType = false,
            advertisedForAnyImage = true,
            installed = true,
        )

        assertEquals("image/*", status.mimeType)
    }

    @Test
    fun theDeclaredTypesOwnActivityWinsOverTheWildcard() {
        val status = KPlusTarget.classify(
            declaredMimeType = "image/jpeg",
            advertisedForDeclaredType = true,
            advertisedForAnyImage = false,
            installed = true,
        )

        assertEquals("image/jpeg", status.mimeType)
        assertEquals(KPlusAvailability.READY, status.kPlus)
    }

    @Test
    fun aNonImageTypeIsStillHandedOverAsAnImage() {
        // The app only ever hands over images, so a non-image declared type must
        // never be sent to K PLUS as-is.
        val status = KPlusTarget.classify(
            declaredMimeType = "text/plain",
            advertisedForDeclaredType = false,
            advertisedForAnyImage = false,
            installed = true,
        )

        assertEquals("image/*", status.mimeType)
    }
}
