package io.github.nullbrash.quazio.feature.calendar.ical

import io.github.nullbrash.quazio.feature.calendar.parseDuration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Время из iCalendar: дата (событие на весь день) или дата-время — UTC, с поясом TZID или «плавающее». */
sealed interface IcalTime {
    data class Date(val date: LocalDate) : IcalTime
    data class DateTime(val local: LocalDateTime, val tzid: String?, val utc: Boolean) : IcalTime
}

data class IcalEvent(
    val uid: String,
    val summary: String,
    val location: String?,
    val description: String?,
    val start: IcalTime,
    val end: IcalTime?,
    /** DURATION в миллисекундах — когда нет DTEND. */
    val duration: Long?,
    val rrule: String?,
    val exdates: List<IcalTime>,
    /** Есть — это изменённое повторение серии с тем же UID. */
    val recurrenceId: IcalTime?,
    val cancelled: Boolean,
)

data class IcalCalendar(
    /** X-WR-CALNAME — так Google называет календарь в выгрузке. */
    val name: String?,
    /** X-WR-TIMEZONE — пояс календаря; запасной, если TZID события не распознан. */
    val timeZone: String?,
    val events: List<IcalEvent>,
)

/**
 * Свой разбор iCalendar (RFC 5545) — только то, что нужно для показа событий: VEVENT и
 * свойства календаря. Свой, а не ical4j: та тянет много зависимостей ради малого подмножества.
 * VTIMEZONE не разбирается: Google пишет в TZID имена IANA («Europe/Moscow»).
 */
object IcalParser {

    /** null — это не календарь (например, страница входа вместо выгрузки). */
    fun parse(text: String): IcalCalendar? {
        val lines = unfold(text)
        if (lines.none { it.equals("BEGIN:VCALENDAR", ignoreCase = true) }) return null
        var name: String? = null
        var zone: String? = null
        val events = ArrayList<IcalEvent>()
        val stack = ArrayList<String>()
        var props: MutableList<Prop>? = null
        for (line in lines) {
            val p = parseLine(line) ?: continue
            when (p.name) {
                "BEGIN" -> {
                    stack += p.value.uppercase()
                    if (stack.last() == "VEVENT" && stack.size == 2) props = ArrayList()
                }
                "END" -> {
                    if (stack.lastOrNull() == "VEVENT" && stack.size == 2) props?.let { toEvent(it) }?.let(events::add)
                    if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                    if (stack.lastOrNull() != "VEVENT") props = null
                }
                else -> when {
                    props != null && stack.size == 2 -> props.add(p)
                    stack.size == 1 && p.name == "X-WR-CALNAME" -> name = unescape(p.value).ifBlank { null }
                    stack.size == 1 && p.name == "X-WR-TIMEZONE" -> zone = p.value.trim().ifBlank { null }
                }
            }
        }
        return IcalCalendar(name, zone, events)
    }

    private class Prop(val name: String, val params: Map<String, String>, val value: String)

    private fun toEvent(props: List<Prop>): IcalEvent? {
        fun first(n: String) = props.firstOrNull { it.name == n }
        val start = first("DTSTART")?.let(::time) ?: return null
        return IcalEvent(
            uid = first("UID")?.value?.trim().orEmpty(),
            summary = first("SUMMARY")?.value?.let(::unescape).orEmpty(),
            location = first("LOCATION")?.value?.let(::unescape)?.ifBlank { null },
            description = first("DESCRIPTION")?.value?.let(::unescape)?.ifBlank { null },
            start = start,
            end = first("DTEND")?.let(::time),
            duration = first("DURATION")?.value?.let(::parseDuration),
            rrule = first("RRULE")?.value?.trim(),
            exdates = props.filter { it.name == "EXDATE" }.flatMap { p -> p.value.split(',').mapNotNull { time(Prop(p.name, p.params, it)) } },
            recurrenceId = first("RECURRENCE-ID")?.let(::time),
            cancelled = first("STATUS")?.value?.trim()?.uppercase() == "CANCELLED",
        )
    }

    private fun time(p: Prop): IcalTime? {
        val v = p.value.trim()
        val digits = v.filter { it.isDigit() }
        if (digits.length < 8) return null
        val date = runCatching { LocalDate(digits.take(4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt()) }.getOrNull() ?: return null
        if (p.params["VALUE"]?.uppercase() == "DATE" || (v.length == 8 && digits.length == 8)) return IcalTime.Date(date)
        if (digits.length < 14 || v.getOrNull(8) != 'T') return null
        val time = runCatching { LocalTime(digits.substring(8, 10).toInt(), digits.substring(10, 12).toInt(), digits.substring(12, 14).toInt()) }.getOrNull() ?: return null
        val utc = v.endsWith("Z")
        return IcalTime.DateTime(LocalDateTime(date, time), if (utc) null else p.params["TZID"], utc)
    }

    /** Строки длиннее 75 байт переносятся с пробелом или табуляцией в начале продолжения. */
    private fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.split('\n')) {
            val line = raw.removeSuffix("\r")
            if ((line.startsWith(' ') || line.startsWith('\t')) && out.isNotEmpty()) out[out.size - 1] = out.last() + line.substring(1)
            else if (line.isNotEmpty()) out += line
        }
        return out
    }

    /** «ИМЯ;ПАРАМ=знач;ПАРАМ="в кавычках":значение» — двоеточие внутри кавычек не делит. */
    private fun parseLine(line: String): Prop? {
        var quoted = false
        var colon = -1
        for (i in line.indices) {
            when (line[i]) {
                '"' -> quoted = !quoted
                ':' -> if (!quoted) { colon = i; break }
            }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val parts = ArrayList<String>()
        val sb = StringBuilder()
        quoted = false
        for (c in head) {
            when {
                c == '"' -> quoted = !quoted
                c == ';' && !quoted -> { parts += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
        }
        parts += sb.toString()
        val params = parts.drop(1).filter { '=' in it }.associate { it.substringBefore('=').uppercase() to it.substringAfter('=') }
        return Prop(parts[0].uppercase(), params, line.substring(colon + 1))
    }

    private fun unescape(v: String): String {
        val sb = StringBuilder(v.length)
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                when (val n = v[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
