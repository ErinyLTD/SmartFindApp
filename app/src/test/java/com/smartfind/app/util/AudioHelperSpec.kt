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
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAudioManager
import com.smartfind.app.testing.robolectric.RobolectricTest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import com.smartfind.app.SettingsManager
import com.smartfind.app.util.AudioHelper

@RobolectricTest
class AudioHelperSpec : BehaviorSpec({

    lateinit var context: Context
    lateinit var audioManager: AudioManager
    lateinit var shadowAudioManager: ShadowAudioManager
    lateinit var settings: SettingsManager

    beforeEach {
        context = ApplicationProvider.getApplicationContext()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowAudioManager = Shadows.shadowOf(audioManager)
        SettingsManager.resetCachedPrefsForTesting()
        context.getSharedPreferences("smartfind_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("smartfind_prefs_plain", Context.MODE_PRIVATE).edit().clear().apply()
        settings = SettingsManager(context)
    }

    Given("saveState stores volume and ringer mode") {

        When("current volume is 5 and ringer mode is normal") {
            Then("saved state should reflect those values") {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 5, 0)
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

                AudioHelper.saveState(audioManager, settings)

                settings.getSavedVolume() shouldBe 5
                settings.getSavedRingerMode() shouldBe AudioManager.RINGER_MODE_NORMAL
            }
        }

        When("current volume is zero") {
            Then("saved volume should be zero") {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

                AudioHelper.saveState(audioManager, settings)

                settings.getSavedVolume() shouldBe 0
                settings.getSavedRingerMode() shouldBe AudioManager.RINGER_MODE_NORMAL
            }
        }

        When("ringer mode is silent") {
            Then("saved ringer mode should be silent") {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 3, 0)
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT

                AudioHelper.saveState(audioManager, settings)

                settings.getSavedVolume() shouldBe 3
                settings.getSavedRingerMode() shouldBe AudioManager.RINGER_MODE_SILENT
            }
        }

        When("saveState is called twice with different values") {
            Then("it should overwrite previous saved state") {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 3, 0)
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                AudioHelper.saveState(audioManager, settings)

                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 7, 0)
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                AudioHelper.saveState(audioManager, settings)

                settings.getSavedVolume() shouldBe 7
                settings.getSavedRingerMode() shouldBe AudioManager.RINGER_MODE_VIBRATE
            }
        }
    }

    Given("maximizeAlarm sets volume to max") {

        When("maximizeAlarm is called") {
            Then("alarm volume should be set to max") {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 3, 0)

                AudioHelper.maximizeAlarm(audioManager)

                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.getStreamVolume(AudioManager.STREAM_ALARM) shouldBe maxVolume
            }
        }

        When("maximizeAlarm is called with ringer in vibrate mode") {
            Then("ringer mode should be set to normal") {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE

                AudioHelper.maximizeAlarm(audioManager)

                audioManager.ringerMode shouldBe AudioManager.RINGER_MODE_NORMAL
            }
        }

        When("maximizeAlarm is called and ringer is normal") {
            Then("ringer mode should remain normal and volume should be max") {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)

                AudioHelper.maximizeAlarm(audioManager)

                audioManager.ringerMode shouldBe AudioManager.RINGER_MODE_NORMAL
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.getStreamVolume(AudioManager.STREAM_ALARM) shouldBe maxVolume
            }
        }

        When("volume is already at max") {
            Then("it should remain at max") {
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

                AudioHelper.maximizeAlarm(audioManager)

                audioManager.getStreamVolume(AudioManager.STREAM_ALARM) shouldBe maxVolume
                audioManager.ringerMode shouldBe AudioManager.RINGER_MODE_NORMAL
            }
        }
    }

    Given("restoreState restores saved values") {

        When("state was saved and then restored") {
            Then("volume and ringer mode should match saved values") {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 4, 0)
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                AudioHelper.saveState(audioManager, settings)

                AudioHelper.maximizeAlarm(audioManager)

                AudioHelper.restoreState(audioManager, settings)

                audioManager.getStreamVolume(AudioManager.STREAM_ALARM) shouldBe 4
                audioManager.ringerMode shouldBe AudioManager.RINGER_MODE_VIBRATE
            }
        }

        When("no state was previously saved") {
            Then("it should not change current values") {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 5, 0)
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

                val volumeBefore = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                val ringerBefore = audioManager.ringerMode

                AudioHelper.restoreState(audioManager, settings)

                audioManager.getStreamVolume(AudioManager.STREAM_ALARM) shouldBe volumeBefore
                audioManager.ringerMode shouldBe ringerBefore
            }
        }

        When("saved state was silent mode") {
            Then("it should restore to silent mode") {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                AudioHelper.saveState(audioManager, settings)

                AudioHelper.maximizeAlarm(audioManager)

                AudioHelper.restoreState(audioManager, settings)

                audioManager.getStreamVolume(AudioManager.STREAM_ALARM) shouldBe 0
                audioManager.ringerMode shouldBe AudioManager.RINGER_MODE_SILENT
            }
        }
    }

    Given("full cycle: save, maximize, restore") {

        When("performing a complete save-maximize-restore cycle") {
            Then("audio state should return to original values") {
                val originalVolume = 3
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

                AudioHelper.saveState(audioManager, settings)

                AudioHelper.maximizeAlarm(audioManager)

                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.getStreamVolume(AudioManager.STREAM_ALARM) shouldBe maxVolume
                audioManager.ringerMode shouldBe AudioManager.RINGER_MODE_NORMAL

                AudioHelper.restoreState(audioManager, settings)

                audioManager.getStreamVolume(AudioManager.STREAM_ALARM) shouldBe originalVolume
                audioManager.ringerMode shouldBe AudioManager.RINGER_MODE_NORMAL
            }
        }

        When("performing a full cycle from silent mode") {
            Then("audio state should return to silent mode") {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT

                AudioHelper.saveState(audioManager, settings)

                AudioHelper.maximizeAlarm(audioManager)

                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.getStreamVolume(AudioManager.STREAM_ALARM) shouldBe maxVolume
                audioManager.ringerMode shouldBe AudioManager.RINGER_MODE_NORMAL

                AudioHelper.restoreState(audioManager, settings)

                audioManager.getStreamVolume(AudioManager.STREAM_ALARM) shouldBe 0
                audioManager.ringerMode shouldBe AudioManager.RINGER_MODE_SILENT
            }
        }
    }
})
