package io.github.nullbrash.quazio.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HlcTest {

    private val nodeA = "0199a8f2-1c00-7000-8000-00000000000a"
    private val nodeB = "0199a8f2-1c00-7000-8000-00000000000b"

    @Test
    fun stringRoundTripsAndSortsLikeHlc() {
        val a = Hlc(1_790_000_000_000L, 3, nodeA)
        assertEquals(a, Hlc.parse(a.toString()))
        val samples = listOf(
            Hlc(1_790_000_000_000L, 0, nodeB),
            Hlc(1_790_000_000_000L, 1, nodeA),
            Hlc(1_790_000_000_001L, 0, nodeA),
            Hlc(1_790_000_000_000L, 0, nodeA),
        )
        assertEquals(samples.sorted().map { it.toString() }, samples.map { it.toString() }.sorted())
    }

    @Test
    fun localEventsAreStrictlyIncreasingEvenWhenWallClockStands() {
        var wall = 1_000L
        val clock = HlcClock(nodeA) { wall }
        val first = clock.now()
        val second = clock.now()
        wall = 900 // часы ушли назад
        val third = clock.now()
        assertTrue(first < second && second < third)
        assertEquals(1_000L, third.millis)
    }

    @Test
    fun receivedRemoteTimeMovesClockForward() {
        val clock = HlcClock(nodeA) { 1_000L }
        val remote = Hlc(5_000L, 7, nodeB)
        val afterReceive = clock.receive(remote)
        assertTrue(afterReceive > remote)
        assertTrue(clock.now() > afterReceive)
    }

    @Test
    fun counterOverflowAdvancesMillis() {
        val clock = HlcClock(nodeA) { 1_000L }
        var last = clock.now()
        repeat(Hlc.MAX_COUNTER + 5) {
            val next = clock.now()
            assertTrue(next > last)
            last = next
        }
        assertEquals(1_001L, last.millis)
    }

    @Test
    fun rejectsMalformedText() {
        for (bad in listOf("", "123", "000000000001000-00001", "00000000000100x-00001-n")) {
            assertFailsWith<IllegalArgumentException>(bad) { Hlc.parse(bad) }
        }
    }
}
