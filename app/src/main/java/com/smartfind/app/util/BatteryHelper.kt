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
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

/**
 * Checks battery state to determine whether alarm/location behavior
 * should be reduced to conserve power.
 *
 * Two independent checks:
 * - [isLowBattery]: battery level <= 15% — alarm plays at current volume,
 *   not looping, no location SMS sent.
 * - [isPowerSaveMode]: Android battery saver is ON — SmsReceiver should
 *   be disabled at the system level (handled by [BatterySaverMonitor]).
 */
object BatteryHelper {

    /** Battery percentage at or below which low-battery protections kick in. */
    const val LOW_BATTERY_THRESHOLD = 15

    /**
     * Returns true when battery level is at or below [LOW_BATTERY_THRESHOLD]%.
     *
     * Uses the sticky [Intent.ACTION_BATTERY_CHANGED] broadcast which is
     * always available without registering a receiver.
     */
    fun isLowBattery(context: Context): Boolean {
        val percentage = getBatteryPercentage(context)
        return percentage in 0..LOW_BATTERY_THRESHOLD
    }

    /**
     * Returns the current battery percentage (0-100), or -1 if unavailable.
     * Useful for logging/debugging.
     */
    fun getBatteryPercentage(context: Context): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let {
            context.registerReceiver(null, it)
        }
        if (batteryStatus == null) return -1

        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        if (level < 0 || scale <= 0) return -1

        return (level * 100) / scale
    }

    /**
     * Returns true when Android's battery saver (power save mode) is active.
     */
    fun isPowerSaveMode(context: Context): Boolean {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return false
        return powerManager.isPowerSaveMode
    }
}
