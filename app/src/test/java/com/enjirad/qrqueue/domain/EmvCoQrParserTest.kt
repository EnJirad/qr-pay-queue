package com.enjirad.qrqueue.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These fixtures are real EMVCo merchant-presented QR payloads (PromptPay credit
 * transfer and PromptPay bill payment), generated with an independent CRC-16/
 * CCITT-FALSE implementation. They are not invented formats: the tags, the
 * AIDs and the CRC layout are the ones Thai payment QRs actually use.
 */
class EmvCoQrParserTest {

    private val promptPayDynamicWithAmount =
        "00020101021229370016A0000006770101110113006681234567853037645406750.005802TH5912SOMCHAI SHOP6007Bangkok62160512INV-2026-00163049E28"

    private val promptPayStaticWithoutAmount =
        "00020101021129370016A0000006770101110113006681234567853037645802TH5912SOMCHAI SHOP63046455"

    private val billPaymentWithAmount =
        "00020101021229530016A000000677010112011312345678901230212REF123456789530376454071250.505802TH5923METROPOLITAN WATERWORKS6007Bangkok6304544C"

    private val promptPayNationalId =
        "00020101021229370016A0000006770101110213123456789012153037645406320.005802TH630458FD"

    private val promptPayEWallet =
        "00020101021229390016A00000067701011103150123456789012345303764540599.255802TH63043B68"

    private val unsupportedApplicationId =
        "00020101021229370016A000000123456789011300668123456785303764540550.005802TH63043311"

    @Test
    fun parsesPromptPayWithAmountRecipientAndReference() {
        val outcome = EmvCoQrParser.parse(promptPayDynamicWithAmount)
        assertTrue(outcome is ParseOutcome.Accepted)
        val payload = (outcome as ParseOutcome.Accepted).payload

        assertEquals(QrFormat.PROMPTPAY, payload.format)
        assertEquals(RecipientKind.MOBILE_NUMBER, payload.recipientKind)
        assertEquals("081-234-5678", payload.recipient)
        assertEquals(75_000L, payload.amountSatang)
        assertEquals("SOMCHAI SHOP", payload.merchantName)
        assertEquals("Bangkok", payload.merchantCity)
        assertEquals("764", payload.currencyCode)
        assertEquals("TH", payload.countryCode)
        assertEquals("INV-2026-001", payload.reference)
        assertTrue(payload.isDynamic)
        assertEquals(promptPayDynamicWithAmount, payload.raw)
    }

    @Test
    fun staticQrIsValidButCarriesNoAmount() {
        val outcome = EmvCoQrParser.parse(promptPayStaticWithoutAmount)
        assertTrue(outcome is ParseOutcome.Accepted)
        val payload = (outcome as ParseOutcome.Accepted).payload

        // A static PromptPay QR has no amount. The parser must not invent one.
        assertNull(payload.amountSatang)
        assertTrue(!payload.isDynamic)
        assertEquals("081-234-5678", payload.recipient)
    }

    @Test
    fun parsesBillPayment() {
        val outcome = EmvCoQrParser.parse(billPaymentWithAmount)
        assertTrue(outcome is ParseOutcome.Accepted)
        val payload = (outcome as ParseOutcome.Accepted).payload

        assertEquals(QrFormat.BILL_PAYMENT, payload.format)
        assertEquals(RecipientKind.BILLER, payload.recipientKind)
        assertEquals("METROPOLITAN WATERWORKS", payload.recipient)
        assertEquals("REF123456789", payload.reference)
        assertEquals(125_050L, payload.amountSatang)
    }

    @Test
    fun parsesNationalIdRecipient() {
        val outcome = EmvCoQrParser.parse(promptPayNationalId)
        assertTrue(outcome is ParseOutcome.Accepted)
        val payload = (outcome as ParseOutcome.Accepted).payload

        assertEquals(RecipientKind.NATIONAL_ID, payload.recipientKind)
        assertEquals("1-2345-67890-12-1", payload.recipient)
        assertEquals(32_000L, payload.amountSatang)
    }

    @Test
    fun parsesEWalletRecipient() {
        val outcome = EmvCoQrParser.parse(promptPayEWallet)
        assertTrue(outcome is ParseOutcome.Accepted)
        val payload = (outcome as ParseOutcome.Accepted).payload

        assertEquals(RecipientKind.EWALLET, payload.recipientKind)
        assertEquals("012345678901234", payload.recipient)
        assertEquals(9_925L, payload.amountSatang)
    }

    @Test
    fun rejectsPayloadWhoseCrcDoesNotMatch() {
        val corrupted = promptPayDynamicWithAmount.dropLast(4) + "ABCD"
        val outcome = EmvCoQrParser.parse(corrupted)
        assertTrue(outcome is ParseOutcome.Rejected)
        assertEquals(ValidationIssue.CRC_MISMATCH, (outcome as ParseOutcome.Rejected).issue)
    }

    @Test
    fun rejectsUnsupportedApplicationId() {
        val outcome = EmvCoQrParser.parse(unsupportedApplicationId)
        assertTrue(outcome is ParseOutcome.Rejected)
        assertEquals(ValidationIssue.UNSUPPORTED_PAYLOAD, (outcome as ParseOutcome.Rejected).issue)
    }

    @Test
    fun rejectsUnsupportedCurrency() {
        val outcome = EmvCoQrParser.parse(
            buildPayload(
                tlv("00", "01"),
                tlv("01", "12"),
                promptPayTag("A000000677010111", tlv("01", "0066812345678")),
                tlv("53", "840"),
                tlv("54", "10.00"),
                tlv("58", "US"),
            ),
        )
        assertTrue(outcome is ParseOutcome.Rejected)
        assertEquals(ValidationIssue.UNSUPPORTED_PAYLOAD, (outcome as ParseOutcome.Rejected).issue)
    }

    @Test
    fun rejectsPayloadWithoutPromptPayOrBillPaymentAccount() {
        val outcome = EmvCoQrParser.parse(
            buildPayload(
                tlv("00", "01"),
                tlv("01", "12"),
                tlv("53", "764"),
                tlv("54", "10.00"),
                tlv("58", "TH"),
                tlv("59", "SOME SHOP"),
            ),
        )
        assertTrue(outcome is ParseOutcome.Rejected)
        assertEquals(ValidationIssue.UNSUPPORTED_PAYLOAD, (outcome as ParseOutcome.Rejected).issue)
    }

    @Test
    fun rejectsCredentialsWithoutAnyRecipientInformation() {
        val outcome = EmvCoQrParser.parse(
            buildPayload(
                tlv("00", "01"),
                tlv("01", "12"),
                promptPayTag("A000000677010111", ""),
                tlv("53", "764"),
                tlv("54", "10.00"),
                tlv("58", "TH"),
            ),
        )
        assertTrue(outcome is ParseOutcome.Rejected)
        assertEquals(ValidationIssue.MISSING_PAYMENT_INFO, (outcome as ParseOutcome.Rejected).issue)
    }

    @Test
    fun rejectsMalformedStructureAndNonPaymentQr() {
        assertEquals(
            ValidationIssue.MALFORMED_PAYLOAD,
            (EmvCoQrParser.parse("0002010102") as ParseOutcome.Rejected).issue,
        )
        assertEquals(
            ValidationIssue.MALFORMED_PAYLOAD,
            (EmvCoQrParser.parse("https://example.com/pay") as ParseOutcome.Rejected).issue,
        )
        assertEquals(
            ValidationIssue.MALFORMED_PAYLOAD,
            (EmvCoQrParser.parse("1234567890123") as ParseOutcome.Rejected).issue,
        )
    }

    @Test
    fun rejectsMissingPayloadFormatIndicator() {
        val outcome = EmvCoQrParser.parse(buildPayload(tlv("01", "12"), tlv("53", "764"), tlv("58", "TH")))
        assertTrue(outcome is ParseOutcome.Rejected)
        assertEquals(ValidationIssue.MALFORMED_PAYLOAD, (outcome as ParseOutcome.Rejected).issue)
    }

    @Test
    fun rejectsZeroAndInvalidAmounts() {
        assertEquals(
            ValidationIssue.MALFORMED_PAYLOAD,
            (EmvCoQrParser.parse(
                buildPayload(
                    tlv("00", "01"),
                    tlv("01", "12"),
                    promptPayTag("A000000677010111", tlv("01", "0066812345678")),
                    tlv("53", "764"),
                    tlv("54", "0.00"),
                    tlv("58", "TH"),
                ),
            ) as ParseOutcome.Rejected).issue,
        )
        assertEquals(
            ValidationIssue.MALFORMED_PAYLOAD,
            (EmvCoQrParser.parse(
                buildPayload(
                    tlv("00", "01"),
                    tlv("01", "12"),
                    promptPayTag("A000000677010111", tlv("01", "0066812345678")),
                    tlv("53", "764"),
                    tlv("54", "12.3456"),
                    tlv("58", "TH"),
                ),
            ) as ParseOutcome.Rejected).issue,
        )
    }

    @Test
    fun crcMatchesTheStandardCheckValue() {
        // CRC-16/CCITT-FALSE reference value for "123456789".
        assertEquals(0x29B1, EmvCoQrParser.crc16CcittFalse("123456789"))
    }

    @Test
    fun normalisesThaiMobileNumbers() {
        assertEquals("0812345678", EmvCoQrParser.normalizeMobile("0066812345678"))
        assertEquals("0812345678", EmvCoQrParser.normalizeMobile("66812345678"))
        assertEquals("0812345678", EmvCoQrParser.normalizeMobile("0812345678"))
        assertEquals("0812345678", EmvCoQrParser.normalizeMobile("812345678"))
        assertNull(EmvCoQrParser.normalizeMobile("1234"))
        assertEquals("081-234-5678", EmvCoQrParser.formatMobile("0066812345678"))
    }

    @Test
    fun formatsThaiNationalId() {
        assertEquals("1-2345-67890-12-1", EmvCoQrParser.formatNationalId("1234567890121"))
        assertEquals("12345", EmvCoQrParser.formatNationalId("12345"))
    }

    @Test
    fun parsesTlvEntriesAndRejectsBrokenOnes() {
        val entries = EmvCoQrParser.parseTlv("0002010102TH")
        assertNotNull(entries)
        assertEquals(listOf("00", "01"), entries!!.map { it.tag })
        assertEquals(listOf("01", "TH"), entries.map { it.value })
        assertNull(EmvCoQrParser.parseTlv("0002010102"))
        assertNull(EmvCoQrParser.parseTlv("0A0201"))
    }

    private fun tlv(tag: String, value: String): String =
        tag + value.length.toString().padStart(2, '0') + value

    private fun promptPayTag(applicationId: String, extra: String): String =
        tlv("29", tlv("00", applicationId) + extra)

    /** Builds a payload with a correct CRC so other rules can be isolated. */
    private fun buildPayload(vararg parts: String): String {
        val body = parts.joinToString("")
        val crc = String.format(Locale.US, "%04X", EmvCoQrParser.crc16CcittFalse(body))
        return body + "6304" + crc
    }
}
