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

import com.smartfind.app.util.KeywordMatcher
import com.smartfind.app.util.PhoneNumberHelper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse

/**
 * Kotest BehaviorSpec for the SMS trigger matching logic used in SmsReceiver and SmsCheckWorker.
 *
 * Tests the combination of keyword matching + number matching + guard chain that determines
 * whether an incoming SMS should trigger the alarm, suppress, or produce an alternative action.
 *
 * Keyword matching is CASE SENSITIVE and exact. The keyword is stored
 * as uppercase, so the SMS body (trimmed) must be exactly equal to the
 * keyword to trigger. "FINDING" does NOT match keyword "FIND".
 *
 * Note: SMS spoof detection (`shouldSuppressTrigger`) is NOT modeled here because it
 * happens in SmsReceiver.onReceive() AFTER matching, not inside TriggerProcessor's
 * guard chain. See SmsReceiver for that logic.
 *
 * Low battery is NOT a separate result. Production starts the alarm normally and
 * passes `isLowBattery` as an intent extra to AlarmService, which adjusts volume
 * and looping behavior. The trigger evaluation result is always TRIGGER_ALARM.
 */
/** Result of the trigger evaluation -- mirrors the outcomes in TriggerProcessor. */
@Suppress("unused")
enum class TriggerResult {
    /** Alarm should fire (normal or low-battery mode, determined by isLowBattery flag). */
    TRIGGER_ALARM,
    /** Suppress entirely (active use, cooldown, rate limit, etc.). */
    SUPPRESS,
    /** Car mode -- silent notification only, no alarm. */
    CAR_MODE_SILENT,
    /** Phone call in progress -- silent notification, no loud alarm. */
    PHONE_CALL_SILENT
}

/** Enum capturing why a guard rejected a trigger. Mirrors TriggerProcessor.GuardFailure. */
enum class GuardFailure {
    SERVICE_DISABLED,
    ACTIVE_USE,
    COOLDOWN,
    RATE_LIMITED,
    NO_DESIGNATED_NUMBERS,
    NO_KEYWORD,
    NO_MATCH
}

class SmsTriggerLogicSpec : BehaviorSpec({

    /**
     * Simulates the trigger check logic from TriggerProcessor.runGuardChecks()
     * and TriggerProcessor.handleTriggerMatch() — the guard chain that determines
     * whether an incoming SMS should trigger the alarm or be suppressed.
     *
     * Note: Low battery is not modeled as a separate result. Production always
     * returns TRIGGER_ALARM and passes isLowBattery as an intent extra.
     */
    fun evaluateTrigger(
        designatedNumbers: Set<String>,
        keyword: String,
        senderNumber: String,
        messageBody: String,
        serviceEnabled: Boolean = true,
        isDeviceActivelyInUse: Boolean = false,
        activeUseProtectionEnabled: Boolean = true,
        isCooldownActive: Boolean = false,
        isRateLimited: Boolean = false,
        isInCarMode: Boolean = false,
        carModeProtectionEnabled: Boolean = true,
        isInPhoneCall: Boolean = false,
        phoneCallProtectionEnabled: Boolean = true
    ): TriggerResult {
        if (!serviceEnabled) return TriggerResult.SUPPRESS
        if (activeUseProtectionEnabled && isDeviceActivelyInUse) return TriggerResult.SUPPRESS
        if (isCooldownActive) return TriggerResult.SUPPRESS
        if (isRateLimited) return TriggerResult.SUPPRESS
        if (designatedNumbers.isEmpty()) return TriggerResult.SUPPRESS
        if (keyword.isBlank()) return TriggerResult.SUPPRESS

        val matchedNumber = designatedNumbers.firstOrNull { designated ->
            PhoneNumberHelper.numbersMatch(designated, senderNumber)
        }

        if (matchedNumber == null || !KeywordMatcher.matchesKeyword(messageBody, keyword)) {
            return TriggerResult.SUPPRESS
        }

        if (carModeProtectionEnabled && isInCarMode) return TriggerResult.CAR_MODE_SILENT
        if (phoneCallProtectionEnabled && isInPhoneCall) return TriggerResult.PHONE_CALL_SILENT

        return TriggerResult.TRIGGER_ALARM
    }

    /**
     * Extended evaluator that returns which guard failed, for detailed assertions.
     * Mirrors the guard order in TriggerProcessor.runGuardChecks().
     */
    fun evaluateGuardFailure(
        designatedNumbers: Set<String>,
        keyword: String,
        senderNumber: String,
        messageBody: String,
        serviceEnabled: Boolean = true,
        isDeviceActivelyInUse: Boolean = false,
        activeUseProtectionEnabled: Boolean = true,
        isCooldownActive: Boolean = false,
        isRateLimited: Boolean = false
    ): GuardFailure? {
        if (!serviceEnabled) return GuardFailure.SERVICE_DISABLED
        if (activeUseProtectionEnabled && isDeviceActivelyInUse) return GuardFailure.ACTIVE_USE
        if (isCooldownActive) return GuardFailure.COOLDOWN
        if (isRateLimited) return GuardFailure.RATE_LIMITED
        if (designatedNumbers.isEmpty()) return GuardFailure.NO_DESIGNATED_NUMBERS
        if (keyword.isBlank()) return GuardFailure.NO_KEYWORD

        val matchedNumber = designatedNumbers.firstOrNull { designated ->
            PhoneNumberHelper.numbersMatch(designated, senderNumber)
        }

        if (matchedNumber == null || !KeywordMatcher.matchesKeyword(messageBody, keyword)) {
            return GuardFailure.NO_MATCH
        }

        return null // no guard failed -- trigger would proceed
    }

    /**
     * Legacy helper -- delegates to evaluateTrigger for backwards compatibility.
     */
    fun shouldTriggerAlarm(
        designatedNumbers: Set<String>,
        keyword: String,
        senderNumber: String,
        messageBody: String,
        serviceEnabled: Boolean = true
    ): Boolean {
        return evaluateTrigger(
            designatedNumbers = designatedNumbers,
            keyword = keyword,
            senderNumber = senderNumber,
            messageBody = messageBody,
            serviceEnabled = serviceEnabled
        ) == TriggerResult.TRIGGER_ALARM
    }

    // ==========================================
    // Basic trigger scenarios
    // ==========================================

    Given("basic trigger scenarios") {

        When("keyword and number both match") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("it should trigger the alarm") {
                result.shouldBeTrue()
            }
        }

        When("the message body is lowercase 'find'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "find"
            )
            Then("it should not trigger because matching is case-sensitive") {
                result.shouldBeFalse()
            }
        }

        When("the message body is mixed case 'Find my phone'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "Find my phone"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("the keyword appears in a longer message 'Please FIND my phone'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "Please FIND my phone"
            )
            Then("it should not trigger because exact match is required") {
                result.shouldBeFalse()
            }
        }

        When("the keyword is embedded in a longer message 'Please FIND my phone now'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "Please FIND my phone now"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("a local number matches international sender") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("07834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("it should trigger") {
                result.shouldBeTrue()
            }
        }

        When("a custom keyword 'LOCATE' is used") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "LOCATE",
                senderNumber = "+447834120123",
                messageBody = "LOCATE"
            )
            Then("it should trigger") {
                result.shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Non-trigger scenarios
    // ==========================================

    Given("non-trigger scenarios") {

        When("the service is disabled") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                serviceEnabled = false
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("there are no designated numbers") {
            val result = shouldTriggerAlarm(
                designatedNumbers = emptySet(),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("the keyword is blank") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("the sender is not a designated number") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+449999999999",
                messageBody = "FIND"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("the message does not contain the keyword") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "Hello, how are you?"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("the keyword partially matches 'FINDING'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FINDING"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("the message contains a different keyword 'LOCATE' instead of 'FIND'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "LOCATE"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }
    }

    // ==========================================
    // Multiple designated numbers
    // ==========================================

    Given("multiple designated numbers") {

        When("the sender matches one of multiple designated numbers") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123", "+40741234567", "+15551234567"),
                keyword = "FIND",
                senderNumber = "+40741234567",
                messageBody = "FIND"
            )
            Then("it should trigger") {
                result.shouldBeTrue()
            }
        }

        When("the sender matches none of the multiple designated numbers") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123", "+40741234567"),
                keyword = "FIND",
                senderNumber = "+15559999999",
                messageBody = "FIND"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }
    }

    // ==========================================
    // Contact format scenarios
    // ==========================================

    Given("contact format scenarios") {

        When("the contact is stored with spaces '078 3412 0123'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("078 3412 0123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("it should trigger") {
                result.shouldBeTrue()
            }
        }

        When("the contact is stored with dashes '078-3412-0123'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("078-3412-0123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("it should trigger") {
                result.shouldBeTrue()
            }
        }

        When("the contact is stored with 00 prefix '0044 7834 120123'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("0044 7834 120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("it should trigger") {
                result.shouldBeTrue()
            }
        }

        When("the contact is stored in US parentheses format '(555) 123-4567'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("(555) 123-4567"),
                keyword = "FIND",
                senderNumber = "+15551234567",
                messageBody = "FIND"
            )
            Then("it should trigger") {
                result.shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Edge cases
    // ==========================================

    Given("edge cases for trigger evaluation") {

        When("the keyword is spaces only '   '") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "   ",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("the message body is empty") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = ""
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("a very long keyword does not match in a longer message") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FINDMYPHONE",
                senderNumber = "+447834120123",
                messageBody = "Please FINDMYPHONE right now"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("a very long keyword matches the exact message") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FINDMYPHONE",
                senderNumber = "+447834120123",
                messageBody = "FINDMYPHONE"
            )
            Then("it should trigger") {
                result.shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Active use protection
    // ==========================================

    Given("active use protection") {

        When("the device is actively in use and protection is enabled") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isDeviceActivelyInUse = true,
                activeUseProtectionEnabled = true
            )
            Then("it should suppress") {
                result shouldBe TriggerResult.SUPPRESS
            }
        }

        When("the device is actively in use but protection is disabled") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isDeviceActivelyInUse = true,
                activeUseProtectionEnabled = false
            )
            Then("it should trigger the alarm") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }

        When("the device is not actively in use and protection is enabled") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isDeviceActivelyInUse = false,
                activeUseProtectionEnabled = true
            )
            Then("it should trigger the alarm") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }
    }

    // ==========================================
    // Trigger cooldown
    // ==========================================

    Given("trigger cooldown") {

        When("the cooldown is active") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isCooldownActive = true
            )
            Then("it should suppress") {
                result shouldBe TriggerResult.SUPPRESS
            }
        }

        When("the cooldown is not active") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isCooldownActive = false
            )
            Then("it should trigger the alarm") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }
    }

    // ==========================================
    // Car mode protection
    // ==========================================

    Given("car mode protection") {

        When("in car mode and protection is enabled") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isInCarMode = true,
                carModeProtectionEnabled = true
            )
            Then("it should return CAR_MODE_SILENT") {
                result shouldBe TriggerResult.CAR_MODE_SILENT
            }
        }

        When("in car mode but protection is disabled") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isInCarMode = true,
                carModeProtectionEnabled = false
            )
            Then("it should trigger the alarm") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }

        When("not in car mode and protection is enabled") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isInCarMode = false,
                carModeProtectionEnabled = true
            )
            Then("it should trigger the alarm") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }
    }

    // ==========================================
    // Guard priority / ordering
    // ==========================================
    // Note: GSM/SMS spoof detection is NOT tested here because it happens
    // in SmsReceiver.onReceive() AFTER matching, not inside TriggerProcessor's
    // guard chain. See SmsSpoofDetectorSpec for spoof detection tests.

    Given("guard priority and ordering") {

        When("service is disabled even if all other conditions pass") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                serviceEnabled = false,
                isDeviceActivelyInUse = false,
                isCooldownActive = false,
                isInCarMode = false
            )
            Then("it should suppress") {
                result shouldBe TriggerResult.SUPPRESS
            }
        }

        When("device is actively in use and also in car mode") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isDeviceActivelyInUse = true,
                activeUseProtectionEnabled = true,
                isInCarMode = true,
                carModeProtectionEnabled = true
            )
            Then("it should suppress because active use takes priority over car mode") {
                result shouldBe TriggerResult.SUPPRESS
            }
        }

        When("cooldown is active and also in car mode") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isCooldownActive = true,
                isInCarMode = true,
                carModeProtectionEnabled = true
            )
            Then("it should suppress because cooldown takes priority over car mode") {
                result shouldBe TriggerResult.SUPPRESS
            }
        }

        When("sender does not match but car mode is active") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+449999999999",
                messageBody = "FIND",
                isInCarMode = true,
                carModeProtectionEnabled = true
            )
            Then("it should suppress because car mode only applies after matching") {
                result shouldBe TriggerResult.SUPPRESS
            }
        }
    }

    // ==========================================
    // Combined scenarios
    // ==========================================

    Given("combined scenarios") {

        When("all protections are off but conditions are met") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                activeUseProtectionEnabled = false,
                carModeProtectionEnabled = false,
                isDeviceActivelyInUse = true,
                isInCarMode = true,
                isCooldownActive = false
            )
            Then("it should trigger normally because protections are disabled") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }

        When("all protections are enabled but no conditions are met") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                activeUseProtectionEnabled = true,
                carModeProtectionEnabled = true,
                isDeviceActivelyInUse = false,
                isInCarMode = false,
                isCooldownActive = false
            )
            Then("it should trigger normally") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }
    }

    // ==========================================
    // Rate limiting
    // ==========================================

    Given("rate limiting") {

        When("the trigger is rate limited") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isRateLimited = true
            )
            Then("it should suppress") {
                result shouldBe TriggerResult.SUPPRESS
            }
        }

        When("the trigger is not rate limited") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isRateLimited = false
            )
            Then("it should trigger the alarm") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }

        When("rate limiting and car mode are both active") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isRateLimited = true,
                isInCarMode = true,
                carModeProtectionEnabled = true
            )
            Then("it should suppress because rate limit takes priority over car mode") {
                result shouldBe TriggerResult.SUPPRESS
            }
        }
    }

    // ==========================================
    // Phone call protection
    // ==========================================

    Given("phone call protection") {

        When("in a phone call and protection is enabled") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isInPhoneCall = true,
                phoneCallProtectionEnabled = true
            )
            Then("it should return PHONE_CALL_SILENT") {
                result shouldBe TriggerResult.PHONE_CALL_SILENT
            }
        }

        When("in a phone call but protection is disabled") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isInPhoneCall = true,
                phoneCallProtectionEnabled = false
            )
            Then("it should trigger the alarm") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }

        When("not in a phone call and protection is enabled") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isInPhoneCall = false,
                phoneCallProtectionEnabled = true
            )
            Then("it should trigger the alarm") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }

        When("both car mode and phone call are active") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isInCarMode = true,
                carModeProtectionEnabled = true,
                isInPhoneCall = true,
                phoneCallProtectionEnabled = true
            )
            Then("it should return CAR_MODE_SILENT because car mode is checked first") {
                result shouldBe TriggerResult.CAR_MODE_SILENT
            }
        }
    }

    // ==========================================
    // Low battery behavior
    // ==========================================
    // Note: Low battery is NOT a separate trigger result. Production always returns
    // TRIGGER_ALARM and passes isLowBattery as an intent extra to AlarmService,
    // which adjusts volume (no maximize) and looping (plays once, not looping).
    // This section verifies that low battery does not affect trigger evaluation —
    // the decision is always TRIGGER_ALARM when guards pass and no car/phone suppression.

    Given("low battery behavior") {

        When("all guards pass regardless of battery state") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("it should return TRIGGER_ALARM (battery state is handled by AlarmService, not trigger logic)") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }

        When("phone call is active (battery state is irrelevant to suppression decision)") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isInPhoneCall = true,
                phoneCallProtectionEnabled = true
            )
            Then("it should return PHONE_CALL_SILENT regardless of battery") {
                result shouldBe TriggerResult.PHONE_CALL_SILENT
            }
        }

        When("car mode is active (battery state is irrelevant to suppression decision)") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isInCarMode = true,
                carModeProtectionEnabled = true
            )
            Then("it should return CAR_MODE_SILENT regardless of battery") {
                result shouldBe TriggerResult.CAR_MODE_SILENT
            }
        }
    }

    // ==========================================
    // Exact keyword matching (word boundary tests)
    // ==========================================

    Given("exact keyword matching with word boundaries") {

        When("'FIND' matches as standalone word") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("it should trigger") {
                result.shouldBeTrue()
            }
        }

        When("'FINDING something' does not match keyword 'FIND'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FINDING something"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("'REFIND it' does not match keyword 'FIND'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "REFIND it"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("'FIND!' with punctuation does not match keyword 'FIND'") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND!"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }

        When("'Please FIND my phone' -- keyword in middle of sentence") {
            val result = shouldTriggerAlarm(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "Please FIND my phone"
            )
            Then("it should not trigger") {
                result.shouldBeFalse()
            }
        }
    }

    // ==========================================
    // Full guard chain -- all protections active
    // ==========================================

    Given("full guard chain scenarios") {

        When("all protections enabled and no conditions met") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                serviceEnabled = true,
                isDeviceActivelyInUse = false,
                activeUseProtectionEnabled = true,
                isCooldownActive = false,
                isRateLimited = false,
                isInCarMode = false,
                carModeProtectionEnabled = true,
                isInPhoneCall = false,
                phoneCallProtectionEnabled = true
            )
            Then("it should trigger the alarm normally") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }

        When("all protections disabled and all conditions met") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                serviceEnabled = true,
                isDeviceActivelyInUse = true,
                activeUseProtectionEnabled = false,
                isCooldownActive = false,
                isRateLimited = false,
                isInCarMode = true,
                carModeProtectionEnabled = false,
                isInPhoneCall = true,
                phoneCallProtectionEnabled = false
            )
            Then("it should trigger the alarm normally because all protections are disabled") {
                result shouldBe TriggerResult.TRIGGER_ALARM
            }
        }

        When("suppression guards are checked before match logic") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isRateLimited = true,
                isCooldownActive = false
            )
            Then("it should suppress even though number and keyword match") {
                result shouldBe TriggerResult.SUPPRESS
            }
        }
    }

    // ==========================================
    // GuardFailure enum behavior
    // ==========================================

    Given("GuardFailure reporting when guards reject a trigger") {

        When("the service is disabled") {
            val failure = evaluateGuardFailure(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                serviceEnabled = false
            )
            Then("the failure reason should be SERVICE_DISABLED") {
                failure shouldBe GuardFailure.SERVICE_DISABLED
            }
        }

        When("the device is actively in use with protection enabled") {
            val failure = evaluateGuardFailure(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isDeviceActivelyInUse = true,
                activeUseProtectionEnabled = true
            )
            Then("the failure reason should be ACTIVE_USE") {
                failure shouldBe GuardFailure.ACTIVE_USE
            }
        }

        When("cooldown is active") {
            val failure = evaluateGuardFailure(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isCooldownActive = true
            )
            Then("the failure reason should be COOLDOWN") {
                failure shouldBe GuardFailure.COOLDOWN
            }
        }

        When("rate limiting is active") {
            val failure = evaluateGuardFailure(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isRateLimited = true
            )
            Then("the failure reason should be RATE_LIMITED") {
                failure shouldBe GuardFailure.RATE_LIMITED
            }
        }

        When("there are no designated numbers") {
            val failure = evaluateGuardFailure(
                designatedNumbers = emptySet(),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("the failure reason should be NO_DESIGNATED_NUMBERS") {
                failure shouldBe GuardFailure.NO_DESIGNATED_NUMBERS
            }
        }

        When("the keyword is blank") {
            val failure = evaluateGuardFailure(
                designatedNumbers = setOf("+447834120123"),
                keyword = "",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("the failure reason should be NO_KEYWORD") {
                failure shouldBe GuardFailure.NO_KEYWORD
            }
        }

        When("the sender does not match and keyword does not match") {
            val failure = evaluateGuardFailure(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+449999999999",
                messageBody = "Hello"
            )
            Then("the failure reason should be NO_MATCH") {
                failure shouldBe GuardFailure.NO_MATCH
            }
        }

        When("number matches but keyword does not match") {
            val failure = evaluateGuardFailure(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "LOCATE"
            )
            Then("the failure reason should be NO_MATCH") {
                failure shouldBe GuardFailure.NO_MATCH
            }
        }

        When("keyword matches but sender does not match") {
            val failure = evaluateGuardFailure(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+449999999999",
                messageBody = "FIND"
            )
            Then("the failure reason should be NO_MATCH") {
                failure shouldBe GuardFailure.NO_MATCH
            }
        }

        When("everything matches and no guards are triggered") {
            val failure = evaluateGuardFailure(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("the failure reason should be null (no guard failed)") {
                failure shouldBe null
            }
        }
    }

    // ==========================================
    // GuardFailure priority ordering
    // ==========================================

    Given("GuardFailure priority ordering when multiple guards would fail") {

        When("service is disabled AND active use AND cooldown all active") {
            val failure = evaluateGuardFailure(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                serviceEnabled = false,
                isDeviceActivelyInUse = true,
                activeUseProtectionEnabled = true,
                isCooldownActive = true
            )
            Then("the failure reason should be SERVICE_DISABLED because it is checked first") {
                failure shouldBe GuardFailure.SERVICE_DISABLED
            }
        }

        When("active use AND cooldown AND rate limiting all active") {
            val failure = evaluateGuardFailure(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isDeviceActivelyInUse = true,
                activeUseProtectionEnabled = true,
                isCooldownActive = true,
                isRateLimited = true
            )
            Then("the failure reason should be ACTIVE_USE because it is checked before cooldown and rate limit") {
                failure shouldBe GuardFailure.ACTIVE_USE
            }
        }

        When("cooldown AND rate limiting AND no designated numbers") {
            val failure = evaluateGuardFailure(
                designatedNumbers = emptySet(),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isCooldownActive = true,
                isRateLimited = true
            )
            Then("the failure reason should be COOLDOWN because it is checked before rate limit and designated numbers") {
                failure shouldBe GuardFailure.COOLDOWN
            }
        }

        When("rate limiting AND no designated numbers AND blank keyword") {
            val failure = evaluateGuardFailure(
                designatedNumbers = emptySet(),
                keyword = "",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isRateLimited = true
            )
            Then("the failure reason should be RATE_LIMITED because it is checked first") {
                failure shouldBe GuardFailure.RATE_LIMITED
            }
        }

        When("no designated numbers AND blank keyword") {
            val failure = evaluateGuardFailure(
                designatedNumbers = emptySet(),
                keyword = "",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("the failure reason should be NO_DESIGNATED_NUMBERS because it is checked before NO_KEYWORD") {
                failure shouldBe GuardFailure.NO_DESIGNATED_NUMBERS
            }
        }
    }

    // ==========================================
    // Rate limit not counting suppressed triggers (car mode / phone call)
    // ==========================================

    Given("rate limit should not count suppressed triggers from car mode or phone call") {

        When("a trigger is suppressed by car mode") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isInCarMode = true,
                carModeProtectionEnabled = true
            )
            Then("the result is CAR_MODE_SILENT, not SUPPRESS -- rate limiter should not count this") {
                result shouldBe TriggerResult.CAR_MODE_SILENT
                // CAR_MODE_SILENT is distinct from SUPPRESS, so a rate limiter
                // tracking only TRIGGER_ALARM outcomes would not count this
                (result != TriggerResult.SUPPRESS).shouldBeTrue()
            }
        }

        When("a trigger is suppressed by phone call") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isInPhoneCall = true,
                phoneCallProtectionEnabled = true
            )
            Then("the result is PHONE_CALL_SILENT, not SUPPRESS -- rate limiter should not count this") {
                result shouldBe TriggerResult.PHONE_CALL_SILENT
                (result != TriggerResult.SUPPRESS).shouldBeTrue()
            }
        }

        When("a trigger succeeds normally without car mode or phone call") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("the result is TRIGGER_ALARM which should count toward rate limit") {
                result shouldBe TriggerResult.TRIGGER_ALARM
                (result != TriggerResult.SUPPRESS).shouldBeTrue()
                (result != TriggerResult.CAR_MODE_SILENT).shouldBeTrue()
                (result != TriggerResult.PHONE_CALL_SILENT).shouldBeTrue()
            }
        }

        When("rate limiting is active, even if car mode is also active") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isRateLimited = true,
                isInCarMode = true,
                carModeProtectionEnabled = true
            )
            Then("it should suppress entirely because rate limit is checked before car mode") {
                result shouldBe TriggerResult.SUPPRESS
            }
        }

        When("rate limiting is active, even if phone call is also active") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND",
                isRateLimited = true,
                isInPhoneCall = true,
                phoneCallProtectionEnabled = true
            )
            Then("it should suppress entirely because rate limit is checked before phone call") {
                result shouldBe TriggerResult.SUPPRESS
            }
        }

        When("all guards pass and no car/phone suppression") {
            val result = evaluateTrigger(
                designatedNumbers = setOf("+447834120123"),
                keyword = "FIND",
                senderNumber = "+447834120123",
                messageBody = "FIND"
            )
            Then("the result is TRIGGER_ALARM which counts toward rate limit (battery state is irrelevant)") {
                result shouldBe TriggerResult.TRIGGER_ALARM
                (result != TriggerResult.SUPPRESS).shouldBeTrue()
            }
        }
    }
})
