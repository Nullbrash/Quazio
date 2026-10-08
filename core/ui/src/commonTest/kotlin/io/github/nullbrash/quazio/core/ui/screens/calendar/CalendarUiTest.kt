package io.github.nullbrash.quazio.core.ui.screens.calendar

import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarUiTest {
    private val zone = TimeZone.of("Europe/Moscow")
    private val day = LocalDate(2026, 10, 7)

    private fun event(from: String, to: String) = CalendarEvent(
        "1", "c", "Отметка", LocalDateTime.parse(from).millis(zone), LocalDateTime.parse(to).millis(zone),
        false, zone.id, null, null, false, LocalDateTime.parse(from).millis(zone),
    )

    @Test
    fun timeText() {
        assertEquals("15:00", event("2026-10-07T15:00", "2026-10-07T15:00").timeText(day, zone, "Весь день"))
        assertEquals("15:00–16:30", event("2026-10-07T15:00", "2026-10-07T16:30").timeText(day, zone, "Весь день"))
        assertEquals("23:00–…", event("2026-10-07T23:00", "2026-10-08T01:00").timeText(day, zone, "Весь день"))
    }
}
