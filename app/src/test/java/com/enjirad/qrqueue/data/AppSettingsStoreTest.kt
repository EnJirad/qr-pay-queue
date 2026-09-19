package com.enjirad.qrqueue.data

import com.enjirad.qrqueue.domain.HandPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the [InMemoryAppSettingsStore], which mirrors [SharedPreferencesAppSettingsStore]
 * but stores values in memory so we can unit-test the hand-preference, auto-reset-toggle,
 * and last-reset-date persistence without requiring AndroidContext.
 */
class InMemoryAppSettingsStoreTest {

    private lateinit var store: InMemoryAppSettingsStore

    @Before
    fun setUp() {
        store = InMemoryAppSettingsStore()
    }

    // ---- hand preference ----

    @Test
    fun `default hand preference is RIGHT`() {
        assertEquals(HandPreference.RIGHT, store.loadHandPreference())
    }

    @Test
    fun `saveHandPreference persists LEFT`() {
        store.saveHandPreference(HandPreference.LEFT)
        assertEquals(HandPreference.LEFT, store.loadHandPreference())
    }

    @Test
    fun `saveHandPreference overwrites previous value`() {
        store.saveHandPreference(HandPreference.LEFT)
        store.saveHandPreference(HandPreference.RIGHT)
        assertEquals(HandPreference.RIGHT, store.loadHandPreference())
    }

    // ---- auto daily reset ----

    @Test
    fun `default auto reset is false`() {
        assertFalse(store.loadAutoDailyReset())
    }

    @Test
    fun `saveAutoDailyReset persists true`() {
        store.saveAutoDailyReset(true)
        assertTrue(store.loadAutoDailyReset())
    }

    @Test
    fun `saveAutoDailyReset persists false`() {
        store.saveAutoDailyReset(true)
        store.saveAutoDailyReset(false)
        assertFalse(store.loadAutoDailyReset())
    }

    // ---- last reset date ----

    @Test
    fun `default last reset date is null`() {
        assertNull(store.loadLastResetDate())
    }

    @Test
    fun `saveLastResetDate persists value`() {
        store.saveLastResetDate("2026-09-20")
        assertEquals("2026-09-20", store.loadLastResetDate())
    }

    @Test
    fun `saveLastResetDate overwrites previous value`() {
        store.saveLastResetDate("2026-09-19")
        store.saveLastResetDate("2026-09-20")
        assertEquals("2026-09-20", store.loadLastResetDate())
    }

    // ---- home lock (V0.7) ----

    @Test
    fun `default home lock is false`() {
        assertFalse(store.loadHomeLocked())
    }

    @Test
    fun `saveHomeLocked persists true`() {
        store.saveHomeLocked(true)
        assertTrue(store.loadHomeLocked())
    }

    @Test
    fun `saveHomeLocked persists false`() {
        store.saveHomeLocked(true)
        store.saveHomeLocked(false)
        assertFalse(store.loadHomeLocked())
    }
}

/**
 * In-memory implementation of [AppSettingsStore] for unit tests.
 */
class InMemoryAppSettingsStore : AppSettingsStore {
    private var handPref: HandPreference = HandPreference.RIGHT
    private var autoReset: Boolean = false
    private var lastResetDate: String? = null
    private var homeLocked: Boolean = false

    override fun saveHandPreference(pref: HandPreference): Boolean {
        handPref = pref
        return true
    }

    override fun loadHandPreference(): HandPreference = handPref

    override fun saveAutoDailyReset(enabled: Boolean): Boolean {
        autoReset = enabled
        return true
    }

    override fun loadAutoDailyReset(): Boolean = autoReset

    override fun saveLastResetDate(dateString: String): Boolean {
        lastResetDate = dateString
        return true
    }

    override fun loadLastResetDate(): String? = lastResetDate

    override fun saveHomeLocked(locked: Boolean): Boolean {
        homeLocked = locked
        return true
    }

    override fun loadHomeLocked(): Boolean = homeLocked
}
