package io.github.nullbrash.quazio.engine.recurrence

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecurrenceTest {

    private fun d(s: String) = LocalDate.parse(s)

    /** Правило, начало, сколько взять → ожидаемые даты. Примеры — из RFC 5545 и правил Google. */
    private val table = listOf(
        Triple("FREQ=DAILY", "2026-01-30", "2026-01-30 2026-01-31 2026-02-01"),
        Triple("FREQ=DAILY;INTERVAL=10;COUNT=3", "2026-03-01", "2026-03-01 2026-03-11 2026-03-21"),
        Triple("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", "2026-10-09", "2026-10-09 2026-10-12 2026-10-13 2026-10-14"),
        // Каждые две недели, вт и чт; начало — четверг: вторник той же недели раньше начала — пропуск.
        Triple("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;WKST=MO", "2026-10-08", "2026-10-08 2026-10-20 2026-10-22 2026-11-03"),
        Triple("FREQ=WEEKLY", "2026-10-08", "2026-10-08 2026-10-15 2026-10-22"),
        // 31-е — только в месяцах, где оно есть.
        Triple("FREQ=MONTHLY", "2026-01-31", "2026-01-31 2026-03-31 2026-05-31 2026-07-31"),
        Triple("FREQ=MONTHLY;BYMONTHDAY=-1", "2026-01-31", "2026-01-31 2026-02-28 2026-03-31 2026-04-30"),
        Triple("FREQ=MONTHLY;BYDAY=-1FR", "2026-10-30", "2026-10-30 2026-11-27 2026-12-25 2027-01-29"),
        Triple("FREQ=MONTHLY;BYDAY=2MO", "2026-10-12", "2026-10-12 2026-11-09 2026-12-14"),
        // Последний рабочий день месяца.
        Triple("FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1", "2026-10-30", "2026-10-30 2026-11-30 2026-12-31 2027-01-29"),
        Triple("FREQ=MONTHLY;INTERVAL=3;BYMONTHDAY=15", "2026-01-15", "2026-01-15 2026-04-15 2026-07-15"),
        // 29 февраля — только в високосные годы.
        Triple("FREQ=YEARLY", "2024-02-29", "2024-02-29 2028-02-29 2032-02-29"),
        Triple("FREQ=YEARLY;BYMONTH=11;BYDAY=4TH", "2026-11-26", "2026-11-26 2027-11-25 2028-11-23"),
        Triple("FREQ=YEARLY;BYMONTH=1,7;BYMONTHDAY=10", "2026-01-10", "2026-01-10 2026-07-10 2027-01-10"),
        Triple("FREQ=DAILY;UNTIL=20261010", "2026-10-08", "2026-10-08 2026-10-09 2026-10-10"),
    )

    @Test
    fun table() {
        for ((text, start, expected) in table) {
            val rule = assertNotNull(RRule.parse(text), text)
            val want = expected.split(' ').map(::d)
            assertEquals(want, Recurrence.dates(rule, d(start)).take(want.size + 1).toList().take(want.size), text)
        }
    }

    @Test
    fun countAndUntilStopTheSeries() {
        assertEquals(3, Recurrence.starts(RRule.parse("FREQ=DAILY;INTERVAL=10;COUNT=3")!!, LocalDateTime.parse("2026-03-01T09:00")).toList().size)
        assertEquals(3, Recurrence.dates(RRule.parse("FREQ=DAILY;UNTIL=20261010")!!, d("2026-10-08")).toList().size)
        // Никогда не срабатывающее правило не зависает.
        assertEquals(listOf(d("2026-01-30")), Recurrence.dates(RRule.parse("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30")!!, d("2026-01-30")).toList())
    }

    @Test
    fun untilInUtcIsComparedAsMoment() {
        // 21:00 в Москве = 18:00 UTC: повторение 10-го ещё до UNTIL 18:00Z, 11-го — уже после.
        val moscow = TimeZone.of("Europe/Moscow")
        val rule = RRule.parse("FREQ=DAILY;UNTIL=20261010T180000Z")!!
        val starts = Recurrence.starts(rule, LocalDateTime.parse("2026-10-08T21:00"), moscow).toList()
        assertEquals(listOf("2026-10-08T21:00", "2026-10-09T21:00", "2026-10-10T21:00").map(LocalDateTime::parse), starts)
        // Время серии остаётся местным и после перевода часов.
        val berlin = TimeZone.of("Europe/Berlin")
        val around = Recurrence.starts(RRule.parse("FREQ=DAILY;COUNT=2")!!, LocalDateTime.parse("2026-10-24T09:00"), berlin)
            .map { (it.toInstant(berlin).toEpochMilliseconds() / 3_600_000) % 24 }.toList()
        assertEquals(listOf(7L, 8L), around) // 09:00 летнего и зимнего времени — 07:00 и 08:00 UTC
    }

    @Test
    fun unsupportedOrBrokenRulesAreRejected() {
        assertNull(RRule.parse("FREQ=HOURLY"))
        assertNull(RRule.parse("FREQ=YEARLY;BYWEEKNO=20"))
        assertNull(RRule.parse("INTERVAL=2"))
        assertNull(RRule.parse("FREQ=DAILY;COUNT=2;UNTIL=20261010"))
        assertNull(RRule.parse("FREQ=MONTHLY;BYMONTHDAY=32"))
        assertEquals(WeekdayNum(kotlinx.datetime.DayOfWeek.FRIDAY, -1), RRule.parse("RRULE:FREQ=MONTHLY;BYDAY=-1FR")!!.byDay.single())
    }
}
