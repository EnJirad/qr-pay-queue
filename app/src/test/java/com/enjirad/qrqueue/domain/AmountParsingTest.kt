package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParsingTest {

    @Test
    fun parsesPlainDecimalAmountsExactly() {
        assertEquals(75_000L, parseSatang("750.00"))
        assertEquals(125_050L, parseSatang("1250.50"))
        assertEquals(75L, parseSatang("0.75"))
        assertEquals(75_050L, parseSatang("750.5"))
        assertEquals(75_000L, parseSatang("750"))
        assertEquals(1L, parseSatang("0.01"))
        assertEquals(75_000L, parseSatang(" 750.00 "))
    }

    @Test
    fun refusesAnythingThatIsNotAPlainAmount() {
        assertNull(parseSatang(""))
        assertNull(parseSatang("   "))
        assertNull(parseSatang("abc"))
        assertNull(parseSatang("-5.00"))
        assertNull(parseSatang("+5.00"))
        assertNull(parseSatang("1e3"))
        assertNull(parseSatang("12.345"))
        assertNull(parseSatang("1,250.00"))
        assertNull(parseSatang("750.00THB"))
        assertNull(parseSatang("99999999999999999999"))
    }

    @Test
    fun formattedAmountsRoundTripThroughParsing() {
        val satang = parseSatang("1250.50")
        assertEquals("฿1,250.50", formatSatang(satang ?: 0L))
    }
}
