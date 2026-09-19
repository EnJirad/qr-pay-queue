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
