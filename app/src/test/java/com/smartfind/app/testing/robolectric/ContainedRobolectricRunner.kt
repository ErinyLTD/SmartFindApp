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

import org.junit.Test
import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.internal.AndroidSandbox
import org.robolectric.internal.bytecode.InstrumentationConfiguration

/**
 * A self-contained Robolectric runner that can be used outside of JUnit 4.
 *
 * It bootstraps a Robolectric sandbox against a tiny [PlaceholderTest] class,
 * then exposes [containedBefore] / [containedAfter] so that a Kotest extension
 * can set up and tear down the Android environment around each root test.
 *
 * This is a port of the `ContainedRobolectricRunner` from
 * `kotest-extensions-android`, adapted for Robolectric 4.11.x and
 * Kotest 5.9.x on Kotlin 1.9.22.
 */
class ContainedRobolectricRunner(
    config: Config = Config.Builder().build()
) : RobolectricTestRunner(
    PlaceholderTest::class.java,
    kotestInjector(config)
) {
    private val placeHolderMethod: FrameworkMethod = children[0] as FrameworkMethod
    val sdkEnvironment: AndroidSandbox = getSandbox(placeHolderMethod).also { sandbox ->
        configureSandbox(sandbox, placeHolderMethod)
    }
    private val bootStrapMethod: java.lang.reflect.Method = sdkEnvironment
        .bootstrappedClass<Any>(testClass.javaClass)
        .getMethod("bootStrapMethod")

    /**
     * Called before each root test to bootstrap the Robolectric environment.
     */
    fun containedBefore() {
        Thread.currentThread().contextClassLoader =
            sdkEnvironment.robolectricClassLoader
        beforeTest(sdkEnvironment, placeHolderMethod, bootStrapMethod)
    }

    /**
     * Called after each root test to tear down the Robolectric environment.
     */
    fun containedAfter() {
        afterTest(placeHolderMethod, bootStrapMethod)
        finallyAfterTest(placeHolderMethod)
        Thread.currentThread().contextClassLoader =
            ContainedRobolectricRunner::class.java.classLoader
    }

    /**
     * Exclude `io.kotest` and `kotlinx.coroutines` from Robolectric's
     * bytecode instrumentation so the test framework itself is not affected.
     */
    override fun createClassLoaderConfig(method: FrameworkMethod): InstrumentationConfiguration {
        return InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
            .doNotAcquirePackage("io.kotest")
            .doNotAcquirePackage("kotlinx.coroutines")
            .build()
    }

    companion object {
        /**
         * Build a custom injector that feeds the given [Config] into the
         * Robolectric runner's configuration strategy, merging it with the
         * defaults provided by robolectric.properties.
         */
        private fun kotestInjector(config: Config) =
            defaultInjector()
                .bind(
                    Config::class.java,
                    Config.Builder(config).build()
                )
                .build()
    }

    // Minimal test class required by RobolectricTestRunner's constructor.
    class PlaceholderTest {
        @Test fun testPlaceholder() {}
        fun bootStrapMethod() {}
    }
}
