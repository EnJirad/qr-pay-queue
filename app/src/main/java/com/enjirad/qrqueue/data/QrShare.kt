package com.enjirad.qrqueue.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * A pure description of one image hand-off to K PLUS.
 *
 * Keeping the action, MIME type, target package and grant rules out of the
 * Android layer means the exact contract the app relies on (a standard
 * `ACTION_SEND`, a `content://` URI, an image MIME type, a temporary read grant,
 * and K PLUS as the only addressee) is covered by fast JVM unit tests, while
 * [QrShare] only turns the spec into a real [Intent].
 */
data class ShareIntentSpec(
    val action: String,
    val mimeType: String,
    /** The `content://` URI passed as [Intent.EXTRA_STREAM]. */
    val streamUri: String,
    val grantReadUriPermission: Boolean,
    /** The only application this hand-off is addressed to (K PLUS). */
    val targetPackage: String,
)

/**
 * Android hand-off to K PLUS, and nothing more.
 *
 * The workflow fixes the destination: the user pressed "แชร์ไป K PLUS", so the
 * app addresses K PLUS directly and never opens Android's "share with..." chooser
 * first. The intent is still an ordinary `ACTION_SEND` image share — K PLUS
 * decides what it does with the image.
 *
 * The app only ever hands the image over. It never types a PIN, password or OTP,
 * never touches biometric prompts, never clicks anything inside K PLUS, never
 * reads K PLUS screens and never calls a bank API. The actual payment stays with
 * the user, inside K PLUS.
 *
 * The image is always shared through `FileProvider` as a `content://` URI with a
 * temporary read grant — never a `file://` URI, which banking apps cannot read
 * and which the platform forbids across apps.
 */
object QrShare {

    /** Standard Android share action; pinned so it can be unit tested. */
    const val ACTION_SEND = "android.intent.action.SEND"

    /** URI extra name; pinned so it can be unit tested. */
    const val EXTRA_STREAM = "android.intent.extra.STREAM"

    /** The chooser action, used only to assert that a chooser is never built. */
    const val ACTION_CHOOSER = "android.intent.action.CHOOSER"

    const val IMAGE_MIME_PREFIX = "image/"
    const val ANY_IMAGE_MIME_TYPE = "image/*"
    const val CONTENT_URI_PREFIX = "content://"

    /**
     * Builds the hand-off description, or null when the image has no usable
     * `content://` URI.
     *
     * Only a real `content://` URI (from `FileProvider`) is accepted: a `file://`
     * URI would be rejected by the receiving banking app. A missing or non-image
     * MIME type falls back to the generic image type, which image-capable
     * receivers advertise.
     */
    fun shareSpec(
        contentUri: String?,
        mimeType: String?,
        targetPackage: String,
    ): ShareIntentSpec? {
        val uri = contentUri?.trim().orEmpty()
        if (!uri.startsWith(CONTENT_URI_PREFIX)) return null
        val type = mimeType?.trim()
            ?.takeIf { it.startsWith(IMAGE_MIME_PREFIX) }
            ?: ANY_IMAGE_MIME_TYPE
        return ShareIntentSpec(
            action = ACTION_SEND,
            mimeType = type,
            streamUri = uri,
            grantReadUriPermission = true,
            targetPackage = targetPackage,
        )
    }

    /**
     * The direct hand-off to a banking app.
     *
     * The intent is addressed to the bank by package, so Android opens it
     * straight away and the user never has to pick an app. If the bank cannot
     * receive it, the launch fails with `ActivityNotFoundException` and the item
     * is recorded as FAILED — the app does not fall back to a chooser and does
     * not invent a workaround.
     *
     * @return null when the image has no shareable `content://` URI.
     */
    fun bankShareIntent(
        context: Context,
        file: File,
        mimeType: String,
        targetPackage: String,
    ): Intent? {
        val uri = contentUri(context, file) ?: return null
        val spec = shareSpec(uri.toString(), mimeType, targetPackage) ?: return null
        return Intent(spec.action).apply {
            type = spec.mimeType
            putExtra(EXTRA_STREAM, uri)
            // Direct hand-off: only the selected bank is addressed, so no chooser
            // is shown.
            setPackage(spec.targetPackage)
            if (spec.grantReadUriPermission) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                // Needed only when there is no activity task to launch into; from
                // our own activity the bank app opens from this task, so returning
                // from it lands back on the queue.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /** Opens the stored QR image in whatever app can display it. */
    fun viewIntent(context: Context, file: File, mimeType: String): Intent? {
        val uri = contentUri(context, file) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun contentUri(context: Context, file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}
