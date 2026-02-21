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

package com.smartfind.app.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

/**
 * Kotest BehaviorSpec for [AlarmService.sanitizeSender].
 *
 * Pure unit test (no Android dependencies needed). Tests the Unicode
 * sanitization that prevents visual spoofing in notification sender strings.
 *
 * sanitizeSender strips:
 * - Control characters (\p{Cc}): null bytes, bell, backspace, etc.
 * - Format characters (\p{Cf}): zero-width joiners, directional overrides, BOM
 * - Private-use characters (\p{Co}): PUA codepoints
 *
 * Then trims leading/trailing whitespace.
 */
class AlarmServiceSanitizeSpec : BehaviorSpec({

    // ==========================================
    // Normal input (no stripping needed)
    // ==========================================

    Given("normal sender strings") {

        When("a plain phone number is passed") {
            Then("it is returned unchanged") {
                AlarmService.sanitizeSender("+447834120123") shouldBe "+447834120123"
            }
        }

        When("a sender with a name is passed") {
            Then("it is returned unchanged") {
                AlarmService.sanitizeSender("John Doe") shouldBe "John Doe"
            }
        }

        When("an empty string is passed") {
            Then("it returns empty string") {
                AlarmService.sanitizeSender("") shouldBe ""
            }
        }

        When("whitespace-only string is passed") {
            Then("it returns empty string after trim") {
                AlarmService.sanitizeSender("   ") shouldBe ""
            }
        }
    }

    // ==========================================
    // Control characters (Cc)
    // ==========================================

    Given("sender strings with control characters") {

        When("null byte is embedded") {
            Then("it is stripped") {
                AlarmService.sanitizeSender("Hello\u0000World") shouldBe "HelloWorld"
            }
        }

        When("bell character is embedded") {
            Then("it is stripped") {
                AlarmService.sanitizeSender("Hello\u0007World") shouldBe "HelloWorld"
            }
        }

        When("tab and newline are embedded") {
            Then("they are stripped") {
                AlarmService.sanitizeSender("Hello\t\nWorld") shouldBe "HelloWorld"
            }
        }

        When("multiple control characters are mixed in") {
            Then("all are stripped") {
                val input = "\u0001Hello\u0002 \u0003World\u0004"
                val result = AlarmService.sanitizeSender(input)
                result shouldBe "Hello World"
            }
        }
    }

    // ==========================================
    // Format characters (Cf) — bidirectional overrides
    // ==========================================

    Given("sender strings with format/bidirectional characters") {

        When("right-to-left override is embedded") {
            Then("it is stripped") {
                val rtlOverride = "\u202E" // RLO
                AlarmService.sanitizeSender("${rtlOverride}+447834120123") shouldBe "+447834120123"
            }
        }

        When("left-to-right mark is embedded") {
            Then("it is stripped") {
                val ltrMark = "\u200E" // LRM
                AlarmService.sanitizeSender("Hello${ltrMark}World") shouldBe "HelloWorld"
            }
        }

        When("right-to-left mark is embedded") {
            Then("it is stripped") {
                val rtlMark = "\u200F" // RLM
                AlarmService.sanitizeSender("Hello${rtlMark}World") shouldBe "HelloWorld"
            }
        }

        When("zero-width joiner is embedded") {
            Then("it is stripped") {
                val zwj = "\u200D" // ZWJ
                AlarmService.sanitizeSender("Hello${zwj}World") shouldBe "HelloWorld"
            }
        }

        When("zero-width non-joiner is embedded") {
            Then("it is stripped") {
                val zwnj = "\u200C" // ZWNJ
                AlarmService.sanitizeSender("Hello${zwnj}World") shouldBe "HelloWorld"
            }
        }

        When("byte order mark (BOM) is embedded") {
            Then("it is stripped") {
                val bom = "\uFEFF"
                AlarmService.sanitizeSender("${bom}Hello") shouldBe "Hello"
            }
        }

        When("multiple bidirectional overrides are combined for spoofing") {
            Then("all are stripped, leaving safe text") {
                val rlo = "\u202E"
                val lro = "\u202D"
                val pdf = "\u202C"
                val input = "${rlo}Evil${pdf}${lro}Safe${pdf}"
                val result = AlarmService.sanitizeSender(input)
                result shouldBe "EvilSafe"
            }
        }
    }

    // ==========================================
    // Private-use characters (Co)
    // ==========================================

    Given("sender strings with private-use area characters") {

        When("PUA character U+E000 is embedded") {
            Then("it is stripped") {
                AlarmService.sanitizeSender("Hello\uE000World") shouldBe "HelloWorld"
            }
        }

        When("PUA character U+F8FF is embedded") {
            Then("it is stripped") {
                AlarmService.sanitizeSender("Hello\uF8FFWorld") shouldBe "HelloWorld"
            }
        }
    }

    // ==========================================
    // Trimming
    // ==========================================

    Given("sender strings with leading/trailing whitespace") {

        When("spaces surround the sender") {
            Then("they are trimmed") {
                AlarmService.sanitizeSender("  +447834120123  ") shouldBe "+447834120123"
            }
        }

        When("control chars leave trailing whitespace after stripping") {
            Then("the result is trimmed") {
                AlarmService.sanitizeSender("\u0001 Hello \u0002") shouldBe "Hello"
            }
        }
    }

    // ==========================================
    // Combined attack strings
    // ==========================================

    Given("combined spoofing attack strings") {

        When("RTL override is used to make a number appear reversed") {
            Then("the override is stripped, number reads correctly") {
                val rlo = "\u202E"
                val input = "${rlo}321-0214387+"
                val result = AlarmService.sanitizeSender(input)
                result shouldBe "321-0214387+"
                result shouldNotContain "\u202E"
            }
        }

        When("zero-width chars are inserted between digits to break matching") {
            Then("they are stripped, digits are contiguous") {
                val zwsp = "\u200B" // zero-width space (Cf)
                val input = "+4${zwsp}4${zwsp}7834120123"
                val result = AlarmService.sanitizeSender(input)
                result shouldBe "+447834120123"
            }
        }

        When("mixed control, format, and PUA characters") {
            Then("all are stripped cleanly") {
                val input = "\u0000\u200E\uE000Hello\u202E World\u0007\uFEFF"
                val result = AlarmService.sanitizeSender(input)
                result shouldBe "Hello World"
            }
        }
    }

    // ==========================================
    // Safe characters preserved
    // ==========================================

    Given("safe characters that should be preserved") {

        When("international phone format with + and spaces") {
            Then("they are preserved") {
                AlarmService.sanitizeSender("+44 7834 120123") shouldBe "+44 7834 120123"
            }
        }

        When("parentheses and dashes (US format)") {
            Then("they are preserved") {
                AlarmService.sanitizeSender("(555) 123-4567") shouldBe "(555) 123-4567"
            }
        }

        When("accented characters in contact names") {
            Then("they are preserved") {
                AlarmService.sanitizeSender("Jose Maria") shouldBe "Jose Maria"
            }
        }

        When("CJK characters in contact names") {
            Then("they are preserved") {
                AlarmService.sanitizeSender("\u5F20\u4E09") shouldBe "\u5F20\u4E09"
            }
        }

        When("emoji in sender string") {
            Then("they are preserved (emoji are not Cc/Cf/Co)") {
                AlarmService.sanitizeSender("John \uD83D\uDE00") shouldBe "John \uD83D\uDE00"
            }
        }
    }
})
