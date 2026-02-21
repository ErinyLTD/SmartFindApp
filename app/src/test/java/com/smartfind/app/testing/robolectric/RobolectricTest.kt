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

package com.smartfind.app.testing.robolectric

/**
 * Annotation that marks a Kotest spec as requiring the Robolectric sandbox.
 *
 * Equivalent to the `@RobolectricTest` from `kotest-extensions-android`,
 * but compatible with Kotest 5.9.x + Kotlin 1.9.22 (unlike the third-party
 * library which transitively pulls in Kotest 6.x / Kotlin 2.0).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RobolectricTest
