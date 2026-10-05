package io.github.nullbrash.quazio.feature.finance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmountExpressionTest {

    private fun calc(s: String) = AmountExpression.evaluate(s)

    @Test
    fun singleNumbers() {
        assertEquals(35_000L, calc("350"))
        assertEquals(120_050L, calc("1 200,50"))
        assertEquals(-30_000L, calc("−300"))
    }

    @Test
    fun precedenceLikeACalculator() {
        assertEquals(59_000L, calc("350+120×2"))
        assertEquals(110_000L, calc("1000+200÷2"))
        assertEquals(10_051L, calc("100,50+0,01"))
        assertEquals(-5_000L, calc("100−150"))
    }

    @Test
    fun typedOperatorsAreAccepted() {
        assertEquals(59_000L, calc("350+120*2"))
        assertEquals(50_000L, calc("1000/2"))
        assertEquals(50_000L, calc("600-100"))
    }

    @Test
    fun roundingToKopecks() {
        assertEquals(33_333L, calc("1000÷3"))   // 333,333… → 333,33
        assertEquals(66_667L, calc("2000÷3"))   // 666,666… → 666,67
        assertEquals(2L, calc("0,01×1,5"))      // 0,015 → 0,02 (половина — от нуля)
    }

    @Test
    fun incompleteOrInvalid() {
        for (bad in listOf("", "+", "350+", "×2", "1++2", "10÷0", "abc", "1,234,5")) {
            assertNull(calc(bad), bad)
        }
    }

    @Test
    fun operatorsDetection() {
        assertTrue(AmountExpression.hasOperators("350+120"))
        assertFalse(AmountExpression.hasOperators("350"))
        assertFalse(AmountExpression.hasOperators("−350")) // минус в начале — знак, не операция
    }
}
