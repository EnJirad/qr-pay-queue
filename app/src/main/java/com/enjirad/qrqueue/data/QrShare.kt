package com.enjirad.qrqueue.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * A pure description of one image hand-off.
 *
 * Keeping the action / MIME / grant rules out of the Android layer means the
 * exact contract the app relies on (standard `ACTION_SEND`, a `content://` URI,
 * an image MIME type, a temporary read grant) is covered by fast JVM unit tests,
 * while [QrShare] only turns the spec into a real [Intent].
 */
data class ShareIntentSpec(
    val action: String,
    val mimeType: String,
    /** The `content://` URI passed as [Intent.EXTRA_STREAM]. */
    val streamUri: String,
    val grantReadUriPermission: Boolean,
)

/**
 * Android hand-off to another application, and nothing more.
 *
 * The app only ever hands the image to the app the user picks (or opens it in a
 * viewer). It never types a PIN, password or OTP, never touches biometric
 * prompts, never clicks anything inside a banking app and never calls a bank
 * API. The actual payment stays with the user, inside K PLUS.
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

    const val IMAGE_MIME_PREFIX = "image/"
    const val ANY_IMAGE_MIME_TYPE = "image/*"
    const val CONTENT_URI_PREFIX = "content://"

    /**
     * Builds the hand-off description, or null when the image has no usable
     * `content://` URI.
     *
     * Only a real `content://` URI (from `FileProvider`) is accepted: a
     * `file://` URI would be rejected by the receiving banking app. A missing or
     * non-image MIME type falls back to the generic image wildcard type, which
     * every image-capable share target advertises.
     */
    fun shareSpec(contentUri: String?, mimeType: String?): ShareIntentSpec? {
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
        )
    }

    /** Chooser that hands the QR image to a banking or gallery app. */
    fun shareIntent(context: Context, file: File, mimeType: String, chooserTitle: String): Intent? {
        val uri = contentUri(context, file) ?: return null
        val spec = shareSpec(uri.toString(), mimeType) ?: return null
        val send = Intent(spec.action).apply {
            type = spec.mimeType
            putExtra(EXTRA_STREAM, uri)
            if (spec.grantReadUriPermission) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, chooserTitle).apply {
            // The chooser forwards the read grant to whichever app the user picks.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                // Needed only when there is no activity task to launch into; from
                // our own activity the chooser stays in this task, so returning
                // from the banking app lands back on the queue.
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
