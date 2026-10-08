package io.github.nullbrash.quazio.engine.recurrence

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** День недели из BYDAY; [n] ≠ 0 — номер в месяце или году («-1FR» — последняя пятница). */
data class WeekdayNum(val day: DayOfWeek, val n: Int = 0)

/** UNTIL: дата, момент UTC («…Z») или «плавающее» местное время — RFC 5545 разрешает все три. */
sealed interface Until {
    data class Date(val date: LocalDate) : Until
    data class Utc(val instant: Instant) : Until
    data class Floating(val dateTime: LocalDateTime) : Until
}

/**
 * Правило повтора RFC 5545 — подмножество, которое пишут календари (Google, Android) и платежи
 * Quazio: FREQ, INTERVAL, COUNT, UNTIL, BYDAY, BYMONTHDAY, BYMONTH, BYSETPOS, WKST.
 * Остальное (BYYEARDAY, BYWEEKNO, BYHOUR…) — [parse] возвращает null: лучше показать только
 * первое повторение, чем выдумать неверные.
 */
data class RRule(
    val freq: Frequency,
    val interval: Int = 1,
    val count: Int? = null,
    val until: Until? = null,
    val byDay: List<WeekdayNum> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val bySetPos: List<Int> = emptyList(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    init {
        require(interval >= 1) { "INTERVAL < 1" }
    }

    companion object {
        /** Текст правила с «RRULE:» или без; null — правило неверное или с неподдерживаемыми частями. */
        fun parse(text: String): RRule? {
            val parts = text.trim().removePrefix("RRULE:").split(';').filter { it.isNotBlank() }
            var freq: Frequency? = null
            var rule = RRule(Frequency.DAILY)
            for (part in parts) {
                val key = part.substringBefore('=').trim().uppercase()
                val value = part.substringAfter('=', "").trim()
                rule = when (key) {
                    "FREQ" -> {
                        freq = Frequency.entries.firstOrNull { it.name == value.uppercase() } ?: return null
                        rule
                    }
                    "INTERVAL" -> rule.copy(interval = value.toIntOrNull()?.takeIf { it >= 1 } ?: return null)
                    "COUNT" -> rule.copy(count = value.toIntOrNull()?.takeIf { it >= 1 } ?: return null)
                    "UNTIL" -> rule.copy(until = parseUntil(value) ?: return null)
                    "BYDAY" -> rule.copy(byDay = value.split(',').map { parseWeekdayNum(it) ?: return null })
                    "BYMONTHDAY" -> rule.copy(byMonthDay = ints(value, 1..31) ?: return null)
                    "BYMONTH" -> rule.copy(byMonth = ints(value, 1..12, signed = false) ?: return null)
                    "BYSETPOS" -> rule.copy(bySetPos = ints(value, 1..366) ?: return null)
                    "WKST" -> rule.copy(weekStart = weekday(value) ?: return null)
                    else -> return null
                }
            }
            return rule.copy(freq = freq ?: return null).takeIf { it.count == null || it.until == null }
        }

        private fun ints(value: String, range: IntRange, signed: Boolean = true): List<Int>? =
            value.split(',').map { s ->
                val n = s.trim().toIntOrNull() ?: return null
                if (n in range || (signed && -n in range)) n else return null
            }

        private fun parseWeekdayNum(s: String): WeekdayNum? {
            val t = s.trim().uppercase()
            if (t.length < 2) return null
            val day = weekday(t.takeLast(2)) ?: return null
            val num = t.dropLast(2)
            if (num.isEmpty()) return WeekdayNum(day)
            val n = num.toIntOrNull()?.takeIf { it != 0 && it in -53..53 } ?: return null
            return WeekdayNum(day, n)
        }

        private fun weekday(code: String): DayOfWeek? = CODES.entries.firstOrNull { it.value == code.uppercase() }?.key

        private fun parseUntil(v: String): Until? {
            val digits = v.filter { it.isDigit() }
            fun date() = runCatching { LocalDate(digits.take(4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt()) }.getOrNull()
            return when {
                v.length == 8 && digits.length == 8 -> date()?.let { Until.Date(it) }
                digits.length == 14 && v.length >= 15 && v[8] == 'T' -> {
                    val d = date() ?: return null
                    val t = runCatching { LocalTime(digits.substring(8, 10).toInt(), digits.substring(10, 12).toInt(), digits.substring(12, 14).toInt()) }.getOrNull() ?: return null
                    val local = LocalDateTime(d, t)
                    if (v.endsWith("Z")) Until.Utc(local.toInstantUtc()) else Until.Floating(local)
                }
                else -> null
            }
        }

        internal val CODES = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE", DayOfWeek.THURSDAY to "TH",
            DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
        )
    }
}

internal fun LocalDateTime.toInstantUtc(): Instant =
    Instant.fromEpochSeconds(date.toEpochDays() * 86_400L + time.toSecondOfDay())
