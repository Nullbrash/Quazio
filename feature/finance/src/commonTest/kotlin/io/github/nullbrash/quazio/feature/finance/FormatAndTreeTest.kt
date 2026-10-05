package io.github.nullbrash.quazio.feature.finance

import io.github.nullbrash.quazio.core.model.Money
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatAndTreeTest {

    @Test
    fun parsesRussianAmounts() {
        assertEquals(45_000L, parseAmountMinor("450"))
        assertEquals(123_456L, parseAmountMinor("1 234,56"))
        assertEquals(123_456L, parseAmountMinor("1 234,56"))
        assertEquals(123_450L, parseAmountMinor("1234.5"))
        assertEquals(-30_000L, parseAmountMinor("-300"))
        assertEquals(5L, parseAmountMinor("0,05"))
    }

    @Test
    fun rejectsNonAmounts() {
        for (bad in listOf("", "abc", "1,234", "12,345", "1.2.3", "--5", ",5")) {
            assertNull(parseAmountMinor(bad), bad)
        }
    }

    @Test
    fun formatsMoneyTheRussianWay() {
        assertEquals("450 ₽", formatMoney(Money.rub(45_000)))
        assertEquals("1 234,56 ₽", formatMoney(Money.rub(123_456)))
        assertEquals("1 000 000 ₽", formatMoney(Money.rub(100_000_000)))
        assertEquals("−300,05 ₽", formatMoney(Money.rub(-30_005)))
        assertEquals("1234,56", formatAmountForEdit(123_456))
        assertEquals("450", formatAmountForEdit(45_000))
    }

    @Test
    fun treeOrdersChildrenAfterParentAndInheritsColor() {
        val rows = listOf(
            CategoryRow("b", null, "Транспорт", CategoryKind.EXPENSE, 0xFF0000FF, "001"),
            CategoryRow("a", null, "Магазины", CategoryKind.EXPENSE, 0xFF00FF00, "000"),
            CategoryRow("a2", "a", "Аптеки", CategoryKind.EXPENSE, null, "001"),
            CategoryRow("a1", "a", "Продукты", CategoryKind.EXPENSE, null, "000"),
            CategoryRow("a1x", "a1", "SPAR", CategoryKind.EXPENSE, null, ""),
        )
        val tree = buildCategoryTree(rows)
        assertEquals(listOf("a", "a1", "a1x", "a2", "b"), tree.map { it.id })
        assertEquals(listOf(0, 1, 2, 1, 0), tree.map { it.depth })
        assertEquals(0xFF00FF00, tree.first { it.id == "a1x" }.color)
        assertEquals("Магазины / Продукты / SPAR", tree.first { it.id == "a1x" }.path)
    }

    @Test
    fun orphanIsLiftedToRoot() {
        val tree = buildCategoryTree(listOf(CategoryRow("x", "удалённый", "Сирота", CategoryKind.EXPENSE, null, "")))
        assertEquals(0, tree.single().depth)
        assertNull(tree.single().parentId)
    }

    @Test
    fun monthRangeFollowsLocalTime() {
        val moscow = TimeZone.of("Europe/Moscow")
        val (from, to) = monthRange(YearMonth(2026, 10), moscow)
        // 1 октября 00:00 МСК = 30 сентября 21:00 UTC.
        assertEquals(1_790_802_000_000L, from)
        assertEquals(1_793_480_400_000L, to)
        assertEquals(YearMonth(2027, 1), YearMonth(2026, 12).next())
        assertEquals(YearMonth(2025, 12), YearMonth(2026, 1).previous())
    }
}
