package io.github.nullbrash.quazio.feature.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DialColorsTest {

    private val h = 3_600_000L
    private val day = 1_790_000_000_000L / (24 * h) * (24 * h)
    private val blue = 0xFF039BE5

    private fun ev(t: String, from: Long, to: Long, color: Long? = blue) =
        CalendarEvent(t, "1", t, from, to, false, "UTC", color, null, false, from)

    private fun layout(vararg events: CalendarEvent) =
        DialLayouts.layout(DialMode.DAY_24, events.toList(), day, day, day + 24 * h, { t -> (((t - day) / 60_000) % 1440).toInt() })

    @Test
    fun backToBackSameColourGetsAnotherShade() {
        val l = layout(ev("a", day + 9 * h, day + 10 * h), ev("b", day + 10 * h, day + 11 * h), ev("c", day + 11 * h, day + 12 * h))
        val c = DialColors.of(l.sectors, distinguish = true)
        assertEquals(blue, c[0]) // первое — своим цветом
        assertNotEquals(c[0], c[1]) // вплотную — другой оттенок
        assertNotEquals(c[1], c[2])
        assertEquals(listOf(blue, blue, blue), DialColors.of(l.sectors, distinguish = false)) // настройка выключена
    }

    @Test
    fun separatedOrDifferentColoursStayAsTheyAre() {
        val red = 0xFFD50000
        val l = layout(ev("a", day + 9 * h, day + 10 * h), ev("b", day + 11 * h, day + 12 * h), ev("c", day + 12 * h, day + 13 * h, red))
        assertEquals(listOf(blue, blue, red), DialColors.of(l.sectors, distinguish = true)) // пауза в час; соседи разного цвета
    }

    @Test
    fun baseColourForEventsWithoutColourOrForAll() {
        val purple = 0xFF8E24AA
        val l = layout(ev("a", day + 9 * h, day + 10 * h), ev("b", day + 11 * h, day + 12 * h, color = null))
        assertEquals(listOf(blue, purple), DialColors.of(l.sectors, distinguish = true, fallback = purple))
        assertEquals(listOf(purple, purple), DialColors.of(l.sectors, distinguish = true, fallback = purple, allFallback = true))
        // Вплотную и все базовым — второй всё равно другим оттенком.
        val touching = layout(ev("a", day + 9 * h, day + 10 * h), ev("b", day + 10 * h, day + 11 * h))
        val c = DialColors.of(touching.sectors, distinguish = true, fallback = purple, allFallback = true)
        assertEquals(purple, c[0]); kotlin.test.assertNotEquals(purple, c[1])
    }

    @Test
    fun overlappingSameColourAllDiffer() {
        val l = layout(ev("a", day + 12 * h, day + 13 * h), ev("b", day + 12 * h + h / 2, day + 14 * h), ev("c", day + 12 * h + 3 * h / 4, day + 13 * h + h / 2))
        assertEquals(3, DialColors.of(l.sectors, distinguish = true).toSet().size)
    }
}
