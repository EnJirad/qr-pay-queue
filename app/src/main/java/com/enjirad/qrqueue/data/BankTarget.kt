package com.enjirad.qrqueue.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.enjirad.qrqueue.domain.BankAvailability
import com.enjirad.qrqueue.domain.BankInfo
import com.enjirad.qrqueue.domain.BankTargetStatus

/**
 * Resolves whether a direct image hand-off to any banking app is possible.
 *
 * [classify] is pure so the decision logic is covered by unit tests; [query] is
 * the thin Android layer. Accurate results on Android 11+ rely on the `<queries>`
 * block in the manifest — without it the platform hides other apps.
 */
object BankTarget {

    const val IMAGE_MIME_PREFIX = "image/"
    const val ANY_IMAGE_MIME_TYPE = "image/*"

    /**
     * Decides what the app may do for one bank and one image.
     *
     * A non-image declared type is ignored: this app only ever hands over images,
     * so the generic image type is used instead.
     */
    fun classify(
        declaredMimeType: String?,
        advertisedForDeclaredType: Boolean,
        advertisedForAnyImage: Boolean,
        installed: Boolean,
        bank: BankInfo,
    ): BankTargetStatus {
        val advertised = advertisedForDeclaredType || advertisedForAnyImage
        val availability = when {
            !installed -> BankAvailability.NOT_INSTALLED
            advertised -> BankAvailability.READY
            else -> BankAvailability.INSTALLED_NOT_ADVERTISED
        }
        return BankTargetStatus(
            bank = bank,
            availability = availability,
            advertised = advertised,
        )
    }

    /**
     * Resolves the real state of one banking app on this device.
     *
     * The probe uses the exact intent shape the app builds for the hand-off:
     * `ACTION_SEND`, the generic image MIME type, addressed to the bank's
     * package. This is as close as we can get to the real share without actually
     * launching it.
     */
    fun query(context: Context, bank: BankInfo): BankTargetStatus {
        val installed = isInstalled(context, bank.name)
        val advertisedDeclared = installed && handles(context, bank.name, ANY_IMAGE_MIME_TYPE)
        return classify(
            declaredMimeType = null,
            advertisedForDeclaredType = false,
            advertisedForAnyImage = advertisedDeclared,
            installed = installed,
            bank = bank,
        )
    }

    /**
     * True when the bank's package has an activity that can receive the exact
     * intent the app builds: a standard image share, addressed to that package.
     */
    private fun handles(context: Context, packageName: String, mimeType: String): Boolean =
        runCatching {
            val probe = Intent(Intent.ACTION_SEND)
                .setType(mimeType)
                .setPackage(packageName)
            context.packageManager
                .queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
                .any { resolveInfo -> resolveInfo.activityInfo?.packageName == packageName }
        }.getOrDefault(false)

    private fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
    }.isSuccess
}
