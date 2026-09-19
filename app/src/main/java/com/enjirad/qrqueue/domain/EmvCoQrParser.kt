package com.enjirad.qrqueue.domain

import java.util.Locale

/** One EMVCo TLV entry: a two-digit tag, a two-digit length and its value. */
data class TlvEntry(
    val tag: String,
    val value: String,
    /** Index of the tag inside the parsed text, used to locate the CRC block. */
    val startIndex: Int,
)

/** Result of reading a decoded QR payload. */
sealed interface ParseOutcome {
    data class Accepted(val payload: QrPayload) : ParseOutcome
    data class Rejected(val issue: ValidationIssue, val detail: String? = null) : ParseOutcome
}

/**
 * Parser for the EMVCo merchant-presented QR format used by Thai payment QRs:
 * PromptPay credit transfer (tag 29, AID `A000000677010111`) and Thai QR /
 * PromptPay bill payment (tag 30, AID `A000000677010112`).
 *
 * The structure is taken from the EMVCo QR specification as implemented by
 * Thai payment QRs, not invented for this app:
 *
 * - `00` payload format indicator (must be `01`)
 * - `01` point of initiation method (`11` static, `12` dynamic)
 * - `29` PromptPay merchant account: `00` AID, `01` mobile, `02` national/tax
 *   ID, `03` e-Wallet ID
 * - `30` bill payment merchant account: `00` AID, `01` biller ID, `02`/`03`
 *   reference fields
 * - `53` transaction currency (`764` = THB), `54` transaction amount
 * - `58` country code (`TH`), `59` merchant name, `60` merchant city
 * - `62` additional data: `01` bill number, `05` reference label, `07` terminal
 * - `63` CRC-16/CCITT-FALSE over everything before it
 *
 * Nothing here fabricates payment data. A payload that does not match this
 * structure is rejected with a specific [ValidationIssue], never guessed.
 */
object EmvCoQrParser {

    const val PAYLOAD_FORMAT_INDICATOR = "01"

    private const val TAG_PAYLOAD_FORMAT = "00"
    private const val TAG_POINT_OF_INITIATION = "01"
    private const val TAG_PROMPTPAY = "29"
    private const val TAG_BILL_PAYMENT = "30"
    private const val TAG_CURRENCY = "53"
    private const val TAG_AMOUNT = "54"
    private const val TAG_COUNTRY = "58"
    private const val TAG_MERCHANT_NAME = "59"
    private const val TAG_MERCHANT_CITY = "60"
    private const val TAG_ADDITIONAL_DATA = "62"
    private const val TAG_CRC = "63"

    private const val SUB_AID = "00"
    private const val SUB_MOBILE = "01"
    private const val SUB_NATIONAL_ID = "02"
    private const val SUB_EWALLET = "03"
    private const val SUB_BILLER_ID = "01"
    private const val SUB_BILL_REFERENCE_1 = "02"
    private const val SUB_BILL_REFERENCE_2 = "03"

    private const val REF_BILL_NUMBER = "01"
    private const val REF_REFERENCE = "05"
    private const val REF_TERMINAL = "07"

    private const val PROMPTPAY_AID = "A000000677010111"
    private const val BILL_PAYMENT_AID = "A000000677010112"
    private const val CURRENCY_THB = "764"
    private const val COUNTRY_TH = "TH"
    private const val CRC_LENGTH = 4
    private const val MIN_PAYLOAD_LENGTH = 10

    /**
     * Parses a decoded QR payload.
     *
     * @return [ParseOutcome.Accepted] when the payload is a supported Thai
     *   payment QR with verified structure and CRC, otherwise
     *   [ParseOutcome.Rejected] with the reason.
     */
    fun parse(raw: String): ParseOutcome {
        val text = raw.trim()
        if (text.length < MIN_PAYLOAD_LENGTH) {
            return ParseOutcome.Rejected(ValidationIssue.MALFORMED_PAYLOAD, "the payload is too short")
        }
        val entries = parseTlv(text)
            ?: return ParseOutcome.Rejected(
                ValidationIssue.MALFORMED_PAYLOAD,
                "the tag/length structure is invalid",
            )
        val values = entries.associate { entry -> entry.tag to entry.value }

        val formatIndicator = values[TAG_PAYLOAD_FORMAT]
            ?: return ParseOutcome.Rejected(
                ValidationIssue.MALFORMED_PAYLOAD,
                "the payload format indicator is missing",
            )
        if (formatIndicator != PAYLOAD_FORMAT_INDICATOR) {
            return ParseOutcome.Rejected(
                ValidationIssue.UNSUPPORTED_PAYLOAD,
                "payload format $formatIndicator is not supported",
            )
        }

        val crcEntry = entries.lastOrNull { entry -> entry.tag == TAG_CRC }
            ?: return ParseOutcome.Rejected(ValidationIssue.MALFORMED_PAYLOAD, "the CRC tag is missing")
        if (crcEntry.value.length != CRC_LENGTH) {
            return ParseOutcome.Rejected(ValidationIssue.MALFORMED_PAYLOAD, "the CRC field is not 4 characters")
        }
        val expectedCrc = hex4(crc16CcittFalse(text.substring(0, crcEntry.startIndex)))
        if (!crcEntry.value.equals(expectedCrc, ignoreCase = true)) {
            return ParseOutcome.Rejected(
                ValidationIssue.CRC_MISMATCH,
                "CRC is ${crcEntry.value.uppercase(Locale.US)}, expected $expectedCrc",
            )
        }

        val currency = values[TAG_CURRENCY]
        if (currency != null && currency != CURRENCY_THB) {
            return ParseOutcome.Rejected(
                ValidationIssue.UNSUPPORTED_PAYLOAD,
                "currency $currency is not supported",
            )
        }
        val country = values[TAG_COUNTRY]
        if (country != null && country != COUNTRY_TH) {
            return ParseOutcome.Rejected(
                ValidationIssue.UNSUPPORTED_PAYLOAD,
                "country $country is not supported",
            )
        }

        var amountSatang: Long? = null
        val amountText = values[TAG_AMOUNT]
        if (amountText != null) {
            val parsedAmount = parseSatang(amountText)
            if (parsedAmount == null || parsedAmount <= 0L) {
                return ParseOutcome.Rejected(
                    ValidationIssue.MALFORMED_PAYLOAD,
                    "amount \"$amountText\" is not a positive decimal amount",
                )
            }
            amountSatang = parsedAmount
        }

        val promptPayBlock = values[TAG_PROMPTPAY]
        val billPaymentBlock = values[TAG_BILL_PAYMENT]
        if (promptPayBlock == null && billPaymentBlock == null) {
            return ParseOutcome.Rejected(
                ValidationIssue.UNSUPPORTED_PAYLOAD,
                "no supported merchant account tag (29 or 30)",
            )
        }

        val parts = PayloadParts(
            raw = text,
            amountSatang = amountSatang,
            currencyCode = currency,
            countryCode = country,
            merchantName = values[TAG_MERCHANT_NAME]?.takeIf { it.isNotBlank() },
            merchantCity = values[TAG_MERCHANT_CITY]?.takeIf { it.isNotBlank() },
            pointOfInitiationMethod = values[TAG_POINT_OF_INITIATION],
            additional = parseTlv(values[TAG_ADDITIONAL_DATA].orEmpty())
                ?.associate { entry -> entry.tag to entry.value }
                .orEmpty(),
        )

        if (promptPayBlock != null) {
            // The application ID identifies the scheme: a bill-payment block that
            // some issuers place under tag 29 is still treated as bill payment
            // instead of being rejected, while unknown AIDs stay unsupported.
            val promptPayAid = parseTlv(promptPayBlock)
                ?.firstOrNull { entry -> entry.tag == SUB_AID }
                ?.value
            return if (promptPayAid == BILL_PAYMENT_AID) {
                buildBillPayment(promptPayBlock, parts)
            } else {
                buildPromptPay(promptPayBlock, parts)
            }
        }
        val billBlock = billPaymentBlock
            ?: return ParseOutcome.Rejected(
                ValidationIssue.UNSUPPORTED_PAYLOAD,
                "no supported merchant account tag (29 or 30)",
            )
        return buildBillPayment(billBlock, parts)
    }

    /** Splits a TLV text into entries; null when the structure is malformed. */
    fun parseTlv(input: String): List<TlvEntry>? {
        if (input.isEmpty()) return emptyList()
        val entries = mutableListOf<TlvEntry>()
        var index = 0
        while (index < input.length) {
            if (index + 4 > input.length) return null
            val tag = input.substring(index, index + 2)
            if (!tag.all { it.isDigit() }) return null
            val lengthText = input.substring(index + 2, index + 4)
            if (!lengthText.all { it.isDigit() }) return null
            val length = lengthText.toIntOrNull() ?: return null
            val valueStart = index + 4
            val valueEnd = valueStart + length
            if (valueEnd > input.length) return null
            entries += TlvEntry(tag, input.substring(valueStart, valueEnd), index)
            index = valueEnd
        }
        return entries
    }

    /**
     * CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF) — the checksum EMVCo QR
     * payloads carry in tag 63.
     */
    fun crc16CcittFalse(input: String): Int {
        var crc = 0xFFFF
        for (character in input) {
            crc = crc xor ((character.code and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }

    /** Normalises an EMVCo mobile value (`0066…`, `66…`, `0…`) to `0812345678`. */
    fun normalizeMobile(value: String): String? {
        val digits = value.filter { it.isDigit() }
        val local = when {
            digits.startsWith("0066") -> "0" + digits.removePrefix("0066")
            digits.startsWith("66") && digits.length == 11 -> "0" + digits.removePrefix("66")
            digits.length == 9 -> "0$digits"
            else -> digits
        }
        return local.takeIf { it.length == 10 && it.startsWith("0") }
    }

    /** Formats a 10-digit Thai mobile number as `081-234-5678`. */
    fun formatMobile(value: String): String? {
        val local = normalizeMobile(value) ?: return null
        return "${local.substring(0, 3)}-${local.substring(3, 6)}-${local.substring(6)}"
    }

    /** Formats a 13-digit Thai national / tax ID as `1-2345-67890-12-3`. */
    fun formatNationalId(value: String): String {
        val digits = value.filter { it.isDigit() }
        if (digits.length != 13) return digits.ifEmpty { value }
        return buildString {
            append(digits, 0, 1)
            append('-')
            append(digits, 1, 5)
            append('-')
            append(digits, 5, 10)
            append('-')
            append(digits, 10, 12)
            append('-')
            append(digits, 12, 13)
        }
    }

    private fun buildPromptPay(accountBlock: String, parts: PayloadParts): ParseOutcome {
        val subValues = parseTlv(accountBlock)
            ?.associate { entry -> entry.tag to entry.value }
            ?: return ParseOutcome.Rejected(
                ValidationIssue.MALFORMED_PAYLOAD,
                "the PromptPay account block is malformed",
            )
        val aid = subValues[SUB_AID]
        if (aid != PROMPTPAY_AID) {
            return ParseOutcome.Rejected(
                ValidationIssue.UNSUPPORTED_PAYLOAD,
                "unsupported application id ${aid ?: "(missing)"}",
            )
        }
        val mobile = subValues[SUB_MOBILE]?.takeIf { it.isNotBlank() }
        val nationalId = subValues[SUB_NATIONAL_ID]?.takeIf { it.isNotBlank() }
        val eWallet = subValues[SUB_EWALLET]?.takeIf { it.isNotBlank() }
        val reference = parts.additional[REF_REFERENCE]
            ?: parts.additional[REF_BILL_NUMBER]
            ?: parts.additional[REF_TERMINAL]
        return when {
            mobile != null -> parts.accepted(
                format = QrFormat.PROMPTPAY,
                recipientKind = RecipientKind.MOBILE_NUMBER,
                recipient = formatMobile(mobile) ?: mobile,
                reference = reference,
            )
            nationalId != null -> parts.accepted(
                format = QrFormat.PROMPTPAY,
                recipientKind = RecipientKind.NATIONAL_ID,
                recipient = formatNationalId(nationalId),
                reference = reference,
            )
            eWallet != null -> parts.accepted(
                format = QrFormat.PROMPTPAY,
                recipientKind = RecipientKind.EWALLET,
                recipient = eWallet,
                reference = reference,
            )
            parts.merchantName != null -> parts.accepted(
                format = QrFormat.PROMPTPAY,
                recipientKind = RecipientKind.MERCHANT,
                recipient = parts.merchantName,
                reference = reference,
            )
            else -> ParseOutcome.Rejected(
                ValidationIssue.MISSING_PAYMENT_INFO,
                "the PromptPay account has no number, ID or merchant name",
            )
        }
    }

    private fun buildBillPayment(accountBlock: String, parts: PayloadParts): ParseOutcome {
        val subValues = parseTlv(accountBlock)
            ?.associate { entry -> entry.tag to entry.value }
            ?: return ParseOutcome.Rejected(
                ValidationIssue.MALFORMED_PAYLOAD,
                "the bill payment account block is malformed",
            )
        val aid = subValues[SUB_AID]
        if (aid != BILL_PAYMENT_AID) {
            return ParseOutcome.Rejected(
                ValidationIssue.UNSUPPORTED_PAYLOAD,
                "unsupported application id ${aid ?: "(missing)"}",
            )
        }
        val billerId = subValues[SUB_BILLER_ID]?.takeIf { it.isNotBlank() }
        val merchantName = parts.merchantName
        if (billerId == null && merchantName == null) {
            return ParseOutcome.Rejected(
                ValidationIssue.MISSING_PAYMENT_INFO,
                "the bill payment block has no biller reference",
            )
        }
        val billReferences = listOfNotNull(
            subValues[SUB_BILL_REFERENCE_1]?.takeIf { it.isNotBlank() },
            subValues[SUB_BILL_REFERENCE_2]?.takeIf { it.isNotBlank() },
        )
        val reference = billReferences.takeIf { it.isNotEmpty() }?.joinToString(" ")
            ?: parts.additional[REF_REFERENCE]
            ?: parts.additional[REF_BILL_NUMBER]
        return parts.accepted(
            format = QrFormat.BILL_PAYMENT,
            recipientKind = RecipientKind.BILLER,
            recipient = merchantName ?: formatNationalId(billerId.orEmpty()),
            reference = reference,
        )
    }

    private fun hex4(value: Int): String = value.toString(16).padStart(4, '0').uppercase(Locale.US)

    /** Payload-level fields shared by every supported merchant account type. */
    private data class PayloadParts(
        val raw: String,
        val amountSatang: Long?,
        val currencyCode: String?,
        val countryCode: String?,
        val merchantName: String?,
        val merchantCity: String?,
        val pointOfInitiationMethod: String?,
        val additional: Map<String, String>,
    ) {
        fun accepted(
            format: QrFormat,
            recipientKind: RecipientKind,
            recipient: String,
            reference: String?,
        ): ParseOutcome = ParseOutcome.Accepted(
            QrPayload(
                raw = raw,
                format = format,
                recipientKind = recipientKind,
                recipient = recipient,
                merchantName = merchantName,
                merchantCity = merchantCity,
                amountSatang = amountSatang,
                currencyCode = currencyCode,
                countryCode = countryCode,
                reference = reference,
                pointOfInitiationMethod = pointOfInitiationMethod,
            ),
        )
    }
}
