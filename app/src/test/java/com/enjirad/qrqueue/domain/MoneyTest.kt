package com.enjirad.qrqueue.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun formatsZeroAmount() {
        assertEquals("฿0.00", formatSatang(0))
    }

    @Test
    fun formatsWholeBahtWithGrouping() {
        assertEquals("฿1,234.00", formatSatang(123_400))
    }

    @Test
    fun formatsSatangRemainder() {
        assertEquals("฿1,234.56", formatSatang(123_456))
    }

    @Test
    fun formatsSmallAmounts() {
        assertEquals("฿0.75", formatSatang(75))
    }

    @Test
    fun formatsNegativeAmounts() {
        assertEquals("-฿0.75", formatSatang(-75))
    }
}
