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

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.smartfind.app.receiver.BatterySaverMonitor
import com.smartfind.app.receiver.NotificationDismissReceiver
import com.smartfind.app.receiver.NumbersReminderReceiver
import com.smartfind.app.util.DeviceStateHelper

import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartFindApplication : Application() {

    companion object {
        const val CHANNEL_ALARM = "smartfind_alarm"
        const val CHANNEL_TRIGGER = "smartfind_trigger"
        const val CHANNEL_SERVICE = "smartfind_service"
        const val CHANNEL_REMINDER = "smartfind_reminder"
        private const val REMINDER_REQUEST_CODE = 3001

        fun scheduleNumbersReminder(context: Context) {
            val settings = SettingsManager(context)
            if (settings.getPhoneNumbers().isEmpty()) return

            val lastConfirmed = settings.getNumbersConfirmedTimestamp()
            val triggerAt = if (lastConfirmed > 0) {
                lastConfirmed + SettingsManager.SIX_MONTHS_MS
            } else {
                System.currentTimeMillis() + SettingsManager.SIX_MONTHS_MS
            }

            val intent = Intent(context, NumbersReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REMINDER_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(AlarmManager::class.java)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }

        internal const val SERVICE_ACTIVE_NOTIFICATION_ID = 4001

        /**
         * Shows or hides a persistent notification indicating that SmartFind
         * is actively monitoring SMS. This makes the service visible to the
         * phone owner so it cannot be configured covertly.
         */
        fun updateServiceActiveNotification(context: Context) {
            val settings = SettingsManager(context)
            val notificationManager =
                context.getSystemService(NotificationManager::class.java)

            if (!settings.isServiceEnabled()) {
                notificationManager.cancel(SERVICE_ACTIVE_NOTIFICATION_ID)
                return
            }

            // After the phone owner has unlocked the device enough times,
            // they have clearly seen the notification — stop re-showing it.
            if (settings.getServiceUnlockCount() >= SettingsManager.SERVICE_NOTIFICATION_UNLOCK_THRESHOLD) {
                return
            }

            // On Android 13+, POST_NOTIFICATIONS permission is required for
            // non-foreground-service notifications. Skip if not yet granted
            // (the notification will appear once the user grants the permission
            // and the method is called again).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val count = settings.getPhoneNumbers().size
            val contactWord = if (count == 1) "contact" else "contacts"

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 4, openIntent, PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(NotificationDismissReceiver.ACTION_NOTIFICATION_DISMISSED)
                .setPackage(context.packageName)
            val deletePendingIntent = PendingIntent.getBroadcast(
                context, SERVICE_ACTIVE_NOTIFICATION_ID, dismissIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
                .setContentTitle("SmartFind is active")
                .setContentText("Monitoring SMS from $count designated $contactWord")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)

            // Lock screen privacy — hide contact count on lock screen
            if (settings.isLockScreenPrivacyEnabled()) {
                builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                builder.setPublicVersion(
                    NotificationCompat.Builder(context, CHANNEL_SERVICE)
                        .setContentTitle("SmartFind is active")
                        .setContentText("SMS monitoring enabled")
                        .setSmallIcon(R.drawable.ic_notification)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setSilent(true)
                        .build()
                )
            }

            val notification = builder.build()

            notificationManager.notify(SERVICE_ACTIVE_NOTIFICATION_ID, notification)
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }

        createNotificationChannels()

        // Warm the SharedPreferences cache on a background thread.
        // EncryptedSharedPreferences performs AndroidKeyStore crypto during
        // construction which can take several seconds on some devices.
        // By starting this early on a background thread, it will typically
        // complete before any Activity calls SettingsManager(context).
        Thread({
            SettingsManager.getOrCreatePrefs(applicationContext)
        }, "SmartFind-PrefsInit").start()

        DeviceStateHelper.register(this)
        BatterySaverMonitor.register(this)
        BatterySaverMonitor.syncReceiverState(this)
        scheduleNumbersReminder(this)
        updateServiceActiveNotification(this)
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            "SmartFind Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm triggered by SMS"
            setBypassDnd(true)
            enableVibration(true)
            setSound(null, null) // We handle sound via MediaPlayer
        }

        val triggerChannel = NotificationChannel(
            CHANNEL_TRIGGER,
            "SmartFind Trigger Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notification when alarm is triggered by a contact"
            enableVibration(true)
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "SmartFind Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background SMS monitoring"
        }

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "SmartFind Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Periodic reminders to confirm phone numbers"
        }

        notificationManager.createNotificationChannel(alarmChannel)
        notificationManager.createNotificationChannel(triggerChannel)
        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(reminderChannel)
    }
}
