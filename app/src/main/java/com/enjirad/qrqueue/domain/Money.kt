package com.enjirad.qrqueue.domain

import java.util.Locale
import kotlin.math.abs

/**
 * Formats an amount in satang (1 THB = 100 satang) as `฿1,234.56`.
 *
 * Money is kept as a whole number of satang end-to-end; this is the only place
 * it is turned into display text, so rounding can never drift between screens.
 */
fun formatSatang(satang: Long): String {
    val absolute = abs(satang)
    val digits = String.format(Locale.US, "%,d.%02d", absolute / 100, absolute % 100)
    return if (satang < 0) "-฿$digits" else "฿$digits"
}

/** Digits with at most two decimals, no sign, no separators, no exponent. */
private val AMOUNT_PATTERN = Regex("^(\\d{1,12})(?:\\.(\\d{1,2}))?$")

/**
 * Parses an amount exactly as it appears in a QR payload into satang.
 *
 * Returns null when the text is not a plain decimal amount (for example
 * `-5`, `1e3`, `12.345`, ``, `abc`). Callers must treat null as "this payload
 * does not contain a usable amount" — never invent a number.
 */
fun parseSatang(text: String): Long? {
    val match = AMOUNT_PATTERN.find(text.trim()) ?: return null
    val baht = match.groupValues[1].toLongOrNull() ?: return null
    if (baht > MAX_BAHT) return null
    val minorText = match.groupValues[2].padEnd(2, '0')
    val minor = if (minorText.isEmpty()) 0L else minorText.toLongOrNull() ?: return null
    return baht * 100 + minor
}

private const val MAX_BAHT = 1_000_000_000_000L
