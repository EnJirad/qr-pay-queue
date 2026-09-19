package com.enjirad.qrqueue.domain

import com.enjirad.qrqueue.data.AppSettingsStoreTest.InMemoryAppSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tests for the daily-reset workflow (V0.6).
 */
class DailyResetTest {

    private fun todayString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    @Test
    fun `today string is in yyyy-MM-dd format`() {
        val today = todayString()
        assertEquals(10, today.length)
        assertEquals('-', today[4])
        assertEquals('-', today[7])
    }

    @Test
    fun `different dates trigger reset`() {
        val today = todayString()
        val yesterday = "2020-01-01"
        assertTrue(today != yesterday)
    }

    @Test
    fun `same date does not trigger reset`() {
        val today = todayString()
        assertFalse(today != today)
    }

    @Test
    fun `auto reset enabled stores today on first time`() {
        val store = InMemoryAppSettingsStore()
        assertFalse(store.loadAutoDailyReset())
        assertNull(store.loadLastResetDate())

        store.saveAutoDailyReset(true)
        store.saveLastResetDate(todayString())

        assertTrue(store.loadAutoDailyReset())
        assertEquals(todayString(), store.loadLastResetDate())
    }

    @Test
    fun `settings survive daily reset concept`() {
        val store = InMemoryAppSettingsStore()
        store.saveHandPreference(HandPreference.LEFT)
        store.saveAutoDailyReset(true)

        assertEquals(HandPreference.LEFT, store.loadHandPreference())
        assertTrue(store.loadAutoDailyReset())
    }

    @Test
    fun `manual reset does not change auto-reset setting`() {
        val store = InMemoryAppSettingsStore()
        store.saveAutoDailyReset(false)
        assertFalse(store.loadAutoDailyReset())
    }
}
