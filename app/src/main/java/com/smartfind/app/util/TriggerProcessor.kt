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

package com.smartfind.app.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.smartfind.app.SettingsManager
import com.smartfind.app.data.EventLogger
import com.smartfind.app.service.AlarmService

/**
 * Shared trigger-processing logic used by both
 * [SmsReceiver][com.smartfind.app.receiver.SmsReceiver] (real-time broadcast)
 * and [SmsCheckWorker][com.smartfind.app.worker.SmsCheckWorker] (polling).
 *
 * This class encapsulates:
 * - Pre-trigger guard checks (service enabled, active use, cooldown, rate limit, config)
 * - Body redaction for debug logging
 * - Post-match safety checks (car mode, phone call, battery)
 * - Alarm start with fallback notification
 *
 * Callers retain responsibility for:
 * - Obtaining SMS data (from broadcast intent or content provider)
 * - SMS spoof detection (SmsReceiver only — requires [android.telephony.SmsMessage])
 * - Foreground promotion (caller-specific)
 */
class TriggerProcessor(
    private val context: Context,
    private val settings: SettingsManager
) {
    /** Reason a guard check failed. */
    enum class GuardFailure {
        SERVICE_DISABLED,
        ACTIVE_USE,
        COOLDOWN,
        RATE_LIMITED,
        NO_DESIGNATED_NUMBERS,
        NO_KEYWORD
    }

    /**
     * Result of [runGuardChecks]. When [passed] is true, [designatedNumbers]
     * and [keyword] are guaranteed non-empty and safe to use for matching.
     * When [passed] is false, [failureReason] indicates which guard blocked.
     */
    data class GuardResult(
        val passed: Boolean,
        val designatedNumbers: Set<String> = emptySet(),
        val keyword: String = "",
        val failureReason: GuardFailure? = null
    )

    /** Outcome of [handleTriggerMatch]. */
    enum class TriggerOutcome {
        /** Alarm started via foreground service. */
        ALARM_STARTED,
        /** Foreground service failed; fallback notification posted. */
        ALARM_FALLBACK,
        /** Trigger suppressed by car mode — silent notification posted. */
        SUPPRESSED_CAR_MODE,
        /** Trigger suppressed by active phone call — silent notification posted. */
        SUPPRESSED_PHONE_CALL,
        /** Alarm already active — no action taken. */
        ALREADY_ACTIVE,
        /** Could not acquire [AlarmLock] — another component is starting. */
        LOCK_NOT_ACQUIRED
    }

    companion object {
        private const val TAG = "TriggerProcessor"
    }

    // ------------------------------------------------------------------
    // Guard checks
    // ------------------------------------------------------------------

    /**
     * Runs all pre-trigger guard checks with debug logging.
     * Returns a [GuardResult] indicating whether processing should continue.
     *
     * Guards checked (in order):
     * 1. Service enabled
     * 2. Active use protection
     * 3. Cooldown period
     * 4. Rate limit
     * 5. Designated numbers configured
     * 6. Keyword configured
     *
     * @param cid Correlation ID for grouping related debug log entries
     * @param skipServiceCheck True when the caller has already verified
     *        the service is enabled (e.g. SmsCheckWorker checks in doWork)
     */
    fun runGuardChecks(cid: String, skipServiceCheck: Boolean = false): GuardResult {
        // Guard 1: Service enabled
        if (!skipServiceCheck) {
            if (!settings.isServiceEnabled()) {
                Log.d(TAG, "[$cid] service_enabled=false|result=EXIT")
                return GuardResult(passed = false, failureReason = GuardFailure.SERVICE_DISABLED)
            }
            Log.d(TAG, "[$cid] service_enabled=true|result=PASS")
        }

        // Guard 2: Active use protection
        val activeUseEnabled = settings.isActiveUseProtectionEnabled()
        val deviceActivelyInUse = if (activeUseEnabled) {
            DeviceStateHelper.isDeviceActivelyInUse(context)
        } else false
        val deviceDetail = DeviceStateHelper.getActiveUseDetail(context)
        if (activeUseEnabled && deviceActivelyInUse) {
            Log.d(TAG, "[$cid] active_use|SUPPRESSED|$deviceDetail")
            EventLogger.logTriggerSuppressed(context, "active_use|$deviceDetail", cid)
            return GuardResult(passed = false, failureReason = GuardFailure.ACTIVE_USE)
        }
        Log.d(TAG, "[$cid] active_use_protection|enabled=$activeUseEnabled|in_use=$deviceActivelyInUse" +
            "|$deviceDetail|result=PASS")

        // Guard 3: Cooldown
        if (settings.isCooldownActive()) {
            val elapsed = System.currentTimeMillis() - settings.getAlarmStoppedTimestamp()
            val cooldownMs = settings.getCooldownMinutes() * 60 * 1000L
            Log.d(TAG, "[$cid] cooldown|SUPPRESSED|elapsed_ms=$elapsed|cooldown_ms=$cooldownMs" +
                "|remaining_ms=${cooldownMs - elapsed}")
            EventLogger.logTriggerSuppressed(context, "cooldown|elapsed_ms=$elapsed|cooldown_ms=$cooldownMs" +
                "|remaining_ms=${cooldownMs - elapsed}", cid)
             return GuardResult(passed = false, failureReason = GuardFailure.COOLDOWN)
        }
        Log.d(TAG, "[$cid] cooldown_active=false|result=PASS")

        // Guard 4: Rate limit
        if (settings.isRateLimited()) {
            val recentCount = settings.getRateLimitTimestamps().count {
                it > System.currentTimeMillis() - SettingsManager.RATE_LIMIT_WINDOW_MS
            }
            Log.d(TAG, "[$cid] rate_limited|SUPPRESSED|recent_triggers=$recentCount" +
                "|max=${SettingsManager.RATE_LIMIT_MAX_TRIGGERS}")
            EventLogger.logTriggerSuppressed(context, "rate_limited|recent_triggers=$recentCount" +
                "|max=${SettingsManager.RATE_LIMIT_MAX_TRIGGERS}", cid)
            return GuardResult(passed = false, failureReason = GuardFailure.RATE_LIMITED)
        }
        Log.d(TAG, "[$cid] rate_limited=false|result=PASS")

        // Config check: designated numbers
        val designatedNumbers = settings.getPhoneNumbers()
        if (designatedNumbers.isEmpty()) {
            Log.d(TAG, "[$cid] designated_numbers=EMPTY|result=EXIT")
            return GuardResult(passed = false, failureReason = GuardFailure.NO_DESIGNATED_NUMBERS)
        }
        Log.d(TAG, "[$cid] designated_numbers_count=${designatedNumbers.size}|result=PASS")

        // Config check: keyword
        val keyword = settings.getTriggerKeyword()
        if (keyword.isBlank()) {
            Log.d(TAG, "[$cid] keyword=BLANK|result=EXIT")
            return GuardResult(passed = false, failureReason = GuardFailure.NO_KEYWORD)
        }
        Log.d(TAG, "[$cid] keyword_configured=true|result=PASS")

            return GuardResult(passed = true, designatedNumbers = designatedNumbers, keyword = keyword)
    }

    // ------------------------------------------------------------------
    // Body redaction
    // ------------------------------------------------------------------

    /**
     * Redacts a message body for debug logging. Shows length and
     * first/last characters while hiding the content.
     *
     * Short messages (<=6 chars) are fully redacted to prevent
     * leaking short trigger keywords.
     */
    fun redactBody(body: String): String {
        return if (body.length <= 6) "***"
        else "${body.take(2)}...${body.takeLast(2)}(${body.length})"
    }

    // ------------------------------------------------------------------
    // Trigger handling
    // ------------------------------------------------------------------

    /**
     * Handles a confirmed trigger match: runs post-match safety checks
     * (car mode, phone call, battery) and starts the alarm or posts
     * a suppression notification.
     *
     * Call this after sender/keyword matching and any spoof checks.
     *
     * @param matchedNumber The designated number that matched (as stored in settings)
     * @param senderAddress The raw sender address from the SMS
     * @param cid Correlation ID for debug logging
     * @param beforeAlarmStart Optional callback invoked just before starting
     *        the foreground service. The SmsCheckWorker should call
     *        [setForeground] before this method and pass null here.
     * @return The [TriggerOutcome] describing what action was taken
     */
    fun handleTriggerMatch(
        matchedNumber: String,
        senderAddress: String,
        cid: String,
        beforeAlarmStart: (() -> Unit)? = null
    ): TriggerOutcome {
        // Car mode check
        val carModeEnabled = settings.isCarModeProtectionEnabled()
        val inCarMode = if (carModeEnabled) DeviceStateHelper.isInCarMode(context) else false
        if (carModeEnabled && inCarMode) {
            Log.d(TAG, "[$cid] car_mode=true|result=SILENT_NOTIFICATION")
            EventLogger.logTriggerCarMode(context, senderAddress, cid)
            AlarmService.postSilentSuppressionNotification(
                context, matchedNumber, senderAddress,
                reason = "Android Auto", isCarMode = true
            )
            return TriggerOutcome.SUPPRESSED_CAR_MODE
        }
        Log.d(TAG, "[$cid] car_mode|enabled=$carModeEnabled|active=$inCarMode|result=PASS")

        // Phone call check
        val phoneCallEnabled = settings.isPhoneCallProtectionEnabled()
        val inPhoneCall = if (phoneCallEnabled) DeviceStateHelper.isInPhoneCall(context) else false
        if (phoneCallEnabled && inPhoneCall) {
            Log.d(TAG, "[$cid] phone_call=true|result=SILENT_NOTIFICATION")
            EventLogger.logTriggerSuppressed(context, "phone_call", cid)
            AlarmService.postSilentSuppressionNotification(
                context, matchedNumber, senderAddress,
                reason = "active call", isCarMode = false
            )
            return TriggerOutcome.SUPPRESSED_PHONE_CALL
        }
        Log.d(TAG, "[$cid] phone_call|enabled=$phoneCallEnabled|active=$inPhoneCall|result=PASS")

        // Record trigger for rate limiting — placed after car mode and phone call
        // checks so that suppressed triggers don't consume rate limit slots.
        // Without this, 5 suppressed triggers (e.g. while driving) would block
        // the 6th trigger even after the suppression condition clears.
        settings.recordTriggerTimestamp()

        // Low battery check
        val lowBatteryEnabled = settings.isLowBatteryProtectionEnabled()
        val isLowBattery = lowBatteryEnabled && BatteryHelper.isLowBattery(context)
        val batteryPct = BatteryHelper.getBatteryPercentage(context)

        Log.d(TAG, "[$cid] battery|enabled=$lowBatteryEnabled|low=$isLowBattery|pct=$batteryPct%")

        if (isLowBattery) {
            EventLogger.logTriggerLowBattery(context, senderAddress, cid)
        } else {
            EventLogger.logTriggerAlarm(context, senderAddress, cid)
        }

        // Post trigger notification
        AlarmService.postTriggerNotification(context, matchedNumber, senderAddress)

        // Start alarm
        return startAlarm(matchedNumber, senderAddress, isLowBattery, cid, beforeAlarmStart)
    }

    /**
     * Attempts to start the alarm via foreground service, with fallback
     * to a high-priority notification if the service start is blocked.
     */
    private fun startAlarm(
        matchedNumber: String,
        senderAddress: String,
        isLowBattery: Boolean,
        cid: String,
        beforeAlarmStart: (() -> Unit)?
    ): TriggerOutcome {
        val alarmAlreadyActive = settings.isAlarmActive()
        val lockAcquired = if (!alarmAlreadyActive) AlarmLock.tryAcquire() else false

        Log.d(TAG, "[$cid] alarm_active=$alarmAlreadyActive|lock_acquired=$lockAcquired" +
            "|low_battery=$isLowBattery")

        if (alarmAlreadyActive) {
            Log.d(TAG, "[$cid] SKIPPED|reason=alarm_already_active")
            return TriggerOutcome.ALREADY_ACTIVE
        }

        if (!lockAcquired) {
            Log.d(TAG, "[$cid] SKIPPED|reason=lock_not_acquired")
            return TriggerOutcome.LOCK_NOT_ACQUIRED
        }

        return try {
            // Allow caller to do pre-start work (e.g. worker foreground promotion)
            beforeAlarmStart?.invoke()

            // Mark alarm active eagerly — before startForegroundService returns —
            // to close the race window between AlarmLock.release() and
            // AlarmService.onStartCommand() setting this flag. Without this,
            // a second trigger could slip through after the lock is released
            // but before the service has executed onStartCommand().
            settings.setAlarmActive(true)

            val alarmIntent = Intent(context, AlarmService::class.java)
            alarmIntent.putExtra(AlarmService.EXTRA_LOW_BATTERY, isLowBattery)
            context.startForegroundService(alarmIntent)
            Log.d(TAG, "[$cid] startForegroundService=OK")
            TriggerOutcome.ALARM_STARTED
        } catch (e: Exception) {
            // Roll back eager activation — the service won't start
            settings.setAlarmActive(false)
            Log.d(TAG, "[$cid] startForegroundService=FAILED|error=${e.javaClass.simpleName}: ${e.message}" +
                "|falling_back_to_notification")
            AlarmService.postEmergencyFallbackNotification(context, matchedNumber, senderAddress)
            TriggerOutcome.ALARM_FALLBACK
        } finally {
            AlarmLock.release()
        }
    }
}
