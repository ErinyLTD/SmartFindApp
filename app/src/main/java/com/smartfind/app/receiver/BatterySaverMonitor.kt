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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.smartfind.app.SettingsManager

/**
 * Dynamically registered BroadcastReceiver that enables/disables
 * the manifest-registered [SmsReceiver] component based on service state.
 *
 * Registration/unregistration is handled in SmartFindApplication.onCreate()
 * and BootReceiver.
 */
object BatterySaverMonitor {

    private var receiver: BroadcastReceiver? = null

    /**
     * Registers the dynamic receiver. Safe to call multiple times —
     * will unregister any previous instance first.
     */
    fun register(context: Context) {
        unregister(context)

        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                    syncReceiverState(ctx)
                }
            }
        }

        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        ContextCompat.registerReceiver(
            context.applicationContext,
            newReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiver = newReceiver

        // Sync immediately on registration
        syncReceiverState(context)
    }

    /**
     * Unregisters the dynamic receiver if currently registered.
     */
    fun unregister(context: Context) {
        receiver?.let {
            try {
                context.applicationContext.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }
        receiver = null
    }

    /**
     * Enables or disables the manifest-registered [SmsReceiver] component
     * based on service enabled state.
     */
    fun syncReceiverState(context: Context) {
        val settings = SettingsManager(context)
        val pm = context.packageManager
        val componentName = ComponentName(context, SmsReceiver::class.java)

        val newState = if (!settings.isServiceEnabled()) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }

        pm.setComponentEnabledSetting(
            componentName,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }
}
