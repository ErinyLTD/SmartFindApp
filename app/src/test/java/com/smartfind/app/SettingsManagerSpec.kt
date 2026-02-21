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

package com.smartfind.app

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.smartfind.app.testing.robolectric.RobolectricTest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldNotContain

@RobolectricTest
class SettingsManagerSpec : BehaviorSpec({

    lateinit var settings: SettingsManager

    beforeEach {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
    }

    // ==========================================
    // Trigger Keyword
    // ==========================================

    Given("Trigger Keyword") {

        When("no keyword has been set") {
            Then("default keyword is FIND") {
                settings.getTriggerKeyword() shouldBe "FIND"
            }
        }

        When("setTriggerKeyword is called with leading and trailing spaces") {
            Then("it stores uppercased trimmed keyword") {
                settings.setTriggerKeyword("  hello  ")
                settings.getTriggerKeyword() shouldBe "HELLO"
            }
        }

        When("setTriggerKeyword is called with mixed case") {
            Then("it stores uppercased keyword") {
                settings.setTriggerKeyword("FindMe")
                settings.getTriggerKeyword() shouldBe "FINDME"
            }
        }

        When("setTriggerKeyword is called twice") {
            Then("it overwrites previous keyword") {
                settings.setTriggerKeyword("FIRST")
                settings.setTriggerKeyword("SECOND")
                settings.getTriggerKeyword() shouldBe "SECOND"
            }
        }
    }

    // ==========================================
    // Phone Numbers
    // ==========================================

    Given("Phone Numbers") {

        When("no phone numbers have been added") {
            Then("default phone numbers is empty") {
                settings.getPhoneNumbers().shouldBeEmpty()
            }
        }

        When("addPhoneNumber is called with a valid number") {
            Then("it adds the number") {
                settings.addPhoneNumber("+447834120123")
                settings.getPhoneNumbers() shouldBe setOf("+447834120123")
            }
        }

        When("addPhoneNumber is called with whitespace") {
            Then("it trims whitespace") {
                settings.addPhoneNumber("  +447834120123  ")
                settings.getPhoneNumbers().contains("+447834120123").shouldBeTrue()
            }
        }

        When("addPhoneNumber is called with multiple numbers") {
            Then("it adds all numbers") {
                settings.addPhoneNumber("+447834120123")
                settings.addPhoneNumber("+40741234567")
                settings.addPhoneNumber("+15551234567")
                settings.getPhoneNumbers() shouldHaveSize 3
                settings.getPhoneNumbers().contains("+447834120123").shouldBeTrue()
                settings.getPhoneNumbers().contains("+40741234567").shouldBeTrue()
                settings.getPhoneNumbers().contains("+15551234567").shouldBeTrue()
            }
        }

        When("addPhoneNumber is called with a duplicate number") {
            Then("it does not duplicate existing number") {
                settings.addPhoneNumber("+447834120123")
                settings.addPhoneNumber("+447834120123")
                settings.getPhoneNumbers() shouldHaveSize 1
            }
        }

        When("removePhoneNumber is called with an existing number") {
            Then("it removes the number") {
                settings.addPhoneNumber("+447834120123")
                settings.addPhoneNumber("+40741234567")
                settings.removePhoneNumber("+447834120123")
                settings.getPhoneNumbers() shouldBe setOf("+40741234567")
            }
        }

        When("removePhoneNumber is called with a non-existing number") {
            Then("it does nothing") {
                settings.addPhoneNumber("+447834120123")
                settings.removePhoneNumber("+99999999")
                settings.getPhoneNumbers() shouldHaveSize 1
            }
        }

        When("removePhoneNumber is called with whitespace") {
            Then("it trims and removes the number") {
                settings.addPhoneNumber("+447834120123")
                settings.removePhoneNumber("  +447834120123  ")
                settings.getPhoneNumbers().shouldBeEmpty()
            }
        }

        When("addPhoneNumber is called with fewer than 7 digits") {
            Then("it rejects the number") {
                settings.addPhoneNumber("+1234")
                settings.getPhoneNumbers().shouldBeEmpty()
            }
        }

        When("addPhoneNumber is called with an empty string") {
            Then("it rejects the empty string") {
                settings.addPhoneNumber("")
                settings.getPhoneNumbers().shouldBeEmpty()
            }
        }
    }

    // ==========================================
    // Service Enabled
    // ==========================================

    Given("Service Enabled") {

        When("no value has been set") {
            Then("default service enabled is false") {
                settings.isServiceEnabled().shouldBeFalse()
            }
        }

        When("setServiceEnabled is called with true") {
            Then("it returns true") {
                settings.setServiceEnabled(true)
                settings.isServiceEnabled().shouldBeTrue()
            }
        }

        When("setServiceEnabled is toggled") {
            Then("it toggles correctly") {
                settings.setServiceEnabled(true)
                settings.isServiceEnabled().shouldBeTrue()
                settings.setServiceEnabled(false)
                settings.isServiceEnabled().shouldBeFalse()
            }
        }
    }

    // ==========================================
    // Last Checked SMS Timestamp
    // ==========================================

    Given("Last Checked SMS Timestamp") {

        When("no timestamp has been set") {
            Then("default last checked timestamp is 0") {
                settings.getLastCheckedSmsTimestamp() shouldBe 0L
            }
        }

        When("setLastCheckedSmsTimestamp is called") {
            Then("it stores the value") {
                val ts = System.currentTimeMillis()
                settings.setLastCheckedSmsTimestamp(ts)
                settings.getLastCheckedSmsTimestamp() shouldBe ts
            }
        }
    }

    // ==========================================
    // Audio State
    // ==========================================

    Given("Audio State") {

        When("no audio state has been saved") {
            Then("default saved volume is -1") {
                settings.getSavedVolume() shouldBe -1
            }

            Then("default saved ringer mode is NORMAL") {
                settings.getSavedRingerMode() shouldBe AudioManager.RINGER_MODE_NORMAL
            }
        }

        When("saveAudioState is called") {
            Then("it stores volume and ringer mode") {
                settings.saveAudioState(7, AudioManager.RINGER_MODE_VIBRATE)
                settings.getSavedVolume() shouldBe 7
                settings.getSavedRingerMode() shouldBe AudioManager.RINGER_MODE_VIBRATE
            }
        }

        When("saveAudioState is called twice") {
            Then("it overwrites previous values") {
                settings.saveAudioState(5, AudioManager.RINGER_MODE_SILENT)
                settings.saveAudioState(10, AudioManager.RINGER_MODE_NORMAL)
                settings.getSavedVolume() shouldBe 10
                settings.getSavedRingerMode() shouldBe AudioManager.RINGER_MODE_NORMAL
            }
        }
    }

    // ==========================================
    // Alarm Active
    // ==========================================

    Given("Alarm Active") {

        When("no value has been set") {
            Then("default alarm active is false") {
                settings.isAlarmActive().shouldBeFalse()
            }
        }

        When("setAlarmActive is called with true") {
            Then("it returns true") {
                settings.setAlarmActive(true)
                settings.isAlarmActive().shouldBeTrue()
            }
        }

        When("setAlarmActive is toggled") {
            Then("it toggles correctly") {
                settings.setAlarmActive(true)
                settings.isAlarmActive().shouldBeTrue()
                settings.setAlarmActive(false)
                settings.isAlarmActive().shouldBeFalse()
            }
        }
    }

    // ==========================================
    // Numbers Confirmation
    // ==========================================

    Given("Numbers Confirmation") {

        When("no timestamp has been set") {
            Then("default numbers confirmed timestamp is 0") {
                settings.getNumbersConfirmedTimestamp() shouldBe 0L
            }
        }

        When("setNumbersConfirmedTimestamp is called") {
            Then("it stores the value") {
                val ts = System.currentTimeMillis()
                settings.setNumbersConfirmedTimestamp(ts)
                settings.getNumbersConfirmedTimestamp() shouldBe ts
            }
        }

        When("isNumbersConfirmationDue is checked with no timestamp set") {
            Then("it returns false (first install, not due)") {
                settings.isNumbersConfirmationDue().shouldBeFalse()
            }
        }

        When("isNumbersConfirmationDue is checked after recent confirmation") {
            Then("it returns false") {
                settings.setNumbersConfirmedTimestamp(System.currentTimeMillis())
                settings.isNumbersConfirmationDue().shouldBeFalse()
            }
        }

        When("isNumbersConfirmationDue is checked after more than 6 months") {
            Then("it returns true") {
                val sevenMonthsAgo = System.currentTimeMillis() - (210L * 24 * 60 * 60 * 1000)
                settings.setNumbersConfirmedTimestamp(sevenMonthsAgo)
                settings.isNumbersConfirmationDue().shouldBeTrue()
            }
        }

        When("isNumbersConfirmationDue is checked just under 6 months") {
            Then("it returns false") {
                val fiveMonthsAgo = System.currentTimeMillis() - (150L * 24 * 60 * 60 * 1000)
                settings.setNumbersConfirmedTimestamp(fiveMonthsAgo)
                settings.isNumbersConfirmationDue().shouldBeFalse()
            }
        }

        When("SIX_MONTHS_MS constant is checked") {
            Then("it is approximately 180 days") {
                val expectedMs = 180L * 24 * 60 * 60 * 1000
                SettingsManager.SIX_MONTHS_MS shouldBe expectedMs
            }
        }
    }

    // ==========================================
    // Contact Names
    // ==========================================

    Given("Contact Names") {

        When("no name is stored for a number") {
            Then("getContactName returns null") {
                settings.getContactName("+447834120123").shouldBeNull()
            }
        }

        When("setContactName is called") {
            Then("it stores and retrieves the name") {
                settings.setContactName("+447834120123", "John")
                settings.getContactName("+447834120123") shouldBe "John"
            }
        }

        When("setContactName is called with whitespace") {
            Then("it trims whitespace") {
                settings.setContactName("  +447834120123  ", "  John  ")
                settings.getContactName("+447834120123") shouldBe "John"
            }
        }

        When("setContactName is called twice for the same number") {
            Then("it overwrites previous name") {
                settings.setContactName("+447834120123", "John")
                settings.setContactName("+447834120123", "Jane")
                settings.getContactName("+447834120123") shouldBe "Jane"
            }
        }

        When("multiple contact names are stored") {
            Then("they are stored independently") {
                settings.setContactName("+447834120123", "John")
                settings.setContactName("+40741234567", "Maria")
                settings.getContactName("+447834120123") shouldBe "John"
                settings.getContactName("+40741234567") shouldBe "Maria"
            }
        }

        When("addPhoneNumber is called with contactName") {
            Then("it stores both number and name") {
                settings.addPhoneNumber("+447834120123", "John")
                settings.getPhoneNumbers().contains("+447834120123").shouldBeTrue()
                settings.getContactName("+447834120123") shouldBe "John"
            }
        }

        When("addPhoneNumber is called without contactName") {
            Then("it does not set name") {
                settings.addPhoneNumber("+447834120123")
                settings.getPhoneNumbers().contains("+447834120123").shouldBeTrue()
                settings.getContactName("+447834120123").shouldBeNull()
            }
        }

        When("removePhoneNumber is called for a number with a name") {
            Then("it also removes the contact name") {
                settings.addPhoneNumber("+447834120123", "John")
                settings.removePhoneNumber("+447834120123")
                settings.getPhoneNumbers().shouldBeEmpty()
                settings.getContactName("+447834120123").shouldBeNull()
            }
        }

        When("removePhoneNumber does not affect other contact names") {
            Then("other names remain") {
                settings.addPhoneNumber("+447834120123", "John")
                settings.addPhoneNumber("+40741234567", "Maria")
                settings.removePhoneNumber("+447834120123")
                settings.getContactName("+447834120123").shouldBeNull()
                settings.getContactName("+40741234567") shouldBe "Maria"
            }
        }

        When("getContactName is called for an unknown number") {
            Then("it returns null") {
                settings.setContactName("+447834120123", "John")
                settings.getContactName("+99999999999").shouldBeNull()
            }
        }
    }

    // ==========================================
    // Active Use Protection (always enabled)
    // ==========================================

    Given("Active Use Protection") {

        When("isActiveUseProtectionEnabled is checked") {
            Then("it is always enabled") {
                settings.isActiveUseProtectionEnabled().shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Car Mode Protection (always enabled)
    // ==========================================

    Given("Car Mode Protection") {

        When("isCarModeProtectionEnabled is checked") {
            Then("it is always enabled") {
                settings.isCarModeProtectionEnabled().shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Trigger Cooldown
    // ==========================================

    Given("Trigger Cooldown") {

        When("no cooldown has been set") {
            Then("default cooldown is 5 minutes") {
                settings.getCooldownMinutes() shouldBe 5
            }
        }

        When("DEFAULT_COOLDOWN_MINUTES constant is checked") {
            Then("it is 5") {
                SettingsManager.DEFAULT_COOLDOWN_MINUTES shouldBe 5
            }
        }

        When("MAX_COOLDOWN_MINUTES constant is checked") {
            Then("it is 60") {
                SettingsManager.MAX_COOLDOWN_MINUTES shouldBe 60
            }
        }

        When("setCooldownMinutes is called with a valid value") {
            Then("it stores the value") {
                settings.setCooldownMinutes(15)
                settings.getCooldownMinutes() shouldBe 15
            }
        }

        When("no alarm stopped timestamp has been set") {
            Then("alarm stopped timestamp defaults to 0") {
                settings.getAlarmStoppedTimestamp() shouldBe 0L
            }
        }

        When("setAlarmStoppedTimestamp is called") {
            Then("it stores the value") {
                val ts = System.currentTimeMillis()
                settings.setAlarmStoppedTimestamp(ts)
                settings.getAlarmStoppedTimestamp() shouldBe ts
            }
        }

        When("isCooldownActive is checked with no alarm ever stopped") {
            Then("it returns false") {
                settings.isCooldownActive().shouldBeFalse()
            }
        }

        When("cooldown is set to 0 (coerced to minimum)") {
            Then("isCooldownActive returns true when alarm just stopped") {
                settings.setCooldownMinutes(0)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis())
                settings.isCooldownActive().shouldBeTrue()
            }
        }

        When("alarm stopped within cooldown period") {
            Then("isCooldownActive returns true") {
                settings.setCooldownMinutes(5)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis() - 2 * 60 * 1000L)
                settings.isCooldownActive().shouldBeTrue()
            }
        }

        When("cooldown period has elapsed") {
            Then("isCooldownActive returns false") {
                settings.setCooldownMinutes(5)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis() - 10 * 60 * 1000L)
                settings.isCooldownActive().shouldBeFalse()
            }
        }

        When("alarm stopped at exactly cooldown boundary minus 1 second") {
            Then("isCooldownActive returns true") {
                settings.setCooldownMinutes(5)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis() - (5 * 60 * 1000L - 1000L))
                settings.isCooldownActive().shouldBeTrue()
            }
        }

        When("alarm stopped just past cooldown boundary") {
            Then("isCooldownActive returns false") {
                settings.setCooldownMinutes(5)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis() - (5 * 60 * 1000L + 1000L))
                settings.isCooldownActive().shouldBeFalse()
            }
        }

        When("custom cooldown value of 30 minutes is used") {
            Then("isCooldownActive returns true within 30-minute cooldown") {
                settings.setCooldownMinutes(30)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis() - 20 * 60 * 1000L)
                settings.isCooldownActive().shouldBeTrue()
            }
        }

        When("below-minimum cooldown is set (1 minute, coerced to 2)") {
            Then("isCooldownActive returns true within coerced 2-minute window at 30s") {
                settings.setCooldownMinutes(1)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis() - 30 * 1000L)
                settings.isCooldownActive().shouldBeTrue()
            }

            Then("isCooldownActive returns true within coerced 2-minute window at 90s") {
                settings.setCooldownMinutes(1)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis() - 90 * 1000L)
                settings.isCooldownActive().shouldBeTrue()
            }

            Then("isCooldownActive returns false past coerced 2-minute window at 3 min") {
                settings.setCooldownMinutes(1)
                settings.setAlarmStoppedTimestamp(System.currentTimeMillis() - 3 * 60 * 1000L)
                settings.isCooldownActive().shouldBeFalse()
            }
        }

        When("setCooldownMinutes is called with value above 60") {
            Then("it coerces to 60") {
                settings.setCooldownMinutes(120)
                settings.getCooldownMinutes() shouldBe 60
            }
        }

        When("a stored value above 60 is read") {
            Then("getCooldownMinutes coerces it to 60") {
                val context = ApplicationProvider.getApplicationContext<Context>()
                // In Robolectric, EncryptedSharedPreferences is unavailable so
                // SettingsManager falls back to the plain prefs file.
                context.getSharedPreferences("smartfind_prefs_plain", Context.MODE_PRIVATE)
                    .edit().putInt("cooldown_minutes", 999).apply()
                val freshSettings = SettingsManager(context)
                freshSettings.getCooldownMinutes() shouldBe 60
            }
        }
    }

    // ==========================================
    // Cooldown Minimum Floor
    // ==========================================

    Given("Cooldown Minimum Floor") {

        When("MIN_COOLDOWN_MINUTES constant is checked") {
            Then("it is 2") {
                SettingsManager.MIN_COOLDOWN_MINUTES shouldBe 2
            }
        }

        When("setCooldownMinutes is called with 0") {
            Then("it enforces minimum of 2") {
                settings.setCooldownMinutes(0)
                settings.getCooldownMinutes() shouldBe 2
            }
        }

        When("setCooldownMinutes is called with value at minimum") {
            Then("it allows value at minimum") {
                settings.setCooldownMinutes(2)
                settings.getCooldownMinutes() shouldBe 2
            }
        }

        When("setCooldownMinutes is called with value above minimum") {
            Then("it allows value above minimum") {
                settings.setCooldownMinutes(10)
                settings.getCooldownMinutes() shouldBe 10
            }
        }

        When("a legacy value below minimum is stored directly in prefs") {
            Then("getCooldownMinutes coerces the legacy value") {
                val context = ApplicationProvider.getApplicationContext<Context>()
                // In Robolectric, EncryptedSharedPreferences is unavailable so
                // SettingsManager falls back to the plain prefs file.
                context.getSharedPreferences("smartfind_prefs_plain", Context.MODE_PRIVATE)
                    .edit().putInt("cooldown_minutes", 0).apply()
                val freshSettings = SettingsManager(context)
                freshSettings.getCooldownMinutes() shouldBe 2
            }
        }
    }

    // ==========================================
    // Contact Name Sanitization
    // ==========================================

    Given("Contact Name Sanitization") {

        When("sanitizeContactName is called with colon characters") {
            Then("it strips colon characters") {
                settings.sanitizeContactName("John:Doe") shouldBe "JohnDoe"
            }
        }

        When("sanitizeContactName is called with pipe characters") {
            Then("it strips pipe characters") {
                settings.sanitizeContactName("John|Doe") shouldBe "JohnDoe"
            }
        }

        When("sanitizeContactName is called with both colon and pipe") {
            Then("it strips both characters") {
                settings.sanitizeContactName("John:|Doe") shouldBe "JohnDoe"
            }
        }

        When("sanitizeContactName is called with whitespace") {
            Then("it trims whitespace") {
                settings.sanitizeContactName("  John  ") shouldBe "John"
            }
        }

        When("sanitizeContactName is called with a normal name") {
            Then("it preserves the normal name") {
                settings.sanitizeContactName("John Doe") shouldBe "John Doe"
            }
        }

        When("sanitizeContactName is called with an empty string") {
            Then("it handles empty string") {
                settings.sanitizeContactName("") shouldBe ""
            }
        }

        When("sanitizeContactName is called with only special chars") {
            Then("it returns empty string") {
                settings.sanitizeContactName(":|:|") shouldBe ""
            }
        }

        When("sanitizeContactName is called with control characters") {
            Then("it strips control characters") {
                settings.sanitizeContactName("Hello\u0000 \u200FWorld\u0007") shouldBe "Hello World"
            }
        }

        When("sanitizeContactName is called with a name longer than 100 characters") {
            Then("it enforces 100 character max length") {
                val longName = "A".repeat(150)
                settings.sanitizeContactName(longName).length shouldBe 100
            }
        }

        When("sanitizeContactName is called with a name needing both strip and truncate") {
            Then("it strips special chars then truncates to 100") {
                val name = "A:B|C\u0000D".repeat(50)
                val sanitized = settings.sanitizeContactName(name)
                sanitized.length shouldBe 100
                sanitized shouldNotContain ":"
                sanitized shouldNotContain "|"
            }
        }

        When("setContactName is called with special chars in name") {
            Then("it stores sanitized name") {
                settings.setContactName("+447834120123", "John:Doe|Sr")
                settings.getContactName("+447834120123") shouldBe "JohnDoeSr"
            }
        }

        When("addPhoneNumber is called with name containing special chars") {
            Then("it sanitizes the contact name") {
                settings.addPhoneNumber("+447834120123", "Test:Name|Here")
                settings.getContactName("+447834120123") shouldBe "TestNameHere"
            }
        }
    }

    // ==========================================
    // Lock Screen Privacy (always enabled)
    // ==========================================

    Given("Lock Screen Privacy") {

        When("isLockScreenPrivacyEnabled is checked") {
            Then("it is always enabled") {
                settings.isLockScreenPrivacyEnabled().shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Phone Call Protection (always enabled)
    // ==========================================

    Given("Phone Call Protection") {

        When("isPhoneCallProtectionEnabled is checked") {
            Then("it is always enabled") {
                settings.isPhoneCallProtectionEnabled().shouldBeTrue()
            }
        }
    }

    // ==========================================
    // First Run Complete
    // ==========================================

    Given("First Run Complete") {

        When("no value has been set") {
            Then("first run is not complete by default") {
                settings.isFirstRunComplete().shouldBeFalse()
            }
        }

        When("setFirstRunComplete is called with true") {
            Then("it stores the value") {
                settings.setFirstRunComplete(true)
                settings.isFirstRunComplete().shouldBeTrue()
            }
        }

        When("setFirstRunComplete is toggled") {
            Then("it toggles correctly") {
                settings.setFirstRunComplete(true)
                settings.isFirstRunComplete().shouldBeTrue()
                settings.setFirstRunComplete(false)
                settings.isFirstRunComplete().shouldBeFalse()
            }
        }
    }

    // ==========================================
    // Low Battery Protection (always enabled)
    // ==========================================

    Given("Low Battery Protection") {

        When("isLowBatteryProtectionEnabled is checked") {
            Then("it is always enabled") {
                settings.isLowBatteryProtectionEnabled().shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Service Unlock Counter
    // ==========================================

    Given("Service Unlock Counter") {

        When("no unlock count has been set") {
            Then("default unlock count is 0") {
                settings.getServiceUnlockCount() shouldBe 0
            }
        }

        When("incrementServiceUnlockCount is called once") {
            Then("count increments to 1") {
                settings.incrementServiceUnlockCount()
                settings.getServiceUnlockCount() shouldBe 1
            }
        }

        When("incrementServiceUnlockCount is called multiple times") {
            Then("count increments correctly") {
                settings.incrementServiceUnlockCount()
                settings.incrementServiceUnlockCount()
                settings.incrementServiceUnlockCount()
                settings.getServiceUnlockCount() shouldBe 3
            }
        }

        When("resetServiceUnlockCount is called") {
            Then("count resets to 0") {
                settings.incrementServiceUnlockCount()
                settings.incrementServiceUnlockCount()
                settings.resetServiceUnlockCount()
                settings.getServiceUnlockCount() shouldBe 0
            }
        }

        When("resetServiceUnlockCount is called on already-zero counter") {
            Then("count stays at 0") {
                settings.resetServiceUnlockCount()
                settings.getServiceUnlockCount() shouldBe 0
            }
        }

        When("increment, reset, then increment again") {
            Then("count starts fresh after reset") {
                settings.incrementServiceUnlockCount()
                settings.incrementServiceUnlockCount()
                settings.resetServiceUnlockCount()
                settings.incrementServiceUnlockCount()
                settings.getServiceUnlockCount() shouldBe 1
            }
        }

        When("count reaches the notification threshold") {
            Then("count equals SERVICE_NOTIFICATION_UNLOCK_THRESHOLD") {
                repeat(SettingsManager.SERVICE_NOTIFICATION_UNLOCK_THRESHOLD) {
                    settings.incrementServiceUnlockCount()
                }
                settings.getServiceUnlockCount() shouldBe SettingsManager.SERVICE_NOTIFICATION_UNLOCK_THRESHOLD
            }
        }

        When("SERVICE_NOTIFICATION_UNLOCK_THRESHOLD constant is checked") {
            Then("it is 3") {
                SettingsManager.SERVICE_NOTIFICATION_UNLOCK_THRESHOLD shouldBe 3
            }
        }
    }

    // ==========================================
    // Rate Limiting
    // ==========================================

    Given("Rate Limiting") {

        When("RATE_LIMIT_MAX_TRIGGERS constant is checked") {
            Then("it is 5") {
                SettingsManager.RATE_LIMIT_MAX_TRIGGERS shouldBe 5
            }
        }

        When("RATE_LIMIT_WINDOW_MS constant is checked") {
            Then("it is 1 hour") {
                SettingsManager.RATE_LIMIT_WINDOW_MS shouldBe 60 * 60 * 1000L
            }
        }

        When("no triggers have been recorded") {
            Then("getRateLimitTimestamps returns empty list") {
                settings.getRateLimitTimestamps().shouldBeEmpty()
            }
        }

        When("recordTriggerTimestamp is called once") {
            Then("it adds the timestamp") {
                val now = System.currentTimeMillis()
                settings.recordTriggerTimestamp(now)
                val timestamps = settings.getRateLimitTimestamps()
                timestamps shouldHaveSize 1
                timestamps[0] shouldBe now
            }
        }

        When("recordTriggerTimestamp is called multiple times") {
            Then("it adds multiple timestamps") {
                val now = System.currentTimeMillis()
                settings.recordTriggerTimestamp(now - 3000)
                settings.recordTriggerTimestamp(now - 2000)
                settings.recordTriggerTimestamp(now - 1000)
                settings.getRateLimitTimestamps() shouldHaveSize 3
            }
        }

        When("recordTriggerTimestamp is called with old and new timestamps") {
            Then("it prunes old timestamps") {
                val now = System.currentTimeMillis()
                val oldTimestamp = now - SettingsManager.RATE_LIMIT_WINDOW_MS - 1000
                settings.recordTriggerTimestamp(oldTimestamp)
                settings.recordTriggerTimestamp(now)
                val timestamps = settings.getRateLimitTimestamps()
                timestamps shouldHaveSize 1
                timestamps[0] shouldBe now
            }
        }

        When("isRateLimited is checked with no triggers") {
            Then("it returns false") {
                settings.isRateLimited().shouldBeFalse()
            }
        }

        When("isRateLimited is checked with fewer than 5 recent triggers") {
            Then("it returns false") {
                val now = System.currentTimeMillis()
                for (i in 1..4) {
                    settings.recordTriggerTimestamp(now - i * 1000L)
                }
                settings.isRateLimited().shouldBeFalse()
            }
        }

        When("isRateLimited is checked with exactly 5 recent triggers") {
            Then("it returns true") {
                val now = System.currentTimeMillis()
                for (i in 1..5) {
                    settings.recordTriggerTimestamp(now - i * 1000L)
                }
                settings.isRateLimited().shouldBeTrue()
            }
        }

        When("isRateLimited is checked with more than 5 recent triggers") {
            Then("it returns true") {
                val now = System.currentTimeMillis()
                for (i in 1..7) {
                    settings.recordTriggerTimestamp(now - i * 1000L)
                }
                settings.isRateLimited().shouldBeTrue()
            }
        }

        When("isRateLimited is checked with all triggers outside window") {
            Then("it returns false") {
                val now = System.currentTimeMillis()
                val outsideWindow = now - SettingsManager.RATE_LIMIT_WINDOW_MS - 10000
                for (i in 1..10) {
                    settings.recordTriggerTimestamp(outsideWindow - i * 1000L)
                }
                settings.isRateLimited().shouldBeFalse()
            }
        }

        When("isRateLimited is checked with old triggers and recent triggers below limit") {
            Then("it ignores old triggers and counts only recent ones") {
                val now = System.currentTimeMillis()
                val outsideWindow = now - SettingsManager.RATE_LIMIT_WINDOW_MS - 10000
                for (i in 1..3) {
                    settings.recordTriggerTimestamp(outsideWindow - i * 1000L)
                }
                for (i in 1..4) {
                    settings.recordTriggerTimestamp(now - i * 1000L)
                }
                settings.isRateLimited().shouldBeFalse()
            }
        }

        When("isRateLimited is checked with mixed old and new triggers exceeding limit") {
            Then("it returns true") {
                val now = System.currentTimeMillis()
                val outsideWindow = now - SettingsManager.RATE_LIMIT_WINDOW_MS - 10000
                for (i in 1..3) {
                    settings.recordTriggerTimestamp(outsideWindow - i * 1000L)
                }
                for (i in 1..5) {
                    settings.recordTriggerTimestamp(now - i * 1000L)
                }
                settings.isRateLimited().shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Prefs Backend Isolation
    // ==========================================

    Given("Prefs Backend Isolation") {

        When("first_run_complete is written to the old plain prefs filename") {
            Then("a new SettingsManager does not read it (no cross-contamination)") {
                val context = ApplicationProvider.getApplicationContext<Context>()
                // Simulate a stale plain prefs file left from a prior version
                // that used the same filename for both backends.
                context.getSharedPreferences("smartfind_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("first_run_complete", true).apply()

                val freshSettings = SettingsManager(context)
                // In Robolectric, EncryptedSharedPreferences is unavailable so
                // createPrefs() falls back to "smartfind_prefs_plain".  The stale
                // value in "smartfind_prefs" must NOT be visible.
                freshSettings.isFirstRunComplete().shouldBeFalse()
            }
        }

        When("two SettingsManager instances are created consecutively") {
            Then("they resolve to the same backend and share state") {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val first = SettingsManager(context)
                val second = SettingsManager(context)

                first.setFirstRunComplete(true)
                second.isFirstRunComplete().shouldBeTrue()

                first.setTriggerKeyword("TESTWORD")
                second.getTriggerKeyword() shouldBe "TESTWORD"
            }
        }

        When("first_run_complete is set via one SettingsManager instance") {
            Then("a new instance reads the same value") {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val writer = SettingsManager(context)
                writer.setFirstRunComplete(true)

                val reader = SettingsManager(context)
                reader.isFirstRunComplete().shouldBeTrue()
            }
        }
    }
})
