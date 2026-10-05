package io.github.nullbrash.quazio.core.ui.screens.calendar

import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal val WEEKDAYS_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
internal val MONTHS_NOM = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
internal val MONTHS_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

internal fun LocalDate.startMillis(zone: TimeZone): Long = atStartOfDayIn(zone).toEpochMilliseconds()

internal fun LocalDate.nextDay(): LocalDate = plus(DatePeriod(days = 1))

internal fun millisToLocal(millis: Long, zone: TimeZone): LocalDateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)

internal fun LocalDateTime.millis(zone: TimeZone): Long = toInstant(zone).toEpochMilliseconds()

internal fun hm(dt: LocalDateTime) = "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"

/** «Пн, 5 окт.» — как в окне события Sectograph. */
internal fun shortDate(d: LocalDate) = "${WEEKDAYS_SHORT[d.dayOfWeek.ordinal]}, ${d.day} ${MONTHS_GEN[d.month.ordinal].take(3)}."

internal fun longDate(d: LocalDate) = "${WEEKDAYS_SHORT[d.dayOfWeek.ordinal]}, ${d.day} ${MONTHS_GEN[d.month.ordinal]}"

/** Начало недели, в которую входит [d]: понедельник, или воскресенье по настройке. */
internal fun weekStart(d: LocalDate, sunday: Boolean): LocalDate {
    val first = if (sunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
    val back = (d.dayOfWeek.ordinal - first.ordinal + 7) % 7
    return d.minus(DatePeriod(days = back))
}

/** Местная дата начала и конца (включительно) события; у событий на весь день — даты UTC. */
internal fun CalendarEvent.dates(zone: TimeZone): Pair<LocalDate, LocalDate> = if (allDay) {
    val from = millisToLocal(start, TimeZone.UTC).date
    val to = millisToLocal(maxOf(start, end - 1), TimeZone.UTC).date
    from to to
} else {
    millisToLocal(start, zone).date to millisToLocal(maxOf(start, end - 1), zone).date
}

internal fun CalendarEvent.occursOn(d: LocalDate, zone: TimeZone): Boolean {
    val (a, b) = dates(zone)
    return d in a..b
}

/** «09:00–10:30» для дня [d] (событие через полночь — «с 22:00» / «до 01:00»). */
internal fun CalendarEvent.timeText(d: LocalDate, zone: TimeZone, allDayText: String): String {
    if (allDay) return allDayText
    val s = millisToLocal(start, zone)
    val e = millisToLocal(end, zone)
    val from = if (s.date == d) hm(s) else "…"
    val to = if (e.date == d || (e.date == d.nextDay() && e.hour == 0 && e.minute == 0)) hm(e) else "…"
    return "$from–$to"
}

/** «4:39» — сколько осталось. */
internal fun hoursMinutes(minutes: Long) = "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
