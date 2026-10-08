package io.github.nullbrash.quazio.feature.calendar.ical

import io.github.nullbrash.quazio.engine.recurrence.RRule
import io.github.nullbrash.quazio.engine.recurrence.Recurrence
import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import io.github.nullbrash.quazio.feature.calendar.EventDraft
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * События выгрузки iCalendar в виде Quazio: повторения на отрезок времени (считает
 * `:engine:recurrence`), изменённые и отменённые повторения серии, события на весь день —
 * полночь UTC, как в календарях Android.
 */
internal class IcalExpander(
    private val calendar: IcalCalendar,
    private val calendarId: String,
    private val color: Long?,
    /** Пояс устройства: для «плавающего» времени и нераспознанных TZID. */
    private val deviceZone: TimeZone,
) {
    private val keyed = calendar.events.mapIndexed { i, e -> (e.uid.ifBlank { "#$i" }) to e }
    private val masters = keyed.filter { it.second.recurrenceId == null }.groupBy({ it.first }, { it.second }).mapValues { it.value.first() }
    private val overrides = keyed.filter { it.second.recurrenceId != null }.groupBy({ it.first }, { it.second })

    fun eventId(uid: String) = "$calendarId/$uid"

    fun events(from: Long, to: Long): List<CalendarEvent> {
        val out = ArrayList<CalendarEvent>()
        for ((uid, master) in masters) {
            if (master.cancelled) continue
            val changed = overrides[uid].orEmpty()
            expandMaster(uid, master, changed.mapNotNullTo(HashSet()) { it.recurrenceId?.let(::millis) }, from, to, out)
        }
        // Изменённые повторения — со своим временем; отменённые просто не показываются.
        for ((uid, list) in overrides) {
            for (e in list) {
                if (e.cancelled) continue
                val (s, end) = span(e)
                if (s < to && maxOf(end, s + 1) > from) out += event(uid, e, s, end, recurring = true, instanceStart = millis(e.recurrenceId!!))
            }
        }
        return out
    }

    fun draft(uid: String): EventDraft? {
        val e = masters[uid] ?: overrides[uid]?.firstOrNull() ?: return null
        val (s, end) = span(e)
        return EventDraft(
            calendarId = calendarId, title = e.summary, start = s, end = end, allDay = e.start is IcalTime.Date,
            timeZone = zoneId(e.start), location = e.location.orEmpty(), description = e.description.orEmpty(),
            rrule = e.rrule?.removePrefix("RRULE:"),
        )
    }

    private fun expandMaster(uid: String, e: IcalEvent, changed: Set<Long>, from: Long, to: Long, out: MutableList<CalendarEvent>) {
        val (s, end) = span(e)
        val duration = end - s
        val rule = e.rrule?.let(RRule::parse)
        if (rule == null) {
            // Неподдерживаемое правило — только первое повторение, без выдуманных.
            if (s < to && maxOf(end, s + 1) > from && s !in changed) out += event(uid, e, s, end, recurring = e.rrule != null, instanceStart = s)
            return
        }
        val excluded = e.exdates.mapTo(HashSet(changed)) { millis(it) }
        val starts: Sequence<Long> = when (val st = e.start) {
            is IcalTime.Date -> Recurrence.dates(rule, st.date).map { LocalDateTime(it, LocalTime(0, 0)).toInstant(TimeZone.UTC).toEpochMilliseconds() }
            is IcalTime.DateTime -> {
                val zone = zoneOf(st)
                Recurrence.starts(rule, st.local, zone).map { it.toInstant(zone).toEpochMilliseconds() }
            }
        }
        for (start in starts.take(MAX_OCCURRENCES)) {
            if (start >= to) break
            if (start + maxOf(duration, 1) > from && start !in excluded) out += event(uid, e, start, start + duration, recurring = true, instanceStart = start)
        }
    }

    private fun event(uid: String, e: IcalEvent, start: Long, end: Long, recurring: Boolean, instanceStart: Long) = CalendarEvent(
        eventId = eventId(uid), calendarId = calendarId, title = e.summary, start = start, end = end,
        allDay = e.start is IcalTime.Date, timeZone = zoneId(e.start), color = color, location = e.location,
        recurring = recurring, instanceStart = instanceStart,
    )

    /** Начало и конец, мс: DTEND, иначе DURATION, иначе день (весь день) или ноль. */
    private fun span(e: IcalEvent): Pair<Long, Long> {
        val s = millis(e.start)
        val end = e.end?.let(::millis) ?: e.duration?.let { s + it } ?: if (e.start is IcalTime.Date) s + DAY_MS else s
        return s to maxOf(end, s)
    }

    private fun millis(t: IcalTime): Long = when (t) {
        is IcalTime.Date -> LocalDateTime(t.date, LocalTime(0, 0)).toInstant(TimeZone.UTC).toEpochMilliseconds()
        is IcalTime.DateTime -> t.local.toInstant(zoneOf(t)).toEpochMilliseconds()
    }

    private fun zoneId(t: IcalTime): String = when (t) {
        is IcalTime.Date -> "UTC"
        is IcalTime.DateTime -> zoneOf(t).id
    }

    private fun zoneOf(t: IcalTime.DateTime): TimeZone = when {
        t.utc -> TimeZone.UTC
        t.tzid == null -> deviceZone // «плавающее» время — время того, кто смотрит (RFC 5545)
        else -> resolveZone(t.tzid) ?: calendar.timeZone?.let(::resolveZone) ?: deviceZone
    }

    private companion object {
        const val DAY_MS = 86_400_000L

        /** Предохранитель: ежедневная серия за 100 лет — 36 500; больше — ошибка в данных. */
        const val MAX_OCCURRENCES = 50_000
    }

    // Пояса распознаются один раз заранее: дальше только чтение — события читают из разных потоков.
    private val zones: Map<String, TimeZone?> = calendar.events
        .flatMap { e -> listOfNotNull(e.start, e.end, e.recurrenceId) + e.exdates }
        .mapNotNullTo(HashSet()) { (it as? IcalTime.DateTime)?.tzid }
        .plus(listOfNotNull(calendar.timeZone))
        .associateWith(::lookupZone)

    private fun resolveZone(tzid: String): TimeZone? = zones[tzid]

    /** Имя IANA; у некоторых программ — с префиксом («/mozilla.org/…/Europe/Berlin»). */
    private fun lookupZone(tzid: String): TimeZone? {
        val parts = tzid.trim().trim('"').split('/').filter { it.isNotBlank() }
        return listOf(tzid.trim().trim('"'), parts.takeLast(2).joinToString("/"), parts.takeLast(3).joinToString("/"))
            .firstNotNullOfOrNull { runCatching { TimeZone.of(it) }.getOrNull() }
    }
}
