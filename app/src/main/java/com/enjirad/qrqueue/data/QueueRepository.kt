package com.enjirad.qrqueue.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.enjirad.qrqueue.domain.PaymentQueue
import com.enjirad.qrqueue.domain.PaymentStatus
import com.enjirad.qrqueue.domain.QueueImport
import com.enjirad.qrqueue.domain.QueueItem
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** One image copied into app-private storage. */
data class StoredImage(
    val file: File,
    /** Original name from the picker, for display only. */
    val displayName: String,
    val mimeType: String,
)

/**
 * The single owner of everything this app writes to disk:
 *
 * ```
 * filesDir/qrqueue/queue.json              the queue state (id, items, statuses)
 * filesDir/qrqueue/images/<itemId>.img     imported copies of the selected images
 * ```
 *
 * Importing copies the picked image into the app; the gallery original is never
 * moved, renamed or deleted. A queue is self-contained: clearing it removes only
 * these app-private files.
 *
 * V0.4 stores no QR content at all — only the image location, the queue order
 * and where each item stands in the hand-off flow.
 */
class QueueRepository(private val context: Context) {

    // ---- images -------------------------------------------------------------

    /**
     * Copies a picked image into app-private storage.
     *
     * @return the stored copy, or null when the image could not be read or the
     *   copy could not be written. The caller reports that honestly and does not
     *   create a queue item for a file that does not exist.
     */
    fun importImage(uri: Uri, itemId: String): StoredImage? {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri)
        val declaredMimeType = runCatching { resolver.getType(uri) }.getOrNull()
        val extension = QueueImport.fileExtensionFor(declaredMimeType, displayName)
        val mimeType = declaredMimeType ?: QueueImport.mimeTypeForExtension(extension)
        val directory = imageDirectory()
        if (!directory.isDirectory && !directory.mkdirs()) return null
        val target = File(directory, "$itemId.$extension")

        val stream = runCatching { resolver.openInputStream(uri) }.getOrNull() ?: return null
        val copied = runCatching {
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.isSuccess
        if (!copied || target.length() <= 0L) {
            target.delete()
            return null
        }
        return StoredImage(target, displayName ?: DEFAULT_DISPLAY_NAME, mimeType)
    }

    /** Resolves the stored copy of an item image, when it still exists. */
    fun storedFile(path: String?): File? = path?.let { File(it) }?.takeIf { it.isFile }

    /** Deletes specific stored image copies (never anything outside them). */
    fun deleteImages(paths: List<String>): Int =
        paths.count { path -> storedFile(path)?.delete() == true }

    /**
     * Deletes image copies that no queue item references — for example after an
     * import was interrupted before the queue state was written. The original
     * gallery images are never involved: only this app's own image directory is
     * inspected.
     */
    fun sweepOrphanImages(referencedPaths: Set<String>): Int {
        val files = imageDirectory().listFiles() ?: return 0
        val referenced = referencedPaths
            .mapNotNull { path -> runCatching { File(path).canonicalPath }.getOrNull() }
            .toSet()
        var deleted = 0
        files.forEach { file ->
            if (!file.isFile) return@forEach
            val canonical = runCatching { file.canonicalPath }.getOrNull() ?: return@forEach
            if (canonical !in referenced && file.delete()) deleted++
        }
        return deleted
    }

    // ---- queue state --------------------------------------------------------

    /**
     * Reads the persisted queue, or null when there is none / it is unreadable.
     *
     * An item whose stored image no longer exists is marked [PaymentStatus.FAILED]
     * instead of crashing the app or pretending the item can be shared, so the
     * queue screen can explain what happened.
     */
    fun loadQueue(): PaymentQueue? {
        val file = stateFile()
        if (!file.isFile) return null
        return runCatching { parseQueue(JSONObject(file.readText())) }
            .getOrNull()
            ?.let { queue ->
                queue.copy(
                    items = queue.items.map { item -> markMissingImage(item) },
                ).normalized()
            }
    }

    /** An active item whose image file is gone can never be shared. */
    private fun markMissingImage(item: QueueItem): QueueItem = when {
        item.status.isCompleted -> item
        storedFile(item.storedImagePath) != null -> item
        else -> item.copy(
            status = PaymentStatus.FAILED,
            failureDetail = "The imported image file is missing from this device.",
        )
    }

    /**
     * Persists the queue state, so recreation, backgrounding or a restart cannot
     * lose it.
     *
     * @return true when the state really reached disk; callers must tell the user
     *   when it did not, because a queue that cannot be persisted is a payment
     *   queue with an unknown future.
     */
    fun saveQueue(queue: PaymentQueue): Boolean {
        val directory = rootDirectory()
        if (!directory.isDirectory && !directory.mkdirs()) return false
        return runCatching { stateFile().writeText(serializeQueue(queue).toString()) }.isSuccess
    }

    /**
     * Deletes the queue and its imported image copies. Gallery originals are
     * untouched: this only ever removes files this app created.
     */
    fun clearQueue() {
        imageDirectory().deleteRecursively()
        stateFile().delete()
    }

    // ---- paths --------------------------------------------------------------

    private fun rootDirectory(): File = File(context.filesDir, ROOT_DIR)

    private fun imageDirectory(): File = File(rootDirectory(), IMAGES_DIR)

    private fun stateFile(): File = File(rootDirectory(), STATE_FILE)

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    // ---- json ---------------------------------------------------------------

    private fun serializeQueue(queue: PaymentQueue): JSONObject = JSONObject().apply {
        put("version", SCHEMA_VERSION)
        put("queueId", queue.queueId)
        put("createdAt", queue.createdAt)
        put("updatedAtMillis", queue.updatedAtMillis)
        put("items", JSONArray().apply { queue.items.sortedBy { it.position }.forEach { put(serializeItem(it)) } })
    }

    private fun serializeItem(item: QueueItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("position", item.position)
        put("status", item.status.name)
        put("displayName", item.displayName)
        put("sourceUri", item.sourceUri)
        put("storedImagePath", item.storedImagePath)
        put("mimeType", item.mimeType)
        put("createdAt", item.createdAt)
        put("updatedAt", item.updatedAt)
        putNullable("failureDetail", item.failureDetail)
    }

    private fun parseQueue(json: JSONObject): PaymentQueue {
        val itemsJson = json.optJSONArray("items") ?: JSONArray()
        val items = (0 until itemsJson.length()).mapNotNull { index ->
            itemsJson.optJSONObject(index)?.let { parseItem(it, index) }
        }
        return PaymentQueue(
            queueId = json.stringOrNull("queueId") ?: DEFAULT_QUEUE_ID,
            createdAt = json.optLong("createdAt"),
            items = items.sortedBy { it.position },
            updatedAtMillis = json.optLong("updatedAtMillis", 0L),
        )
    }

    /**
     * Reads one item. The 0.3.x schema stored the same image facts under
     * `fileName` and `importedAtMillis`; those are accepted so an existing queue
     * still opens, and its QR-only fields (payload, amount, recipient, issue) are
     * simply no longer read.
     */
    private fun parseItem(json: JSONObject, fallbackPosition: Int): QueueItem? {
        val id = json.stringOrNull("id") ?: return null
        val storedImagePath = json.stringOrNull("storedImagePath").orEmpty()
        val extension = storedImagePath.substringAfterLast('.', "")
        return QueueItem(
            id = id,
            position = json.optInt("position", fallbackPosition),
            sourceUri = json.stringOrNull("sourceUri").orEmpty(),
            storedImagePath = storedImagePath,
            displayName = json.stringOrNull("displayName")
                ?: json.stringOrNull("fileName")
                ?: DEFAULT_DISPLAY_NAME,
            mimeType = json.stringOrNull("mimeType")
                ?: QueueImport.mimeTypeForExtension(extension),
            status = paymentStatusOf(json.stringOrNull("status")),
            createdAt = json.longOrNull("createdAt") ?: json.longOrNull("importedAtMillis") ?: 0L,
            updatedAt = json.longOrNull("updatedAt") ?: 0L,
            failureDetail = json.stringOrNull("failureDetail"),
        )
    }

    /** Maps a stored status name, including the pre-0.4 names, onto the V0.4 model. */
    private fun paymentStatusOf(raw: String?): PaymentStatus {
        if (raw == null) return PaymentStatus.QUEUED
        PaymentStatus.entries.firstOrNull { it.name == raw }?.let { return it }
        return when (raw) {
            "SUCCESS", "PAID", "RECONCILED" -> PaymentStatus.COMPLETED
            "SUBMITTED", "WAITING_CONFIRMATION" -> PaymentStatus.WAITING_USER
            "PAYMENT_FAILED" -> PaymentStatus.FAILED
            "UNKNOWN" -> PaymentStatus.UNKNOWN
            else -> PaymentStatus.QUEUED
        }
    }

    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private fun JSONObject.longOrNull(name: String): Long? =
        if (isNull(name)) null else optLong(name)

    private companion object {
        const val ROOT_DIR = "qrqueue"
        const val IMAGES_DIR = "images"
        const val STATE_FILE = "queue.json"

        /** Bumped from 1: the item model no longer carries QR data. */
        const val SCHEMA_VERSION = 2
        const val DEFAULT_DISPLAY_NAME = "QR image"
        const val DEFAULT_QUEUE_ID = "queue"
    }
}
