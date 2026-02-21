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
import android.os.Build
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager

/**
 * Attempts to detect SMS spoofing by validating the subscription/SIM
 * that received the SMS message.
 *
 * Spoofing detection is best-effort — SMS spoofing is fundamentally a
 * network-level problem that cannot be fully solved at the app level.
 * This provides a reasonable heuristic:
 *
 * 1. Checks that the SMS was received on an active SIM subscription
 * 2. Validates the service center address is non-empty (spoofed SMS
 *    sometimes arrive without a valid service center)
 * 3. On Android 11+, verifies the subscription ID from the SmsMessage
 *
 * These checks reduce the attack surface but cannot prevent all spoofing.
 */
object SmsSpoofDetector {

    /**
     * Returns true if the SMS message appears suspicious (possibly spoofed).
     * Returns false if it appears legitimate or if detection is inconclusive.
     *
     * @param context Application context
     * @param smsMessage The parsed SmsMessage to check
     * @param subscriptionId The subscription ID from the SMS intent (-1 if unknown)
     */
    fun isSuspicious(
        context: Context,
        smsMessage: SmsMessage,
        subscriptionId: Int = -1
    ): Boolean {
        // Check 1: Service center address — spoofed SMS may lack this.
        // Not used as a standalone signal because many carriers legitimately
        // omit the SCA. Could be used in a future combined-signal scoring
        // system (e.g. flag only when >=2 suspicious signals fire).
        // val sca = smsMessage.serviceCenterAddress

        // Check 2: Validate subscription ID is active (Android 5.1+)
        if (subscriptionId > 0) {
            if (!isActiveSubscription(context, subscriptionId)) {
                return true // SMS claims to be from a non-existent SIM
            }
        }

        // Check 3: Verify display address vs originating address consistency
        val display = smsMessage.displayOriginatingAddress ?: ""
        val originating = smsMessage.originatingAddress ?: ""
        if (display.isNotEmpty() && originating.isNotEmpty()) {
            // Strip formatting for comparison
            val displayDigits = display.filter { it.isDigit() }
            val originatingDigits = originating.filter { it.isDigit() }
            // If both have digits but they differ significantly, suspicious
            if (displayDigits.length >= 5 && originatingDigits.length >= 5) {
                val displayLast = displayDigits.takeLast(7)
                val originatingLast = originatingDigits.takeLast(7)
                if (displayLast != originatingLast) {
                    return true // Display and originating addresses don't match
                }
            }
        }

        return false
    }

    /**
     * Checks if the given subscription ID corresponds to an active SIM.
     */
    private fun isActiveSubscription(context: Context, subscriptionId: Int): Boolean {
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager ?: return true // Can't check, assume OK

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val activeIds = sm.activeSubscriptionInfoList
                    ?.map { it.subscriptionId } ?: return true
                subscriptionId in activeIds
            } else {
                // On older APIs, just check the default
                true
            }
        } catch (_: SecurityException) {
            true // Missing READ_PHONE_STATE permission — can't verify, assume OK
        }
    }

    /**
     * Extracts the subscription ID from an SMS intent extras.
     * Returns -1 if not available.
     */
    fun getSubscriptionId(intent: Intent): Int {
        return intent.getIntExtra("subscription", -1)
    }
}
