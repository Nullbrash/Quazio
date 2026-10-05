package io.github.nullbrash.quazio.feature.calendar

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepeatRuleTest {

    @Test
    fun simpleRepeatsRoundTrip() {
        for (kind in listOf(RepeatKind.DAILY, RepeatKind.WEEKDAYS, RepeatKind.WEEKLY, RepeatKind.MONTHLY, RepeatKind.YEARLY)) {
            val r = Repeat(kind)
            assertEquals(r, Repeat.parse(r.toRrule()))
            val until = Repeat(kind, until = LocalDate(2026, 12, 31))
            assertEquals(until, Repeat.parse(until.toRrule()))
        }
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;UNTIL=20261231T235959Z", Repeat(RepeatKind.WEEKDAYS, LocalDate(2026, 12, 31)).toRrule())
        assertNull(Repeat.NONE.toRrule())
        assertEquals(Repeat.NONE, Repeat.parse(null))
    }

    @Test
    fun googleVariantsAreUnderstoodOrKept() {
        // Google пишет «лишние» части — смысл тот же.
        assertEquals(RepeatKind.WEEKLY, Repeat.parse("FREQ=WEEKLY;WKST=MO").kind)
        assertEquals(RepeatKind.DAILY, Repeat.parse("RRULE:FREQ=DAILY;INTERVAL=1").kind)
        assertEquals(LocalDate(2026, 11, 3), Repeat.parse("FREQ=DAILY;UNTIL=20261103").until)
        // Сложное правило не упрощается — сохраняется как есть.
        val custom = Repeat.parse("FREQ=MONTHLY;BYDAY=-1FR")
        assertEquals(RepeatKind.CUSTOM, custom.kind)
        assertEquals("FREQ=MONTHLY;BYDAY=-1FR", custom.toRrule())
        assertEquals(RepeatKind.CUSTOM, Repeat.parse("FREQ=DAILY;INTERVAL=2").kind)
    }

    @Test
    fun durations() {
        assertEquals(3_600_000L, parseDuration("P3600S"))
        assertEquals(86_400_000L, parseDuration("P1D"))
        assertEquals(5_400_000L, parseDuration("PT1H30M"))
        assertEquals(7 * 86_400_000L, parseDuration("P1W"))
        assertNull(parseDuration("ерунда"))
        assertNull(parseDuration(null))
    }
}
