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
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.smartfind.app.SettingsManager
import com.smartfind.app.testing.robolectric.RobolectricTest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.string.shouldEndWith

/**
 * Kotest BehaviorSpec for [TriggerProcessor].
 *
 * Tests the production guard chain (runGuardChecks), trigger handling
 * (handleTriggerMatch post-match checks), and body redaction (redactBody).
 *
 * Uses Robolectric for Android Context access (SettingsManager, DeviceStateHelper).
 */
@RobolectricTest
class TriggerProcessorSpec : BehaviorSpec({

    lateinit var context: Context
    lateinit var settings: SettingsManager
    lateinit var processor: TriggerProcessor

    /**
     * Sets DeviceStateHelper to simulate "not actively in use" state.
     * In Robolectric, PowerManager.isInteractive=true and KeyguardManager
     * reports unlocked, so the only way to make isDeviceActivelyInUse()
     * return false is to set screenOnElapsedRealtime to "just now" so
     * isScreenRecentlyWoken() returns true (simulates notification wake).
     */
    fun simulateNotActivelyInUse() {
        DeviceStateHelper.screenOnElapsedRealtime = SystemClock.elapsedRealtime()
    }

    /**
     * Sets DeviceStateHelper to simulate "actively in use" state.
     * Screen is on (Robolectric default), unlocked (Robolectric default),
     * and screen has been on longer than the grace period.
     */
    fun simulateActivelyInUse() {
        DeviceStateHelper.screenOnElapsedRealtime =
            SystemClock.elapsedRealtime() - DeviceStateHelper.SCREEN_WAKE_GRACE_PERIOD_MS - 10_000L
    }

    beforeEach {
        context = ApplicationProvider.getApplicationContext()
        // Reset the singleton cache so each test gets a fresh SharedPreferences
        // instance and doesn't leak state from a previous test.
        SettingsManager.resetCachedPrefsForTesting()
        // Clear both the encrypted-path and plain-fallback prefs files to
        // ensure no state leaks between tests regardless of which backend
        // SettingsManager.createPrefs() resolves to.
        context.getSharedPreferences("smartfind_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
        context.getSharedPreferences("smartfind_prefs_plain", Context.MODE_PRIVATE)
            .edit().clear().apply()
        settings = SettingsManager(context)
        processor = TriggerProcessor(context, settings)

        // Reset AlarmLock state between tests
        AlarmLock.release()

        // Default to "not actively in use" — most tests need guards to pass
        simulateNotActivelyInUse()
    }

    // ==========================================
    // runGuardChecks — Guard 1: Service enabled
    // ==========================================

    Given("runGuardChecks with service disabled") {

        When("the service is not enabled") {
            Then("it returns SERVICE_DISABLED") {
                settings.setServiceEnabled(false)
                val result = processor.runGuardChecks("test-cid")
                result.passed.shouldBeFalse()
                result.failureReason shouldBe TriggerProcessor.GuardFailure.SERVICE_DISABLED
            }
        }

        When("the service is not enabled but skipServiceCheck is true") {
            Then("it skips the service check and continues to next guard") {
                settings.setServiceEnabled(false)
                // Set up remaining config so other guards pass
                settings.addPhoneNumber("+447834120123")
                settings.setTriggerKeyword("FIND")
                val result = processor.runGuardChecks("test-cid", skipServiceCheck = true)
                // Should NOT fail with SERVICE_DISABLED
                if (!result.passed) {
                    (result.failureReason != TriggerProcessor.GuardFailure.SERVICE_DISABLED).shouldBeTrue()
                }
            }
        }

        When("the service is enabled") {
            Then("it passes the service check") {
                settings.setServiceEnabled(true)
                settings.addPhoneNumber("+447834120123")
                settings.setTriggerKeyword("FIND")
                val result = processor.runGuardChecks("test-cid")
                // Either passes or fails on a different guard
                if (!result.passed) {
                    (result.failureReason != TriggerProcessor.GuardFailure.SERVICE_DISABLED).shouldBeTrue()
                }
            }
        }
    }

    // ==========================================
    // runGuardChecks — Guard 2: Active use protection
    // ==========================================

    Given("runGuardChecks with active use protection") {

        When("device is actively in use (screen on, unlocked, past grace period)") {
            Then("it returns ACTIVE_USE") {
                settings.setServiceEnabled(true)
                simulateActivelyInUse()

                val result = processor.runGuardChecks("test-cid")
                result.passed.shouldBeFalse()
                result.failureReason shouldBe TriggerProcessor.GuardFailure.ACTIVE_USE
            }
        }

        When("device screen is on but within grace period") {
            Then("it passes the active use check (treats as notification wake)") {
                settings.setServiceEnabled(true)
                settings.addPhoneNumber("+447834120123")
                settings.setTriggerKeyword("FIND")
                simulateNotActivelyInUse()

                val result = processor.runGuardChecks("test-cid")
                // Should not fail with ACTIVE_USE
                if (!result.passed) {
                    (result.failureReason != TriggerProcessor.GuardFailure.ACTIVE_USE).shouldBeTrue()
                }
            }
        }
    }

    // ==========================================
    // runGuardChecks — Guard 3: Cooldown
    // ==========================================

    Given("runGuardChecks with cooldown") {

        When("cooldown is active (alarm recently stopped)") {
            Then("it returns COOLDOWN") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                settings.setCooldownMinutes(5)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis() - 60_000) // 1 min ago
                val result = processor.runGuardChecks("test-cid")
                result.passed.shouldBeFalse()
                result.failureReason shouldBe TriggerProcessor.GuardFailure.COOLDOWN
            }
        }

        When("cooldown has expired") {
            Then("it passes the cooldown check") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                settings.setCooldownMinutes(5)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis() - 10 * 60_000) // 10 min ago
                settings.addPhoneNumber("+447834120123")
                settings.setTriggerKeyword("FIND")
                val result = processor.runGuardChecks("test-cid")
                if (!result.passed) {
                    (result.failureReason != TriggerProcessor.GuardFailure.COOLDOWN).shouldBeTrue()
                }
            }
        }
    }

    // ==========================================
    // runGuardChecks — Guard 4: Rate limit
    // ==========================================

    Given("runGuardChecks with rate limiting") {

        When("rate limit is exceeded (5+ triggers in window)") {
            Then("it returns RATE_LIMITED") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                val now = System.currentTimeMillis()
                repeat(5) { i ->
                    settings.recordTriggerTimestamp(now - (i + 1) * 1000L)
                }
                val result = processor.runGuardChecks("test-cid")
                result.passed.shouldBeFalse()
                result.failureReason shouldBe TriggerProcessor.GuardFailure.RATE_LIMITED
            }
        }

        When("rate limit is not exceeded (fewer than 5 triggers)") {
            Then("it passes the rate limit check") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                settings.addPhoneNumber("+447834120123")
                settings.setTriggerKeyword("FIND")
                val now = System.currentTimeMillis()
                repeat(3) { i ->
                    settings.recordTriggerTimestamp(now - (i + 1) * 1000L)
                }
                val result = processor.runGuardChecks("test-cid")
                if (!result.passed) {
                    (result.failureReason != TriggerProcessor.GuardFailure.RATE_LIMITED).shouldBeTrue()
                }
            }
        }
    }

    // ==========================================
    // runGuardChecks — Config: designated numbers
    // ==========================================

    Given("runGuardChecks with no designated numbers") {

        When("no phone numbers are configured") {
            Then("it returns NO_DESIGNATED_NUMBERS") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                settings.setTriggerKeyword("FIND")
                // Don't add any phone numbers
                val result = processor.runGuardChecks("test-cid")
                result.passed.shouldBeFalse()
                result.failureReason shouldBe TriggerProcessor.GuardFailure.NO_DESIGNATED_NUMBERS
            }
        }
    }

    // ==========================================
    // runGuardChecks — Config: keyword
    // ==========================================

    Given("runGuardChecks with no keyword") {

        When("keyword is blank") {
            Then("it returns NO_KEYWORD") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                settings.addPhoneNumber("+447834120123")
                settings.setTriggerKeyword("   ") // blank after trim
                val result = processor.runGuardChecks("test-cid")
                result.passed.shouldBeFalse()
                result.failureReason shouldBe TriggerProcessor.GuardFailure.NO_KEYWORD
            }
        }
    }

    // ==========================================
    // runGuardChecks — All guards pass
    // ==========================================

    Given("runGuardChecks with all guards passing") {

        When("service is enabled, no active use, no cooldown, no rate limit, numbers and keyword configured") {
            Then("it returns a passing result with designatedNumbers and keyword") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                settings.addPhoneNumber("+447834120123")
                settings.addPhoneNumber("+40741234567")
                settings.setTriggerKeyword("LOCATE")

                val result = processor.runGuardChecks("test-cid")
                result.passed.shouldBeTrue()
                result.failureReason.shouldBeNull()
                result.designatedNumbers shouldBe setOf("+447834120123", "+40741234567")
                result.keyword shouldBe "LOCATE"
            }
        }

        When("skipServiceCheck is true and service is disabled") {
            Then("it still passes if all other guards pass") {
                settings.setServiceEnabled(false) // disabled, but skipped
                simulateNotActivelyInUse()
                settings.addPhoneNumber("+447834120123")
                settings.setTriggerKeyword("FIND")

                val result = processor.runGuardChecks("test-cid", skipServiceCheck = true)
                result.passed.shouldBeTrue()
                result.failureReason.shouldBeNull()
                result.designatedNumbers shouldBe setOf("+447834120123")
                result.keyword shouldBe "FIND"
            }
        }
    }

    // ==========================================
    // runGuardChecks — Guard ordering
    // ==========================================

    Given("runGuardChecks guard ordering") {

        When("service disabled AND cooldown active") {
            Then("SERVICE_DISABLED is returned first") {
                settings.setServiceEnabled(false)
                settings.setCooldownMinutes(5)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis())
                val result = processor.runGuardChecks("test-cid")
                result.failureReason shouldBe TriggerProcessor.GuardFailure.SERVICE_DISABLED
            }
        }

        When("cooldown active AND rate limited AND no numbers") {
            Then("COOLDOWN is returned (checked before rate limit)") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                settings.setCooldownMinutes(5)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis())
                val now = System.currentTimeMillis()
                repeat(5) { settings.recordTriggerTimestamp(now - it * 1000L) }
                val result = processor.runGuardChecks("test-cid")
                result.failureReason shouldBe TriggerProcessor.GuardFailure.COOLDOWN
            }
        }

        When("rate limited AND no designated numbers AND no keyword") {
            Then("RATE_LIMITED is returned (checked before config)") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                val now = System.currentTimeMillis()
                repeat(5) { settings.recordTriggerTimestamp(now - it * 1000L) }
                settings.setTriggerKeyword("  ")
                val result = processor.runGuardChecks("test-cid")
                result.failureReason shouldBe TriggerProcessor.GuardFailure.RATE_LIMITED
            }
        }

        When("no designated numbers AND no keyword") {
            Then("NO_DESIGNATED_NUMBERS is returned (checked before keyword)") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                settings.setTriggerKeyword("  ")
                val result = processor.runGuardChecks("test-cid")
                result.failureReason shouldBe TriggerProcessor.GuardFailure.NO_DESIGNATED_NUMBERS
            }
        }
    }

    // ==========================================
    // runGuardChecks — GuardResult data integrity
    // ==========================================

    Given("GuardResult data integrity") {

        When("guards fail") {
            Then("designatedNumbers and keyword are empty defaults") {
                settings.setServiceEnabled(false)
                val result = processor.runGuardChecks("test-cid")
                result.passed.shouldBeFalse()
                result.designatedNumbers shouldBe emptySet()
                result.keyword shouldBe ""
            }
        }

        When("guards pass") {
            Then("designatedNumbers and keyword are populated from settings") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                settings.addPhoneNumber("+15551234567")
                settings.setTriggerKeyword("HELP")
                val result = processor.runGuardChecks("test-cid")
                result.passed.shouldBeTrue()
                result.designatedNumbers shouldBe setOf("+15551234567")
                result.keyword shouldBe "HELP"
            }
        }
    }

    // ==========================================
    // handleTriggerMatch — Car mode suppression
    // ==========================================

    Given("handleTriggerMatch with car mode") {

        // Note: In Robolectric, UiModeManager.currentModeType defaults to
        // UI_MODE_TYPE_NORMAL, so car mode is NOT active by default.

        When("car mode is not active") {
            Then("it does not return SUPPRESSED_CAR_MODE") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                val outcome = processor.handleTriggerMatch(
                    matchedNumber = "+447834120123",
                    senderAddress = "+447834120123",
                    cid = "test-cid"
                )
                (outcome != TriggerProcessor.TriggerOutcome.SUPPRESSED_CAR_MODE).shouldBeTrue()
            }
        }
    }

    // ==========================================
    // handleTriggerMatch — Phone call suppression
    // ==========================================

    Given("handleTriggerMatch with phone call") {

        // Note: In Robolectric, TelephonyManager.callState defaults to
        // CALL_STATE_IDLE, so phone call is NOT active by default.

        When("phone call is not active") {
            Then("it does not return SUPPRESSED_PHONE_CALL") {
                settings.setServiceEnabled(true)
                simulateNotActivelyInUse()
                val outcome = processor.handleTriggerMatch(
                    matchedNumber = "+447834120123",
                    senderAddress = "+447834120123",
                    cid = "test-cid"
                )
                (outcome != TriggerProcessor.TriggerOutcome.SUPPRESSED_PHONE_CALL).shouldBeTrue()
            }
        }
    }

    // ==========================================
    // handleTriggerMatch — Alarm already active
    // ==========================================

    Given("handleTriggerMatch when alarm is already active") {

        When("alarm is already active") {
            Then("it returns ALREADY_ACTIVE") {
                settings.setAlarmActive(true)
                val outcome = processor.handleTriggerMatch(
                    matchedNumber = "+447834120123",
                    senderAddress = "+447834120123",
                    cid = "test-cid"
                )
                outcome shouldBe TriggerProcessor.TriggerOutcome.ALREADY_ACTIVE
            }
        }
    }

    // ==========================================
    // handleTriggerMatch — AlarmLock not acquired
    // ==========================================

    Given("handleTriggerMatch when AlarmLock is already held") {

        When("AlarmLock.tryAcquire() returns false") {
            Then("it returns LOCK_NOT_ACQUIRED") {
                settings.setAlarmActive(false)
                // Acquire the lock first so the processor can't get it
                AlarmLock.tryAcquire().shouldBeTrue()
                val outcome = processor.handleTriggerMatch(
                    matchedNumber = "+447834120123",
                    senderAddress = "+447834120123",
                    cid = "test-cid"
                )
                outcome shouldBe TriggerProcessor.TriggerOutcome.LOCK_NOT_ACQUIRED
                AlarmLock.release() // cleanup
            }
        }
    }

    // ==========================================
    // handleTriggerMatch — Rate limit timestamp recording
    // ==========================================

    Given("handleTriggerMatch records rate limit timestamp after car/phone checks") {

        When("a trigger match proceeds past car mode and phone call checks") {
            Then("a rate limit timestamp is recorded") {
                settings.setAlarmActive(false)
                val beforeCount = settings.getRateLimitTimestamps().size

                // The alarm start will fail (no real AlarmService in Robolectric)
                // but recordTriggerTimestamp should still be called before that
                try {
                    processor.handleTriggerMatch(
                        matchedNumber = "+447834120123",
                        senderAddress = "+447834120123",
                        cid = "test-cid"
                    )
                } catch (_: Exception) {
                    // Expected — startForegroundService may fail in test
                }

                val afterCount = settings.getRateLimitTimestamps().size
                (afterCount > beforeCount).shouldBeTrue()
            }
        }
    }

    // ==========================================
    // handleTriggerMatch — Eager alarm active flag
    // ==========================================

    Given("handleTriggerMatch sets alarmActive eagerly") {

        When("alarm start fails (foreground service blocked)") {
            Then("alarmActive is rolled back to false") {
                settings.setAlarmActive(false)

                val outcome = processor.handleTriggerMatch(
                    matchedNumber = "+447834120123",
                    senderAddress = "+447834120123",
                    cid = "test-cid"
                )

                // In Robolectric, startForegroundService may or may not throw
                // but alarmActive should be set during the attempt
                // If it succeeded, alarmActive stays true; if it failed, it rolls back
                if (outcome == TriggerProcessor.TriggerOutcome.ALARM_FALLBACK) {
                    settings.isAlarmActive().shouldBeFalse()
                }
                // If it succeeded as ALARM_STARTED, alarmActive stays true
                if (outcome == TriggerProcessor.TriggerOutcome.ALARM_STARTED) {
                    settings.isAlarmActive().shouldBeTrue()
                }
            }
        }
    }

    // ==========================================
    // handleTriggerMatch — AlarmLock is always released
    // ==========================================

    Given("handleTriggerMatch always releases AlarmLock") {

        When("alarm start succeeds or fails") {
            Then("AlarmLock is released so subsequent triggers can proceed") {
                settings.setAlarmActive(false)

                try {
                    processor.handleTriggerMatch(
                        matchedNumber = "+447834120123",
                        senderAddress = "+447834120123",
                        cid = "test-cid"
                    )
                } catch (_: Exception) {
                    // ignored
                }

                // After handleTriggerMatch, the lock should be released
                // We can verify by trying to acquire it
                AlarmLock.tryAcquire().shouldBeTrue()
                AlarmLock.release() // cleanup
            }
        }
    }

    // ==========================================
    // handleTriggerMatch — beforeAlarmStart callback
    // ==========================================

    Given("handleTriggerMatch invokes beforeAlarmStart callback") {

        When("a callback is provided and alarm is not already active") {
            Then("the callback is invoked before starting the service") {
                settings.setAlarmActive(false)
                var callbackInvoked = false

                try {
                    processor.handleTriggerMatch(
                        matchedNumber = "+447834120123",
                        senderAddress = "+447834120123",
                        cid = "test-cid",
                        beforeAlarmStart = { callbackInvoked = true }
                    )
                } catch (_: Exception) {
                    // ignored
                }

                callbackInvoked.shouldBeTrue()
            }
        }

        When("alarm is already active") {
            Then("the callback is NOT invoked") {
                settings.setAlarmActive(true)
                var callbackInvoked = false

                processor.handleTriggerMatch(
                    matchedNumber = "+447834120123",
                    senderAddress = "+447834120123",
                    cid = "test-cid",
                    beforeAlarmStart = { callbackInvoked = true }
                )

                callbackInvoked.shouldBeFalse()
            }
        }
    }

    // ==========================================
    // redactBody
    // ==========================================

    Given("redactBody") {

        When("the body is short (6 chars or fewer)") {
            Then("it returns '***' to prevent leaking short keywords") {
                processor.redactBody("FIND") shouldBe "***"
                processor.redactBody("") shouldBe "***"
                processor.redactBody("A") shouldBe "***"
                processor.redactBody("ABCDEF") shouldBe "***"
            }
        }

        When("the body is longer than 6 chars") {
            Then("it shows first 2 and last 2 chars with length") {
                processor.redactBody("Hello World") shouldBe "He...ld(11)"
                processor.redactBody("ABCDEFG") shouldBe "AB...FG(7)"
            }
        }

        When("the body is exactly 7 chars") {
            Then("it shows the redacted form") {
                processor.redactBody("1234567") shouldBe "12...67(7)"
            }
        }

        When("the body contains Unicode") {
            Then("it redacts correctly") {
                val body = "Hello \uD83D\uDE00 World!"
                val result = processor.redactBody(body)
                result shouldStartWith "He"
                result shouldContain "..."
                result shouldEndWith "(${body.length})"
            }
        }
    }

    // ==========================================
    // TriggerOutcome enum values
    // ==========================================

    Given("TriggerOutcome enum completeness") {

        When("checking all enum values") {
            Then("all expected outcomes exist") {
                val values = TriggerProcessor.TriggerOutcome.values()
                values.map { it.name }.toSet() shouldBe setOf(
                    "ALARM_STARTED",
                    "ALARM_FALLBACK",
                    "SUPPRESSED_CAR_MODE",
                    "SUPPRESSED_PHONE_CALL",
                    "ALREADY_ACTIVE",
                    "LOCK_NOT_ACQUIRED"
                )
            }
        }
    }

    // ==========================================
    // GuardFailure enum values
    // ==========================================

    Given("GuardFailure enum completeness") {

        When("checking all enum values") {
            Then("all expected guard failures exist") {
                val values = TriggerProcessor.GuardFailure.values()
                values.map { it.name }.toSet() shouldBe setOf(
                    "SERVICE_DISABLED",
                    "ACTIVE_USE",
                    "COOLDOWN",
                    "RATE_LIMITED",
                    "NO_DESIGNATED_NUMBERS",
                    "NO_KEYWORD"
                )
            }
        }
    }
})
