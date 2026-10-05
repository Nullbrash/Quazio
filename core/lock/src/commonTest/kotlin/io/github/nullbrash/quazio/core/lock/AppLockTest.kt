package io.github.nullbrash.quazio.core.lock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockTest {

    private var now = 0L
    private val lock = AppLock({ now })

    @Test
    fun startsLockedAndUnlocks() {
        assertTrue(lock.isLocked)
        lock.unlock()
        assertFalse(lock.isLocked)
    }

    @Test
    fun shortBackgroundKeepsItOpen() {
        lock.unlock()
        lock.onBackground()
        now += LockTimeout.MIN_5.millis - 1
        lock.onForeground()
        assertFalse(lock.isLocked)
    }

    @Test
    fun longBackgroundLocks() {
        lock.unlock()
        lock.onBackground()
        now += LockTimeout.MIN_5.millis
        lock.onForeground()
        assertTrue(lock.isLocked)
    }

    @Test
    fun immediateLocksOnEveryReturn() {
        lock.setTimeout(LockTimeout.IMMEDIATE)
        lock.unlock()
        lock.onBackground()
        lock.onForeground()
        assertTrue(lock.isLocked)
    }

    @Test
    fun backgroundTimeCountsFromFirstLeave() {
        lock.unlock()
        lock.onBackground()
        now += 4 * 60_000
        lock.onBackground() // повторное событие не сбрасывает отсчёт
        now += 2 * 60_000
        lock.onForeground()
        assertTrue(lock.isLocked)
    }

    @Test
    fun systemLockClosesImmediately() {
        lock.unlock()
        lock.lockNow()
        assertTrue(lock.isLocked)
        assertTrue(lock.state.value.locked)
    }

    @Test
    fun timeoutIdsRoundTrip() {
        LockTimeout.entries.forEach { assertEquals(it, LockTimeout.fromId(it.id)) }
        assertEquals(LockTimeout.DEFAULT, LockTimeout.fromId("мусор"))
        assertEquals(LockTimeout.MIN_5, LockTimeout.DEFAULT)
    }
}
