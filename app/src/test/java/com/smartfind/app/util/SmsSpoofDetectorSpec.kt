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
import android.telephony.SmsMessage
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.smartfind.app.testing.robolectric.RobolectricTest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse

@RobolectricTest
class SmsSpoofDetectorSpec : BehaviorSpec({

    lateinit var context: Context
    lateinit var smsMessage: SmsMessage
    lateinit var subscriptionManager: SubscriptionManager

    beforeEach {
        context = mock()
        smsMessage = mock()
        subscriptionManager = mock()

        whenever(context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE))
            .thenReturn(subscriptionManager)
    }

    // ==========================================
    // Not suspicious (legitimate messages)
    // ==========================================

    Given("isSuspicious - matching addresses") {

        When("display matches originating and all checks pass") {
            Then("not suspicious") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeFalse()
            }
        }

        When("subscription ID is -1 (no subscription)") {
            Then("not suspicious") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeFalse()
            }
        }

        When("subscription ID is 0") {
            Then("not suspicious (skips active subscription check)") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, 0).shouldBeFalse()
            }
        }

        When("addresses have different formatting but same last 7 digits") {
            Then("not suspicious") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("07834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeFalse()
            }
        }

        When("display address is empty") {
            Then("not suspicious (address comparison is skipped)") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeFalse()
            }
        }

        When("originating address is empty") {
            Then("not suspicious") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeFalse()
            }
        }

        When("addresses are null") {
            Then("not suspicious") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn(null)
                whenever(smsMessage.originatingAddress).thenReturn(null)

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeFalse()
            }
        }

        When("addresses have fewer than 5 digits (short codes)") {
            Then("not suspicious (comparison is skipped)") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("1234")
                whenever(smsMessage.originatingAddress).thenReturn("5678")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeFalse()
            }
        }
    }

    // ==========================================
    // Suspicious — mismatched addresses
    // ==========================================

    Given("isSuspicious - mismatched addresses") {

        When("display and originating addresses have different last 7 digits") {
            Then("suspicious") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+449999888777")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeTrue()
            }
        }

        When("display shows different number than originating") {
            Then("suspicious") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+15551234567")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Suspicious — invalid subscription ID
    // ==========================================

    Given("isSuspicious - subscription validation") {

        When("subscription ID is not in active subscriptions") {
            Then("suspicious") {
                val subInfo1: SubscriptionInfo = mock()
                whenever(subInfo1.subscriptionId).thenReturn(1)
                val subInfo2: SubscriptionInfo = mock()
                whenever(subInfo2.subscriptionId).thenReturn(2)

                whenever(subscriptionManager.activeSubscriptionInfoList)
                    .thenReturn(listOf(subInfo1, subInfo2))

                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, 99).shouldBeTrue()
            }
        }

        When("subscription ID is in active subscriptions") {
            Then("not suspicious") {
                val subInfo1: SubscriptionInfo = mock()
                whenever(subInfo1.subscriptionId).thenReturn(1)
                val subInfo2: SubscriptionInfo = mock()
                whenever(subInfo2.subscriptionId).thenReturn(2)

                whenever(subscriptionManager.activeSubscriptionInfoList)
                    .thenReturn(listOf(subInfo1, subInfo2))

                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, 1).shouldBeFalse()
            }
        }

        When("SubscriptionManager is null") {
            Then("not suspicious (can't check subscriptions, assume OK)") {
                whenever(context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE))
                    .thenReturn(null)

                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, 5).shouldBeFalse()
            }
        }

        When("activeSubscriptionInfoList is null") {
            Then("not suspicious (null list, can't verify, assume OK)") {
                whenever(subscriptionManager.activeSubscriptionInfoList).thenReturn(null)

                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, 5).shouldBeFalse()
            }
        }

        When("SecurityException is thrown") {
            Then("not suspicious (can't verify, assume OK)") {
                whenever(subscriptionManager.activeSubscriptionInfoList)
                    .thenThrow(SecurityException("Missing READ_PHONE_STATE"))

                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, 5).shouldBeFalse()
            }
        }
    }

    // ==========================================
    // getSubscriptionId
    // ==========================================

    Given("getSubscriptionId") {

        When("intent contains subscription extra") {
            Then("returns value from intent") {
                val intent = Intent().putExtra("subscription", 42)
                SmsSpoofDetector.getSubscriptionId(intent) shouldBe 42
            }
        }

        When("intent does not contain subscription extra") {
            Then("returns -1") {
                val intent = Intent()
                SmsSpoofDetector.getSubscriptionId(intent) shouldBe -1
            }
        }
    }

    // ==========================================
    // Service center address
    // ==========================================

    Given("service center address checks") {

        When("service center is null") {
            Then("missing service center alone does not make message suspicious") {
                whenever(smsMessage.serviceCenterAddress).thenReturn(null)
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeFalse()
            }
        }

        When("service center is blank") {
            Then("blank service center alone does not make message suspicious") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeFalse()
            }
        }
    }

    // ==========================================
    // Combined checks
    // ==========================================

    Given("combined checks") {

        When("addresses mismatch but subscription is valid") {
            Then("address mismatch takes priority - suspicious") {
                val subInfo: SubscriptionInfo = mock()
                whenever(subInfo.subscriptionId).thenReturn(1)
                whenever(subscriptionManager.activeSubscriptionInfoList)
                    .thenReturn(listOf(subInfo))

                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+449999888777")

                SmsSpoofDetector.isSuspicious(context, smsMessage, 1).shouldBeTrue()
            }
        }

        When("addresses match but subscription is invalid") {
            Then("invalid subscription takes priority - suspicious") {
                val subInfo: SubscriptionInfo = mock()
                whenever(subInfo.subscriptionId).thenReturn(1)
                whenever(subscriptionManager.activeSubscriptionInfoList)
                    .thenReturn(listOf(subInfo))

                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, 99).shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Edge cases
    // ==========================================

    Given("edge cases") {

        When("display address has fewer than 5 digits") {
            Then("not suspicious") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("1234")
                whenever(smsMessage.originatingAddress).thenReturn("+447834120123")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeFalse()
            }
        }

        When("originating address has fewer than 5 digits") {
            Then("not suspicious") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("+447834120123")
                whenever(smsMessage.originatingAddress).thenReturn("5678")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeFalse()
            }
        }

        When("both addresses are short") {
            Then("not suspicious") {
                whenever(smsMessage.serviceCenterAddress).thenReturn("+441234567890")
                whenever(smsMessage.displayOriginatingAddress).thenReturn("12")
                whenever(smsMessage.originatingAddress).thenReturn("34")

                SmsSpoofDetector.isSuspicious(context, smsMessage, -1).shouldBeFalse()
            }
        }
    }
})
