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

/**
 * Performs exact keyword matching on SMS message bodies.
 *
 * The entire message body (after trimming whitespace) must be exactly
 * equal to the keyword. This prevents false triggers from everyday
 * messages that happen to contain the keyword among other words.
 *
 * The keyword match is case-sensitive (keywords are stored as uppercase).
 */
object KeywordMatcher {

    /**
     * Returns true if the message body, after trimming leading/trailing
     * whitespace, is exactly equal to the keyword (case-sensitive).
     *
     * Examples with keyword "FIND":
     *   "FIND"                 → true  (exact match)
     *   "  FIND  "             → true  (whitespace trimmed)
     *   "FIND A CAT"           → false (extra words)
     *   "Please FIND my phone" → false (extra words)
     *   "FINDING"              → false (not exact)
     *   "find"                 → false (case sensitive)
     */
    fun matchesKeyword(body: String, keyword: String): Boolean {
        if (keyword.isBlank() || body.isEmpty()) return false
        return body.trim() == keyword
    }
}
