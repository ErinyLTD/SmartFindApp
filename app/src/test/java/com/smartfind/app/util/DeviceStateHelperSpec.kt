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

import android.app.KeyguardManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.telephony.TelephonyManager
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.smartfind.app.testing.robolectric.RobolectricTest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.string.shouldContain

@RobolectricTest
class DeviceStateHelperSpec : BehaviorSpec({

    lateinit var context: Context
    lateinit var powerManager: PowerManager
    lateinit var keyguardManager: KeyguardManager
    lateinit var uiModeManager: UiModeManager
    lateinit var telephonyManager: TelephonyManager
    lateinit var audioManager: AudioManager

    fun setFullyUnlocked() {
        whenever(keyguardManager.isDeviceLocked).thenReturn(false)
        whenever(keyguardManager.isKeyguardLocked).thenReturn(false)
    }

    fun setSecureLocked() {
        whenever(keyguardManager.isDeviceLocked).thenReturn(true)
        whenever(keyguardManager.isKeyguardLocked).thenReturn(true)
    }

    fun setSmartLockBypassed() {
        whenever(keyguardManager.isDeviceLocked).thenReturn(false)
        whenever(keyguardManager.isKeyguardLocked).thenReturn(true)
    }

    beforeEach {
        context = mock()
        powerManager = mock()
        keyguardManager = mock()
        uiModeManager = mock()
        telephonyManager = mock()
        audioManager = mock()

        whenever(context.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager)
        whenever(context.getSystemService(Context.KEYGUARD_SERVICE)).thenReturn(keyguardManager)
        whenever(context.getSystemService(Context.UI_MODE_SERVICE)).thenReturn(uiModeManager)
        whenever(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(telephonyManager)
        whenever(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(audioManager)

        DeviceStateHelper.screenOnElapsedRealtime = 0L
    }

    // ==========================================
    // isDeviceActivelyInUse — original behavior
    // ==========================================

    Given("isDeviceActivelyInUse - original behavior") {

        When("screen is on, unlocked, and screen on long enough") {
            Then("actively in use") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setFullyUnlocked()
                DeviceStateHelper.screenOnElapsedRealtime =
                    SystemClock.elapsedRealtime() - DeviceStateHelper.SCREEN_WAKE_GRACE_PERIOD_MS - 1000
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeTrue()
            }
        }

        When("screen is off and device is unlocked") {
            Then("not actively in use") {
                whenever(powerManager.isInteractive).thenReturn(false)
                setFullyUnlocked()
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }

        When("screen is on and device is secure locked") {
            Then("not actively in use") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setSecureLocked()
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }

        When("screen is off and device is locked") {
            Then("not actively in use") {
                whenever(powerManager.isInteractive).thenReturn(false)
                setSecureLocked()
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }
    }

    // ==========================================
    // isDeviceActivelyInUse — Smart Lock scenarios
    // ==========================================

    Given("isDeviceActivelyInUse - Smart Lock scenarios") {

        When("Smart Lock bypasses secure lock but keyguard is showing") {
            Then("not actively in use") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setSmartLockBypassed()
                DeviceStateHelper.screenOnElapsedRealtime =
                    SystemClock.elapsedRealtime() - DeviceStateHelper.SCREEN_WAKE_GRACE_PERIOD_MS - 1000
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }

        When("Smart Lock active and screen on for 30 seconds") {
            Then("not actively in use") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setSmartLockBypassed()
                DeviceStateHelper.screenOnElapsedRealtime =
                    SystemClock.elapsedRealtime() - 30_000
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }

        When("AOD on and Smart Lock bypassed") {
            Then("not actively in use") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setSmartLockBypassed()
                DeviceStateHelper.screenOnElapsedRealtime =
                    SystemClock.elapsedRealtime() - 60_000
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }
    }

    // ==========================================
    // isDeviceActivelyInUse — screen wake grace period
    // ==========================================

    Given("isDeviceActivelyInUse - screen wake grace period") {

        When("screen just turned on (within grace period)") {
            Then("not actively in use") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setFullyUnlocked()
                DeviceStateHelper.screenOnElapsedRealtime = SystemClock.elapsedRealtime()
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }

        When("screen turned on 1 second ago") {
            Then("not actively in use") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setFullyUnlocked()
                DeviceStateHelper.screenOnElapsedRealtime =
                    SystemClock.elapsedRealtime() - 1000
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }

        When("screen has been on past grace period") {
            Then("actively in use") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setFullyUnlocked()
                DeviceStateHelper.screenOnElapsedRealtime =
                    SystemClock.elapsedRealtime() - 5000
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeTrue()
            }
        }

        When("screen on timestamp is zero (no data - receiver not registered)") {
            Then("not actively in use (safe default — allow alarm to fire)") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setFullyUnlocked()
                DeviceStateHelper.screenOnElapsedRealtime = 0L
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }

        When("screen recently woken even if unlocked (notification wake 500ms ago)") {
            Then("not actively in use") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setFullyUnlocked()
                DeviceStateHelper.screenOnElapsedRealtime =
                    SystemClock.elapsedRealtime() - 500
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }

        When("screen is off but screenOnElapsedRealtime indicates recent wake") {
            Then("grace period does not apply when screen is off") {
                whenever(powerManager.isInteractive).thenReturn(false)
                setFullyUnlocked()
                DeviceStateHelper.screenOnElapsedRealtime = SystemClock.elapsedRealtime()
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }

        When("screen on and recently woken but device is locked") {
            Then("grace period does not apply when device is locked") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setSecureLocked()
                DeviceStateHelper.screenOnElapsedRealtime =
                    SystemClock.elapsedRealtime() - DeviceStateHelper.SCREEN_WAKE_GRACE_PERIOD_MS - 1000
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }
    }

    // ==========================================
    // isScreenRecentlyWoken
    // ==========================================

    Given("isScreenRecentlyWoken") {

        When("screenOnElapsedRealtime is 0 (no data)") {
            Then("returns false") {
                DeviceStateHelper.screenOnElapsedRealtime = 0L
                DeviceStateHelper.isScreenRecentlyWoken().shouldBeFalse()
            }
        }

        When("screen was just turned on") {
            Then("returns true within grace period") {
                DeviceStateHelper.screenOnElapsedRealtime = SystemClock.elapsedRealtime()
                DeviceStateHelper.isScreenRecentlyWoken().shouldBeTrue()
            }
        }

        When("screen has been on past grace period") {
            Then("returns false after grace period") {
                DeviceStateHelper.screenOnElapsedRealtime =
                    SystemClock.elapsedRealtime() - DeviceStateHelper.SCREEN_WAKE_GRACE_PERIOD_MS - 1
                DeviceStateHelper.isScreenRecentlyWoken().shouldBeFalse()
            }
        }
    }

    // ==========================================
    // getScreenOnDurationMs
    // ==========================================

    Given("getScreenOnDurationMs") {

        When("screenOnElapsedRealtime is 0 (no data)") {
            Then("returns -1") {
                DeviceStateHelper.screenOnElapsedRealtime = 0L
                DeviceStateHelper.getScreenOnDurationMs() shouldBe -1L
            }
        }

        When("screen has been on for 5 seconds") {
            Then("returns positive value") {
                DeviceStateHelper.screenOnElapsedRealtime =
                    SystemClock.elapsedRealtime() - 5000
                DeviceStateHelper.getScreenOnDurationMs() shouldBeGreaterThanOrEqual 5000
            }
        }
    }

    // ==========================================
    // isScreenOn
    // ==========================================

    Given("isScreenOn") {

        When("power manager reports interactive") {
            Then("returns true") {
                whenever(powerManager.isInteractive).thenReturn(true)
                DeviceStateHelper.isScreenOn(context).shouldBeTrue()
            }
        }

        When("power manager reports not interactive") {
            Then("returns false") {
                whenever(powerManager.isInteractive).thenReturn(false)
                DeviceStateHelper.isScreenOn(context).shouldBeFalse()
            }
        }

        When("PowerManager is null") {
            Then("returns false") {
                whenever(context.getSystemService(Context.POWER_SERVICE)).thenReturn(null)
                DeviceStateHelper.isScreenOn(context).shouldBeFalse()
            }
        }
    }

    // ==========================================
    // isDeviceLocked
    // ==========================================

    Given("isDeviceLocked") {

        When("secure keyguard is locked") {
            Then("returns true") {
                whenever(keyguardManager.isDeviceLocked).thenReturn(true)
                whenever(keyguardManager.isKeyguardLocked).thenReturn(true)
                DeviceStateHelper.isDeviceLocked(context).shouldBeTrue()
            }
        }

        When("device is fully unlocked") {
            Then("returns false") {
                whenever(keyguardManager.isDeviceLocked).thenReturn(false)
                whenever(keyguardManager.isKeyguardLocked).thenReturn(false)
                DeviceStateHelper.isDeviceLocked(context).shouldBeFalse()
            }
        }

        When("KeyguardManager is null") {
            Then("returns true (safe default)") {
                whenever(context.getSystemService(Context.KEYGUARD_SERVICE)).thenReturn(null)
                DeviceStateHelper.isDeviceLocked(context).shouldBeTrue()
            }
        }

        When("keyguard showing but Smart Lock bypassed") {
            Then("returns true") {
                whenever(keyguardManager.isDeviceLocked).thenReturn(false)
                whenever(keyguardManager.isKeyguardLocked).thenReturn(true)
                DeviceStateHelper.isDeviceLocked(context).shouldBeTrue()
            }
        }

        When("only secure lock active (edge case)") {
            Then("returns true") {
                whenever(keyguardManager.isDeviceLocked).thenReturn(true)
                whenever(keyguardManager.isKeyguardLocked).thenReturn(false)
                DeviceStateHelper.isDeviceLocked(context).shouldBeTrue()
            }
        }
    }

    // ==========================================
    // isInCarMode
    // ==========================================

    Given("isInCarMode") {

        When("UI mode is car") {
            Then("returns true") {
                whenever(uiModeManager.currentModeType).thenReturn(Configuration.UI_MODE_TYPE_CAR)
                DeviceStateHelper.isInCarMode(context).shouldBeTrue()
            }
        }

        When("UI mode is normal") {
            Then("returns false") {
                whenever(uiModeManager.currentModeType).thenReturn(Configuration.UI_MODE_TYPE_NORMAL)
                DeviceStateHelper.isInCarMode(context).shouldBeFalse()
            }
        }

        When("UI mode is television") {
            Then("returns false") {
                whenever(uiModeManager.currentModeType).thenReturn(Configuration.UI_MODE_TYPE_TELEVISION)
                DeviceStateHelper.isInCarMode(context).shouldBeFalse()
            }
        }

        When("UI mode is desk") {
            Then("returns false") {
                whenever(uiModeManager.currentModeType).thenReturn(Configuration.UI_MODE_TYPE_DESK)
                DeviceStateHelper.isInCarMode(context).shouldBeFalse()
            }
        }

        When("UiModeManager is null") {
            Then("returns false") {
                whenever(context.getSystemService(Context.UI_MODE_SERVICE)).thenReturn(null)
                DeviceStateHelper.isInCarMode(context).shouldBeFalse()
            }
        }
    }

    // ==========================================
    // Edge cases: actively in use with null managers
    // ==========================================

    Given("isDeviceActivelyInUse - null manager edge cases") {

        When("PowerManager is null") {
            Then("not actively in use") {
                whenever(context.getSystemService(Context.POWER_SERVICE)).thenReturn(null)
                setFullyUnlocked()
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }

        When("KeyguardManager is null") {
            Then("not actively in use (safe default: locked)") {
                whenever(powerManager.isInteractive).thenReturn(true)
                whenever(context.getSystemService(Context.KEYGUARD_SERVICE)).thenReturn(null)
                DeviceStateHelper.isDeviceActivelyInUse(context).shouldBeFalse()
            }
        }
    }

    // ==========================================
    // isInPhoneCall — TelephonyManager checks
    // ==========================================

    Given("isInPhoneCall - TelephonyManager checks") {

        When("call state is OFFHOOK") {
            Then("returns true") {
                @Suppress("DEPRECATION")
                whenever(telephonyManager.callState).thenReturn(TelephonyManager.CALL_STATE_OFFHOOK)
                whenever(audioManager.mode).thenReturn(AudioManager.MODE_NORMAL)
                DeviceStateHelper.isInPhoneCall(context).shouldBeTrue()
            }
        }

        When("call state is RINGING") {
            Then("returns true") {
                @Suppress("DEPRECATION")
                whenever(telephonyManager.callState).thenReturn(TelephonyManager.CALL_STATE_RINGING)
                whenever(audioManager.mode).thenReturn(AudioManager.MODE_NORMAL)
                DeviceStateHelper.isInPhoneCall(context).shouldBeTrue()
            }
        }

        When("call state is IDLE and audio mode NORMAL") {
            Then("returns false") {
                @Suppress("DEPRECATION")
                whenever(telephonyManager.callState).thenReturn(TelephonyManager.CALL_STATE_IDLE)
                whenever(audioManager.mode).thenReturn(AudioManager.MODE_NORMAL)
                DeviceStateHelper.isInPhoneCall(context).shouldBeFalse()
            }
        }
    }

    // ==========================================
    // isInPhoneCall — AudioManager checks (VoIP)
    // ==========================================

    Given("isInPhoneCall - AudioManager checks (VoIP)") {

        When("audio mode is IN_CALL") {
            Then("returns true") {
                @Suppress("DEPRECATION")
                whenever(telephonyManager.callState).thenReturn(TelephonyManager.CALL_STATE_IDLE)
                whenever(audioManager.mode).thenReturn(AudioManager.MODE_IN_CALL)
                DeviceStateHelper.isInPhoneCall(context).shouldBeTrue()
            }
        }

        When("audio mode is IN_COMMUNICATION") {
            Then("returns true") {
                @Suppress("DEPRECATION")
                whenever(telephonyManager.callState).thenReturn(TelephonyManager.CALL_STATE_IDLE)
                whenever(audioManager.mode).thenReturn(AudioManager.MODE_IN_COMMUNICATION)
                DeviceStateHelper.isInPhoneCall(context).shouldBeTrue()
            }
        }

        When("audio mode is RINGTONE") {
            Then("returns false") {
                @Suppress("DEPRECATION")
                whenever(telephonyManager.callState).thenReturn(TelephonyManager.CALL_STATE_IDLE)
                whenever(audioManager.mode).thenReturn(AudioManager.MODE_RINGTONE)
                DeviceStateHelper.isInPhoneCall(context).shouldBeFalse()
            }
        }
    }

    // ==========================================
    // isInPhoneCall — null manager edge cases
    // ==========================================

    Given("isInPhoneCall - null manager edge cases") {

        When("TelephonyManager is null and audio mode NORMAL") {
            Then("returns false") {
                whenever(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(null)
                whenever(audioManager.mode).thenReturn(AudioManager.MODE_NORMAL)
                DeviceStateHelper.isInPhoneCall(context).shouldBeFalse()
            }
        }

        When("TelephonyManager is null but audio mode IN_CALL") {
            Then("returns true") {
                whenever(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(null)
                whenever(audioManager.mode).thenReturn(AudioManager.MODE_IN_CALL)
                DeviceStateHelper.isInPhoneCall(context).shouldBeTrue()
            }
        }

        When("AudioManager is null and call state IDLE") {
            Then("returns false") {
                @Suppress("DEPRECATION")
                whenever(telephonyManager.callState).thenReturn(TelephonyManager.CALL_STATE_IDLE)
                whenever(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(null)
                DeviceStateHelper.isInPhoneCall(context).shouldBeFalse()
            }
        }

        When("AudioManager is null but call state OFFHOOK") {
            Then("returns true") {
                @Suppress("DEPRECATION")
                whenever(telephonyManager.callState).thenReturn(TelephonyManager.CALL_STATE_OFFHOOK)
                whenever(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(null)
                DeviceStateHelper.isInPhoneCall(context).shouldBeTrue()
            }
        }

        When("both TelephonyManager and AudioManager are null") {
            Then("returns false") {
                whenever(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(null)
                whenever(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(null)
                DeviceStateHelper.isInPhoneCall(context).shouldBeFalse()
            }
        }
    }

    // ==========================================
    // isInPhoneCall — combined (both managers agree)
    // ==========================================

    Given("isInPhoneCall - combined checks") {

        When("both telephony OFFHOOK and audio IN_CALL") {
            Then("returns true") {
                @Suppress("DEPRECATION")
                whenever(telephonyManager.callState).thenReturn(TelephonyManager.CALL_STATE_OFFHOOK)
                whenever(audioManager.mode).thenReturn(AudioManager.MODE_IN_CALL)
                DeviceStateHelper.isInPhoneCall(context).shouldBeTrue()
            }
        }
    }

    // ==========================================
    // SCREEN_WAKE_GRACE_PERIOD_MS
    // ==========================================

    Given("SCREEN_WAKE_GRACE_PERIOD_MS constant") {

        When("the constant is checked") {
            Then("it is 3000") {
                DeviceStateHelper.SCREEN_WAKE_GRACE_PERIOD_MS shouldBe 3000L
            }
        }
    }

    // ==========================================
    // getActiveUseDetail — diagnostic output
    // ==========================================

    Given("getActiveUseDetail") {

        When("device is unlocked and screen recently woken") {
            Then("includes all state fields") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setFullyUnlocked()
                DeviceStateHelper.screenOnElapsedRealtime =
                    SystemClock.elapsedRealtime() - 500
                val detail = DeviceStateHelper.getActiveUseDetail(context)
                detail shouldContain "screen_on=true"
                detail shouldContain "locked=false"
                detail shouldContain "secure_locked=false"
                detail shouldContain "keyguard_locked=false"
                detail shouldContain "screen_on_for="
                detail shouldContain "recently_woken=true"
                detail shouldContain "grace_period="
            }
        }

        When("Smart Lock is active") {
            Then("shows Smart Lock state") {
                whenever(powerManager.isInteractive).thenReturn(true)
                setSmartLockBypassed()
                DeviceStateHelper.screenOnElapsedRealtime =
                    SystemClock.elapsedRealtime() - 30_000
                val detail = DeviceStateHelper.getActiveUseDetail(context)
                detail shouldContain "secure_locked=false"
                detail shouldContain "keyguard_locked=true"
                detail shouldContain "locked=true"
            }
        }
    }
})
