package com.enjirad.qrqueue.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Whether K PLUS can be reached through Android's share sheet on this device.
 *
 * This describes the share sheet only. The app never launches K PLUS by package
 * name and never interacts with it; the Android resolver always picks the target.
 */
enum class KPlusAvailability {
    /** K PLUS appears as a share target for QR images. */
    SHARE_TARGET,

    /** K PLUS is installed, but it does not advertise an image share filter. */
    INSTALLED_NOT_SHARE_TARGET,

    /** K PLUS was not found on this device. */
    NOT_INSTALLED,
}

/**
 * What this device can do with a shared QR image.
 *
 * @param declaredMimeType the MIME type of the stored image copy.
 * @param shareMimeType the type to hand to the chooser, or null when no app on
 *   this device can receive an image at all.
 * @param imageTargetCount number of distinct apps that can receive it.
 */
data class ShareTargetStatus(
    val declaredMimeType: String,
    val shareMimeType: String?,
    val imageTargetCount: Int,
    val kPlus: KPlusAvailability,
) {
    val canShare: Boolean get() = shareMimeType != null
}

/**
 * Resolves which apps can receive the stored QR image, and reports K PLUS
 * availability without ever forcing a target.
 *
 * [classify] is pure so the decision logic is covered by unit tests; [query] is
 * the thin Android layer. Accurate results on Android 11+ rely on the
 * `<queries>` block in the manifest — without it the platform hides other apps.
 */
object ShareTargets {

    /** Known K PLUS package names, used only to describe the share sheet. */
    val K_PLUS_PACKAGES: List<String> = listOf("com.kasikornbank.kplus")

    const val ANY_IMAGE_MIME_TYPE = "image/*"

    fun classify(
        declaredMimeType: String?,
        targetsForDeclaredType: Set<String>,
        targetsForAnyImage: Set<String>,
        installedKPlusPackages: Set<String>,
    ): ShareTargetStatus {
        val declared = declaredMimeType?.takeIf { it.startsWith("image/") }
        val shareMimeType = when {
            declared != null && targetsForDeclaredType.isNotEmpty() -> declared
            targetsForAnyImage.isNotEmpty() -> ANY_IMAGE_MIME_TYPE
            else -> null
        }
        val allTargets = targetsForDeclaredType + targetsForAnyImage
        val kPlus = when {
            installedKPlusPackages.isEmpty() -> KPlusAvailability.NOT_INSTALLED
            installedKPlusPackages.any { packageName -> packageName in allTargets } ->
                KPlusAvailability.SHARE_TARGET
            else -> KPlusAvailability.INSTALLED_NOT_SHARE_TARGET
        }
        return ShareTargetStatus(
            declaredMimeType = declared ?: ANY_IMAGE_MIME_TYPE,
            shareMimeType = shareMimeType,
            imageTargetCount = allTargets.size,
            kPlus = kPlus,
        )
    }

    /** Resolves the real share targets on this device. */
    fun query(context: Context, declaredMimeType: String?): ShareTargetStatus {
        val declared = declaredMimeType?.takeIf { it.startsWith("image/") } ?: ANY_IMAGE_MIME_TYPE
        val targetsForDeclared = imageShareTargets(context, declared)
        val targetsForAnyImage = if (declared == ANY_IMAGE_MIME_TYPE) {
            targetsForDeclared
        } else {
            imageShareTargets(context, ANY_IMAGE_MIME_TYPE)
        }
        val installedKPlus = K_PLUS_PACKAGES
            .filter { packageName -> isInstalled(context, packageName) }
            .toSet()
        return classify(declaredMimeType, targetsForDeclared, targetsForAnyImage, installedKPlus)
    }

    private fun imageShareTargets(context: Context, mimeType: String): Set<String> {
        val probe = Intent(Intent.ACTION_SEND).setType(mimeType)
        return runCatching {
            context.packageManager
                .queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { resolveInfo -> resolveInfo.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
    }.isSuccess
}
