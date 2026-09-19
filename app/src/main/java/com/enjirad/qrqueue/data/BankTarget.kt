package com.enjirad.qrqueue.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.enjirad.qrqueue.domain.BankAvailability
import com.enjirad.qrqueue.domain.BankInfo
import com.enjirad.qrqueue.domain.BankShareReadiness
import com.enjirad.qrqueue.domain.BankTargetStatus

/**
 * Resolves whether a direct image hand-off to any banking app is possible.
 *
 * [classify] is pure so the decision logic is covered by unit tests; [query] is
 * the thin Android layer. Accurate results on Android 11+ rely on the `<queries>`
 * block in the manifest — without it the platform hides other apps.
 *
 * "Installed" and "can receive this intent" are answered separately: a package
 * that is present but advertises no share activity is
 * [BankAvailability.INSTALLED_BUT_NOT_SHARE_CAPABLE], never "not installed".
 */
object BankTarget {

    const val IMAGE_MIME_PREFIX = "image/"
    const val ANY_IMAGE_MIME_TYPE = "image/*"

    /**
     * The pure part of the share gate: decides whether a hand-off may be
     * attempted from the two facts known without touching Android — whether a
     * bank is selected, and whether its package is installed.
     *
     * An installed bank whose share activity is hidden is still attempted, so it
     * is [BankShareReadiness.READY] here and the launch result is reported
     * honestly.
     */
    fun preflight(
        selectedBank: BankInfo?,
        installed: Boolean,
    ): BankShareReadiness = when {
        selectedBank == null -> BankShareReadiness.NO_BANK_SELECTED
        !installed -> BankShareReadiness.BANK_NOT_INSTALLED
        else -> BankShareReadiness.READY
    }

    /**
     * Decides what the app may do for one bank and one image.
     *
     * @param installed true = present, false = absent, **null = could not be
     *   determined** (a visibility/security error), which maps to
     *   [BankAvailability.UNKNOWN] instead of pretending the bank is missing.
     */
    fun classify(
        declaredMimeType: String?,
        advertisedForDeclaredType: Boolean,
        advertisedForAnyImage: Boolean,
        installed: Boolean?,
        bank: BankInfo,
    ): BankTargetStatus {
        val advertised = installed == true && (advertisedForDeclaredType || advertisedForAnyImage)
        val availability = when {
            installed == null -> BankAvailability.UNKNOWN
            !installed -> BankAvailability.NOT_INSTALLED
            advertised -> BankAvailability.SHARE_CAPABLE
            else -> BankAvailability.INSTALLED_BUT_NOT_SHARE_CAPABLE
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
        val installed = installState(context, bank.packageName)
        val advertised = installed == true && canReceiveQrImage(context, bank)
        return classify(
            declaredMimeType = null,
            advertisedForDeclaredType = false,
            advertisedForAnyImage = advertised,
            installed = installed,
            bank = bank,
        )
    }

    /**
     * True when the bank's package has an activity that can receive the exact
     * intent the app builds: a standard image share, addressed to that package.
     *
     * This is NOT the same question as whether the package is installed. A bank
     * can be installed and still not advertise the share activity; the app must
     * not report that as "not installed".
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

    /**
     * True when the bank advertises an activity for the exact image share intent
     * the app builds. Separate from the install check on purpose: Android 11+
     * package-visibility rules and per-build share activities mean the two answer
     * different questions.
     */
    fun canReceiveQrImage(context: Context, bank: BankInfo): Boolean =
        handles(context, bank.packageName, ANY_IMAGE_MIME_TYPE)

    /**
     * `true` = installed, `false` = the package really is absent, `null` = the
     * probe could not be completed (e.g. a `SecurityException` from a visibility
     * problem). A missing answer must never be reported as "not installed".
     */
    private fun installState(context: Context, packageName: String): Boolean? = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (notFound: PackageManager.NameNotFoundException) {
        false
    } catch (other: RuntimeException) {
        null
    }
}
