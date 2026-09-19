package com.enjirad.qrqueue.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.enjirad.qrqueue.domain.PaymentQueue
import com.enjirad.qrqueue.domain.PaymentStatus
import com.enjirad.qrqueue.domain.QueueImport
import com.enjirad.qrqueue.domain.QueueItem
import com.enjirad.qrqueue.domain.ValidationIssue
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
 * filesDir/qrqueue/queue.json          the queue state (id, items, statuses)
 * filesDir/qrqueue/images/<itemId>.png imported copies of the selected images
 * ```
 *
 * Importing copies the picked image into the app; the gallery original is never
 * moved, renamed or deleted. A queue is self-contained: clearing it removes only
 * these app-private files.
 */
class QueueRepository(private val context: Context) {

    // ---- images -------------------------------------------------------------

    /**
     * Copies a picked image into app-private storage.
     *
     * @return the stored copy, or null when the image could not be read or the
     *   copy could not be written (the caller records that honestly).
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

    /** Reads the persisted queue, or null when there is none / it is unreadable. */
    fun loadQueue(): PaymentQueue? {
        val file = stateFile()
        if (!file.isFile) return null
        return runCatching { parseQueue(JSONObject(file.readText())) }.getOrNull()
    }

    /**
     * Persists the queue state, so recreation or backgrounding cannot lose it.
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
        put("currentIndex", queue.currentIndex)
        put("started", queue.started)
        put("finished", queue.finished)
        put("updatedAtMillis", queue.updatedAtMillis)
        put("items", JSONArray().apply { queue.items.forEach { put(serializeItem(it)) } })
    }

    private fun serializeItem(item: QueueItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("fileName", item.fileName)
        put("status", item.status.name)
        putNullable("sourceUri", item.sourceUri)
        putNullable("storedImagePath", item.storedImagePath)
        putNullable("mimeType", item.mimeType)
        putNullable("amountSatang", item.amountSatang)
        putNullable("recipient", item.recipient)
        putNullable("reference", item.reference)
        putNullable("issue", item.issue?.name)
        putNullable("issueDetail", item.issueDetail)
        putNullable("payloadLabel", item.payloadLabel)
        putNullable("rawPayload", item.rawPayload)
        putNullable("importedAtMillis", item.importedAtMillis)
        putNullable("decidedAtMillis", item.decidedAtMillis)
    }

    private fun parseQueue(json: JSONObject): PaymentQueue {
        val itemsJson = json.optJSONArray("items") ?: JSONArray()
        val items = (0 until itemsJson.length()).mapNotNull { index ->
            itemsJson.optJSONObject(index)?.let { parseItem(it) }
        }
        return PaymentQueue(
            queueId = json.optString("queueId"),
            createdAt = json.optLong("createdAt"),
            items = items,
            currentIndex = json.optInt("currentIndex", PaymentQueue.NO_CURRENT_ITEM),
            started = json.optBoolean("started", false),
            finished = json.optBoolean("finished", false),
            updatedAtMillis = json.optLong("updatedAtMillis", 0L),
        )
    }

    private fun parseItem(json: JSONObject): QueueItem? {
        val id = json.stringOrNull("id") ?: return null
        return QueueItem(
            id = id,
            fileName = json.stringOrNull("fileName") ?: DEFAULT_DISPLAY_NAME,
            sourceUri = json.stringOrNull("sourceUri"),
            storedImagePath = json.stringOrNull("storedImagePath"),
            mimeType = json.stringOrNull("mimeType"),
            amountSatang = json.longOrNull("amountSatang"),
            recipient = json.stringOrNull("recipient"),
            reference = json.stringOrNull("reference"),
            status = paymentStatusOf(json.stringOrNull("status")),
            issue = validationIssueOf(json.stringOrNull("issue")),
            issueDetail = json.stringOrNull("issueDetail"),
            payloadLabel = json.stringOrNull("payloadLabel"),
            rawPayload = json.stringOrNull("rawPayload"),
            importedAtMillis = json.longOrNull("importedAtMillis"),
            decidedAtMillis = json.longOrNull("decidedAtMillis"),
        )
    }

    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private fun JSONObject.longOrNull(name: String): Long? =
        if (isNull(name)) null else optLong(name)

    private fun paymentStatusOf(raw: String?): PaymentStatus =
        PaymentStatus.entries.firstOrNull { it.name == raw } ?: PaymentStatus.DISCOVERED

    private fun validationIssueOf(raw: String?): ValidationIssue? =
        raw?.let { name -> ValidationIssue.entries.firstOrNull { it.name == name } }

    private companion object {
        const val ROOT_DIR = "qrqueue"
        const val IMAGES_DIR = "images"
        const val STATE_FILE = "queue.json"
        const val SCHEMA_VERSION = 1
        const val DEFAULT_DISPLAY_NAME = "QR image"
    }
}
