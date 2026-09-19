package com.enjirad.qrqueue.domain

/**
 * Why an imported image did not become a payable queue item.
 *
 * Every issue maps onto a status from [PaymentStatus] so the queue UI can show
 * one honest status chip plus a specific, human-readable reason. The mapping is
 * deliberately explicit: a QR that cannot be read is never quietly dropped and
 * never silently treated as payable.
 */
enum class ValidationIssue(val label: String, val status: PaymentStatus) {
    UNREADABLE_IMAGE("This image could not be read", PaymentStatus.INVALID),
    QR_NOT_FOUND("No QR code could be read from this image", PaymentStatus.INVALID),
    MALFORMED_PAYLOAD("The QR data is malformed", PaymentStatus.INVALID),
    CRC_MISMATCH("The QR checksum is invalid", PaymentStatus.INVALID),
    UNSUPPORTED_PAYLOAD("This QR format is not supported", PaymentStatus.INVALID),
    MISSING_PAYMENT_INFO("The QR has no payment information", PaymentStatus.INVALID),
    DUPLICATE_PAYLOAD("Duplicate of another QR in this queue", PaymentStatus.DUPLICATE),

    /**
     * More than one QR code in the same image. The app never guesses which code
     * the user meant, because paying the wrong one is worse than skipping it.
     */
    MULTIPLE_QR_CODES("This image contains more than one QR code", PaymentStatus.INVALID),
    ;
}
