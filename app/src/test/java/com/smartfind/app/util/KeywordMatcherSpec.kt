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

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * Kotest BehaviorSpec for exact keyword matching.
 * The entire SMS body (trimmed) must equal the keyword exactly (case-sensitive).
 */
class KeywordMatcherSpec : BehaviorSpec({

    // ==========================================
    // Exact match
    // ==========================================

    Given("an SMS body that exactly matches the keyword") {

        When("the body is exactly 'FIND' and the keyword is 'FIND'") {
            val result = KeywordMatcher.matchesKeyword("FIND", "FIND")
            Then("it should match") {
                result shouldBe true
            }
        }

        When("the body has leading and trailing whitespace '  FIND  '") {
            val result = KeywordMatcher.matchesKeyword("  FIND  ", "FIND")
            Then("it should match after trimming") {
                result shouldBe true
            }
        }

        When("the body has tab and newline whitespace") {
            val result = KeywordMatcher.matchesKeyword("\t FIND \n", "FIND")
            Then("it should match after trimming") {
                result shouldBe true
            }
        }
    }

    // ==========================================
    // Does not match keyword among other words
    // ==========================================

    Given("an SMS body containing the keyword among other words") {

        When("the keyword appears in a sentence 'Please FIND my phone'") {
            val result = KeywordMatcher.matchesKeyword("Please FIND my phone", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("the keyword is at the start 'FIND my phone now'") {
            val result = KeywordMatcher.matchesKeyword("FIND my phone now", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("the keyword is at the end 'Please FIND'") {
            val result = KeywordMatcher.matchesKeyword("Please FIND", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("the keyword has trailing punctuation 'FIND!'") {
            val result = KeywordMatcher.matchesKeyword("FIND!", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("the keyword is next to a comma 'Hello, FIND, please'") {
            val result = KeywordMatcher.matchesKeyword("Hello, FIND, please", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("the keyword is in quotes 'Send \"FIND\" to trigger'") {
            val result = KeywordMatcher.matchesKeyword("Send \"FIND\" to trigger", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("a multi-word keyword appears in a longer message") {
            val result = KeywordMatcher.matchesKeyword("Please FIND MY PHONE now", "FIND MY PHONE")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("a single-token keyword appears embedded in a longer message") {
            val result = KeywordMatcher.matchesKeyword("Please FINDMYPHONE right now", "FINDMYPHONE")
            Then("it should not match") {
                result shouldBe false
            }
        }
    }

    // ==========================================
    // Substring rejection
    // ==========================================

    Given("an SMS body that is a superstring of the keyword") {

        When("the body is 'FINDING'") {
            val result = KeywordMatcher.matchesKeyword("FINDING", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("the body is 'REFIND'") {
            val result = KeywordMatcher.matchesKeyword("REFIND", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("the body is 'FINDME'") {
            val result = KeywordMatcher.matchesKeyword("FINDME", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("the body is 'UNFINDABLE'") {
            val result = KeywordMatcher.matchesKeyword("UNFINDABLE", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }
    }

    // ==========================================
    // Case sensitivity
    // ==========================================

    Given("case sensitivity of keyword matching") {

        When("the body is lowercase 'find' and keyword is 'FIND'") {
            val result = KeywordMatcher.matchesKeyword("find", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("the body is mixed case 'Find' and keyword is 'FIND'") {
            val result = KeywordMatcher.matchesKeyword("Find", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }
    }

    // ==========================================
    // Edge cases
    // ==========================================

    Given("edge-case inputs") {

        When("the body is empty") {
            val result = KeywordMatcher.matchesKeyword("", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("the keyword is empty") {
            val result = KeywordMatcher.matchesKeyword("FIND", "")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("the keyword is whitespace-only") {
            val result = KeywordMatcher.matchesKeyword("FIND", "   ")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("the body is whitespace-only") {
            val result = KeywordMatcher.matchesKeyword("   ", "FIND")
            Then("it should not match") {
                result shouldBe false
            }
        }
    }

    // ==========================================
    // Special characters and multi-word keywords
    // ==========================================

    Given("keywords with special characters or multiple words") {

        When("the keyword contains a dot and body matches exactly 'FIND.ME'") {
            val result = KeywordMatcher.matchesKeyword("FIND.ME", "FIND.ME")
            Then("it should match") {
                result shouldBe true
            }
        }

        When("the keyword contains a dot but body has extra words 'Use FIND.ME now'") {
            val result = KeywordMatcher.matchesKeyword("Use FIND.ME now", "FIND.ME")
            Then("it should not match") {
                result shouldBe false
            }
        }

        When("a multi-word keyword matches exactly 'FIND MY PHONE'") {
            val result = KeywordMatcher.matchesKeyword("FIND MY PHONE", "FIND MY PHONE")
            Then("it should match") {
                result shouldBe true
            }
        }
    }

    // ==========================================
    // Custom keywords
    // ==========================================

    Given("custom keywords") {

        When("the keyword is 'LOCATE' and body is exactly 'LOCATE'") {
            val result = KeywordMatcher.matchesKeyword("LOCATE", "LOCATE")
            Then("it should match") {
                result shouldBe true
            }
        }

        When("the keyword is 'LOCATE' but body is 'RELOCATE'") {
            val result = KeywordMatcher.matchesKeyword("RELOCATE", "LOCATE")
            Then("it should not match") {
                result shouldBe false
            }
        }
    }

    // ==========================================
    // Long keywords
    // ==========================================

    Given("long keywords") {

        When("the long keyword 'FINDMYPHONE' matches exactly") {
            val result = KeywordMatcher.matchesKeyword("FINDMYPHONE", "FINDMYPHONE")
            Then("it should match") {
                result shouldBe true
            }
        }

        When("the long keyword 'FINDMYPHONE' has a prefix 'XFINDMYPHONE'") {
            val result = KeywordMatcher.matchesKeyword("XFINDMYPHONE", "FINDMYPHONE")
            Then("it should not match") {
                result shouldBe false
            }
        }
    }
})
