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

package com.smartfind.app.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import com.smartfind.app.MainActivity
import com.smartfind.app.R
import com.smartfind.app.SettingsManager
import com.smartfind.app.SmartFindApplication

class NumbersReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = SettingsManager(context)

        // Only notify if there are phone numbers configured
        if (settings.getPhoneNumbers().isEmpty()) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SmartFindApplication.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SmartFind: Confirm Your Numbers")
            .setContentText("It's been 6 months. Please verify your designated phone numbers are still correct.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "It's been 6 months since you last confirmed your designated phone numbers. " +
                "Open SmartFind to verify they are still correct."
            ))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        // Schedule the next reminder in 6 months
        SmartFindApplication.scheduleNumbersReminder(context)
    }

    companion object {
        const val NOTIFICATION_ID = 2001
    }
}
