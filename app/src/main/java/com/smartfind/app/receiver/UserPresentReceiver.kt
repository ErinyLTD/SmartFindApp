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

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartfind.app.SettingsManager
import com.smartfind.app.SmartFindApplication

/**
 * Counts device unlocks while the service is active. After
 * [SettingsManager.SERVICE_NOTIFICATION_UNLOCK_THRESHOLD] unlocks the
 * persistent "SmartFind is active" notification is dismissed — the phone
 * owner has clearly seen it.
 */
class UserPresentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_USER_PRESENT) return

        val settings = SettingsManager(context)
        if (!settings.isServiceEnabled()) return

        val count = settings.getServiceUnlockCount() + 1
        settings.incrementServiceUnlockCount()

        if (count >= SettingsManager.SERVICE_NOTIFICATION_UNLOCK_THRESHOLD) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.cancel(SmartFindApplication.SERVICE_ACTIVE_NOTIFICATION_ID)
        }
    }
}
