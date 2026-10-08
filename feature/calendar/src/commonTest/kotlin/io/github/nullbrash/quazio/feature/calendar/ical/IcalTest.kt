package io.github.nullbrash.quazio.feature.calendar.ical

import io.github.nullbrash.quazio.feature.calendar.KeyValueStore
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Выгрузка в духе Google (данные выдуманные): перенос строк, экранирование, пояса, серия с исключениями. */
class IcalTest {

    private val ics = """
        BEGIN:VCALENDAR
        PRODID:-//Google Inc//Google Calendar 70.9054//EN
        VERSION:2.0
        X-WR-CALNAME:Работа
        X-WR-TIMEZONE:Europe/Moscow
        BEGIN:VTIMEZONE
        TZID:Europe/Moscow
        BEGIN:STANDARD
        DTSTART:19700101T000000
        TZOFFSETFROM:+0300
        TZOFFSETTO:+0300
        END:STANDARD
        END:VTIMEZONE
        BEGIN:VEVENT
        DTSTART;TZID=Europe/Moscow:20261005T100000
        DTEND;TZID=Europe/Moscow:20261005T103000
        RRULE:FREQ=WEEKLY;BYDAY=MO,WE
        EXDATE;TZID=Europe/Moscow:20261007T100000
        UID:standup@example.com
        SUMMARY:Планёрка\, короткая
        LOCATION:Переговорная "Север"
        DESCRIPTION:Первая строка\nвторая строка с очень длинным текстом\, который G
         oogle переносит на следующую строку
        BEGIN:VALARM
        ACTION:DISPLAY
        DESCRIPTION:Напоминание
        TRIGGER:-P0DT0H10M0S
        END:VALARM
        END:VEVENT
        BEGIN:VEVENT
        DTSTART;TZID=Europe/Moscow:20261012T120000
        DTEND;TZID=Europe/Moscow:20261012T123000
        RECURRENCE-ID;TZID=Europe/Moscow:20261012T100000
        UID:standup@example.com
        SUMMARY:Планёрка (перенесена)
        END:VEVENT
        BEGIN:VEVENT
        RECURRENCE-ID;TZID=Europe/Moscow:20261014T100000
        DTSTART;TZID=Europe/Moscow:20261014T100000
        DTEND;TZID=Europe/Moscow:20261014T103000
        UID:standup@example.com
        STATUS:CANCELLED
        SUMMARY:Планёрка
        END:VEVENT
        BEGIN:VEVENT
        DTSTART;VALUE=DATE:20261009
        DTEND;VALUE=DATE:20261011
        UID:trip@example.com
        SUMMARY:Поездка
        END:VEVENT
        BEGIN:VEVENT
        DTSTART:20261008T150000Z
        DURATION:PT45M
        UID:call@example.com
        SUMMARY:Созвон
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n")

    private val moscow = TimeZone.of("Europe/Moscow")
    private fun ms(s: String, zone: TimeZone = moscow) = LocalDateTime.parse(s).toInstant(zone).toEpochMilliseconds()

    @Test
    fun parsesPropertiesFoldingAndEscapes() {
        val cal = assertNotNull(IcalParser.parse(ics))
        assertEquals("Работа", cal.name)
        assertEquals("Europe/Moscow", cal.timeZone)
        assertEquals(5, cal.events.size)
        val standup = cal.events.first()
        assertEquals("Планёрка, короткая", standup.summary)
        assertEquals("Переговорная \"Север\"", standup.location)
        assertEquals("Первая строка\nвторая строка с очень длинным текстом, который Google переносит на следующую строку", standup.description)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE", standup.rrule)
        assertNull(IcalParser.parse("<html>Войдите в аккаунт</html>"))
    }

    @Test
    fun expandsSeriesWithExdateMovedAndCancelledOccurrences() {
        val ex = IcalExpander(IcalParser.parse(ics)!!, "ical:w", 0xFF039BE5, moscow)
        val events = ex.events(ms("2026-10-05T00:00"), ms("2026-10-19T00:00"))
        val standups = events.filter { it.title.startsWith("Планёрка") }.sortedBy { it.start }
        // пн 5, (ср 7 — EXDATE), пн 12 — перенесён на 12:00, (ср 14 — отменён).
        assertEquals(listOf(ms("2026-10-05T10:00"), ms("2026-10-12T12:00")), standups.map { it.start })
        assertEquals("Планёрка (перенесена)", standups[1].title)
        assertEquals(ms("2026-10-12T10:00"), standups[1].instanceStart) // правка «этого» — по исходному началу
        assertTrue(standups.all { it.recurring && it.eventId == "ical:w/standup@example.com" })

        val trip = events.single { it.title == "Поездка" }
        assertTrue(trip.allDay)
        assertEquals(ms("2026-10-09T00:00", TimeZone.UTC), trip.start) // весь день — полночь UTC, как в Android
        assertEquals(ms("2026-10-11T00:00", TimeZone.UTC), trip.end)

        val call = events.single { it.title == "Созвон" }
        assertEquals(ms("2026-10-08T18:00"), call.start)
        assertEquals(45 * 60_000L, call.end - call.start)
        assertEquals(0xFF039BE5, call.color)
    }

    @Test
    fun draftDescribesWholeSeries() {
        val d = assertNotNull(IcalExpander(IcalParser.parse(ics)!!, "ical:w", null, moscow).draft("standup@example.com"))
        assertEquals(ms("2026-10-05T10:00"), d.start)
        assertEquals(30 * 60_000L, d.end - d.start)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE", d.rrule)
        assertEquals("Europe/Moscow", d.timeZone)
    }

    private class MemoryStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    @Test
    fun linksAreCheckedStoredAndKeptOfflineAfterAnError() {
        val store = MemoryStore()
        var answer: () -> String = { ics }
        val urls = ArrayList<String>()
        val linked = LinkedCalendars(store, { url -> urls += url; answer() }, clock = { 1_000L }, deviceZone = { moscow })

        assertFailsWith<IllegalArgumentException> { linked.add("http://calendar.google.com/x.ics", "", "Календарь") }
        answer = { "<html/>" }
        assertFailsWith<FetchException> { linked.add("https://calendar.google.com/bad.ics", "", "Календарь") }
        assertTrue(linked.links().isEmpty()) // неработающая ссылка не сохраняется

        answer = { ics }
        val link = linked.add("webcal://calendar.google.com/calendar/ical/x/private-secret/basic.ics", "", "Календарь")
        assertEquals("https://calendar.google.com/calendar/ical/x/private-secret/basic.ics", link.url)
        assertEquals("Работа", link.name) // имя — из самого календаря
        val cal = linked.calendars().single()
        assertEquals("ical:${link.id}", cal.id)
        assertTrue(!cal.writable)

        // Сеть пропала — показывается прошлая загрузка, у ссылки — отметка об ошибке.
        answer = { throw FetchException(FetchProblem.OFFLINE) }
        assertTrue(linked.refresh())
        assertEquals(FetchProblem.OFFLINE, linked.status(link.id).problem)
        val reopened = LinkedCalendars(store, { error("без сети") }, clock = { 2_000L }, deviceZone = { moscow })
        assertEquals("Планёрка, короткая", reopened.events(ms("2026-10-05T00:00"), ms("2026-10-06T00:00"), setOf(cal.id)).single().title)
        assertTrue(reopened.events(ms("2026-10-05T00:00"), ms("2026-10-12T00:00"), setOf(cal.id)).any { it.title == "Созвон" })
        assertNotNull(reopened.event("ical:${link.id}/standup@example.com"))

        // Сеть вернулась, выгрузка та же — отметка об ошибке снимается.
        answer = { ics }
        assertTrue(linked.refresh())
        assertNull(linked.status(link.id).problem)
        assertEquals(false, linked.refresh()) // ничего не изменилось

        linked.remove(link.id)
        assertTrue(linked.links().isEmpty())
        assertTrue(store.map.keys.none { it.startsWith("calendar.link.") }) // выгрузка удалена вместе со ссылкой
    }
}
