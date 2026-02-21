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

import android.media.AudioManager
import com.smartfind.app.SettingsManager

object AudioHelper {

    fun saveState(audioManager: AudioManager, settings: SettingsManager) {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val currentRingerMode = audioManager.ringerMode
        settings.saveAudioState(currentVolume, currentRingerMode)
    }

    fun maximizeAlarm(audioManager: AudioManager) {
        // Override ringer mode to normal (bypass silent/vibrate)
        try {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        } catch (_: SecurityException) {
            // May fail if DND access not granted - alarm still plays via STREAM_ALARM
        }

        // Set alarm volume to maximum
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            maxVolume,
            0 // No flags - silent volume change
        )
    }

    fun restoreState(audioManager: AudioManager, settings: SettingsManager) {
        val savedVolume = settings.getSavedVolume()
        val savedRingerMode = settings.getSavedRingerMode()

        if (savedVolume >= 0) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
            } catch (_: Exception) { }
        }

        try {
            audioManager.ringerMode = savedRingerMode
        } catch (_: SecurityException) { }
    }
}
