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

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Convenience wrapper for logging audit events to the Room database.
 * All logging is fire-and-forget on IO dispatcher to avoid blocking callers.
 */
object EventLogger {

    /** Events are pruned when total count exceeds this threshold. */
    private const val PRUNE_THRESHOLD = 600
    /** After pruning, the newest N events are kept. */
    private const val PRUNE_TARGET = 500

    /**
     * Persistent scope for fire-and-forget log writes. Uses [SupervisorJob]
     * so a failure in one log call does not cancel others.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun getDao(context: Context): AuditEventDao {
        return SmartFindDatabase.getInstance(context).auditEventDao()
    }

    /**
     * Generates a short correlation ID (first 8 chars of a UUID) to group
     * related log entries from the same trigger processing flow.
     */
    fun newCorrelationId(): String = UUID.randomUUID().toString().take(8)

    /**
     * Redacts a phone number to show only the last 4 digits for privacy.
     * Prevents PII from being stored in the unencrypted Room database.
     * Example: "+447834120123" -> "***0123"
     */
    internal fun redactNumber(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length > 4) {
            "***${digits.takeLast(4)}"
        } else {
            "***"
        }
    }

    /**
     * Logs an audit event asynchronously.
     */
    fun log(context: Context, type: String, detail: String = "", correlationId: String? = null) {
        scope.launch {
            try {
                val dao = getDao(context)
                dao.insert(AuditEvent(type = type, detail = detail, correlationId = correlationId))
                val count = dao.getCount()
                if (count > PRUNE_THRESHOLD) {
                    dao.pruneOldEvents(PRUNE_TARGET)
                }
            } catch (_: Exception) {
                // Logging should never crash the app
            }
        }
    }

    fun logTriggerAlarm(context: Context, sender: String, correlationId: String? = null) {
        log(context, AuditEvent.TYPE_TRIGGER_ALARM, "from: ${redactNumber(sender)}", correlationId)
    }

    fun logTriggerSuppressed(context: Context, reason: String, correlationId: String? = null) {
        log(context, AuditEvent.TYPE_TRIGGER_SUPPRESSED, reason, correlationId)
    }

    fun logTriggerCarMode(context: Context, sender: String, correlationId: String? = null) {
        log(context, AuditEvent.TYPE_TRIGGER_CAR_MODE, "from: ${redactNumber(sender)}", correlationId)
    }

    fun logTriggerLowBattery(context: Context, sender: String, correlationId: String? = null) {
        log(context, AuditEvent.TYPE_TRIGGER_LOW_BATTERY, "from: ${redactNumber(sender)}", correlationId)
    }

    fun logAlarmStopped(context: Context) {
        log(context, AuditEvent.TYPE_ALARM_STOPPED)
    }

    fun logServiceToggled(context: Context, enabled: Boolean) {
        log(context, if (enabled) AuditEvent.TYPE_SERVICE_ENABLED else AuditEvent.TYPE_SERVICE_DISABLED)
    }

    fun logContactAdded(context: Context, number: String) {
        log(context, AuditEvent.TYPE_CONTACT_ADDED, redactNumber(number))
    }

    fun logContactRemoved(context: Context, number: String) {
        log(context, AuditEvent.TYPE_CONTACT_REMOVED, redactNumber(number))
    }

    /**
     * Logs a keyword change event. The actual keyword value is never
     * logged — it is a shared secret between the user and their contacts.
     */
    fun logKeywordChanged(context: Context) {
        log(context, AuditEvent.TYPE_KEYWORD_CHANGED, "keyword_changed")
    }

    fun logSettingChanged(context: Context, setting: String, value: String) {
        log(context, AuditEvent.TYPE_SETTING_CHANGED, "$setting=$value")
    }

    fun logSmsSpoofingBlocked(context: Context, sender: String, correlationId: String? = null) {
        log(context, AuditEvent.TYPE_SMS_SPOOFING_BLOCKED, "from: ${redactNumber(sender)}", correlationId)
    }

}
