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

/**
 * Process-level atomic guard to prevent concurrent alarm starts
 * from SmsReceiver and SmsCheckWorker racing on the same SMS message.
 *
 * Usage:
 *   if (AlarmLock.tryAcquire()) {
 *       try { startAlarm() } finally { AlarmLock.release() }
 *   }
 */
object AlarmLock {
    @Volatile
    private var locked = false

    /**
     * Attempts to acquire the lock. Returns true if successful,
     * false if another component is already starting an alarm.
     */
    @Synchronized
    fun tryAcquire(): Boolean {
        if (locked) return false
        locked = true
        return true
    }

    /**
     * Releases the lock after the alarm start sequence completes.
     */
    @Synchronized
    fun release() {
        locked = false
    }
}
