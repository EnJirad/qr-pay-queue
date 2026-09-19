package com.enjirad.qrqueue.data

import android.content.Context
import com.enjirad.qrqueue.domain.HandPreference

/**
 * Persists app settings that survive every form of process death.
 * Hand preference, auto-reset toggle, and the last daily-reset date are
 * stored in a single SharedPreferences file so they can be read synchronously
 * on the main thread at startup.
 */
interface AppSettingsStore {
    fun saveHandPreference(pref: HandPreference): Boolean
    fun loadHandPreference(): HandPreference
    fun saveAutoDailyReset(enabled: Boolean): Boolean
    fun loadAutoDailyReset(): Boolean
    fun saveLastResetDate(dateString: String): Boolean
    fun loadLastResetDate(): String?
    fun saveHomeLocked(locked: Boolean): Boolean
    fun loadHomeLocked(): Boolean
}

class SharedPreferencesAppSettingsStore(context: Context) : AppSettingsStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveHandPreference(pref: HandPreference): Boolean = runCatching {
        prefs.edit().putString(KEY_HAND, pref.name).apply()
    }.isSuccess

    override fun loadHandPreference(): HandPreference {
        val raw = prefs.getString(KEY_HAND, null)
        return HandPreference.entries.firstOrNull { it.name == raw } ?: HandPreference.RIGHT
    }

    override fun saveAutoDailyReset(enabled: Boolean): Boolean = runCatching {
        prefs.edit().putBoolean(KEY_AUTO_RESET, enabled).apply()
    }.isSuccess

    override fun loadAutoDailyReset(): Boolean = prefs.getBoolean(KEY_AUTO_RESET, false)

    override fun saveLastResetDate(dateString: String): Boolean = runCatching {
        prefs.edit().putString(KEY_LAST_RESET, dateString).apply()
    }.isSuccess

    override fun loadLastResetDate(): String? = prefs.getString(KEY_LAST_RESET, null)

    override fun saveHomeLocked(locked: Boolean): Boolean = runCatching {
        prefs.edit().putBoolean(KEY_HOME_LOCKED, locked).apply()
    }.isSuccess

    override fun loadHomeLocked(): Boolean = prefs.getBoolean(KEY_HOME_LOCKED, false)

    private companion object {
        const val PREFS_NAME = "qr_queue_settings"
        const val KEY_HAND = "hand_preference"
        const val KEY_AUTO_RESET = "auto_daily_reset"
        const val KEY_LAST_RESET = "last_reset_date"
        const val KEY_HOME_LOCKED = "home_locked"
    }
}
