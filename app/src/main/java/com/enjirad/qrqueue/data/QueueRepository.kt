package com.enjirad.qrqueue.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.enjirad.qrqueue.domain.BankInfo
import com.enjirad.qrqueue.domain.PaymentAttempt
import com.enjirad.qrqueue.domain.PaymentAttemptResult
import com.enjirad.qrqueue.domain.PaymentQueue
import com.enjirad.qrqueue.domain.PaymentStatus
import com.enjirad.qrqueue.domain.QrVersion
import com.enjirad.qrqueue.domain.QrVersionStatus
import com.enjirad.qrqueue.domain.QueueImport
import com.enjirad.qrqueue.domain.QueueItem
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** One image copied into app-private storage. */
data class StoredImage(
    val file: File,
    /** Original name from the picker, for display only. */
    val displayName: String,
    val mimeType: String,
    /** Deterministic content hash of the copied bytes, or null if unavailable. */
    val fingerprint: String? = null,
)

/**
 * The single owner of everything this app writes to disk:
 *
 * ```
 * filesDir/qrqueue/queue.json            payment items, QR versions, attempts
 * filesDir/qrqueue/images/<key>.<ext>    imported copies of the selected images
 * ```
 *
 * Importing copies the picked image into the app; the gallery original is never
 * moved, renamed or deleted. A queue is self-contained: clearing it removes only
 * these app-private files.
 *
 * V0.5 stores no QR content at all — only image locations, queue order, QR
 * versions and where each item stands in the hand-off flow.
 */
class QueueRepository(
    private val context: Context,
    private val bankSelection: BankSelectionStore = SharedPreferencesBankSelectionStore(context),
) {

    // ---- images -------------------------------------------------------------

    /**
     * Copies a picked image into app-private storage and fingerprints it.
     *
     * @param fileKey the storage key for the copy (an item id for an import, a
     *   version id for a replacement).
     * @return the stored copy, or null when the image could not be read or the
     *   copy could not be written. The caller reports that honestly and does not
     *   create a record for a file that does not exist.
     */
    fun importImage(uri: Uri, fileKey: String): StoredImage? {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri)
        val declaredMimeType = runCatching { resolver.getType(uri) }.getOrNull()
        val extension = QueueImport.fileExtensionFor(declaredMimeType, displayName)
        val mimeType = declaredMimeType ?: QueueImport.mimeTypeForExtension(extension)
        val directory = imageDirectory()
        if (!directory.isDirectory && !directory.mkdirs()) return null
        val target = File(directory, "$fileKey.$extension")

        val stream = runCatching { resolver.openInputStream(uri) }.getOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        val copied = runCatching {
            stream.use { input ->
                target.outputStream().use { fileOutput ->
                    val digestOutput = DigestOutputStream(fileOutput, digest)
                    input.copyTo(digestOutput)
                    digestOutput.flush()
                }
            }
        }.isSuccess
        if (!copied || target.length() <= 0L) {
            target.delete()
            return null
        }
        return StoredImage(
            file = target,
            displayName = displayName ?: DEFAULT_DISPLAY_NAME,
            mimeType = mimeType,
            fingerprint = QueueImport.fingerprintHex(digest.digest()),
        )
    }

    /** Resolves the stored copy of an image, when it still exists. */
    fun storedFile(path: String?): File? = path?.let { File(it) }?.takeIf { it.isFile }

    /** Deletes specific stored image copies (never anything outside them). */
    fun deleteImages(paths: List<String>): Int =
        paths.count { path -> storedFile(path)?.delete() == true }

    /**
     * Deletes image copies that no QR version references — for example after an
     * import was interrupted before the queue state was written, or a replacement
     * image that could not be recorded. The original gallery images are never
     * involved: only this app's own image directory is inspected.
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
     * An item whose current QR image no longer exists is marked
     * [PaymentStatus.FAILED] instead of crashing the app or pretending the item
     * can be shared, so the queue screen can explain what happened.
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

    /** An open item whose current QR file is gone can never be shared. */
    private fun markMissingImage(item: QueueItem): QueueItem {
        if (item.status.isCompleted) return item
        // An item waiting for a replacement QR has no current version: leave it
        // exactly as it is, and let the user pick a new image.
        val version = item.currentVersion ?: return item
        if (storedFile(version.filePath) != null) return item
        return item.copy(
            status = PaymentStatus.FAILED,
            failureDetail = MISSING_IMAGE_DETAIL,
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

    // ---- selected bank ------------------------------------------------------

    /**
     * Persists the user's chosen bank so it survives every form of process
     * death. Delegates to [BankSelectionStore]; the encode/decode rules live in
     * [BankSelectionCodec] so they are unit-tested without Android.
     */
    fun saveSelectedBank(bank: BankInfo): Boolean = bankSelection.save(bank)

    /**
     * Restores the previously selected bank, or null when nothing was saved yet
     * (first install). A stored value no longer in the bank registry decodes to
     * null so the user picks again.
     */
    fun loadSelectedBank(): BankInfo? = bankSelection.load()

    fun clearSelectedBank() = bankSelection.clear()

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
        put(
            "items",
            JSONArray().apply {
                queue.items.sortedBy { item -> item.position }.forEach { put(serializeItem(it)) }
            },
        )
    }

    private fun serializeItem(item: QueueItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("position", item.position)
        put("status", item.status.name)
        put("createdAt", item.createdAt)
        put("updatedAt", item.updatedAt)
        putNullable("completedAt", item.completedAt)
        putNullable("lastAttemptAt", item.lastAttemptAt)
        putNullable("failureDetail", item.failureDetail)
        put(
            "versions",
            JSONArray().apply { item.versions.forEach { put(serializeVersion(it)) } },
        )
        put(
            "attempts",
            JSONArray().apply { item.attempts.forEach { put(serializeAttempt(it)) } },
        )
    }

    private fun serializeVersion(version: QrVersion): JSONObject = JSONObject().apply {
        put("qrVersionId", version.id)
        put("paymentItemId", version.paymentItemId)
        put("filePath", version.filePath)
        put("createdAt", version.createdAt)
        put("versionNumber", version.versionNumber)
        put("status", version.status.name)
        put("isCurrent", version.isCurrent)
        put("mimeType", version.mimeType)
        put("displayName", version.displayName)
        put("sourceUri", version.sourceUri)
        putNullable("fingerprint", version.fingerprint)
    }

    private fun serializeAttempt(attempt: PaymentAttempt): JSONObject = JSONObject().apply {
        put("attemptId", attempt.id)
        put("paymentItemId", attempt.paymentItemId)
        put("qrVersionId", attempt.qrVersionId)
        put("startedAt", attempt.startedAt)
        put("result", attempt.result.name)
        putNullable("reason", attempt.reason)
    }

    private fun parseQueue(json: JSONObject): PaymentQueue {
        val itemsJson = json.optJSONArray("items") ?: JSONArray()
        val items = (0 until itemsJson.length()).mapNotNull { index ->
            itemsJson.optJSONObject(index)?.let { parseItem(it, index) }
        }
        return PaymentQueue(
            queueId = json.stringOrNull("queueId") ?: DEFAULT_QUEUE_ID,
            createdAt = json.optLong("createdAt"),
            items = items.sortedBy { item -> item.position },
            updatedAtMillis = json.optLong("updatedAtMillis", 0L),
        )
    }

    /**
     * Reads one Payment Item.
     *
     * Schema 3 stores QR versions and attempts explicitly. Older queues stored a
     * single image per item (`storedImagePath`, `displayName`, `mimeType`) and
     * the 0.3.x schema used `fileName` / `importedAtMillis`; those are still
     * accepted and become the item's v1 QR version, so an existing queue opens
     * unchanged. Removed QR-only fields (payload, amount, recipient, issue) are
     * simply no longer read.
     */
    private fun parseItem(json: JSONObject, fallbackPosition: Int): QueueItem? {
        val id = json.stringOrNull("id") ?: return null
        val createdAt = json.longOrNull("createdAt") ?: json.longOrNull("importedAtMillis") ?: 0L
        val legacyPath = json.stringOrNull("storedImagePath").orEmpty()
        val legacyName = json.stringOrNull("displayName")
            ?: json.stringOrNull("fileName")
            ?: DEFAULT_DISPLAY_NAME
        val legacyExtension = legacyPath.substringAfterLast('.', "")
        val legacyMime = json.stringOrNull("mimeType")
            ?: QueueImport.mimeTypeForExtension(legacyExtension)

        val versionsJson = json.optJSONArray("versions")
        val versions = if (versionsJson != null && versionsJson.length() > 0) {
            (0 until versionsJson.length()).mapNotNull { index ->
                versionsJson.optJSONObject(index)?.let { parseVersion(it, id, index) }
            }
        } else if (legacyPath.isNotEmpty()) {
            listOf(
                QrVersion(
                    id = QueueImport.newVersionId(),
                    paymentItemId = id,
                    filePath = legacyPath,
                    createdAt = createdAt,
                    versionNumber = QueueImport.FIRST_VERSION_NUMBER,
                    status = QrVersionStatus.CURRENT,
                    mimeType = legacyMime,
                    displayName = legacyName,
                    sourceUri = json.stringOrNull("sourceUri").orEmpty(),
                ),
            )
        } else {
            emptyList()
        }

        val attemptsJson = json.optJSONArray("attempts")
        val attempts = if (attemptsJson == null) {
            emptyList()
        } else {
            (0 until attemptsJson.length()).mapNotNull { index ->
                attemptsJson.optJSONObject(index)?.let { parseAttempt(it, id) }
            }
        }

        return QueueItem(
            id = id,
            position = json.optInt("position", fallbackPosition),
            status = paymentStatusOf(json.stringOrNull("status")),
            createdAt = createdAt,
            updatedAt = json.longOrNull("updatedAt") ?: 0L,
            completedAt = json.longOrNull("completedAt"),
            lastAttemptAt = json.longOrNull("lastAttemptAt"),
            failureDetail = json.stringOrNull("failureDetail"),
            versions = versions,
            attempts = attempts,
        ).withNormalizedVersions()
    }

    private fun parseVersion(json: JSONObject, itemId: String, fallbackNumber: Int): QrVersion? {
        val filePath = json.stringOrNull("filePath") ?: return null
        return QrVersion(
            id = json.stringOrNull("qrVersionId") ?: QueueImport.newVersionId(),
            paymentItemId = json.stringOrNull("paymentItemId") ?: itemId,
            filePath = filePath,
            createdAt = json.longOrNull("createdAt") ?: 0L,
            versionNumber = json.optInt("versionNumber", fallbackNumber + 1),
            status = qrVersionStatusOf(json.stringOrNull("status"), json.optBoolean("isCurrent", false)),
            mimeType = json.stringOrNull("mimeType") ?: QueueImport.mimeTypeForExtension(
                filePath.substringAfterLast('.', ""),
            ),
            displayName = json.stringOrNull("displayName") ?: DEFAULT_DISPLAY_NAME,
            sourceUri = json.stringOrNull("sourceUri").orEmpty(),
            fingerprint = json.stringOrNull("fingerprint"),
        )
    }

    private fun parseAttempt(json: JSONObject, itemId: String): PaymentAttempt? {
        val result = json.stringOrNull("result") ?: return null
        return PaymentAttempt(
            id = json.stringOrNull("attemptId") ?: QueueImport.newVersionId(),
            paymentItemId = json.stringOrNull("paymentItemId") ?: itemId,
            qrVersionId = json.stringOrNull("qrVersionId").orEmpty(),
            startedAt = json.longOrNull("startedAt") ?: 0L,
            result = PaymentAttemptResult.entries.firstOrNull { it.name == result }
                ?: PaymentAttemptResult.UNKNOWN,
            reason = json.stringOrNull("reason"),
        )
    }

    private fun qrVersionStatusOf(raw: String?, isCurrent: Boolean): QrVersionStatus {
        val known = QrVersionStatus.entries.firstOrNull { it.name == raw }
        if (known != null) return known
        return if (isCurrent) QrVersionStatus.CURRENT else QrVersionStatus.SUPERSEDED
    }

    /** Maps a stored status name, including the pre-0.5 names, onto the V0.5 model. */
    private fun paymentStatusOf(raw: String?): PaymentStatus {
        if (raw == null) return PaymentStatus.READY
        PaymentStatus.entries.firstOrNull { it.name == raw }?.let { return it }
        return when (raw) {
            "QUEUED" -> PaymentStatus.READY
            "WAITING_USER" -> PaymentStatus.AWAITING_USER_CONFIRMATION
            "SUCCESS", "PAID", "RECONCILED" -> PaymentStatus.COMPLETED
            "SUBMITTED", "WAITING_CONFIRMATION" -> PaymentStatus.AWAITING_USER_CONFIRMATION
            "PAYMENT_FAILED" -> PaymentStatus.FAILED
            "UNKNOWN" -> PaymentStatus.UNKNOWN
            else -> PaymentStatus.READY
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

        /** Bumped from 2: items now carry QR versions and payment attempts. */
        const val SCHEMA_VERSION = 3
        const val DEFAULT_DISPLAY_NAME = "QR image"
        const val DEFAULT_QUEUE_ID = "queue"
        const val MISSING_IMAGE_DETAIL = "The imported image file is missing from this device."
    }
}
