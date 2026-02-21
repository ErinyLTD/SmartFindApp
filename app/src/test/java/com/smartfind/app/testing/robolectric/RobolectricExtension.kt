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

import io.kotest.core.extensions.ConstructorExtension
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.core.test.isRootTest
import io.kotest.engine.test.TestResult
import java.util.WeakHashMap
import kotlin.reflect.KClass

/**
 * Kotest extension that bootstraps the Robolectric sandbox for specs
 * annotated with [RobolectricTest].
 *
 * Implements:
 * - [ConstructorExtension]: intercepts spec instantiation so the spec class
 *   is loaded by Robolectric's instrumented classloader.
 * - [TestCaseExtension]: calls [ContainedRobolectricRunner.containedBefore]
 *   before each root test and [ContainedRobolectricRunner.containedAfter]
 *   after, ensuring the Android environment is set up and torn down properly.
 *
 * Register this extension in your Kotest [ProjectConfig]:
 * ```
 * object ProjectConfig : AbstractProjectConfig() {
 *     override fun extensions() = listOf(RobolectricExtension())
 * }
 * ```
 */
class RobolectricExtension : ConstructorExtension, TestCaseExtension {

    private val runnerMap = WeakHashMap<Spec, ContainedRobolectricRunner>()

    override fun <T : Spec> instantiate(clazz: KClass<T>): Spec? {
        // Only intercept specs annotated with @RobolectricTest
        val hasAnnotation = clazz.annotations.any { it is RobolectricTest }
        if (!hasAnnotation) return null

        val runner = ContainedRobolectricRunner()
        val spec = runner.sdkEnvironment
            .bootstrappedClass<Spec>(clazz.java)
            .newInstance()

        runnerMap[spec] = runner
        return spec
    }

    override suspend fun intercept(
        testCase: TestCase,
        execute: suspend (TestCase) -> TestResult
    ): TestResult {
        val runner = runnerMap[testCase.spec] ?: return execute(testCase)

        return try {
            if (testCase.isRootTest()) {
                runner.containedBefore()
            }
            execute(testCase)
        } catch (t: Throwable) {
            TestResult.Error(kotlin.time.Duration.ZERO, t)
        } finally {
            if (testCase.isRootTest()) {
                runner.containedAfter()
            }
        }
    }
}
