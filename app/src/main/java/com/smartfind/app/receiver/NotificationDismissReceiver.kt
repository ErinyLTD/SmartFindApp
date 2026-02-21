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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartfind.app.SettingsManager

/**
 * Handles the user swiping away the "SmartFind is active" notification.
 * Sets the unlock counter to the threshold so the notification is not
 * re-shown by [com.smartfind.app.SmartFindApplication.updateServiceActiveNotification].
 */
class NotificationDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_NOTIFICATION_DISMISSED) return
        val settings = SettingsManager(context)
        // Mark as fully acknowledged so it won't be re-shown
        repeat(SettingsManager.SERVICE_NOTIFICATION_UNLOCK_THRESHOLD - settings.getServiceUnlockCount()) {
            settings.incrementServiceUnlockCount()
        }
    }

    companion object {
        const val ACTION_NOTIFICATION_DISMISSED = "com.smartfind.app.NOTIFICATION_DISMISSED"
    }
}
