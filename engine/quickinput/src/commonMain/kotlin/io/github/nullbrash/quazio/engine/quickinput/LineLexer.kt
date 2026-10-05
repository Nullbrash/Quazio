package io.github.nullbrash.quazio.engine.quickinput

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus

internal enum class LineType { NORMAL, TOTAL, BALANCE }

internal class Token(val text: String) {
    val norm: String = normalize(text)
    var used = false
}

/** Строка после выделения суммы, даты и времени; остальное — слова. */
internal class Lexed(
    val text: String,
    val type: LineType,
    /** Модуль суммы в копейках; null — суммы нет (ноль тоже считается «нет»). */
    val amount: Long?,
    val sign: Int?,
    val date: LocalDate?,
    /** «23.06: …» — дата в начале строки списка. */
    val datePrefix: Boolean,
    val time: LocalTime?,
    /** Строка — только дата и, может быть, время: «29 августа 2022 17 09 17». */
    val dateOnly: Boolean,
    val tokens: List<Token>,
)

internal fun normalize(s: String) = s.lowercase().replace('ё', 'е')

// Границы слова без \b: в Java \b не считает кириллицу буквами.
private const val NB = "(?<![\\p{L}\\p{N}])"
private const val NA = "(?![\\p{L}\\p{N}])"
private const val SP = "[ \\u00A0\\u202F]"
private const val NUM = "(\\d{1,3}(?:$SP\\d{3})+|\\d+)"

private val listMarker = Regex("^\\d{1,2}\\)\\s*")
private val dateRange = Regex("$NB\\d{1,2}\\.\\d{1,2}(?:\\.\\d{2,4})?\\s*[-–—]\\s*\\d{1,2}\\.\\d{1,2}(?:\\.\\d{2,4})?$NA")
private val ordinalDay = Regex("$NB(\\d{1,2})((?:\\s*[-–]\\s*\\d{1,2})*)\\s*-\\s*(?:ое|го|ого|е)$NA")
private val monthDate = Regex(
    "$NB(\\d{1,2})\\s+(январ|феврал|март|апрел|ма[яй]|июн|июл|август|сентябр|октябр|ноябр|декабр)\\p{L}*(?:\\s+(\\d{4}))?$NA",
    RegexOption.IGNORE_CASE,
)
private val numericDate = Regex(
    "$NB(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{4}|\\d{2}))?(?![\\d,]|\\.\\d|\\s*(?:к|k|тыс|р|руб|₽)(?!\\p{L}))(\\s*:)?",
    RegexOption.IGNORE_CASE,
)
private val timeOfDay = Regex("$NB([01]?\\d|2[0-3]):([0-5]\\d)$NA")
private val product = Regex("$NB$NUM\\s*[×xх*]\\s*$NUM$NA")
private val amountRe = Regex(
    "(?<![\\p{L}\\p{N}.,])([+\\-−–]\\s*)?$NUM(?:[.,](\\d{1,2}))?(?!\\d)" +
        "(?:([кk])(?!\\p{L})|\\s*(тыс\\p{L}*\\.?))?\\s*(р\\.?|руб\\p{L}*\\.?|₽)?(?!\\p{L})",
    RegexOption.IGNORE_CASE,
)
private val tokenRe = Regex("[#@]?[\\p{L}\\p{N}][\\p{L}\\p{N}\\-'’]*")
private val onlySmallNumbers = Regex("^[\\s:.\\d]*$")

internal object LineLexer {

    fun lex(raw: String, today: LocalDate): Lexed {
        var s = raw.trim().replace(listMarker, "")
        var type = LineType.NORMAL
        if (s.startsWith("=")) {
            type = LineType.TOTAL
            s = s.drop(1)
        } else {
            val n = normalize(s)
            val head = listOf("остаток", "баланс").firstOrNull { n.startsWith(it) }
            if (head != null) {
                type = LineType.BALANCE
                s = s.drop(head.length).trimStart(' ', ':')
            }
        }

        // «5 × 500 = + 2 500р.»: сумма — после «=», слева — расчёт.
        var amountSource: String? = null
        val eq = s.lastIndexOf('=')
        if (type == LineType.NORMAL && eq > 0) {
            amountSource = s.substring(eq + 1)
            s = s.substring(0, eq).replace(product, " ")
        }

        val rest = StringBuilder(s)
        fun blank(r: IntRange) { for (i in r) rest.setCharAt(i, ' ') }

        dateRange.findAll(rest.toString()).forEach { blank(it.range) }
        var date: LocalDate? = null
        var datePrefix = false
        ordinalDay.findAll(rest.toString()).forEach { m ->
            if (m.groupValues[2].isEmpty()) date = date ?: dayOfRecentMonth(m.groupValues[1].toInt(), today)
            blank(m.range)
        }
        monthDate.find(rest.toString())?.let { m ->
            val month = MONTH_STEMS.indexOfFirst { normalize(m.groupValues[2]).startsWith(it) } + 1
            val d = makeDate(m.groupValues[3].toIntOrNull(), month, m.groupValues[1].toInt(), today)
            if (d != null) { date = d; blank(m.range) }
        }
        if (date == null) {
            val text = rest.toString()
            for (m in numericDate.findAll(text)) {
                val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
                val d = makeDate(year, m.groupValues[2].toInt(), m.groupValues[1].toInt(), today) ?: continue
                val colon = m.groupValues[4].isNotEmpty()
                val otherDigits = (text.removeRange(m.range) + (amountSource ?: "")).any { it.isDigit() }
                if (colon || year != null || otherDigits) {
                    date = d
                    datePrefix = colon || text.substring(0, m.range.first).isBlank()
                    blank(m.range)
                    break
                }
            }
        }
        var time: LocalTime? = null
        timeOfDay.find(rest.toString())?.let { m ->
            time = LocalTime(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            blank(m.range)
        }

        if (date != null && amountSource == null && type == LineType.NORMAL) {
            val left = rest.toString()
            val numbers = Regex("\\d+").findAll(left).map { it.value }.toList()
            if (onlySmallNumbers.matches(left) && numbers.all { it.length <= 2 }) {
                return Lexed(raw.trim(), type, null, null, date, false, time, dateOnly = true, tokens = emptyList())
            }
        }

        var amount: Long? = null
        var sign: Int? = null
        if (amountSource != null) {
            amountRe.find(amountSource)?.let { m -> amount = valueOf(m); sign = signOf(m) }
        } else {
            val text = rest.toString()
            val prod = product.find(text)
            if (prod != null) {
                amount = multiply(prod.groupValues[1], prod.groupValues[2])
                blank(prod.range)
            } else {
                val candidates = amountRe.findAll(text).filter { valueOf(it) != null }.toList()
                val pick = candidates.firstOrNull { signOf(it) != null || it.groupValues[4].isNotEmpty() || it.groupValues[5].isNotEmpty() || it.groupValues[6].isNotEmpty() }
                    ?: candidates.firstOrNull()
                if (pick != null) {
                    amount = valueOf(pick)
                    sign = signOf(pick)
                    blank(pick.range)
                }
            }
        }
        if (amount == 0L) amount = null

        val tokens = tokenRe.findAll(rest.toString()).map { Token(it.value) }.toList()
        return Lexed(raw.trim(), type, amount, sign, date, datePrefix, time, dateOnly = false, tokens = tokens)
    }

    private fun signOf(m: MatchResult): Int? = when (m.groupValues[1].trim()) {
        "+" -> 1
        "" -> null
        else -> -1
    }

    private fun valueOf(m: MatchResult): Long? {
        val digits = m.groupValues[2].filter { it.isDigit() }
        if (digits.length > 12) return null
        val frac = m.groupValues[3].let { if (it.length == 1) it.toLong() * 10 else it.toLongOrNull() ?: 0 }
        val minor = digits.toLong() * 100 + frac
        val thousands = m.groupValues[4].isNotEmpty() || m.groupValues[5].isNotEmpty()
        return if (thousands) minor * 1000 else minor
    }

    private fun multiply(a: String, b: String): Long? {
        val x = a.filter { it.isDigit() }.toLongOrNull() ?: return null
        val y = b.filter { it.isDigit() }.toLongOrNull() ?: return null
        return if (x > 1_000_000_000 || y > 1_000_000_000) null else x * y * 100
    }
}

private val MONTH_STEMS = listOf("январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр")

/** Дата без года — ближайшая прошедшая (в январе «28.12» — прошлый год). */
internal fun makeDate(year: Int?, month: Int, day: Int, today: LocalDate): LocalDate? {
    fun at(y: Int) = try { LocalDate(y, month, day) } catch (e: IllegalArgumentException) { null }
    if (year != null) return at(year)
    val d = at(today.year) ?: return null
    return if (d > today) at(today.year - 1) else d
}

/** «5-го» — это число текущего месяца, если оно уже прошло, иначе прошлого. */
internal fun dayOfRecentMonth(day: Int, today: LocalDate): LocalDate? {
    val month = today.month.ordinal + 1
    fun at(y: Int, m: Int) = try { LocalDate(y, m, day) } catch (e: IllegalArgumentException) { null }
    val d = at(today.year, month)
    if (d != null && d <= today) return d
    val prev = today.minus(DatePeriod(months = 1))
    return at(prev.year, prev.month.ordinal + 1)
}
