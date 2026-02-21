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
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse

/**
 * Kotest BehaviorSpec for PhoneNumberHelper.
 *
 * Tests cover:
 * - toSignificantDigits: stripping prefixes (0, 00, +) and formatting chars
 * - numbersMatch: matching numbers in various formats (local, international, contact formats)
 *
 * Note: normalizeNumber() requires an Android Context (TelephonyManager) so it cannot
 * be tested as a pure unit test. It would require Robolectric or instrumented tests.
 */
class PhoneNumberHelperSpec : BehaviorSpec({

    // ==========================================
    // toSignificantDigits tests
    // ==========================================

    Given("toSignificantDigits with various prefixes") {

        When("stripping a single leading zero from '07834120123'") {
            val result = PhoneNumberHelper.toSignificantDigits("07834120123")
            Then("it should return '7834120123'") {
                result shouldBe "7834120123"
            }
        }

        When("stripping the 00 international prefix from '00447834120123'") {
            val result = PhoneNumberHelper.toSignificantDigits("00447834120123")
            Then("it should return '447834120123'") {
                result shouldBe "447834120123"
            }
        }

        When("stripping the plus sign from '+447834120123'") {
            val result = PhoneNumberHelper.toSignificantDigits("+447834120123")
            Then("it should return '447834120123'") {
                result shouldBe "447834120123"
            }
        }

        When("the number has no prefix '7834120123'") {
            val result = PhoneNumberHelper.toSignificantDigits("7834120123")
            Then("it should return unchanged '7834120123'") {
                result shouldBe "7834120123"
            }
        }
    }

    Given("toSignificantDigits with formatting characters") {

        When("stripping spaces and dashes from '078 3412-0123'") {
            val result = PhoneNumberHelper.toSignificantDigits("078 3412-0123")
            Then("it should return '7834120123'") {
                result shouldBe "7834120123"
            }
        }

        When("stripping parentheses from '+44 (0) 7834 120-123'") {
            val result = PhoneNumberHelper.toSignificantDigits("+44 (0) 7834 120-123")
            Then("it should return '4407834120123' with embedded zero preserved") {
                result shouldBe "4407834120123"
            }
        }
    }

    Given("toSignificantDigits edge cases") {

        When("the input is an empty string") {
            val result = PhoneNumberHelper.toSignificantDigits("")
            Then("it should return an empty string") {
                result shouldBe ""
            }
        }

        When("the input is just a zero '0'") {
            val result = PhoneNumberHelper.toSignificantDigits("0")
            Then("it should return '0' because length is not > 1") {
                result shouldBe "0"
            }
        }

        When("the input is a short 00 prefix '001'") {
            val result = PhoneNumberHelper.toSignificantDigits("001")
            Then("it should return '01' since 00-prefix requires length > 4, falls through to single-0 strip") {
                result shouldBe "01"
            }
        }
    }

    Given("toSignificantDigits with Romanian numbers") {

        When("parsing a Romanian local number '0741234567'") {
            val result = PhoneNumberHelper.toSignificantDigits("0741234567")
            Then("it should return '741234567'") {
                result shouldBe "741234567"
            }
        }

        When("parsing a Romanian international number '+40741234567'") {
            val result = PhoneNumberHelper.toSignificantDigits("+40741234567")
            Then("it should return '40741234567'") {
                result shouldBe "40741234567"
            }
        }

        When("parsing a Romanian 0040-prefix number '0040741234567'") {
            val result = PhoneNumberHelper.toSignificantDigits("0040741234567")
            Then("it should return '40741234567'") {
                result shouldBe "40741234567"
            }
        }
    }

    // ==========================================
    // numbersMatch tests - core matching scenarios
    // ==========================================

    Given("numbersMatch with identical numbers") {

        When("both numbers are identical international '+447834120123'") {
            val result = PhoneNumberHelper.numbersMatch("+447834120123", "+447834120123")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("both numbers are identical local '07834120123'") {
            val result = PhoneNumberHelper.numbersMatch("07834120123", "07834120123")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }
    }

    // ==========================================
    // numbersMatch tests - local vs international (the key bug scenario)
    // ==========================================

    Given("numbersMatch comparing local vs international formats") {

        When("local UK '07834120123' vs international UK '+447834120123'") {
            val result = PhoneNumberHelper.numbersMatch("07834120123", "+447834120123")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("international UK '+447834120123' vs local UK '07834120123'") {
            val result = PhoneNumberHelper.numbersMatch("+447834120123", "07834120123")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("local Romanian '0741234567' vs international Romanian '+40741234567'") {
            val result = PhoneNumberHelper.numbersMatch("0741234567", "+40741234567")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("international Romanian '+40741234567' vs local Romanian '0741234567'") {
            val result = PhoneNumberHelper.numbersMatch("+40741234567", "0741234567")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("local German '01711234567' vs international German '+491711234567'") {
            val result = PhoneNumberHelper.numbersMatch("01711234567", "+491711234567")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("US number '+15551234567' vs local '5551234567'") {
            val result = PhoneNumberHelper.numbersMatch("+15551234567", "5551234567")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }
    }

    // ==========================================
    // numbersMatch tests - 00 prefix vs + prefix
    // ==========================================

    Given("numbersMatch comparing 00-prefix vs plus-prefix") {

        When("'00447834120123' vs '+447834120123'") {
            val result = PhoneNumberHelper.numbersMatch("00447834120123", "+447834120123")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("'+447834120123' vs '00447834120123'") {
            val result = PhoneNumberHelper.numbersMatch("+447834120123", "00447834120123")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("'0040741234567' vs '+40741234567'") {
            val result = PhoneNumberHelper.numbersMatch("0040741234567", "+40741234567")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }
    }

    // ==========================================
    // numbersMatch tests - formatted contact numbers
    // ==========================================

    Given("numbersMatch with formatted contact numbers (spaces, dashes, parens)") {

        When("formatted with spaces '078 3412 0123' vs clean international '+447834120123'") {
            val result = PhoneNumberHelper.numbersMatch("078 3412 0123", "+447834120123")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("formatted with dashes '078-3412-0123' vs clean international '+447834120123'") {
            val result = PhoneNumberHelper.numbersMatch("078-3412-0123", "+447834120123")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("formatted with spaces and dashes '+44 7834 120-123' vs clean '+447834120123'") {
            val result = PhoneNumberHelper.numbersMatch("+44 7834 120-123", "+447834120123")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("US format with parentheses '(555) 123-4567' vs '+15551234567'") {
            val result = PhoneNumberHelper.numbersMatch("(555) 123-4567", "+15551234567")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("international formatted with spaces '+40 741 234 567' vs '+40741234567'") {
            val result = PhoneNumberHelper.numbersMatch("+40 741 234 567", "+40741234567")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("both formatted with spaces '+44 7834 120 123' vs '+44 7834 120 123'") {
            val result = PhoneNumberHelper.numbersMatch("+44 7834 120 123", "+44 7834 120 123")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("contact format with country code in parens '(+44) 7834120123' vs '+447834120123'") {
            val result = PhoneNumberHelper.numbersMatch("(+44) 7834120123", "+447834120123")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }
    }

    // ==========================================
    // numbersMatch tests - the (0) display convention
    // ==========================================

    Given("numbersMatch with the +CC (0) display convention") {

        When("'+44 (0) 7834 120123' vs clean international '+447834120123'") {
            val result = PhoneNumberHelper.numbersMatch("+44 (0) 7834 120123", "+447834120123")
            Then("they should match via trunk prefix removal") {
                result.shouldBeTrue()
            }
        }

        When("'+44 (0) 7834 120123' vs local format '07834120123'") {
            val result = PhoneNumberHelper.numbersMatch("+44 (0) 7834 120123", "07834120123")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }
    }

    // ==========================================
    // numbersMatch tests - different numbers should NOT match
    // ==========================================

    Given("numbersMatch with different numbers that should not match") {

        When("numbers differ in last few digits '+447834120123' vs '+447834120999'") {
            val result = PhoneNumberHelper.numbersMatch("+447834120123", "+447834120999")
            Then("they should not match") {
                result.shouldBeFalse()
            }
        }

        When("different country codes but same local digits '+447834120123' vs '+407834120123'") {
            val result = PhoneNumberHelper.numbersMatch("+447834120123", "+407834120123")
            Then("they should not match due to strict cross-country prevention") {
                result.shouldBeFalse()
            }
        }

        When("completely different numbers '+447834120123' vs '+441111222333'") {
            val result = PhoneNumberHelper.numbersMatch("+447834120123", "+441111222333")
            Then("they should not match") {
                result.shouldBeFalse()
            }
        }

        When("short different numbers '+41234567' vs '+47654321'") {
            val result = PhoneNumberHelper.numbersMatch("+41234567", "+47654321")
            Then("they should not match") {
                result.shouldBeFalse()
            }
        }
    }

    // ==========================================
    // numbersMatch tests - edge cases
    // ==========================================

    Given("numbersMatch edge cases") {

        When("both strings are empty") {
            val result = PhoneNumberHelper.numbersMatch("", "")
            Then("they should not match") {
                result.shouldBeFalse()
            }
        }

        When("first string is empty, second is a number") {
            val result = PhoneNumberHelper.numbersMatch("", "+447834120123")
            Then("they should not match") {
                result.shouldBeFalse()
            }
        }

        When("first is a number, second string is empty") {
            val result = PhoneNumberHelper.numbersMatch("+447834120123", "")
            Then("they should not match") {
                result.shouldBeFalse()
            }
        }

        When("both are non-digit strings 'abc' and 'def'") {
            val result = PhoneNumberHelper.numbersMatch("abc", "def")
            Then("they should not match") {
                result.shouldBeFalse()
            }
        }

        When("very short identical numbers '112' (emergency)") {
            val result = PhoneNumberHelper.numbersMatch("112", "112")
            Then("they should not match because less than 7 digits") {
                result.shouldBeFalse()
            }
        }

        When("very short different numbers '112' vs '911'") {
            val result = PhoneNumberHelper.numbersMatch("112", "911")
            Then("they should not match") {
                result.shouldBeFalse()
            }
        }

        When("exactly 7-digit identical numbers '1234567'") {
            val result = PhoneNumberHelper.numbersMatch("1234567", "1234567")
            Then("they should match since 7 is the minimum") {
                result.shouldBeTrue()
            }
        }

        When("6-digit identical numbers '123456'") {
            val result = PhoneNumberHelper.numbersMatch("123456", "123456")
            Then("they should not match because below the 7-digit minimum") {
                result.shouldBeFalse()
            }
        }
    }

    // ==========================================
    // Real-world scenarios
    // ==========================================

    Given("real-world scenarios where a contact sends a FIND SMS") {

        When("UK contact '07862 341222' sends from '+447862341222'") {
            val result = PhoneNumberHelper.numbersMatch("07862 341222", "+447862341222")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("Romanian contact '0741 234 567' sends from '+40741234567'") {
            val result = PhoneNumberHelper.numbersMatch("0741 234 567", "+40741234567")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("US contact '(555) 123-4567' sends from '+15551234567'") {
            val result = PhoneNumberHelper.numbersMatch("(555) 123-4567", "+15551234567")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("international prefix stored '+447862341222' matches itself") {
            val result = PhoneNumberHelper.numbersMatch("+447862341222", "+447862341222")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("contact stored with 00 prefix '0044 7862 341222' sends from '+447862341222'") {
            val result = PhoneNumberHelper.numbersMatch("0044 7862 341222", "+447862341222")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("Indian number '+91 98765 43210' sends from '+919876543210'") {
            val result = PhoneNumberHelper.numbersMatch("+91 98765 43210", "+919876543210")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("French number '06 12 34 56 78' sends from '+33612345678'") {
            val result = PhoneNumberHelper.numbersMatch("06 12 34 56 78", "+33612345678")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("Australian number '0412 345 678' sends from '+61412345678'") {
            val result = PhoneNumberHelper.numbersMatch("0412 345 678", "+61412345678")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("Japanese number '090-1234-5678' sends from '+819012345678'") {
            val result = PhoneNumberHelper.numbersMatch("090-1234-5678", "+819012345678")
            Then("they should match") {
                result.shouldBeTrue()
            }
        }

        When("wrong number '+447999888777' does not match designated '+447862341222'") {
            val result = PhoneNumberHelper.numbersMatch("+447862341222", "+447999888777")
            Then("they should not match") {
                result.shouldBeFalse()
            }
        }
    }
})
