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
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowTelephonyManager
import com.smartfind.app.testing.robolectric.RobolectricTest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import com.smartfind.app.util.PhoneNumberHelper

@RobolectricTest
class PhoneNumberHelperNormalizeSpec : BehaviorSpec({

    lateinit var context: Context
    lateinit var shadowTelephonyManager: ShadowTelephonyManager

    beforeEach {
        context = ApplicationProvider.getApplicationContext()
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        shadowTelephonyManager = Shadows.shadowOf(telephonyManager)
    }

    fun setSimCountry(iso: String) {
        shadowTelephonyManager.setSimCountryIso(iso)
    }

    Given("getCountryCode for various countries") {

        When("SIM country is gb") {
            Then("country code should be 44") {
                setSimCountry("gb")
                PhoneNumberHelper.getCountryCode(context) shouldBe "44"
            }
        }

        When("SIM country is us") {
            Then("country code should be 1") {
                setSimCountry("us")
                PhoneNumberHelper.getCountryCode(context) shouldBe "1"
            }
        }

        When("SIM country is ro") {
            Then("country code should be 40") {
                setSimCountry("ro")
                PhoneNumberHelper.getCountryCode(context) shouldBe "40"
            }
        }

        When("SIM country is de") {
            Then("country code should be 49") {
                setSimCountry("de")
                PhoneNumberHelper.getCountryCode(context) shouldBe "49"
            }
        }

        When("SIM country is fr") {
            Then("country code should be 33") {
                setSimCountry("fr")
                PhoneNumberHelper.getCountryCode(context) shouldBe "33"
            }
        }

        When("SIM country is in") {
            Then("country code should be 91") {
                setSimCountry("in")
                PhoneNumberHelper.getCountryCode(context) shouldBe "91"
            }
        }

        When("SIM country is jp") {
            Then("country code should be 81") {
                setSimCountry("jp")
                PhoneNumberHelper.getCountryCode(context) shouldBe "81"
            }
        }

        When("SIM country is au") {
            Then("country code should be 61") {
                setSimCountry("au")
                PhoneNumberHelper.getCountryCode(context) shouldBe "61"
            }
        }
    }

    Given("getCountryCode with uppercase ISO") {

        When("ISO is GB in uppercase") {
            Then("country code should be 44") {
                setSimCountry("GB")
                PhoneNumberHelper.getCountryCode(context) shouldBe "44"
            }
        }

        When("ISO is US in uppercase") {
            Then("country code should be 1") {
                setSimCountry("US")
                PhoneNumberHelper.getCountryCode(context) shouldBe "1"
            }
        }
    }

    Given("getCountryCode with unknown ISO") {

        When("ISO is an unknown value") {
            Then("country code should be null") {
                setSimCountry("zz")
                PhoneNumberHelper.getCountryCode(context).shouldBeNull()
            }
        }
    }

    Given("getCountryCode with empty ISO") {

        When("ISO is empty string") {
            Then("country code should be null") {
                setSimCountry("")
                PhoneNumberHelper.getCountryCode(context).shouldBeNull()
            }
        }
    }

    Given("normalizeNumber with already international numbers") {

        When("number already has + prefix") {
            Then("it should return the number unchanged") {
                PhoneNumberHelper.normalizeNumber(context, "+441234567890") shouldBe "+441234567890"
            }
        }

        When("number has + prefix with US code") {
            Then("it should return the number unchanged") {
                PhoneNumberHelper.normalizeNumber(context, "+11234567890") shouldBe "+11234567890"
            }
        }
    }

    Given("normalizeNumber with 00 prefix") {

        When("number starts with 00") {
            Then("it should replace 00 with +") {
                PhoneNumberHelper.normalizeNumber(context, "00441234567890") shouldBe "+441234567890"
            }
        }

        When("number starts with 00 for US") {
            Then("it should replace 00 with +") {
                PhoneNumberHelper.normalizeNumber(context, "0011234567890") shouldBe "+11234567890"
            }
        }
    }

    Given("normalizeNumber with 011 prefix") {

        When("number starts with 011") {
            Then("it should replace 011 with +") {
                PhoneNumberHelper.normalizeNumber(context, "011441234567890") shouldBe "+441234567890"
            }
        }

        When("number starts with 011 for another country") {
            Then("it should replace 011 with +") {
                PhoneNumberHelper.normalizeNumber(context, "011491234567890") shouldBe "+491234567890"
            }
        }
    }

    Given("normalizeNumber with local numbers and SIM country set") {

        When("local UK number with SIM set to gb") {
            Then("it should prepend UK country code") {
                setSimCountry("gb")
                PhoneNumberHelper.normalizeNumber(context, "07123456789") shouldBe "+447123456789"
            }
        }

        When("local US number with SIM set to us") {
            Then("it should prepend US country code") {
                setSimCountry("us")
                PhoneNumberHelper.normalizeNumber(context, "2125551234") shouldBe "+12125551234"
            }
        }

        When("local German number with SIM set to de") {
            Then("it should prepend German country code") {
                setSimCountry("de")
                PhoneNumberHelper.normalizeNumber(context, "01511234567") shouldBe "+491511234567"
            }
        }

        When("local French number with SIM set to fr") {
            Then("it should prepend French country code") {
                setSimCountry("fr")
                PhoneNumberHelper.normalizeNumber(context, "0612345678") shouldBe "+33612345678"
            }
        }
    }

    Given("normalizeNumber with unknown SIM country") {

        When("SIM country is unknown") {
            Then("it should return the number as-is with no country code prepended") {
                setSimCountry("zz")
                val result = PhoneNumberHelper.normalizeNumber(context, "07123456789")
                result shouldBe "07123456789"
            }
        }
    }

    Given("normalizeNumber edge cases with formatting") {

        When("number contains spaces") {
            Then("it should strip spaces and normalize") {
                PhoneNumberHelper.normalizeNumber(context, "+44 7123 456 789") shouldBe "+447123456789"
            }
        }

        When("number contains dashes") {
            Then("it should strip dashes and normalize") {
                PhoneNumberHelper.normalizeNumber(context, "+1-212-555-1234") shouldBe "+12125551234"
            }
        }

        When("number contains parentheses") {
            Then("it should strip parentheses and normalize") {
                PhoneNumberHelper.normalizeNumber(context, "+1(212)5551234") shouldBe "+12125551234"
            }
        }
    }

    Given("normalizeNumber with empty strings") {

        When("number is empty") {
            Then("it should return empty string") {
                PhoneNumberHelper.normalizeNumber(context, "") shouldBe ""
            }
        }
    }

    Given("integration of normalizeNumber and numbersMatch") {

        When("comparing same number in different formats") {
            Then("normalized numbers should match") {
                setSimCountry("gb")
                val normalized1 = PhoneNumberHelper.normalizeNumber(context, "+44 7123 456789")
                val normalized2 = PhoneNumberHelper.normalizeNumber(context, "07123456789")
                PhoneNumberHelper.numbersMatch(normalized1, normalized2) shouldBe true
            }
        }

        When("comparing same US number in different formats") {
            Then("normalized numbers should match") {
                setSimCountry("us")
                val normalized1 = PhoneNumberHelper.normalizeNumber(context, "+1-212-555-1234")
                val normalized2 = PhoneNumberHelper.normalizeNumber(context, "2125551234")
                PhoneNumberHelper.numbersMatch(normalized1, normalized2) shouldBe true
            }
        }

        When("comparing different numbers") {
            Then("normalized numbers should not match") {
                setSimCountry("gb")
                val normalized1 = PhoneNumberHelper.normalizeNumber(context, "+447123456789")
                val normalized2 = PhoneNumberHelper.normalizeNumber(context, "+447999888777")
                PhoneNumberHelper.numbersMatch(normalized1, normalized2) shouldBe false
            }
        }

        When("comparing number with 00 prefix against + prefix") {
            Then("normalized numbers should match") {
                val normalized1 = PhoneNumberHelper.normalizeNumber(context, "00441234567890")
                val normalized2 = PhoneNumberHelper.normalizeNumber(context, "+441234567890")
                PhoneNumberHelper.numbersMatch(normalized1, normalized2) shouldBe true
            }
        }

        When("comparing number with 011 prefix against + prefix") {
            Then("normalized numbers should match") {
                val normalized1 = PhoneNumberHelper.normalizeNumber(context, "011441234567890")
                val normalized2 = PhoneNumberHelper.normalizeNumber(context, "+441234567890")
                PhoneNumberHelper.numbersMatch(normalized1, normalized2) shouldBe true
            }
        }
    }
})
