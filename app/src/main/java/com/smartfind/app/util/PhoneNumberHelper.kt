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

object PhoneNumberHelper {

    /** ISO country code → calling code map for common countries */
    private val COUNTRY_CODES = mapOf(
        "af" to "93", "al" to "355", "dz" to "213", "ar" to "54", "am" to "374",
        "au" to "61", "at" to "43", "az" to "994", "bh" to "973", "bd" to "880",
        "by" to "375", "be" to "32", "bo" to "591", "ba" to "387", "br" to "55",
        "bg" to "359", "ca" to "1", "cl" to "56", "cn" to "86", "co" to "57",
        "hr" to "385", "cu" to "53", "cy" to "357", "cz" to "420", "dk" to "45",
        "ec" to "593", "eg" to "20", "ee" to "372", "et" to "251", "fi" to "358",
        "fr" to "33", "ge" to "995", "de" to "49", "gh" to "233", "gr" to "30",
        "gt" to "502", "hk" to "852", "hu" to "36", "is" to "354", "in" to "91",
        "id" to "62", "ir" to "98", "iq" to "964", "ie" to "353", "il" to "972",
        "it" to "39", "jm" to "1876", "jp" to "81", "jo" to "962", "kz" to "7",
        "ke" to "254", "kr" to "82", "kw" to "965", "lv" to "371", "lb" to "961",
        "lt" to "370", "lu" to "352", "my" to "60", "mx" to "52", "md" to "373",
        "ma" to "212", "nl" to "31", "nz" to "64", "ng" to "234", "no" to "47",
        "pk" to "92", "pa" to "507", "pe" to "51", "ph" to "63", "pl" to "48",
        "pt" to "351", "qa" to "974", "ro" to "40", "ru" to "7", "sa" to "966",
        "rs" to "381", "sg" to "65", "sk" to "421", "si" to "386", "za" to "27",
        "es" to "34", "se" to "46", "ch" to "41", "tw" to "886", "th" to "66",
        "tn" to "216", "tr" to "90", "ua" to "380", "ae" to "971", "gb" to "44",
        "us" to "1", "uy" to "598", "uz" to "998", "ve" to "58", "vn" to "84"
    )

    /**
     * Returns the device's country calling code (e.g. "40" for Romania)
     * detected from the SIM card, or null if unavailable.
     */
    fun getCountryCode(context: Context): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null
        val iso = (tm.simCountryIso ?: tm.networkCountryIso)?.lowercase() ?: return null
        return COUNTRY_CODES[iso]
    }

    /**
     * Normalizes a phone number to international format (+CC...).
     *
     * Handles:
     *   "+44786..."     → "+44786..." (unchanged, already international)
     *   "0044786..."    → "+44786..." (00 international dialing prefix)
     *   "011447862..."  → "+447862..." (011 North American international prefix)
     *   "0741234567"    → "+40741234567" (local format, single leading 0, SIM=Romania)
     *   "741234567"     → "+40741234567" (local format, no prefix, SIM=Romania)
     */
    fun normalizeNumber(context: Context, number: String): String {
        val cleaned = number.trim().replace(Regex("[\\s\\-()]"), "")

        // Already international format with +
        if (cleaned.startsWith("+")) return cleaned

        // "00" international dialing prefix (used in Europe, Asia, most of the world)
        if (cleaned.startsWith("00") && cleaned.length > 4) {
            return "+${cleaned.substring(2)}"
        }

        // "011" North American international dialing prefix
        if (cleaned.startsWith("011") && cleaned.length > 5) {
            return "+${cleaned.substring(3)}"
        }

        val countryCode = getCountryCode(context) ?: return cleaned

        // Local format: strip single leading 0
        val local = if (cleaned.startsWith("0")) cleaned.substring(1) else cleaned

        return "+$countryCode$local"
    }

    /**
     * Compares two phone numbers by normalizing both to significant digits.
     *
     * Matching strategy (stricter than last-10-digit):
     * 1. If both numbers have 11+ digits (international format), compare ALL digits.
     *    This prevents cross-country false positives (e.g. +44... vs +40...).
     * 2. If either number has fewer digits (local format), fall back to comparing
     *    the last min(len1, len2, 10) digits for format flexibility.
     *
     * Handles all format combinations:
     *   "07834120123" vs "+447834120123"  → match (local vs international)
     *   "00447834120123" vs "+447834120123" → match (00 prefix stripped)
     *   "+447834120123" vs "+447834120123" → match (identical)
     *   "+447834120123" vs "+407834120123" → NO match (different country codes)
     */
    fun numbersMatch(storedNumber: String, incomingNumber: String): Boolean {
        val stored = toSignificantDigits(storedNumber)
        val incoming = toSignificantDigits(incomingNumber)
        if (stored.isEmpty() || incoming.isEmpty()) return false

        // If both numbers are long enough to include country codes (11+ digits),
        // require a full match to prevent cross-country collisions
        if (stored.length >= 11 && incoming.length >= 11) {
            if (stored == incoming) return true
            // Handle the "+CC (0) local" display convention (e.g. "+44 (0) 7834 120123").
            // The embedded trunk-prefix 0 makes the digit string 1 char longer than
            // the clean international form. Try removing a single embedded 0 from the
            // longer number and compare again.
            return matchWithTrunkPrefixRemoval(stored, incoming)
        }

        // Otherwise, one or both are in local format — compare last N digits
        val minLen = minOf(stored.length, incoming.length, 10)
        // Require at least 7 significant digits to prevent short-number collisions
        if (minLen < 7) return false
        return stored.takeLast(minLen) == incoming.takeLast(minLen)
    }

    /**
     * Handles the "+CC (0) local" display convention where a trunk-prefix 0
     * is embedded between the country code and local number.
     *
     * If one number is exactly 1 digit longer than the other, tries removing
     * each '0' in the longer string (after position 1, to preserve the country
     * code start) and checks if the result matches the shorter string.
     *
     * Returns false for equal-length numbers or when no single-0 removal matches,
     * preserving the strict cross-country collision prevention.
     */
    private fun matchWithTrunkPrefixRemoval(a: String, b: String): Boolean {
        val longer: String
        val shorter: String
        if (a.length == b.length + 1) {
            longer = a; shorter = b
        } else if (b.length == a.length + 1) {
            longer = b; shorter = a
        } else {
            return false // length difference ≠ 1, not a trunk-prefix case
        }

        // Try removing each '0' after position 1 in the longer string
        for (i in 2 until longer.length) {
            if (longer[i] == '0') {
                val candidate = longer.removeRange(i, i + 1)
                if (candidate == shorter) return true
            }
        }
        return false
    }

    /**
     * Strips a phone number down to its significant digits:
     * removes all non-digit chars, then strips leading "00" or single "0" prefix
     * so that local and international formats reduce to the same digit sequence.
     *
     * "07834120123"    → "7834120123"   (local 0 stripped)
     * "00447834120123" → "447834120123" (00 prefix stripped)
     * "+447834120123"  → "447834120123" (+ removed, digits kept)
     * "7834120123"     → "7834120123"   (no prefix, unchanged)
     */
    internal fun toSignificantDigits(number: String): String {
        val digits = number.filter { it.isDigit() }
        // Strip "00" international dialing prefix
        if (digits.startsWith("00") && digits.length > 4) {
            return digits.substring(2)
        }
        // Strip single leading "0" (local prefix)
        if (digits.startsWith("0") && digits.length > 1) {
            return digits.substring(1)
        }
        return digits
    }
}
