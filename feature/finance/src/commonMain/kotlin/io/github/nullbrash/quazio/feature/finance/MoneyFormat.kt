package io.github.nullbrash.quazio.feature.finance

import io.github.nullbrash.quazio.core.model.CurrencyCode
import io.github.nullbrash.quazio.core.model.Money

/**
 * Разбор суммы, как её вводят по-русски: «450», «1 234,56», «1234.5», «-300».
 * Возвращает копейки или null, если это не сумма. Больше двух знаков после запятой — ошибка,
 * а не округление: молча терять копейки нельзя.
 */
fun parseAmountMinor(text: String): Long? {
    val t = text.trim().replace(" ", "").replace(NBSP, "").replace(NNBSP, "").replace(',', '.')
    val m = Regex("""(-?)(\d{1,13})(?:\.(\d{1,2}))?""").matchEntire(t) ?: return null
    val (sign, whole, frac) = m.destructured
    val minor = whole.toLong() * 100 + frac.padEnd(2, '0').toLong()
    return if (sign == "-") -minor else minor
}

/** «1 234,56 ₽», «450 ₽», «−300 ₽»: копейки — только если они есть; разряды — неразрывным пробелом. */
fun formatMoney(money: Money): String {
    val abs = if (money.minor < 0) -money.minor else money.minor
    val whole = (abs / 100).toString().reversed().chunked(3).joinToString(NBSP).reversed()
    val kop = abs % 100
    val number = if (kop == 0L) whole else whole + "," + kop.toString().padStart(2, '0')
    val sign = if (money.minor < 0) "−" else ""
    return "$sign$number$NBSP${symbolOf(money.currency)}"
}

/** Сумма для поля ввода: «1234,56» / «450» — без разрядов, чтобы легко править. */
fun formatAmountForEdit(minor: Long): String {
    val abs = if (minor < 0) -minor else minor
    val kop = abs % 100
    val s = if (kop == 0L) (abs / 100).toString() else "${abs / 100},${kop.toString().padStart(2, '0')}"
    return if (minor < 0) "-$s" else s
}

private fun symbolOf(currency: CurrencyCode): String = when (currency.code) {
    "RUB" -> "₽"
    "USD" -> "$"
    "EUR" -> "€"
    else -> currency.code
}

private const val NBSP = "\u00A0"
private const val NNBSP = "\u202F"
