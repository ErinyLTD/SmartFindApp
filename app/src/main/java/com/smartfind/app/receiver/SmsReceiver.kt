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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.smartfind.app.SettingsManager
import com.smartfind.app.data.EventLogger
import com.smartfind.app.util.KeywordMatcher
import com.smartfind.app.util.PhoneNumberHelper
import com.smartfind.app.util.SmsSpoofDetector
import com.smartfind.app.util.TriggerProcessor

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val settings = SettingsManager(context)
        val processor = TriggerProcessor(context, settings)
        val cid = EventLogger.newCorrelationId()

        Log.d(TAG, "[$cid] onReceive|path=broadcast")

        // Run shared guard checks (service enabled, active use, cooldown, rate limit, config)
        val guards = processor.runGuardChecks(cid)
        if (!guards.passed) return

        // Parse SMS messages from intent
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages == null) {
            Log.d(TAG, "[$cid] getMessagesFromIntent=null|result=EXIT")
            return
        }
        val subscriptionId = SmsSpoofDetector.getSubscriptionId(intent)

        Log.d(TAG, "[$cid] sms_count=${messages.size}|subscription_id=$subscriptionId")

        for ((index, smsMessage) in messages.withIndex()) {
            val sender = smsMessage.displayOriginatingAddress
            val body = smsMessage.messageBody

            if (sender == null || body == null) {
                Log.d(TAG, "[$cid] msg[$index]|sender=${sender != null}|body=${body != null}|result=SKIP")
                continue
            }

            val bodyPreview = processor.redactBody(body)

            // Check sender match
            val matchedNumber = guards.designatedNumbers.firstOrNull { designated ->
                PhoneNumberHelper.numbersMatch(designated, sender)
            }
            val keywordMatch = KeywordMatcher.matchesKeyword(body, guards.keyword)

            Log.d(TAG, "[$cid] msg[$index]|sender_last4=${sender.takeLast(4)}" +
                "|sender_match=${matchedNumber != null}" +
                "|keyword_match=$keywordMatch" +
                "|body_preview=$bodyPreview")

            if (matchedNumber != null && keywordMatch) {
                Log.d(TAG, "[$cid] TRIGGER_MATCH|msg[$index]|proceeding_to_checks")

                // Update timestamp to avoid polling re-processing
                settings.setLastCheckedSmsTimestamp(System.currentTimeMillis())

                // SMS spoofing check (broadcast-only — SmsCheckWorker doesn't
                // have the raw SmsMessage object needed for spoof detection)
                val isSpoofed = SmsSpoofDetector.isSuspicious(context, smsMessage, subscriptionId)
                if (isSpoofed) {
                    Log.d(TAG, "[$cid] spoof_check=SUSPICIOUS|result=BLOCKED")
                    EventLogger.logSmsSpoofingBlocked(context, sender, cid)
                    break
                }
                Log.d(TAG, "[$cid] spoof_check=OK|result=PASS")

                // Delegate trigger handling (car mode, phone call, battery, alarm start)
                processor.handleTriggerMatch(matchedNumber, sender, cid)
                break
            }
        }

        Log.d(TAG, "[$cid] onReceive|DONE")
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
