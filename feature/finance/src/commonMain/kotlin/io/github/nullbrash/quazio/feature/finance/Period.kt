package io.github.nullbrash.quazio.feature.finance

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.ExperimentalTime

data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    init {
        require(month in 1..12) { "Месяц вне 1..12: $month" }
    }

    fun next(): YearMonth = if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)
    fun previous(): YearMonth = if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)

    override fun compareTo(other: YearMonth): Int = compareValuesBy(this, other, YearMonth::year, YearMonth::month)
}

/**
 * Границы месяца [от, до) в мс — по местному времени [timeZone]: трата в 23:30
 * последнего дня должна попасть в свой месяц, а не в следующий по UTC.
 */
@OptIn(ExperimentalTime::class)
fun monthRange(month: YearMonth, timeZone: TimeZone): Pair<Long, Long> {
    val next = month.next()
    val from = LocalDate(month.year, month.month, 1).atStartOfDayIn(timeZone).toEpochMilliseconds()
    val to = LocalDate(next.year, next.month, 1).atStartOfDayIn(timeZone).toEpochMilliseconds()
    return from to to
}
