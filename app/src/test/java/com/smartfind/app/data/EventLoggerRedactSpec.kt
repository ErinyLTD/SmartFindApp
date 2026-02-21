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

package com.smartfind.app.data

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch

/**
 * Kotest BehaviorSpec for [EventLogger.redactNumber] and [EventLogger.newCorrelationId].
 *
 * Pure unit test (no Android dependencies). Tests the PII redaction logic
 * that prevents phone numbers from being stored in the unencrypted Room database,
 * and the correlation ID format.
 */
class EventLoggerRedactSpec : BehaviorSpec({

    // ==========================================
    // redactNumber — standard phone numbers
    // ==========================================

    Given("redactNumber with standard phone numbers") {

        When("a full international number is provided") {
            Then("it shows only the last 4 digits") {
                EventLogger.redactNumber("+447834120123") shouldBe "***0123"
            }
        }

        When("a US number with country code is provided") {
            Then("it shows only the last 4 digits") {
                EventLogger.redactNumber("+15551234567") shouldBe "***4567"
            }
        }

        When("a local UK number is provided") {
            Then("it shows only the last 4 digits") {
                EventLogger.redactNumber("07834120123") shouldBe "***0123"
            }
        }

        When("a Romanian number is provided") {
            Then("it shows only the last 4 digits") {
                EventLogger.redactNumber("+40741234567") shouldBe "***4567"
            }
        }
    }

    // ==========================================
    // redactNumber — short numbers
    // ==========================================

    Given("redactNumber with short numbers") {

        When("a number has exactly 4 digits") {
            Then("it returns '***' (no last-4 because that would reveal everything)") {
                EventLogger.redactNumber("1234") shouldBe "***"
            }
        }

        When("a number has fewer than 4 digits") {
            Then("it returns '***'") {
                EventLogger.redactNumber("12") shouldBe "***"
                EventLogger.redactNumber("1") shouldBe "***"
            }
        }

        When("a number has exactly 5 digits") {
            Then("it shows the last 4 digits") {
                EventLogger.redactNumber("12345") shouldBe "***2345"
            }
        }
    }

    // ==========================================
    // redactNumber — edge cases
    // ==========================================

    Given("redactNumber edge cases") {

        When("an empty string is provided") {
            Then("it returns '***'") {
                EventLogger.redactNumber("") shouldBe "***"
            }
        }

        When("a string with no digits is provided") {
            Then("it returns '***'") {
                EventLogger.redactNumber("no-digits-here") shouldBe "***"
            }
        }

        When("a number with formatting characters is provided") {
            Then("it strips non-digit chars and shows last 4 digits") {
                EventLogger.redactNumber("+44 (0) 7834-120-123") shouldBe "***0123"
            }
        }

        When("a number with spaces between all digits is provided") {
            Then("it extracts digits correctly") {
                EventLogger.redactNumber("0 7 8 3 4 1 2 0 1 2 3") shouldBe "***0123"
            }
        }

        When("a string with mixed alpha and digits is provided") {
            Then("it extracts digits only") {
                EventLogger.redactNumber("abc123def4567") shouldBe "***4567"
            }
        }
    }

    // ==========================================
    // redactNumber — PII safety
    // ==========================================

    Given("redactNumber ensures PII safety") {

        When("a 10-digit US number is redacted") {
            Then("the first 6 digits are not visible") {
                val result = EventLogger.redactNumber("5551234567")
                result shouldBe "***4567"
                result shouldNotContain "555123"
            }
        }

        When("a 13-digit international number is redacted") {
            Then("only the last 4 digits are exposed") {
                val result = EventLogger.redactNumber("+447834120123")
                result shouldBe "***0123"
                result shouldStartWith "***"
                result shouldNotContain "7834"
                result shouldNotContain "4412"
            }
        }
    }

    // ==========================================
    // newCorrelationId — format
    // ==========================================

    Given("newCorrelationId format") {

        When("a new correlation ID is generated") {
            Then("it is 8 characters long") {
                val cid = EventLogger.newCorrelationId()
                cid shouldHaveLength 8
            }
        }

        When("a correlation ID is generated") {
            Then("it contains only hex characters and dashes") {
                val cid = EventLogger.newCorrelationId()
                // UUID.randomUUID().toString().take(8) gives first 8 chars
                // of format "xxxxxxxx-xxxx-..." so it's 8 hex chars
                cid shouldMatch Regex("[0-9a-f]{8}")
            }
        }

        When("two correlation IDs are generated") {
            Then("they are different (with overwhelming probability)") {
                val cid1 = EventLogger.newCorrelationId()
                val cid2 = EventLogger.newCorrelationId()
                (cid1 != cid2) shouldBe true
            }
        }

        When("100 correlation IDs are generated") {
            Then("all are unique") {
                val ids = (1..100).map { EventLogger.newCorrelationId() }.toSet()
                ids.size shouldBe 100
            }
        }
    }
})
