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

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single auditable event in SmartFind.
 * Used to maintain a history of trigger events, alarm activations,
 * configuration changes, and other significant actions.
 */
@Entity(tableName = "audit_events")
data class AuditEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val detail: String = "",
    /** Groups related log entries from the same trigger processing flow. */
    val correlationId: String? = null
) {
    companion object {
        // Event types
        const val TYPE_TRIGGER_ALARM = "TRIGGER_ALARM"
        const val TYPE_TRIGGER_SUPPRESSED = "TRIGGER_SUPPRESSED"
        const val TYPE_TRIGGER_CAR_MODE = "TRIGGER_CAR_MODE"
        const val TYPE_TRIGGER_LOW_BATTERY = "TRIGGER_LOW_BATTERY"
        const val TYPE_ALARM_STOPPED = "ALARM_STOPPED"
        const val TYPE_SERVICE_ENABLED = "SERVICE_ENABLED"
        const val TYPE_SERVICE_DISABLED = "SERVICE_DISABLED"
        const val TYPE_CONTACT_ADDED = "CONTACT_ADDED"
        const val TYPE_CONTACT_REMOVED = "CONTACT_REMOVED"
        const val TYPE_KEYWORD_CHANGED = "KEYWORD_CHANGED"
        const val TYPE_SETTING_CHANGED = "SETTING_CHANGED"
        const val TYPE_RATE_LIMITED = "RATE_LIMITED"
        const val TYPE_SMS_SPOOFING_BLOCKED = "SMS_SPOOFING_BLOCKED"
        const val TYPE_BATTERY_SAVER_ON = "BATTERY_SAVER_ON"
        const val TYPE_BATTERY_SAVER_OFF = "BATTERY_SAVER_OFF"
        const val TYPE_AUTH_REQUIRED = "AUTH_REQUIRED"
        const val TYPE_AUTH_SUCCESS = "AUTH_SUCCESS"
        const val TYPE_AUTH_FAILED = "AUTH_FAILED"

    }
}
