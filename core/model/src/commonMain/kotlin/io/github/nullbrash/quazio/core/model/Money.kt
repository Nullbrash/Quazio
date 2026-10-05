package io.github.nullbrash.quazio.core.model

import kotlin.jvm.JvmInline

/** Код валюты ISO 4217 (`RUB`). Валюта хранится у каждой суммы, даже пока она одна. */
@JvmInline
value class CurrencyCode(val code: String) {
    init {
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Неверный код валюты: \"$code\"" }
    }

    override fun toString(): String = code

    companion object {
        val RUB = CurrencyCode("RUB")
    }
}

/**
 * Деньги в минимальных единицах (копейках): без Double — иначе 0.1 + 0.2 ≠ 0.3.
 * Складывать можно только суммы одной валюты; переполнение — ошибка, а не тихий переход через ноль.
 */
data class Money(val minor: Long, val currency: CurrencyCode) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(addExact(minor, sameCurrency(other).minor), currency)

    operator fun minus(other: Money): Money = Money(addExact(minor, negateExact(sameCurrency(other).minor)), currency)

    operator fun unaryMinus(): Money = Money(negateExact(minor), currency)

    override fun compareTo(other: Money): Int = minor.compareTo(sameCurrency(other).minor)

    val isNegative: Boolean get() = minor < 0

    private fun sameCurrency(other: Money): Money {
        require(other.currency == currency) { "Разные валюты: $currency и ${other.currency}" }
        return other
    }

    companion object {
        fun rub(minor: Long): Money = Money(minor, CurrencyCode.RUB)
        fun zero(currency: CurrencyCode): Money = Money(0, currency)

        private fun addExact(a: Long, b: Long): Long {
            val r = a + b
            if ((a xor r) and (b xor r) < 0) throw ArithmeticException("Переполнение суммы")
            return r
        }

        private fun negateExact(a: Long): Long {
            if (a == Long.MIN_VALUE) throw ArithmeticException("Переполнение суммы")
            return -a
        }
    }
}
