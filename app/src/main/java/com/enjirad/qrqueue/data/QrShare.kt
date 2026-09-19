package com.enjirad.qrqueue.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android hand-off to another application, and nothing more.
 *
 * The app only ever hands the QR image to the app the user picks (or opens it in
 * a viewer). It never types a PIN, password or OTP, never touches biometric
 * prompts, never clicks anything inside a banking app and never calls a bank API.
 * The actual payment stays with the user.
 */
object QrShare {

    /** Chooser that hands the QR image to a banking or gallery app. */
    fun shareIntent(context: Context, file: File, mimeType: String, chooserTitle: String): Intent? {
        val uri = contentUri(context, file) ?: return null
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Opens the QR image in whatever app can display it. */
    fun viewIntent(context: Context, file: File, mimeType: String): Intent? {
        val uri = contentUri(context, file) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun contentUri(context: Context, file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}
