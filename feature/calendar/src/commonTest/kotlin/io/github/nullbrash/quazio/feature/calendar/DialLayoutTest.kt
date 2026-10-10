package io.github.nullbrash.quazio.feature.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DialLayoutTest {

    // Местное время = UTC (для простоты); 5 октября 2026, 00:00.
    private val day = 1_791_158_400_000L
    private val h = 60 * 60_000L
    private val minuteOfDay = { t: Long -> (((t - day) / 60_000L) % 1440 + 1440).toInt() % 1440 }

    private fun ev(id: String, from: Long, to: Long, allDay: Boolean = false) =
        CalendarEvent(id, "c", id, from, to, allDay, null, null, null, false, from)

    @Test
    fun slidingDialLikeTheUsersWidget() {
        // 15:21, событие 20:00–23:00: как на снимке виджета — «4:39» до него, «завтра» сверху.
        val now = day + 15 * h + 21 * 60_000L
        val e = ev("Вечер", day + 20 * h, day + 23 * h)
        val l = DialLayouts.layout(DialMode.SLIDING_12, listOf(e), now, day, day + 24 * h, minuteOfDay)

        val s = l.sectors.single()
        assertEquals(240f, s.startAngle) // 20:00 → 8 часов на обычных часах
        assertEquals(90f, s.sweep) // 3 часа из 12
        assertEquals((3 * 60 + 21) * 0.5f, l.nowAngle) // 15:21 → 3:21
        assertEquals(279L, l.untilNext!!.minutes) // 4:39
        assertEquals(0f, l.tomorrowAngle)
        assertEquals(listOf(16, 17, 18, 19, 20, 21, 22, 23, 0, 1, 2, 3), l.labels.map { it.hour })
        assertEquals(120f, l.labels.first().angle) // 16 → 4 часа
    }

    @Test
    fun eventsAreClippedToTheWindowAndAllDayIsSkipped() {
        val now = day + 10 * h
        val l = DialLayouts.layout(
            DialMode.SLIDING_12,
            listOf(
                ev("Начался раньше", day + 9 * h, day + 11 * h),
                ev("Уходит за окно", day + 21 * h, day + 26 * h),
                ev("Весь день", day, day + 24 * h, allDay = true),
                ev("Прошло", day + 7 * h, day + 8 * h),
            ),
            now, day, day + 24 * h, minuteOfDay,
        )
        assertEquals(listOf("Начался раньше", "Уходит за окно"), l.sectors.map { it.event.title })
        assertEquals(now, l.sectors[0].from)
        assertEquals(30f, l.sectors[0].sweep) // осталась 1 из 2 часов
        assertEquals(now + 12 * h, l.sectors[1].to)
        assertNull(l.tomorrowAngle) // 10:00–22:00 — полночь не попадает
        assertNull(l.untilNext?.takeIf { it.event.title == "Начался раньше" })
    }

    @Test
    fun overlappingEventsGoToDifferentLanes() {
        val l = DialLayouts.layout(
            DialMode.DAY_24,
            listOf(ev("a", day + 9 * h, day + 12 * h), ev("b", day + 10 * h, day + 11 * h), ev("c", day + 12 * h, day + 13 * h)),
            day + 8 * h, day, day + 24 * h, minuteOfDay,
        )
        assertEquals(listOf(0, 1, 0), l.sectors.map { it.lane })
        assertEquals(2, l.lanes)
        // Пересекаются только a и b — у них по две дорожки; c (вплотную после a) — на всю полосу.
        assertEquals(listOf(2, 2, 1), l.sectors.map { it.lanes })
        assertEquals(135f, l.sectors[0].startAngle) // 9:00 на суточном круге
        assertEquals(24, l.labels.size)
    }

    @Test
    fun anotherDayHasNoNowHand() {
        val tomorrow = day + 24 * h
        val l = DialLayouts.layout(DialMode.DAY_24, emptyList(), day + 15 * h, tomorrow, tomorrow + 24 * h, minuteOfDay)
        assertNull(l.nowAngle)
        assertNull(l.untilNext)
    }

    @Test
    fun overloadedDayKeepsSeparateEventsWide() {
        // Дела вплотную по часу, три пересекаются в обед, потом с паузами — как перегруженный день пользователя.
        val l = DialLayouts.layout(
            DialMode.DAY_24,
            listOf(
                ev("7", day + 7 * h, day + 8 * h), ev("8", day + 8 * h, day + 9 * h), ev("9", day + 9 * h, day + 10 * h),
                ev("обед", day + 12 * h, day + 13 * h), ev("вебинар", day + 12 * h + h / 2, day + 14 * h), ev("врач", day + 12 * h + 3 * h / 4, day + 13 * h + h / 2),
                ev("зал", day + 15 * h, day + 16 * h),
            ),
            day + 6 * h, day, day + 24 * h, minuteOfDay,
        )
        val byTitle = l.sectors.associateBy { it.event.title }
        assertEquals(listOf(0, 0, 0), listOf("7", "8", "9").map { byTitle.getValue(it).lane }) // вплотную — одна дорожка
        assertEquals(listOf(1, 1, 1, 1), listOf("7", "8", "9", "зал").map { byTitle.getValue(it).lanes }) // и на всю ширину
        assertEquals(3, byTitle.getValue("обед").lanes)
        assertEquals(setOf(0, 1, 2), listOf("обед", "вебинар", "врач").map { byTitle.getValue(it).lane }.toSet())
    }
}
