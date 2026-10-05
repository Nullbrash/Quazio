package io.github.nullbrash.quazio.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun arithmeticInMinorUnits() {
        assertEquals(Money.rub(30), Money.rub(10) + Money.rub(20))
        assertEquals(Money.rub(-5), Money.rub(10) - Money.rub(15))
        assertEquals(Money.rub(-7), -Money.rub(7))
        assertTrue(Money.rub(-1).isNegative)
        assertTrue(Money.rub(100) > Money.rub(99))
    }

    @Test
    fun differentCurrenciesDoNotMix() {
        val usd = Money(100, CurrencyCode("USD"))
        assertFailsWith<IllegalArgumentException> { Money.rub(100) + usd }
        assertFailsWith<IllegalArgumentException> { Money.rub(100) < usd }
    }

    @Test
    fun overflowIsAnError() {
        assertFailsWith<ArithmeticException> { Money.rub(Long.MAX_VALUE) + Money.rub(1) }
        assertFailsWith<ArithmeticException> { Money.rub(Long.MIN_VALUE) - Money.rub(1) }
        assertFailsWith<ArithmeticException> { -Money.rub(Long.MIN_VALUE) }
    }

    @Test
    fun currencyCodeIsValidated() {
        for (bad in listOf("", "rub", "RU", "RUBL", "R1B")) {
            assertFailsWith<IllegalArgumentException>(bad) { CurrencyCode(bad) }
        }
    }
}
