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

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kotest BehaviorSpec for [AlarmLock].
 *
 * Pure unit test (no Android dependencies). Tests the atomic lock that
 * prevents concurrent alarm starts from SmsReceiver and SmsCheckWorker.
 */
class AlarmLockSpec : BehaviorSpec({

    beforeEach {
        // Ensure clean state before each test
        AlarmLock.release()
    }

    // ==========================================
    // Basic acquire / release
    // ==========================================

    Given("basic acquire and release") {

        When("tryAcquire is called on an unlocked lock") {
            Then("it returns true") {
                AlarmLock.tryAcquire().shouldBeTrue()
            }
        }

        When("tryAcquire is called twice without release") {
            Then("the second call returns false") {
                AlarmLock.tryAcquire().shouldBeTrue()
                AlarmLock.tryAcquire().shouldBeFalse()
            }
        }

        When("tryAcquire is called after release") {
            Then("it returns true again") {
                AlarmLock.tryAcquire().shouldBeTrue()
                AlarmLock.release()
                AlarmLock.tryAcquire().shouldBeTrue()
            }
        }

        When("release is called on an already-released lock") {
            Then("it is idempotent (no error)") {
                AlarmLock.release()
                AlarmLock.release()
                AlarmLock.tryAcquire().shouldBeTrue()
            }
        }

        When("release is called then tryAcquire") {
            Then("the lock can be re-acquired") {
                AlarmLock.tryAcquire().shouldBeTrue()
                AlarmLock.release()
                AlarmLock.tryAcquire().shouldBeTrue()
                AlarmLock.release()
                AlarmLock.tryAcquire().shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Multiple acquire attempts while held
    // ==========================================

    Given("multiple acquire attempts while lock is held") {

        When("three consecutive tryAcquire calls are made") {
            Then("only the first succeeds") {
                AlarmLock.tryAcquire().shouldBeTrue()
                AlarmLock.tryAcquire().shouldBeFalse()
                AlarmLock.tryAcquire().shouldBeFalse()
            }
        }

        When("tryAcquire fails but release makes it available again") {
            Then("the next tryAcquire succeeds") {
                AlarmLock.tryAcquire().shouldBeTrue()
                AlarmLock.tryAcquire().shouldBeFalse()
                AlarmLock.release()
                AlarmLock.tryAcquire().shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Concurrent access
    // ==========================================

    Given("concurrent access from multiple threads") {

        When("10 threads try to acquire simultaneously") {
            Then("exactly one succeeds") {
                val threadCount = 10
                val startLatch = CountDownLatch(1)
                val doneLatch = CountDownLatch(threadCount)
                val acquireCount = AtomicInteger(0)

                repeat(threadCount) {
                    Thread {
                        startLatch.await() // all threads start at the same time
                        if (AlarmLock.tryAcquire()) {
                            acquireCount.incrementAndGet()
                        }
                        doneLatch.countDown()
                    }.start()
                }

                startLatch.countDown() // release all threads
                doneLatch.await()

                acquireCount.get() shouldBe 1
            }
        }

        When("threads compete to acquire and release in sequence") {
            Then("each successful acquire is followed by a successful release cycle") {
                val iterations = 100
                val successCount = AtomicInteger(0)
                val threadCount = 4
                val doneLatch = CountDownLatch(threadCount)

                repeat(threadCount) {
                    Thread {
                        repeat(iterations) {
                            if (AlarmLock.tryAcquire()) {
                                successCount.incrementAndGet()
                                // Simulate brief work
                                Thread.yield()
                                AlarmLock.release()
                            }
                        }
                        doneLatch.countDown()
                    }.start()
                }

                doneLatch.await()

                // At least one thread should have acquired the lock at least once
                (successCount.get() > 0).shouldBeTrue()
                // After all threads finish, lock should be available
                AlarmLock.tryAcquire().shouldBeTrue()
            }
        }
    }

    // ==========================================
    // try/finally pattern (production usage)
    // ==========================================

    Given("try/finally pattern used in production") {

        When("an exception occurs between acquire and release") {
            Then("release in finally block ensures the lock is freed") {
                AlarmLock.tryAcquire().shouldBeTrue()

                try {
                    throw RuntimeException("simulated failure")
                } catch (_: RuntimeException) {
                    // expected
                } finally {
                    AlarmLock.release()
                }

                // Lock should be available after finally block
                AlarmLock.tryAcquire().shouldBeTrue()
            }
        }
    }
})
