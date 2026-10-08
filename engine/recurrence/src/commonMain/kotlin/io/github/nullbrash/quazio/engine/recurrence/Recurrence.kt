package io.github.nullbrash.quazio.engine.recurrence

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Повторения правила RRULE. Считаются в местном времени серии (как требует RFC 5545: «каждый
 * день в 9:00» остаётся 9:00 и после перевода часов), в момент переводятся вызывающим.
 */
object Recurrence {

    /**
     * Начала повторений по порядку, начиная с [start] (оно — всегда первое, даже если не подходит
     * под правило; входит в COUNT). Последовательность может быть бесконечной — ограничивать
     * вызывающему (`takeWhile`). [zone] нужен только для UNTIL в UTC; null — время серии и есть UTC.
     */
    fun starts(rule: RRule, start: LocalDateTime, zone: TimeZone? = null): Sequence<LocalDateTime> =
        allDates(rule, start.date).map { LocalDateTime(it, start.time) }
            .takeWhile { withinUntil(rule.until, it, zone) }
            .let { s -> rule.count?.let { s.take(it) } ?: s }

    /** Даты повторений (платежи и события на весь день): то же, без времени; UNTIL — по дате. */
    fun dates(rule: RRule, start: LocalDate): Sequence<LocalDate> =
        allDates(rule, start)
            .takeWhile { d ->
                when (val u = rule.until) {
                    null -> true
                    is Until.Date -> d <= u.date
                    is Until.Floating -> d <= u.dateTime.date
                    is Until.Utc -> d <= u.instant.toLocalDateTime(TimeZone.UTC).date
                }
            }
            .let { s -> rule.count?.let { s.take(it) } ?: s }

    private fun allDates(rule: RRule, start: LocalDate): Sequence<LocalDate> = sequence {
        yield(start)
        var period = 0L
        var idle = 0
        while (true) {
            val found = candidates(rule, start, period).filter { it > start }
            for (d in found) yield(d)
            // Правило, которое больше никогда не сработает (31-е в феврале), не крутится вечно.
            idle = if (found.isEmpty()) idle + 1 else 0
            if (idle > MAX_IDLE_PERIODS) return@sequence
            period++
        }
    }

    private fun withinUntil(until: Until?, at: LocalDateTime, zone: TimeZone?): Boolean = when (until) {
        null -> true
        is Until.Date -> at.date <= until.date
        is Until.Floating -> at <= until.dateTime
        is Until.Utc -> (zone?.let { at.toInstant(it) } ?: at.toInstantUtc()) <= until.instant
    }

    /** Подходящие даты одного периода (день, неделя, месяц, год) по порядку. */
    private fun candidates(rule: RRule, start: LocalDate, period: Long): List<LocalDate> {
        val step = period * rule.interval
        val days: List<LocalDate> = when (rule.freq) {
            Frequency.DAILY -> {
                val d = start.plus(step, kotlinx.datetime.DateTimeUnit.DAY)
                listOf(d).filter { monthOk(rule, it) && monthDayOk(rule, it) && (rule.byDay.isEmpty() || rule.byDay.any { w -> w.day == it.dayOfWeek }) }
            }
            Frequency.WEEKLY -> {
                val first = weekStart(start, rule.weekStart).plus(step * 7, kotlinx.datetime.DateTimeUnit.DAY)
                val wanted = rule.byDay.map { it.day }.ifEmpty { listOf(start.dayOfWeek) }.toSet()
                (0 until 7).map { first.plus(DatePeriod(days = it)) }.filter { it.dayOfWeek in wanted && monthOk(rule, it) }
            }
            Frequency.MONTHLY -> {
                val ym = YearMonth(start.year, start.month).plus(step, kotlinx.datetime.DateTimeUnit.MONTH)
                if (rule.byMonth.isNotEmpty() && ym.month.number !in rule.byMonth) emptyList()
                else monthDays(rule, ym, start)
            }
            Frequency.YEARLY -> {
                val year = start.year + step.toInt()
                yearDays(rule, year, start)
            }
        }
        return applySetPos(rule.bySetPos, days)
    }

    private fun monthDays(rule: RRule, ym: YearMonth, start: LocalDate): List<LocalDate> {
        if (rule.byMonthDay.isEmpty() && rule.byDay.isEmpty()) return listOfNotNull(dayOrNull(ym, start.day))
        val all = (1..ym.numberOfDays).map { LocalDate(ym.year, ym.month, it) }
        return all.filter { d ->
            (rule.byMonthDay.isEmpty() || matchesMonthDay(rule.byMonthDay, d, ym.numberOfDays)) &&
                (rule.byDay.isEmpty() || matchesWeekday(rule.byDay, d, all))
        }
    }

    private fun yearDays(rule: RRule, year: Int, start: LocalDate): List<LocalDate> {
        // BYDAY с номером без BYMONTH — номер в году («20-й понедельник года»).
        if (rule.byMonth.isEmpty() && rule.byMonthDay.isEmpty() && rule.byDay.isNotEmpty()) {
            val all = generateSequence(LocalDate(year, 1, 1)) { it.plus(DatePeriod(days = 1)) }.takeWhile { it.year == year }.toList()
            return all.filter { matchesWeekday(rule.byDay, it, all) }
        }
        val months = rule.byMonth.ifEmpty { if (rule.byMonthDay.isNotEmpty() || rule.byDay.isNotEmpty()) (1..12).toList() else listOf(start.month.number) }
        return months.sorted().flatMap { m ->
            val ym = YearMonth(year, m)
            if (rule.byMonthDay.isEmpty() && rule.byDay.isEmpty()) listOfNotNull(dayOrNull(ym, start.day))
            else monthDays(rule.copy(byMonth = emptyList()), ym, start)
        }
    }

    private fun applySetPos(setPos: List<Int>, days: List<LocalDate>): List<LocalDate> {
        if (setPos.isEmpty() || days.isEmpty()) return days
        return setPos.mapNotNull { p -> if (p > 0) days.getOrNull(p - 1) else days.getOrNull(days.size + p) }.distinct().sorted()
    }

    private fun matchesMonthDay(byMonthDay: List<Int>, d: LocalDate, length: Int): Boolean =
        byMonthDay.any { n -> if (n > 0) d.day == n else d.day == length + n + 1 }

    /** BYDAY: без номера — любой такой день; с номером — n-й (или n-й с конца) в [scope]. */
    private fun matchesWeekday(byDay: List<WeekdayNum>, d: LocalDate, scope: List<LocalDate>): Boolean =
        byDay.any { w ->
            if (w.day != d.dayOfWeek) return@any false
            if (w.n == 0) return@any true
            val same = scope.filter { it.dayOfWeek == w.day }
            val index = same.indexOf(d)
            if (w.n > 0) index == w.n - 1 else index == same.size + w.n
        }

    private fun monthOk(rule: RRule, d: LocalDate) = rule.byMonth.isEmpty() || d.month.number in rule.byMonth

    private fun monthDayOk(rule: RRule, d: LocalDate) =
        rule.byMonthDay.isEmpty() || matchesMonthDay(rule.byMonthDay, d, YearMonth(d.year, d.month).numberOfDays)

    /** Несуществующая дата (31 апреля, 29 февраля не в високосный) пропускается — так по RFC 5545. */
    private fun dayOrNull(ym: YearMonth, day: Int): LocalDate? = if (day <= ym.numberOfDays) LocalDate(ym.year, ym.month, day) else null

    private fun weekStart(d: LocalDate, first: DayOfWeek): LocalDate {
        val back = (d.dayOfWeek.isoDayNumber - first.isoDayNumber + 7) % 7
        return d.minus(DatePeriod(days = back))
    }

    private const val MAX_IDLE_PERIODS = 400
}
