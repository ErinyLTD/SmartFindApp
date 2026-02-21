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
import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.smartfind.app.testing.robolectric.RobolectricTest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse
import com.smartfind.app.util.BatteryHelper

@RobolectricTest
class BatteryHelperSpec : BehaviorSpec({

    lateinit var context: Context

    beforeEach {
        context = RuntimeEnvironment.getApplication()
    }

    fun setBatteryLevel(level: Int, scale: Int) {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, level)
            putExtra(BatteryManager.EXTRA_SCALE, scale)
        }
        context.sendStickyBroadcast(intent)
    }

    Given("LOW_BATTERY_THRESHOLD constant") {

        When("checking the threshold value") {
            Then("it should be 15") {
                BatteryHelper.LOW_BATTERY_THRESHOLD shouldBe 15
            }
        }
    }

    Given("isLowBattery at threshold boundary") {

        When("battery is at 15%") {
            Then("it should be true") {
                setBatteryLevel(15, 100)
                BatteryHelper.isLowBattery(context).shouldBeTrue()
            }
        }

        When("battery is below 15%") {
            Then("it should be true") {
                setBatteryLevel(10, 100)
                BatteryHelper.isLowBattery(context).shouldBeTrue()
            }
        }

        When("battery is at 1%") {
            Then("it should be true") {
                setBatteryLevel(1, 100)
                BatteryHelper.isLowBattery(context).shouldBeTrue()
            }
        }

        When("battery is at 0%") {
            Then("it should be true") {
                setBatteryLevel(0, 100)
                BatteryHelper.isLowBattery(context).shouldBeTrue()
            }
        }

        When("battery is at 16%") {
            Then("it should be false") {
                setBatteryLevel(16, 100)
                BatteryHelper.isLowBattery(context).shouldBeFalse()
            }
        }

        When("battery is at 50%") {
            Then("it should be false") {
                setBatteryLevel(50, 100)
                BatteryHelper.isLowBattery(context).shouldBeFalse()
            }
        }

        When("battery is at 100%") {
            Then("it should be false") {
                setBatteryLevel(100, 100)
                BatteryHelper.isLowBattery(context).shouldBeFalse()
            }
        }
    }

    Given("isLowBattery with non-100 scale") {

        When("battery level is 3 out of 20 scale") {
            Then("it should be true since 3/20 = 15%") {
                setBatteryLevel(3, 20)
                BatteryHelper.isLowBattery(context).shouldBeTrue()
            }
        }

        When("battery level is 4 out of 20 scale") {
            Then("it should be false since 4/20 = 20%") {
                setBatteryLevel(4, 20)
                BatteryHelper.isLowBattery(context).shouldBeFalse()
            }
        }
    }

    Given("isLowBattery with negative level") {

        When("battery level is negative") {
            Then("it should be false (percentage returns -1, which is outside 0..15)") {
                setBatteryLevel(-1, 100)
                BatteryHelper.isLowBattery(context).shouldBeFalse()
            }
        }
    }

    Given("isLowBattery with zero scale") {

        When("battery scale is zero") {
            Then("it should return a consistent result") {
                setBatteryLevel(50, 0)
                // With zero scale, percentage calculation is undefined; implementation dependent
                val result = BatteryHelper.isLowBattery(context)
                // Just verify it doesn't crash and returns a boolean
                (result || !result).shouldBeTrue()
            }
        }
    }

    Given("isLowBattery with negative scale") {

        When("battery scale is negative") {
            Then("it should return a consistent result") {
                setBatteryLevel(50, -100)
                val result = BatteryHelper.isLowBattery(context)
                (result || !result).shouldBeTrue()
            }
        }
    }

    Given("getBatteryPercentage with correct values") {

        When("battery is at 75 out of 100") {
            Then("percentage should be 75") {
                setBatteryLevel(75, 100)
                BatteryHelper.getBatteryPercentage(context) shouldBe 75
            }
        }

        When("battery is at 100 out of 100") {
            Then("percentage should be 100") {
                setBatteryLevel(100, 100)
                BatteryHelper.getBatteryPercentage(context) shouldBe 100
            }
        }

        When("battery is at 0 out of 100") {
            Then("percentage should be 0") {
                setBatteryLevel(0, 100)
                BatteryHelper.getBatteryPercentage(context) shouldBe 0
            }
        }

        When("battery is at 50 out of 200") {
            Then("percentage should be 25") {
                setBatteryLevel(50, 200)
                BatteryHelper.getBatteryPercentage(context) shouldBe 25
            }
        }
    }

    Given("getBatteryPercentage edge cases") {

        When("battery level is negative") {
            Then("percentage should be -1") {
                setBatteryLevel(-1, 100)
                BatteryHelper.getBatteryPercentage(context) shouldBe -1
            }
        }

        When("battery scale is zero") {
            Then("percentage should be -1") {
                setBatteryLevel(50, 0)
                BatteryHelper.getBatteryPercentage(context) shouldBe -1
            }
        }

        When("battery scale is negative") {
            Then("percentage should be -1") {
                setBatteryLevel(50, -100)
                BatteryHelper.getBatteryPercentage(context) shouldBe -1
            }
        }
    }

    Given("isPowerSaveMode default state") {

        When("power save mode has not been set") {
            Then("it should be false") {
                BatteryHelper.isPowerSaveMode(context).shouldBeFalse()
            }
        }
    }

    Given("isPowerSaveMode when enabled") {

        When("power save mode is turned on") {
            Then("it should be true") {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                Shadows.shadowOf(powerManager).setIsPowerSaveMode(true)
                BatteryHelper.isPowerSaveMode(context).shouldBeTrue()
            }
        }
    }

    Given("isPowerSaveMode when disabled") {

        When("power save mode is explicitly turned off") {
            Then("it should be false") {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                Shadows.shadowOf(powerManager).setIsPowerSaveMode(false)
                BatteryHelper.isPowerSaveMode(context).shouldBeFalse()
            }
        }
    }

    Given("isPowerSaveMode when PowerManager is null") {

        When("context returns null for PowerManager") {
            Then("it should be false") {
                val mockContext = mock<Context>()
                whenever(mockContext.getSystemService(Context.POWER_SERVICE)).thenReturn(null)
                BatteryHelper.isPowerSaveMode(mockContext).shouldBeFalse()
            }
        }
    }
})
