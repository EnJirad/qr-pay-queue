package com.enjirad.qrqueue.domain

/**
 * One payment task in the queue.
 *
 * V0.1 defines the model only: there is no importer yet, so the queue is always
 * empty and no fake or demo transactions are ever created. Fields that QR
 * decoding will fill in later are nullable on purpose.
 *
 * @param amountSatang amount in satang (1 THB = 100 satang) for exact math.
 */
data class QueueItem(
    val id: String,
    val fileName: String,
    val sourceUri: String? = null,
    val amountSatang: Long? = null,
    val recipient: String? = null,
    val reference: String? = null,
    val status: PaymentStatus = PaymentStatus.DISCOVERED,
)
