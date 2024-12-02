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

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlin.js.JsName
import kotlin.jvm.JvmInline

/**
 * A writer-preferred non-reentrant read-write mutual exclusion lock.
 *
 * This mutex allows multiple readers or a single writer to acquire the lock at the same time. It is non-reentrant for
 * both readers (See below) and writers. It only supports ownership tracking for [writeMutex].
 *
 * If any coroutine calls writeMutex's [Mutex.lock] while the lock is already held by one or more readers, concurrent
 * calls to readMutex's [Mutex.lock] will block until the writer has acquired (and released) the lock, to ensure
 * that the lock eventually becomes available to the writer. Note that this **prohibits recursive read-locking**.
 *
 * It is the responsibility of the caller to ensure that the mutex is unlocked by the same coroutine that locked it, and
 * that it is not over-unlocked.
 */
public interface ReadWriteMutex {

    /**
     * A mutex to be used by readers.
     *
     * This mutex is non-reentrant and does not support lock with owner.
     */
    public val readMutex: Mutex

    /**
     * A mutex to be used by writers.
     *
     * This mutex is non-reentrant.
     */
    public val writeMutex: Mutex

    /** A snapshot of the state of the read-write mutex. */
    public val state: State

    /** A snapshot of a read-write mutex's state. */
    @JvmInline
    public value class State internal constructor(private val activeAccessors: Int) {

        /** Returns `true` if the mutex is read-locked and no writer is waiting. */
        public val isReadLocked: Boolean get() = activeAccessors > 0

        /**
         * Returns `true` if the mutex is write-locked.
         *
         * A `false` value does not necessarily mean that read locks are available. Use [isReadLockAvailable] to check
         * if read locks are available to be acquired.
         */
        public val isWriteLocked: Boolean get() = activeAccessors == -ReadWriteMutexImpl.MAX_READERS

        /** Returns `true` if the mutex is unlocked. */
        public val isUnlocked: Boolean get() = activeAccessors == 0

        /**
         * Returns `true` if read locks are available to be acquired.
         *
         * This is the opposite of [isWriteLockedOrPending].
         */
        public val isReadLockAvailable: Boolean get() = activeAccessors >= 0

        /**
         * Returns `true` if the mutex is read-locked or a writer is waiting to acquire the lock.
         *
         * This is the opposite of [isReadLockAvailable].
         */
        public val isWriteLockedOrPending: Boolean get() = activeAccessors < 0

        /** Returns `true` if there are pending readers or writers waiting to acquire the lock. */
        public val hasPendingReadersOrWriters: Boolean
            get() = activeAccessors < 0 && activeAccessors != -ReadWriteMutexImpl.MAX_READERS

        /** Returns the number of active readers only if the mutex is read-locked, otherwise `null`. */
        public val activeReaderCount: Int? get() = if (activeAccessors >= 0) activeAccessors else null

        /** Returns the number of pending readers only if the mutex is write-locked, otherwise `null`. */
        public val pendingOrDepartingReaderCount: Int
            get() = if (activeAccessors < 0) ReadWriteMutexImpl.MAX_READERS + activeAccessors else 0
    }
}

/** Creates a new instance of [ReadWriteMutex]. */
@JsName("createReadWriteMutex")
public fun ReadWriteMutex(): ReadWriteMutex = ReadWriteMutexImpl()

/** Shortcut for [ReadWriteMutex.readMutex]'s [Mutex.lock]. */
public suspend fun ReadWriteMutex.readLock() = readMutex.lock()

/** Shortcut for [ReadWriteMutex.readMutex]'s [Mutex.tryLock]. */
public fun ReadWriteMutex.tryReadLock() = readMutex.tryLock()

/** Shortcut for [ReadWriteMutex.readMutex]'s [Mutex.unlock]. */
public fun ReadWriteMutex.readUnlock() = readMutex.unlock()

/** Shortcut for [ReadWriteMutex.readMutex]'s [Mutex.holdsLock]. */
public suspend inline fun <T> ReadWriteMutex.withReadLock(action: () -> T) = readMutex.withLock<T>(action = action)

/** Shortcut for [ReadWriteMutex.writeMutex]'s [Mutex.lock]. */
public suspend fun ReadWriteMutex.writeLock(owner: Any? = null) = writeMutex.lock(owner)

/** Shortcut for [ReadWriteMutex.writeMutex]'s [Mutex.tryLock]. */
public fun ReadWriteMutex.tryWriteLock(owner: Any? = null) = writeMutex.tryLock(owner)

/** Shortcut for [ReadWriteMutex.writeMutex]'s [Mutex.unlock]. */
public fun ReadWriteMutex.writeUnlock(owner: Any? = null) = writeMutex.unlock(owner)

/** Shortcut for [ReadWriteMutex.writeMutex]'s [Mutex.holdsLock]. */
public fun ReadWriteMutex.holdsWriteLock(owner: Any) = writeMutex.holdsLock(owner)

/** Shortcut for [ReadWriteMutex.writeMutex]'s [Mutex.withLock]. */
public suspend inline fun <T> ReadWriteMutex.withWriteLock(owner: Any? = null, action: () -> T) =
    writeMutex.withLock<T>(owner, action)

private class ReadWriteMutexImpl() : ReadWriteMutex {

    /**
     * A simple mutex to resolve competition between writers.
     *
     * It is locked at the beginning of the write lock and unlocked at the end of the write unlock.
     */
    private val writeOnlyMutex = Mutex()

    /**
     * A mutex that acts as a channel to notify the waiting writers that all readers have left.
     *
     * It is locked by default. When a writer wants to lock the mutex while there are readers, it will request a lock on
     * this mutex, making it wait until it is unlocked which happens when all readers have left.
     *
     * @see [WriteMutex.lock]
     * @see [ReadMutex.unlock]
     */
    private val writePermission = Mutex(locked = true)

    /**
     * A semaphore that acts as a channel to notify the waiting readers that a writer has left.
     *
     * All permits are acquired by default. When a reader wants to lock the mutex while there is a writer, it will
     * request a permit on this semaphore, making it wait until it is released. When the writer leaves, a permit is
     * released for each reader waiting.
     *
     * @see [ReadMutex.lock]
     * @see [WriteMutex.unlock]
     */
    private val readPermissions = Semaphore(MAX_READERS, MAX_READERS)

    /**
     * A two-way counter that represents the number of active (or pending) readers and writers.
     *
     * ```
     * activeAccessors =
     *     active/pending readers (0 until MAX_READERS) -
     *     MAX_READERS * active/pending writers (0 or 1)
     * ```
     *
     * According to the above formula, the counter has 3 states:
     * 1. `value > 0`: which means that there are no active or pending writers and the value represents the number of
     *    active readers.
     * 2. `value < 0`: which means that there is an active writer and `MAX_READERS + value` represents the number of
     *    pending readers.
     *    - `value = -MAX_READERS`: means that there is an active writer and no pending readers.
     * 3. `value = 0`: which means that there are no active or pending readers or writers.
     */
    private val activeAccessors = atomic(0)

    /**
     * A counter that represents the number of readers that are leaving the mutex when a writer starts to wait for lock.
     *
     * It is initially equal to activeAccessors, in contrast, it is not affected by new readers waiting for the writer
     * to acquire (and release) the write lock.
     */
    private val departingReaders = atomic(0)

    /** A non-reentrant non-upgradable mutex to be used by readers. */
    override val readMutex: Mutex = ReadMutex()

    /** A non-reentrant mutex to be used by writers. */
    override val writeMutex: Mutex = WriteMutex()

    /** The current lock state of the mutex. */
    override val state: ReadWriteMutex.State get() = ReadWriteMutex.State(activeAccessors.value)

    override fun toString(): String = "ReadWriteMutex[state=$state]"

    private inner class ReadMutex() : AbstractMutex() {

        // See [activeAccessors] for explanation.
        override val isLocked get() = activeAccessors.value > 0

        override suspend fun lock(owner: Any?) {
            // ReadMutex does not support lock with owner.
            if (owner != null) throw UnsupportedOperationException()

            // If there is an active (or pending) writer, the reader should wait until the writer releases the lock.
            // A permit is released for each reader waiting when the writer leaves. See [readPermissions].
            if (activeAccessors.incrementAndGet() < 0) {
                // Request a read permission from the active (or pending) writer. The permission is released when the
                // writer leaves.
                readPermissions.acquire()
            }
        }

        override fun unlock(owner: Any?) {
            // ReadMutex does not support lock with owner.
            if (owner != null) throw UnsupportedOperationException()

            // Remove the reader from the active readers. See [activeAccessors].
            val newActiveAccessors = activeAccessors.decrementAndGet()

            // Check if there was no active reader. If so, throw an exception. See [activeAccessors] for the meaning of
            // the value.
            // WARNING: The exception is fatal and should not be caught.
            val oldActiveAccessors = newActiveAccessors + 1
            check(oldActiveAccessors != 0 && oldActiveAccessors != -MAX_READERS) { "This mutex is not locked" }

            // If there is a pending writer and the reader is the last one, release the write permission for the waiting
            // writer.
            if (newActiveAccessors < 0 && departingReaders.decrementAndGet() == 0) writePermission.unlock()
        }

        override fun tryLock(owner: Any?): Boolean {
            // ReadMutex does not support lock with owner.
            if (owner != null) throw UnsupportedOperationException()

            while (true) {
                // Capture the current value of activeAccessors.
                val capturedValue = activeAccessors.value

                // If there is an active (or pending) writer, the lock cannot be acquired.
                if (capturedValue < 0) return false

                // If the value has not changed, increment the active readers count and report lock acquisition.
                if (activeAccessors.compareAndSet(capturedValue, capturedValue + 1)) return true

                // If the value has been changed by another coroutine, retry the operation.
            }
        }

        override fun holdsLock(owner: Any): Boolean {
            // ReadMutex does not support lock with owner.
            throw UnsupportedOperationException()
        }
    }

    private inner class WriteMutex() : AbstractMutex() {

        // See [activeAccessors] for explanation.
        override val isLocked get() = activeAccessors.value < 0

        override suspend fun lock(owner: Any?) {
            // Resolve competition between writers.
            writeOnlyMutex.lock(owner)

            // Update the activeAccessors counter to indicate that there is an active (or pending) writer. See
            // [activeAccessors] for the meaning of the value.
            val activeReaders = activeAccessors.getAndAdd(-MAX_READERS)

            // If there are active readers, the writer should wait until all readers leave.
            // The check on `departingReaders` is to prevent the writer from waiting if the last reader already left
            // after updating `activeAccessors` and before updating `departingReaders` (`departingReaders` would be
            // negative in this case).
            if (activeReaders != 0 && departingReaders.addAndGet(activeReaders) != 0) {
                // Request a write permission from the active (or pending) readers. The permission is released when the
                // last reader leaves.
                writePermission.lock()
            }
        }

        override fun unlock(owner: Any?) {
            // See [activeAccessors] for explanation.
            val pendingReaders = activeAccessors.addAndGet(MAX_READERS)

            // pendingReaders should be less than MAX_READERS. If not, it means that the [WriteMutex] was over-unlocked.
            check(pendingReaders < MAX_READERS) { "This mutex is not locked" }

            // Release the read permissions for the waiting readers.
            repeat(pendingReaders) { readPermissions.release() }

            // Release the write-only mutex.
            writeOnlyMutex.unlock(owner)
        }

        override fun tryLock(owner: Any?): Boolean {
            // If locking `writeOnlyMutex` fails, it means that the mutex is already locked by another writer.
            if (!writeOnlyMutex.tryLock(owner)) return false

            // If there are no active readers, acquire the write mutex.
            if (activeAccessors.compareAndSet(0, -MAX_READERS)) return true

            // If there are active readers, release the write-only mutex and return false.
            writeOnlyMutex.unlock(owner)
            return false
        }

        // The ownership is tracked by the `writeOnlyMutex`.
        override fun holdsLock(owner: Any): Boolean = writeOnlyMutex.holdsLock(owner)
    }

    companion object {
        // A reasonable limit for the number of readers. It allows for checking for over-unlock without overflow.
        const val MAX_READERS = 1 shl 30
    }
}

private abstract class AbstractMutex() : Mutex {
    @Deprecated(
        "Mutex.onLock deprecated without replacement. For additional details please refer to #2794",
        level = DeprecationLevel.WARNING
    )
    override val onLock get() = throw UnsupportedOperationException()
}
