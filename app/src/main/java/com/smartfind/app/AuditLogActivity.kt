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

package com.smartfind.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.smartfind.app.data.AuditEvent
import com.smartfind.app.data.SmartFindDatabase
import com.smartfind.app.util.AuthHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only viewer for the audit log. Shows all recorded events in
 * reverse-chronological order with human-readable labels.
 *
 * Requires authentication to open — consistent with every other
 * sensitive screen in the app.
 */
class AuditLogActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audit_log)

        val toolbar = findViewById<MaterialToolbar>(R.id.auditLogToolbar)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.auditLogRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)

        recyclerView.layoutManager = LinearLayoutManager(this)

        AuthHelper.authenticate(
            activity = this,
            title = "View Audit Log",
            subtitle = "Authenticate to view the event history",
            onSuccess = { loadEvents() },
            onFailure = { finish() }
        )
    }

    private fun loadEvents() {
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) {
                SmartFindDatabase.getInstance(this@AuditLogActivity)
                    .auditEventDao()
                    .getRecent(500)
            }

            if (events.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyStateText.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyStateText.visibility = View.GONE
                recyclerView.adapter = AuditLogAdapter(events)
            }
        }
    }

    // ==========================================
    // RecyclerView Adapter
    // ==========================================

    private class AuditLogAdapter(
        private val events: List<AuditEvent>
    ) : RecyclerView.Adapter<AuditLogAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_audit_event, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(events[position])
        }

        override fun getItemCount(): Int = events.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val typeText: TextView = itemView.findViewById(R.id.eventTypeText)
            private val detailText: TextView = itemView.findViewById(R.id.eventDetailText)
            private val timestampText: TextView = itemView.findViewById(R.id.eventTimestampText)

            fun bind(event: AuditEvent) {
                typeText.text = formatEventType(event.type)

                if (event.detail.isNotBlank()) {
                    detailText.text = event.detail
                    detailText.visibility = View.VISIBLE
                } else {
                    detailText.visibility = View.GONE
                }

                timestampText.text = formatTimestamp(event.timestamp)
            }
        }
    }

    companion object {
        private val TIMESTAMP_FORMAT = SimpleDateFormat("MMM d, yyyy  HH:mm:ss", Locale.getDefault())

        fun formatTimestamp(millis: Long): String {
            return TIMESTAMP_FORMAT.format(Date(millis))
        }

        fun formatEventType(type: String): String {
            return when (type) {
                AuditEvent.TYPE_TRIGGER_ALARM -> "Alarm triggered"
                AuditEvent.TYPE_TRIGGER_SUPPRESSED -> "Trigger suppressed"
                AuditEvent.TYPE_TRIGGER_CAR_MODE -> "Trigger received (Android Auto)"
                AuditEvent.TYPE_TRIGGER_LOW_BATTERY -> "Alarm triggered (low battery)"
                AuditEvent.TYPE_ALARM_STOPPED -> "Alarm stopped"
                AuditEvent.TYPE_SERVICE_ENABLED -> "Service enabled"
                AuditEvent.TYPE_SERVICE_DISABLED -> "Service disabled"
                AuditEvent.TYPE_CONTACT_ADDED -> "Contact added"
                AuditEvent.TYPE_CONTACT_REMOVED -> "Contact removed"
                AuditEvent.TYPE_KEYWORD_CHANGED -> "Keyword changed"
                AuditEvent.TYPE_SETTING_CHANGED -> "Setting changed"
                AuditEvent.TYPE_RATE_LIMITED -> "Rate limit reached"
                AuditEvent.TYPE_SMS_SPOOFING_BLOCKED -> "SMS spoofing blocked"
                AuditEvent.TYPE_BATTERY_SAVER_ON -> "Battery saver enabled"
                AuditEvent.TYPE_BATTERY_SAVER_OFF -> "Battery saver disabled"
                AuditEvent.TYPE_AUTH_REQUIRED -> "Authentication required"
                AuditEvent.TYPE_AUTH_SUCCESS -> "Authentication succeeded"
                AuditEvent.TYPE_AUTH_FAILED -> "Authentication failed"
                else -> type
            }
        }
    }
}
