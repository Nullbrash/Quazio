package io.github.nullbrash.quazio.core.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Uuid7Test {

    @Test
    fun formatVersionAndTimestamp() {
        val millis = 1_790_000_000_123L
        val id = Uuid7.generate(millis, Random(1))
        assertTrue(Uuid7.isValid(id), id)
        assertEquals(36, id.length)
        assertEquals('7', id[14])
        assertEquals(millis, Uuid7.timestampOf(id))
    }

    @Test
    fun laterIdsSortAfterEarlierOnes() {
        val random = Random(42)
        val ids = (0 until 50).map { Uuid7.generate(1_790_000_000_000L + it, random) }
        assertEquals(ids.sorted(), ids)
    }

    @Test
    fun idsInSameMillisecondAreUnique() {
        val ids = (0 until 10_000).map { Uuid7.generate(1_790_000_000_000L) }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun rejectsTimeOutside48Bits() {
        assertFailsWith<IllegalArgumentException> { Uuid7.generate(-1) }
        assertFailsWith<IllegalArgumentException> { Uuid7.generate(1L shl 48) }
    }
}
