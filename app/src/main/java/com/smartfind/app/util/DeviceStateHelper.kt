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

import android.app.KeyguardManager
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Checks device state to determine whether SMS triggers should be
 * suppressed or modified for safety and anti-abuse reasons.
 *
 * Call [register] from Application.onCreate() to begin tracking
 * screen-on/off transitions. This allows [isDeviceActivelyInUse]
 * to distinguish genuine user activity from transient screen wakes
 * caused by incoming notifications (including the trigger SMS itself).
 *
 * Public methods ([register], [unregister], [isDeviceActivelyInUse],
 * [getActiveUseDetail], [isInCarMode], [isInPhoneCall]) are called
 * from production code across multiple packages. Lower-level helpers
 * ([isScreenRecentlyWoken], [getScreenOnDurationMs], [isScreenOn],
 * [isDeviceLocked]) are `internal` for unit testing.
 */
object DeviceStateHelper {

    /**
     * Grace period after the screen turns on before we consider the
     * device "actively in use". Incoming SMS notifications wake the
     * screen briefly; without this grace period the trigger would be
     * falsely suppressed because [PowerManager.isInteractive] returns
     * true the instant the screen lights up for the notification.
     *
     * 3 seconds is long enough to filter out notification wakes but
     * short enough that a user who picks up their phone and unlocks
     * it will have the screen on for well over 3 seconds by the time
     * a new SMS arrives.
     */
    internal const val SCREEN_WAKE_GRACE_PERIOD_MS = 3_000L

    /**
     * Timestamp (via [SystemClock.elapsedRealtime]) of the most recent
     * ACTION_SCREEN_ON broadcast. 0L means we have no data yet (receiver
     * not registered or screen hasn't toggled since registration).
     */
    @Volatile
    internal var screenOnElapsedRealtime: Long = 0L

    private var screenStateReceiver: BroadcastReceiver? = null

    /**
     * Registers a dynamic BroadcastReceiver for ACTION_SCREEN_ON and
     * ACTION_SCREEN_OFF to track when the screen turns on. Safe to call
     * multiple times — unregisters any previous instance first.
     *
     * If the screen is already on at registration time, we record the
     * current timestamp so the grace period is measured from "now"
     * (conservative: we don't know exactly when it turned on, but this
     * avoids a stale 0L that would skip the grace period entirely).
     */
    fun register(context: Context) {
        unregister(context)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        screenOnElapsedRealtime = SystemClock.elapsedRealtime()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOnElapsedRealtime = 0L
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenStateReceiver = receiver

        // Seed the timestamp if the screen is already on
        if (isScreenOn(context)) {
            screenOnElapsedRealtime = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Unregisters the screen state receiver if currently registered.
     */
    fun unregister(context: Context) {
        screenStateReceiver?.let {
            try {
                context.applicationContext.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }
        screenStateReceiver = null
    }

    /**
     * Returns true if the device is actively being used:
     * the screen is on, the device is unlocked, AND the screen
     * has been on for longer than [SCREEN_WAKE_GRACE_PERIOD_MS].
     *
     * The grace period prevents false positives from transient screen
     * wakes caused by incoming notifications (including the trigger
     * SMS itself). Without it, every SMS that wakes the screen would
     * cause the trigger to be suppressed.
     *
     * When true, the phone is clearly not lost — triggers should
     * be suppressed to prevent harassment/abuse.
     */
    fun isDeviceActivelyInUse(context: Context): Boolean {
        // If the screen-state receiver hasn't been registered yet we have no
        // data about how long the screen has been on.  Default to "not in use"
        // so the alarm is allowed to fire rather than being silently suppressed.
        if (screenOnElapsedRealtime == 0L) return false
        if (!isScreenOn(context)) return false
        if (isDeviceLocked(context)) return false
        if (isScreenRecentlyWoken()) return false
        return true
    }

    /**
     * Returns a diagnostic string describing the current device state.
     * Used in debug log entries to help diagnose suppression issues.
     */
    fun getActiveUseDetail(context: Context): String {
        val screenOn = isScreenOn(context)
        val locked = isDeviceLocked(context)
        val screenOnMs = getScreenOnDurationMs()
        val recentlyWoken = isScreenRecentlyWoken()

        // Include both keyguard states for diagnostics
        val keyguardManager =
            context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val secureLocked = keyguardManager?.isDeviceLocked ?: true
        val keyguardLocked = keyguardManager?.isKeyguardLocked ?: true

        return "screen_on=$screenOn|locked=$locked" +
            "|secure_locked=$secureLocked|keyguard_locked=$keyguardLocked" +
            "|screen_on_for=${screenOnMs}ms" +
            "|recently_woken=$recentlyWoken" +
            "|grace_period=${SCREEN_WAKE_GRACE_PERIOD_MS}ms"
    }

    /**
     * Returns true if the screen turned on recently (within the grace
     * period). This indicates a notification-triggered wake rather
     * than deliberate user interaction.
     *
     * Returns false (safe default — alarm will fire) when:
     * - The receiver hasn't been registered yet (screenOnElapsedRealtime == 0)
     * - The screen is off (screenOnElapsedRealtime was reset to 0)
     */
    internal fun isScreenRecentlyWoken(): Boolean {
        val onSince = screenOnElapsedRealtime
        if (onSince == 0L) return false
        val elapsed = SystemClock.elapsedRealtime() - onSince
        return elapsed < SCREEN_WAKE_GRACE_PERIOD_MS
    }

    /**
     * Returns how long the screen has been on in milliseconds,
     * or -1 if unknown (receiver not registered or screen is off).
     */
    internal fun getScreenOnDurationMs(): Long {
        val onSince = screenOnElapsedRealtime
        if (onSince == 0L) return -1L
        return SystemClock.elapsedRealtime() - onSince
    }

    /**
     * Returns true when Android Auto or car mode is active.
     * Used to suppress the loud alarm for driving safety.
     */
    fun isInCarMode(context: Context): Boolean {
        val uiModeManager =
            context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
                ?: return false
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR
    }

    /**
     * Returns true if the user is currently on an active phone call.
     * Used to suppress the alarm at max volume during calls for safety.
     *
     * Checks both TelephonyManager call state and AudioManager mode
     * for comprehensive detection (VoIP calls set audio mode but may
     * not change telephony call state).
     */
    fun isInPhoneCall(context: Context): Boolean {
        // Check TelephonyManager for cellular calls
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (telephonyManager != null) {
            @Suppress("DEPRECATION")
            val callState = telephonyManager.callState
            if (callState == TelephonyManager.CALL_STATE_OFFHOOK ||
                callState == TelephonyManager.CALL_STATE_RINGING
            ) {
                return true
            }
        }

        // Check AudioManager for VoIP/app calls
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            val mode = audioManager.mode
            if (mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION
            ) {
                return true
            }
        }

        return false
    }

    /**
     * Returns true if the screen is on (interactive state).
     */
    internal fun isScreenOn(context: Context): Boolean {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return false
        return powerManager.isInteractive
    }

    /**
     * Returns true if the device is locked — either the secure keyguard
     * is engaged (PIN/pattern/password/biometric required) OR the
     * lock screen is showing even without a secure credential.
     *
     * We check both APIs because they cover different scenarios:
     * - [KeyguardManager.isDeviceLocked]: true only when a secure
     *   credential is required. Returns **false** when Smart Lock
     *   (Trusted Places, Trusted Devices, On-Body Detection) has
     *   bypassed the secure lock, or when there is no secure lock.
     * - [KeyguardManager.isKeyguardLocked]: true whenever the lock
     *   screen / keyguard is showing, regardless of whether Smart Lock
     *   has bypassed the credential. This is the key signal that the
     *   user is NOT actively interacting with the phone.
     *
     * Either being true means the user is not actively using the device.
     */
    internal fun isDeviceLocked(context: Context): Boolean {
        val keyguardManager =
            context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                ?: return true // If we can't determine, assume locked (safe default)
        return keyguardManager.isDeviceLocked || keyguardManager.isKeyguardLocked
    }
}
