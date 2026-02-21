/*
 * SmartFind - Find your phone with an SMS from a trusted contact
 * Copyright (C) 2026 ErinyLTD
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.smartfind.app

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.util.Log
import java.security.GeneralSecurityException

class SettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "smartfind_prefs"
        /** Distinct filename for the plain-text fallback SharedPreferences.
         *  Using the same name as [PREFS_NAME] caused encrypted and plain
         *  backends to silently read/write independent data stores, leading
         *  to flags like `first_run_complete` appearing set when they were
         *  written by a prior session that happened to use the other backend. */
        private const val PREFS_FALLBACK_NAME = "smartfind_prefs_plain"
        private const val KEY_TRIGGER_KEYWORD = "trigger_keyword"
        private const val KEY_PHONE_NUMBERS = "phone_numbers"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_LAST_CHECKED_SMS_TS = "last_checked_sms_ts"
        private const val KEY_SAVED_VOLUME = "saved_volume"
        private const val KEY_SAVED_RINGER_MODE = "saved_ringer_mode"
        private const val KEY_ALARM_ACTIVE = "alarm_active"
        private const val KEY_NUMBERS_CONFIRMED_TS = "numbers_confirmed_ts"
        private const val KEY_CONTACT_NAMES = "contact_names"
        private const val KEY_COOLDOWN_MINUTES = "cooldown_minutes"
        private const val KEY_ALARM_STOPPED_TS = "alarm_stopped_ts"

        private const val KEY_FIRST_RUN_COMPLETE = "first_run_complete"
        private const val KEY_RATE_LIMIT_TIMESTAMPS = "rate_limit_timestamps"
        private const val KEY_SERVICE_UNLOCK_COUNT = "service_unlock_count"
        private const val KEY_POLLING_INTERVAL_SEC = "polling_interval_sec"
        /** Number of device unlocks before the service-active notification is dismissed. */
        const val SERVICE_NOTIFICATION_UNLOCK_THRESHOLD = 3
        private const val DEFAULT_KEYWORD = "FIND"
        const val SIX_MONTHS_MS = 180L * 24 * 60 * 60 * 1000 // ~6 months
        const val MIN_COOLDOWN_MINUTES = 2
        const val MAX_COOLDOWN_MINUTES = 60
        const val DEFAULT_COOLDOWN_MINUTES = 5
        /** Minimum digits required for short-number phone matching. */
        const val MIN_MATCH_DIGITS = 7

        const val MIN_POLLING_INTERVAL_SEC = 60
        const val MAX_POLLING_INTERVAL_SEC = 300
        const val DEFAULT_POLLING_INTERVAL_SEC = 180
        private const val TAG = "SettingsManager"

        /** Whether EncryptedSharedPreferences is active. */
        @Volatile
        var isEncryptionAvailable: Boolean = true
            private set

        /** Max triggers allowed within the sliding window. */
        const val RATE_LIMIT_MAX_TRIGGERS = 5
        /** Sliding window duration in milliseconds (1 hour). */
        const val RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000L

        /**
         * Cached [SharedPreferences] instance. Created once per process by
         * [getOrCreatePrefs] and reused by every [SettingsManager] instance.
         * This avoids repeated AndroidKeyStore crypto operations that can
         * take several seconds on some devices and cause ANRs.
         */
        @Volatile
        private var cachedPrefs: SharedPreferences? = null

        /**
         * Returns the cached [SharedPreferences], creating it on first call.
         * Thread-safe via double-checked locking on [SettingsManager::class].
         */
        fun getOrCreatePrefs(context: Context): SharedPreferences {
            cachedPrefs?.let { return it }
            synchronized(SettingsManager::class) {
                cachedPrefs?.let { return it }
                return createPrefs(context).also { cachedPrefs = it }
            }
        }

        /**
         * Creates encrypted SharedPreferences backed by AndroidKeyStore.
         * Falls back to plain SharedPreferences if encryption fails
         * (e.g. device lacks hardware keystore), logging a warning.
         *
         * Uses the security-crypto-ktx factory functions which are the
         * non-deprecated entry points (the underlying classes carry a
         * blanket @Deprecated but have no replacement).
         */
        @Suppress("DEPRECATION")
        fun createPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = androidx.security.crypto.MasterKey(context)

                androidx.security.crypto.EncryptedSharedPreferences(
                    context,
                    PREFS_NAME,
                    masterKey
                ).also { isEncryptionAvailable = true }
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "EncryptedSharedPreferences failed (security): ${e.message}")
                isEncryptionAvailable = false
                context.getSharedPreferences(PREFS_FALLBACK_NAME, Context.MODE_PRIVATE)
            } catch (e: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences failed: ${e.message}")
                isEncryptionAvailable = false
                context.getSharedPreferences(PREFS_FALLBACK_NAME, Context.MODE_PRIVATE)
            }
        }

        /**
         * Resets the cached [SharedPreferences] instance. Only intended for
         * use in tests where each test needs a fresh preferences state.
         */
        fun resetCachedPrefsForTesting() {
            cachedPrefs = null
        }
    }

    private val prefs: SharedPreferences = getOrCreatePrefs(context)

    // Trigger keyword
    fun getTriggerKeyword(): String =
        prefs.getString(KEY_TRIGGER_KEYWORD, DEFAULT_KEYWORD) ?: DEFAULT_KEYWORD

    fun setTriggerKeyword(keyword: String) {
        prefs.edit().putString(KEY_TRIGGER_KEYWORD, keyword.trim().uppercase()).apply()
    }

    // Phone numbers stored as comma-separated string
    fun getPhoneNumbers(): Set<String> {
        val raw = prefs.getString(KEY_PHONE_NUMBERS, "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun addPhoneNumber(number: String, contactName: String? = null) {
        val trimmed = number.trim()
        // Reject empty or digit-less numbers — normalizeNumber can produce
        // bare country codes (e.g. "+44") from empty input, which would
        // pollute the stored numbers list without ever matching anything.
        if (trimmed.isEmpty() || trimmed.count { it.isDigit() } < MIN_MATCH_DIGITS) return

        val numbers = getPhoneNumbers().toMutableSet()
        numbers.add(trimmed)
        prefs.edit().putString(KEY_PHONE_NUMBERS, numbers.joinToString(",")).apply()
        if (contactName != null) {
            setContactName(trimmed, contactName)
        }
    }

    fun removePhoneNumber(number: String) {
        val numbers = getPhoneNumbers().toMutableSet()
        numbers.remove(number.trim())
        prefs.edit().putString(KEY_PHONE_NUMBERS, numbers.joinToString(",")).apply()
        removeContactName(number.trim())
    }

    // Contact names stored as "number1:name1|number2:name2|..."
    // Delimiters (: and |) are sanitized from names to prevent format corruption
    fun getContactName(number: String): String? {
        val map = getContactNamesMap()
        return map[number.trim()]
    }

    fun setContactName(number: String, name: String) {
        val map = getContactNamesMap().toMutableMap()
        map[number.trim()] = sanitizeContactName(name.trim())
        saveContactNamesMap(map)
    }

    /**
     * Strips delimiters, control characters, and enforces max length on
     * contact names to prevent corruption of the serialized map format
     * and storage abuse.
     */
    internal fun sanitizeContactName(name: String): String {
        return name
            .replace(":", "")
            .replace("|", "")
            .replace(Regex("[\\p{Cc}\\p{Cf}]"), "") // strip control & format chars
            .trim()
            .take(100) // enforce max length
    }

    private fun removeContactName(number: String) {
        val map = getContactNamesMap().toMutableMap()
        map.remove(number.trim())
        saveContactNamesMap(map)
    }

    private fun getContactNamesMap(): Map<String, String> {
        val raw = prefs.getString(KEY_CONTACT_NAMES, "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split("|").mapNotNull { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                parts[0] to parts[1]
            } else null
        }.toMap()
    }

    private fun saveContactNamesMap(map: Map<String, String>) {
        val raw = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        prefs.edit().putString(KEY_CONTACT_NAMES, raw).apply()
    }

    // Service enabled
    fun isServiceEnabled(): Boolean = prefs.getBoolean(KEY_SERVICE_ENABLED, false)

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    // Last checked SMS timestamp for polling dedup
    fun getLastCheckedSmsTimestamp(): Long = prefs.getLong(KEY_LAST_CHECKED_SMS_TS, 0L)

    fun setLastCheckedSmsTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_CHECKED_SMS_TS, timestamp).apply()
    }

    // Audio state preservation
    fun saveAudioState(volume: Int, ringerMode: Int) {
        prefs.edit()
            .putInt(KEY_SAVED_VOLUME, volume)
            .putInt(KEY_SAVED_RINGER_MODE, ringerMode)
            .apply()
    }

    fun getSavedVolume(): Int = prefs.getInt(KEY_SAVED_VOLUME, -1)

    fun getSavedRingerMode(): Int =
        prefs.getInt(KEY_SAVED_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)

    // Alarm active state for UI
    fun isAlarmActive(): Boolean = prefs.getBoolean(KEY_ALARM_ACTIVE, false)

    fun setAlarmActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_ALARM_ACTIVE, active).apply()
    }

    // Phone numbers confirmation timestamp
    fun getNumbersConfirmedTimestamp(): Long = prefs.getLong(KEY_NUMBERS_CONFIRMED_TS, 0L)

    fun setNumbersConfirmedTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_NUMBERS_CONFIRMED_TS, timestamp).apply()
    }

    fun isNumbersConfirmationDue(): Boolean {
        val lastConfirmed = getNumbersConfirmedTimestamp()
        if (lastConfirmed == 0L) return false // Never set = first install, not due yet
        return System.currentTimeMillis() - lastConfirmed >= SIX_MONTHS_MS
    }

    // Active use protection — always enabled, cannot be toggled off
    fun isActiveUseProtectionEnabled(): Boolean = true

    // Car mode protection — always enabled, cannot be toggled off
    fun isCarModeProtectionEnabled(): Boolean = true

    // Trigger cooldown — ignore triggers for a period after stopping an alarm
    fun getCooldownMinutes(): Int =
        prefs.getInt(KEY_COOLDOWN_MINUTES, DEFAULT_COOLDOWN_MINUTES)
            .coerceIn(MIN_COOLDOWN_MINUTES, MAX_COOLDOWN_MINUTES)

    fun setCooldownMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_COOLDOWN_MINUTES, minutes.coerceIn(MIN_COOLDOWN_MINUTES, MAX_COOLDOWN_MINUTES)).apply()
    }

    fun getAlarmStoppedTimestamp(): Long = prefs.getLong(KEY_ALARM_STOPPED_TS, 0L)

    fun setAlarmStoppedTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_ALARM_STOPPED_TS, timestamp).apply()
    }

    /**
     * Returns true if a trigger cooldown is active — the alarm was recently
     * stopped and the configured cooldown period has not yet elapsed.
     * Minimum cooldown is 2 minutes; this protection cannot be disabled.
     */
    fun isCooldownActive(): Boolean {
        val cooldownMin = getCooldownMinutes()

        val stoppedAt = getAlarmStoppedTimestamp()
        if (stoppedAt == 0L) return false

        val cooldownMs = cooldownMin.toLong() * 60 * 1000
        val elapsed = System.currentTimeMillis() - stoppedAt
        return elapsed < cooldownMs
    }

    // Low battery protection — always enabled, cannot be toggled off
    fun isLowBatteryProtectionEnabled(): Boolean = true

    // Lock screen notification privacy — always enabled, cannot be toggled off
    fun isLockScreenPrivacyEnabled(): Boolean = true

    // Phone call protection — always enabled, cannot be toggled off
    fun isPhoneCallProtectionEnabled(): Boolean = true

    // Service-active notification unlock counter
    fun getServiceUnlockCount(): Int = prefs.getInt(KEY_SERVICE_UNLOCK_COUNT, 0)

    fun incrementServiceUnlockCount() {
        prefs.edit().putInt(KEY_SERVICE_UNLOCK_COUNT, getServiceUnlockCount() + 1).apply()
    }

    fun resetServiceUnlockCount() {
        prefs.edit().putInt(KEY_SERVICE_UNLOCK_COUNT, 0).apply()
    }

    // First-run onboarding
    fun isFirstRunComplete(): Boolean =
        prefs.getBoolean(KEY_FIRST_RUN_COMPLETE, false)

    fun setFirstRunComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_RUN_COMPLETE, complete).apply()
    }

    // SMS polling interval
    fun getPollingIntervalSec(): Int =
        prefs.getInt(KEY_POLLING_INTERVAL_SEC, DEFAULT_POLLING_INTERVAL_SEC)
            .coerceIn(MIN_POLLING_INTERVAL_SEC, MAX_POLLING_INTERVAL_SEC)

    fun setPollingIntervalSec(seconds: Int) {
        prefs.edit().putInt(KEY_POLLING_INTERVAL_SEC,
            seconds.coerceIn(MIN_POLLING_INTERVAL_SEC, MAX_POLLING_INTERVAL_SEC)).apply()
    }

    fun getPollingIntervalMs(): Long = getPollingIntervalSec() * 1000L

    // ==========================================
    // Rate limiting — sliding window
    // ==========================================

    /**
     * Returns the list of trigger timestamps within the current sliding window.
     * Stored as comma-separated epoch millis.
     */
    fun getRateLimitTimestamps(): List<Long> {
        val raw = prefs.getString(KEY_RATE_LIMIT_TIMESTAMPS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    /**
     * Records a trigger event timestamp for rate limiting.
     * Automatically prunes timestamps older than the sliding window.
     */
    fun recordTriggerTimestamp(timestamp: Long = System.currentTimeMillis()) {
        val cutoff = timestamp - RATE_LIMIT_WINDOW_MS
        val timestamps = getRateLimitTimestamps()
            .filter { it > cutoff }
            .toMutableList()
        timestamps.add(timestamp)
        prefs.edit().putString(KEY_RATE_LIMIT_TIMESTAMPS, timestamps.joinToString(",")).apply()
    }

    /**
     * Returns true if the rate limit has been exceeded — more than
     * [RATE_LIMIT_MAX_TRIGGERS] triggers within the last [RATE_LIMIT_WINDOW_MS].
     */
    fun isRateLimited(): Boolean {
        val now = System.currentTimeMillis()
        val cutoff = now - RATE_LIMIT_WINDOW_MS
        val recentCount = getRateLimitTimestamps().count { it > cutoff }
        return recentCount >= RATE_LIMIT_MAX_TRIGGERS
    }
}
