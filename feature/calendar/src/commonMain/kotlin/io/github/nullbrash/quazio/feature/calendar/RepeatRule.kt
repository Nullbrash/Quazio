package io.github.nullbrash.quazio.feature.calendar

import kotlinx.datetime.LocalDate

/** Повтор в окне события. Чужие правила (сложные, из Google) не трогаем — [CUSTOM] хранит их как есть. */
enum class RepeatKind { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY, CUSTOM }

data class Repeat(
    val kind: RepeatKind,
    /** Последний день повтора включительно; null — без конца. */
    val until: LocalDate? = null,
    /** Исходное правило для [RepeatKind.CUSTOM]. */
    val original: String? = null,
) {
    /** RFC 5545 без «RRULE:»; null — разовое. */
    fun toRrule(): String? {
        val base = when (kind) {
            RepeatKind.NONE -> return null
            RepeatKind.CUSTOM -> return original
            RepeatKind.DAILY -> "FREQ=DAILY"
            RepeatKind.WEEKDAYS -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
            RepeatKind.WEEKLY -> "FREQ=WEEKLY"
            RepeatKind.MONTHLY -> "FREQ=MONTHLY"
            RepeatKind.YEARLY -> "FREQ=YEARLY"
        }
        // UNTIL в UTC; конец дня — чтобы повторение в сам последний день не потерялось.
        return until?.let { "$base;UNTIL=${it.year}${pad(it.month.ordinal + 1)}${pad(it.day)}T235959Z" } ?: base
    }

    companion object {
        val NONE = Repeat(RepeatKind.NONE)

        fun parse(rrule: String?): Repeat {
            if (rrule.isNullOrBlank()) return NONE
            val parts = rrule.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }
                .associate { it.substringBefore('=').uppercase() to it.substringAfter('=') }
                .filterKeys { it != "WKST" }
                .filterNot { (k, v) -> k == "INTERVAL" && v == "1" }
            val until = parts["UNTIL"]?.let(::parseUntil)
            val rest = parts - "UNTIL"
            val kind = when {
                parts.containsKey("UNTIL") && until == null -> null
                rest == mapOf("FREQ" to "DAILY") -> RepeatKind.DAILY
                rest == mapOf("FREQ" to "WEEKLY") -> RepeatKind.WEEKLY
                rest == mapOf("FREQ" to "WEEKLY", "BYDAY" to "MO,TU,WE,TH,FR") -> RepeatKind.WEEKDAYS
                rest == mapOf("FREQ" to "MONTHLY") -> RepeatKind.MONTHLY
                rest == mapOf("FREQ" to "YEARLY") -> RepeatKind.YEARLY
                else -> null
            }
            return if (kind == null) Repeat(RepeatKind.CUSTOM, original = rrule.removePrefix("RRULE:")) else Repeat(kind, until)
        }

        private fun parseUntil(v: String): LocalDate? {
            if (v.length < 8 || !v.take(8).all { it.isDigit() }) return null
            return try {
                LocalDate(v.take(4).toInt(), v.substring(4, 6).toInt(), v.substring(6, 8).toInt())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun pad(n: Int) = n.toString().padStart(2, '0')
    }
}

/** Длительность RFC 2445 из календаря Android: «P3600S», «P1D», «PT1H30M», «P1W». В миллисекундах. */
fun parseDuration(d: String?): Long? {
    if (d.isNullOrBlank()) return null
    val m = Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?(?:(\\d+)S)?$").matchEntire(d.trim()) ?: return null
    fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
    val seconds = g(2) * 7 * 86_400 + g(3) * 86_400 + g(4) * 3_600 + g(5) * 60 + g(6) + g(7)
    return seconds * 1000 * (if (m.groupValues[1] == "-") -1 else 1)
}
