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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AuditEventDao {

    @Insert
    suspend fun insert(event: AuditEvent): Long

    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<AuditEvent>

    @Query("SELECT * FROM audit_events WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByType(type: String, limit: Int = 50): List<AuditEvent>

    @Query("SELECT COUNT(*) FROM audit_events")
    suspend fun getCount(): Int

    /**
     * Prune old events, keeping only the most recent [keepCount] entries.
     * Call periodically to prevent unbounded database growth.
     */
    @Query("DELETE FROM audit_events WHERE id NOT IN (SELECT id FROM audit_events ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun pruneOldEvents(keepCount: Int = 500)

    @Query("DELETE FROM audit_events")
    suspend fun deleteAll()
}
