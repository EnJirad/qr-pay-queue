package com.enjirad.qrqueue.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * What this device can do with a direct image hand-off to K PLUS.
 *
 * The app only ever addresses K PLUS, so the only question worth asking is
 * whether K PLUS is there and whether it advertises an activity for the exact
 * intent the app builds.
 */
enum class KPlusAvailability {
    /** K PLUS is installed and advertises a matching image share target. */
    READY,

    /** K PLUS is installed, but no matching image activity was found. */
    INSTALLED_NOT_ADVERTISED,

    /** K PLUS was not found on this device. */
    NOT_INSTALLED,
}

/**
 * The result of checking K PLUS for one stored image.
 *
 * @param mimeType the type the app will put on the intent.
 * @param advertised true when K PLUS advertises an activity for this exact
 *   intent. A `false` here is a warning, not a verdict: package queries can be
 *   incomplete on some builds, so the hand-off is still attempted.
 * @param kPlus what was found on the device.
 */
data class KPlusTargetStatus(
    val mimeType: String,
    val advertised: Boolean,
    val kPlus: KPlusAvailability,
) {
    /** True when K PLUS is installed at all. */
    val isInstalled: Boolean get() = kPlus != KPlusAvailability.NOT_INSTALLED

    /**
     * True when the app should offer "แชร์ไป K PLUS" for this image.
     *
     * The button is offered whenever K PLUS is installed, even when it does not
     * advertise a matching filter: a refused hand-off is reported honestly as a
     * failure instead of being hidden behind a guess.
     */
    val canHandOff: Boolean get() = isInstalled

    /** True when the app knows K PLUS will accept the image. */
    val isAdvertisedShareTarget: Boolean get() = kPlus == KPlusAvailability.READY
}

/**
 * Resolves whether the direct hand-off to K PLUS is possible for a stored image.
 *
 * [classify] is pure so the decision logic is covered by unit tests; [query] is
 * the thin Android layer. Accurate results on Android 11+ rely on the `<queries>`
 * block in the manifest — without it the platform hides other apps.
 */
object KPlusTarget {

    /** The K PLUS package, pinned so it can be unit tested. */
    const val K_PLUS_PACKAGE = QrShare.K_PLUS_PACKAGE

    const val IMAGE_MIME_PREFIX = "image/"
    const val ANY_IMAGE_MIME_TYPE = "image/*"

    /**
     * Decides what the app may do with K PLUS for one image.
     *
     * A non-image declared type is ignored: this app only ever hands over images,
     * so the generic image type is used instead.
     */
    fun classify(
        declaredMimeType: String?,
        advertisedForDeclaredType: Boolean,
        advertisedForAnyImage: Boolean,
        installed: Boolean,
    ): KPlusTargetStatus {
        val declared = declaredMimeType?.takeIf { it.startsWith(IMAGE_MIME_PREFIX) }
            ?: ANY_IMAGE_MIME_TYPE
        val advertised = advertisedForDeclaredType || advertisedForAnyImage
        val mimeType = when {
            advertisedForDeclaredType -> declared
            advertisedForAnyImage -> ANY_IMAGE_MIME_TYPE
            else -> declared
        }
        val availability = when {
            !installed -> KPlusAvailability.NOT_INSTALLED
            advertised -> KPlusAvailability.READY
            else -> KPlusAvailability.INSTALLED_NOT_ADVERTISED
        }
        return KPlusTargetStatus(
            mimeType = mimeType,
            advertised = advertised,
            kPlus = availability,
        )
    }

    /** Resolves the real K PLUS state on this device. */
    fun query(context: Context, declaredMimeType: String?): KPlusTargetStatus {
        val declared = declaredMimeType?.takeIf { it.startsWith(IMAGE_MIME_PREFIX) }
            ?: ANY_IMAGE_MIME_TYPE
        val installed = isInstalled(context)
        val advertisedDeclared = installed && handles(context, declared)
        val advertisedAny = when {
            !installed -> false
            declared == ANY_IMAGE_MIME_TYPE -> advertisedDeclared
            else -> handles(context, ANY_IMAGE_MIME_TYPE)
        }
        return classify(
            declaredMimeType = declaredMimeType,
            advertisedForDeclaredType = advertisedDeclared,
            advertisedForAnyImage = advertisedAny,
            installed = installed,
        )
    }

    /**
     * True when K PLUS has an activity that can receive the exact intent the app
     * builds: a standard image share, addressed to K PLUS.
     */
    private fun handles(context: Context, mimeType: String): Boolean = runCatching {
        val probe = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .setPackage(K_PLUS_PACKAGE)
        context.packageManager
            .queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .any { resolveInfo -> resolveInfo.activityInfo?.packageName == K_PLUS_PACKAGE }
    }.getOrDefault(false)

    private fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(K_PLUS_PACKAGE, 0)
    }.isSuccess
}
