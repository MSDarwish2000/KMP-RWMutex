/*
 * Copyright 2024 MayakaApps
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mayakapps.rwmutex

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadWriteMutexTest {

    @Test
    fun parallelReaders() {
        testParallelReaders(1, 4)
        testParallelReaders(3, 4)
        testParallelReaders(4, 2)
    }

    private fun testParallelReaders(readerCount: Int, threadCount: Int) = runTest {
        val dispatcher = createMultithreadedDispatcher(threadCount)
        val rwMutex = ReadWriteMutex()

        // Create semaphores to synchronize readers that have no available permits by default
        val readLocked = Semaphore(readerCount, readerCount)
        val unlockRead = Semaphore(readerCount, readerCount)

        // Start readers in parallel
        val readJobs = List(readerCount) {
            launch(dispatcher) { readInParallel(rwMutex.readMutex, readLocked, unlockRead) }
        }

        // Wait for all readers to acquire the read lock
        repeat(readerCount) { readLocked.acquire() }

        // Release the readers
        repeat(readerCount) { unlockRead.release() }

        // Wait for all readers to finish
        readJobs.joinAll()
    }

    private suspend fun readInParallel(
        readMutex: Mutex,
        readLocked: Semaphore,
        unlockRead: Semaphore,
    ) = readMutex.withLock {
        // Inform caller that we have acquired the read lock
        readLocked.release()

        // Wait for the unlock signal
        unlockRead.acquire()
    }

    @Test
    fun tryLock() = runTest {
        ReadWriteMutex().run {
            writeMutex.lock()
            assertFalse(writeMutex.tryLock())
            assertFalse(readMutex.tryLock())
            writeMutex.unlock()

            assertTrue(writeMutex.tryLock())
            writeMutex.unlock()

            assertTrue(readMutex.tryLock())
            assertTrue(readMutex.tryLock())
            assertFalse(writeMutex.tryLock())
            readMutex.unlock()
            readMutex.unlock()
        }
    }

    @Test
    fun readWriteMutualExclusion() = runTest {
        val rwMutex = ReadWriteMutex()
        val writeLocked = Mutex(locked = true)
        val readLocked = Mutex(locked = true)
        val iterations = 100

        launch {
            repeat(iterations) {
                rwMutex.readMutex.lock()
                rwMutex.readMutex.lock()
                readLocked.unlock() // Signal that we have acquired the read lock
                rwMutex.writeMutex.lock()
                writeLocked.unlock() // Signal that we have acquired the write lock
            }
        }

        repeat(iterations) {
            readLocked.lock() // Wait for the read lock to be acquired
            rwMutex.readMutex.unlock() // Release one of the two read locks
            check(!writeLocked.tryLock()) { "writeMutex didn't respect the read lock" }
            rwMutex.readMutex.unlock() // Release the second read lock
            writeLocked.lock() // Wait for the write lock to be acquired
            check(!readLocked.tryLock()) { "readMutex didn't respect the write lock" }
            rwMutex.writeMutex.unlock() // Release the write lock
        }
    }

    @Test
    fun stressReadWriteMutex() {
        stressReadWriteMutex(1, 1)
        stressReadWriteMutex(1, 3)
        stressReadWriteMutex(1, 10)
        stressReadWriteMutex(4, 1)
        stressReadWriteMutex(4, 3)
        stressReadWriteMutex(4, 10)
        stressReadWriteMutex(10, 1)
        stressReadWriteMutex(10, 3)
        stressReadWriteMutex(10, 10)
        stressReadWriteMutex(10, 5)
    }

    private fun stressReadWriteMutex(readerCount: Int, threadCount: Int) = runTest {
        val dispatcher = createMultithreadedDispatcher(threadCount)
        val rwMutex = ReadWriteMutex()

        val jobs = mutableListOf<Job>()
        val activity = atomic(0) // number of active readers + 10000 * number of active writers

        withContext(dispatcher) {
            // Start a single writer with half the readers
            jobs += launch { stressWrite(rwMutex.writeMutex, activity) }
            jobs += List(readerCount / 2) {
                launch { stressRead(rwMutex.readMutex, activity) }
            }

            // Start a new writer with the remaining readers (the +1 is to account for odd reader counts)
            jobs += launch { stressWrite(rwMutex.writeMutex, activity) }
            jobs += List((readerCount + 1) / 2) {
                launch { stressRead(rwMutex.readMutex, activity) }
            }
        }

        // Wait for the 2 writers and all readers to finish
        jobs.joinAll()
    }

    private suspend fun stressRead(readMutex: Mutex, activity: AtomicInt) {
        repeat(STRESS_ITERATIONS) {
            readMutex.lock()
            val newActivity = activity.incrementAndGet()
            if (newActivity !in 1 until 10000) {
                readMutex.unlock()
                throw IllegalStateException(
                    "Invalid activity value: $newActivity, maybe a read mutex locked while a writer is active",
                )
            }
            repeat(100) {} // Simulate some work
            activity.decrementAndGet()
            readMutex.unlock()
        }
    }

    private suspend fun stressWrite(writeMutex: Mutex, activity: AtomicInt) {
        repeat(STRESS_ITERATIONS) {
            writeMutex.lock()
            val newActivity = activity.addAndGet(10000)
            if (newActivity != 10000) {
                writeMutex.unlock()
                throw IllegalStateException(
                    "Invalid activity value: $newActivity, maybe a writer locked while another reader or writer is active",
                )
            }
            repeat(100) {} // Simulate some work
            activity.addAndGet(-10000)
            writeMutex.unlock()
        }
    }

    companion object {
        const val STRESS_ITERATIONS = 10000
    }
}
