package com.enjirad.qrqueue.domain

/**
 * Shared fixtures for the domain tests: one QR version, one Payment Item and one
 * queue, built the same way everywhere so a test can focus on the rule it checks.
 */
internal fun testVersion(
    id: String,
    paymentItemId: String,
    versionNumber: Int = 1,
    status: QrVersionStatus = QrVersionStatus.CURRENT,
    path: String = "/data/user/0/com.enjirad.qrqueue/files/qrqueue/images/$id.png",
    fingerprint: String? = null,
): QrVersion = QrVersion(
    id = id,
    paymentItemId = paymentItemId,
    filePath = path,
    createdAt = 0L,
    versionNumber = versionNumber,
    status = status,
    mimeType = "image/png",
    displayName = "$id.png",
    sourceUri = "content://media/$id",
    fingerprint = fingerprint,
)

internal fun testItem(
    id: String,
    position: Int,
    status: PaymentStatus = PaymentStatus.READY,
    versions: List<QrVersion> = listOf(testVersion("$id-v1", id)),
): QueueItem = QueueItem(
    id = id,
    position = position,
    status = status,
    createdAt = 0L,
    updatedAt = 0L,
    versions = versions,
)

internal fun testQueue(vararg items: QueueItem): PaymentQueue =
    PaymentQueue.create(queueId = "queue-1", createdAt = 1_000L, items = items.toList())
