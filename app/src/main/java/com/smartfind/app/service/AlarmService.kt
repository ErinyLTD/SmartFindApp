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

package com.smartfind.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartfind.app.MainActivity
import com.smartfind.app.R
import com.smartfind.app.SettingsManager
import com.smartfind.app.SmartFindApplication
import com.smartfind.app.data.EventLogger
import com.smartfind.app.util.AudioHelper

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isStopping = false
    private var isLowBattery = false
    private var isTestAlarm = false
    private var alarmStartTime: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: SettingsManager

    companion object {
        private const val TAG = "AlarmService"
        const val ACTION_STOP_ALARM = "com.smartfind.app.STOP_ALARM"
        const val ACTION_ALARM_STATE_CHANGED = "com.smartfind.app.ALARM_STATE_CHANGED"

        /**
         * In-memory running state, queryable without the deprecated
         * [android.app.ActivityManager.getRunningServices] call.
         * Set to true when the alarm starts in [onStartCommand] and
         * reset to false in [stopAlarm]/[onDestroy].
         */
        @Volatile
        var isRunning: Boolean = false
            private set
        const val EXTRA_TRIGGER_SENDER = "trigger_sender"
        const val EXTRA_LOW_BATTERY = "low_battery"
        const val EXTRA_IS_TEST = "is_test"
        const val EXTRA_START_ALARM = "start_alarm"
        private const val NOTIFICATION_ID = 1001
        private const val TRIGGER_NOTIFICATION_ID = 1002
        private const val CAR_MODE_NOTIFICATION_ID = 1003
        private const val PHONE_CALL_NOTIFICATION_ID = 1004
        private const val FALLBACK_NOTIFICATION_ID = 1007
        private const val WAKELOCK_TIMEOUT = 5 * 60 * 1000L // 5 minutes safety timeout
        /** Minimum time the alarm must play before it can be stopped (prevents instant silencing). */
        private const val MIN_ALARM_DURATION_MS = 5000L

        /**
         * Strips Unicode control characters, format characters, and bidirectional
         * overrides from sender strings to prevent visual spoofing in notifications.
         */
        internal fun sanitizeSender(sender: String): String {
            return sender.replace(Regex("[\\p{Cc}\\p{Cf}\\p{Co}]"), "").trim()
        }

        /**
         * Resolves a display-friendly sender string from the stored number
         * and raw carrier sender address. If a contact name is stored,
         * the result is "ContactName (sender)"; otherwise just the sender.
         */
        private fun resolveSenderDisplay(
            context: Context,
            storedNumber: String,
            senderNumber: String
        ): String {
            val settings = SettingsManager(context)
            val safeSender = sanitizeSender(senderNumber)
            val contactName = settings.getContactName(storedNumber)
            return if (contactName != null) {
                "${sanitizeSender(contactName)} ($safeSender)"
            } else {
                safeSender
            }
        }

        /**
         * Applies lock screen privacy to a notification builder when enabled.
         * Shows a generic message on the lock screen and full details only
         * when the device is unlocked.
         */
        private fun applyLockScreenPrivacy(
            context: Context,
            builder: NotificationCompat.Builder,
            channel: String,
            publicTitle: String,
            publicText: String,
            extraConfig: (NotificationCompat.Builder.() -> Unit)? = null
        ) {
            val settings = SettingsManager(context)
            if (!settings.isLockScreenPrivacyEnabled()) return

            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            val publicBuilder = NotificationCompat.Builder(context, channel)
                .setContentTitle(publicTitle)
                .setContentText(publicText)
                .setSmallIcon(R.drawable.ic_notification)
            extraConfig?.invoke(publicBuilder)
            builder.setPublicVersion(publicBuilder.build())
        }

        /**
         * Posts a persistent notification showing who triggered the alarm.
         * This notification stays after the alarm is stopped so the user
         * knows which contact activated SmartFind.
         *
         * @param storedNumber The number as stored in settings (used for contact name lookup)
         * @param senderNumber The raw sender number from the carrier (shown as fallback)
         */
        fun postTriggerNotification(context: Context, storedNumber: String, senderNumber: String) {
            val displaySender = resolveSenderDisplay(context, storedNumber, senderNumber)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context, 2, openIntent, PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, SmartFindApplication.CHANNEL_TRIGGER)
                .setContentTitle("SmartFind Alarm Triggered")
                .setContentText("Triggered by $displaySender")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Triggered by $displaySender"))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)

            applyLockScreenPrivacy(
                context, builder, SmartFindApplication.CHANNEL_TRIGGER,
                publicTitle = "SmartFind Alarm Triggered",
                publicText = "Unlock to see details"
            ) {
                setPriority(NotificationCompat.PRIORITY_HIGH)
                setCategory(NotificationCompat.CATEGORY_ALARM)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(TRIGGER_NOTIFICATION_ID, builder.build())
        }

        /**
         * Posts a silent notification when a trigger is received while in car mode
         * or during a phone call. No alarm is sounded for safety.
         *
         * @param reason Human-readable suppression reason (e.g. "Android Auto" or "active call")
         * @param isCarMode True for car mode suppression, false for phone call suppression.
         *        Uses separate notification IDs so one doesn't overwrite the other.
         */
        fun postSilentSuppressionNotification(
            context: Context,
            storedNumber: String,
            senderNumber: String,
            reason: String,
            isCarMode: Boolean
        ) {
            val displaySender = resolveSenderDisplay(context, storedNumber, senderNumber)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context, 3, openIntent, PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, SmartFindApplication.CHANNEL_TRIGGER)
                .setContentTitle("SmartFind: Trigger received")
                .setContentText("From $displaySender. Alarm suppressed for safety.")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Triggered by $displaySender. The loud alarm was suppressed for safety ($reason). Review when safe."))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSilent(true)

            applyLockScreenPrivacy(
                context, builder, SmartFindApplication.CHANNEL_TRIGGER,
                publicTitle = "SmartFind: Trigger received",
                publicText = "Unlock to see details"
            ) {
                setPriority(NotificationCompat.PRIORITY_DEFAULT)
                setSilent(true)
            }

            val notificationId = if (isCarMode) CAR_MODE_NOTIFICATION_ID else PHONE_CALL_NOTIFICATION_ID
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, builder.build())
        }

        /**
         * Emergency fallback when [Context.startForegroundService] is blocked
         * by Android's foreground service launch restrictions (Android 12+).
         *
         * Posts a high-priority notification with a full-screen intent that
         * launches [MainActivity]. From the foreground Activity the alarm
         * service can then be started normally.
         *
         * The notification uses the alarm channel (IMPORTANCE_HIGH, bypasses
         * DND) so it will make the default notification sound and vibrate,
         * providing at least some alert even if the full alarm can't start.
         */
        fun postEmergencyFallbackNotification(context: Context, storedNumber: String, senderNumber: String) {
            val displaySender = resolveSenderDisplay(context, storedNumber, senderNumber)

            // Full-screen intent launches MainActivity which can start the alarm
            // from the foreground context
            val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_START_ALARM, true)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, 6, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, SmartFindApplication.CHANNEL_ALARM)
                .setContentTitle("SmartFind: Trigger received!")
                .setContentText("Triggered by $displaySender \u2014 tap to activate alarm")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Triggered by $displaySender. The alarm could not start automatically due to system restrictions. Tap this notification to activate it."))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            applyLockScreenPrivacy(
                context, builder, SmartFindApplication.CHANNEL_ALARM,
                publicTitle = "SmartFind: Trigger received!",
                publicText = "Tap to activate alarm"
            ) {
                setFullScreenIntent(fullScreenPendingIntent, true)
                setPriority(NotificationCompat.PRIORITY_MAX)
                setCategory(NotificationCompat.CATEGORY_ALARM)
                setDefaults(NotificationCompat.DEFAULT_ALL)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(FALLBACK_NOTIFICATION_ID, builder.build())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        settings = SettingsManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand|action=${intent?.action}|extras=${intent?.extras?.keySet()}")

        if (intent?.action == ACTION_STOP_ALARM) {
            Log.d(TAG, "ACTION_STOP_ALARM received")
            stopAlarm()
            return START_NOT_STICKY
        }

        // Prevent duplicate alarms — use in-memory state (mediaPlayer),
        // not SharedPreferences. The persisted isAlarmActive() flag can be
        // stale if the app was killed while the alarm was playing.
        if (mediaPlayer != null) {
            Log.d(TAG, "alarm_already_active|skipping (mediaPlayer != null)")
            return START_NOT_STICKY
        }

        isLowBattery = intent?.getBooleanExtra(EXTRA_LOW_BATTERY, false) ?: false
        isTestAlarm = intent?.getBooleanExtra(EXTRA_IS_TEST, false) ?: false

        Log.d(TAG, "starting_alarm|isTest=$isTestAlarm|isLowBattery=$isLowBattery")

        isRunning = true
        alarmStartTime = System.currentTimeMillis()
        startForegroundNotification()
        Log.d(TAG, "foreground_notification=OK")
        acquireWakeLock()
        Log.d(TAG, "wake_lock=OK")
        startAlarmSound()
        // Note: settings.setAlarmActive(true) is set eagerly by
        // TriggerProcessor.startAlarm() before startForegroundService().
        // No need to set it again here. For test alarms (started from
        // MainActivity), we set it now since TriggerProcessor isn't involved.
        if (isTestAlarm) {
            settings.setAlarmActive(true)
        }
        Log.d(TAG, "alarm_active=true")

        // Real triggers confirm numbers are actively used; test alarms do not
        if (!isTestAlarm) {
            settings.setNumbersConfirmedTimestamp(System.currentTimeMillis())
            SmartFindApplication.scheduleNumbersReminder(this)
        }

        // Launch MainActivity over lock screen
        val launchIntent = Intent(this, MainActivity::class.java)
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        startActivity(launchIntent)

        // Broadcast alarm state change for UI update
        sendBroadcast(Intent(ACTION_ALARM_STATE_CHANGED).setPackage(packageName))

        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        // Intent to open the app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop the alarm
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, SmartFindApplication.CHANNEL_ALARM)
            .setContentTitle("SmartFind Alarm Active")
            .setContentText("Tap STOP to silence the alarm")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_notification, "STOP ALARM", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(openPendingIntent, true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SmartFind::AlarmWakeLock"
        ).apply {
            acquire(WAKELOCK_TIMEOUT)
        }
    }

    private fun startAlarmSound() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // Save current audio state
        AudioHelper.saveState(audioManager, settings)

        if (!isLowBattery) {
            // Normal mode: override to max alarm volume
            AudioHelper.maximizeAlarm(audioManager)
        }
        // Low battery mode: keep current volume level (don't maximize)

        val alarmVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        Log.d(TAG, "audio|alarm_vol=$alarmVol/$maxVol|ringer_mode=${audioManager.ringerMode}")

        // Create and start MediaPlayer
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            Log.d(TAG, "alarm_uri=$alarmUri")

            if (alarmUri == null) {
                Log.e(TAG, "alarm_uri=NULL|no system alarm or ringtone URI available")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                Log.d(TAG, "setAudioAttributes=OK")

                setDataSource(this@AlarmService, alarmUri)
                Log.d(TAG, "setDataSource=OK")

                // Low battery: play once (no loop) to conserve power
                // Normal: loop continuously until user stops it
                isLooping = !isLowBattery
                Log.d(TAG, "isLooping=$isLooping")

                prepare()
                Log.d(TAG, "prepare=OK")

                start()
                Log.d(TAG, "MediaPlayer.start=OK|isPlaying=$isPlaying")
            }
        } catch (e: Exception) {
            Log.e(TAG, "startAlarmSound FAILED|${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun stopAlarm() {
        Log.d(TAG, "stopAlarm|isStopping=$isStopping|isTest=$isTestAlarm")
        if (isStopping) return

        // Enforce minimum alarm duration to prevent instant silencing from lock screen.
        // Test alarms skip this — the user deliberately started and wants to stop it.
        if (!isTestAlarm) {
            val elapsed = System.currentTimeMillis() - alarmStartTime
            if (alarmStartTime > 0 && elapsed < MIN_ALARM_DURATION_MS) {
                val remaining = MIN_ALARM_DURATION_MS - elapsed
                Log.d(TAG, "min_duration_not_met|elapsed=${elapsed}ms|deferring=${remaining}ms")
                handler.postDelayed({ stopAlarm() }, remaining)
                return
            }
        }

        isStopping = true

        // Stop and release MediaPlayer
        val wasPlaying = try { mediaPlayer?.isPlaying } catch (_: Exception) { null }
        Log.d(TAG, "stopping_media|mediaPlayer=${mediaPlayer != null}|wasPlaying=$wasPlaying")
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: IllegalStateException) {
                // Expected if MediaPlayer is in an invalid state
            }
        }
        mediaPlayer = null

        // Restore audio settings
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        AudioHelper.restoreState(audioManager, settings)

        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        // Update state and log
        isRunning = false
        settings.setAlarmActive(false)
        settings.setAlarmStoppedTimestamp(System.currentTimeMillis())
        EventLogger.logAlarmStopped(this)
        sendBroadcast(Intent(ACTION_ALARM_STATE_CHANGED).setPackage(packageName))
        Log.d(TAG, "alarm_stopped|state_cleaned_up")

        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "service_stopped")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy|isStopping=$isStopping|mediaPlayer=${mediaPlayer != null}")
        stopAlarm()
        super.onDestroy()
    }
}
