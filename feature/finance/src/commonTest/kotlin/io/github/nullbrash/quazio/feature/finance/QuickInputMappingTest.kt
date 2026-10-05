package io.github.nullbrash.quazio.feature.finance

import io.github.nullbrash.quazio.engine.quickinput.QuickDraft
import io.github.nullbrash.quazio.engine.quickinput.QuickKind
import io.github.nullbrash.quazio.engine.quickinput.WordRule
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuickInputMappingTest {

    private val isDebt = { id: String -> id.startsWith("debt") }
    private val withdraw = QuickDraft(
        kind = QuickKind.TRANSFER, amountMinor = 150_000, date = LocalDate(2026, 10, 5),
        accountId = "savings", secondAccountId = "card", keyWord = "забрал",
    )

    private fun saved(kind: TxnKind, from: String, to: String? = null, category: String? = null) =
        TransactionDraft(kind = kind, amountMinor = 150_000, finAccountId = from, toFinAccountId = to, categoryId = category,
            occurredAt = 0, timeZone = "Europe/Moscow")

    @Test
    fun unchangedDraftTeachesNothing() {
        assertNull(quickCorrection(withdraw, saved(TxnKind.TRANSFER, "savings", "card"), isDebt))
    }

    @Test
    fun changedKindAndAccountAreRemembered() {
        // «Забрал» поправили на расход с копилки.
        assertEquals(
            WordRule(kind = QuickKind.EXPENSE),
            quickCorrection(withdraw, saved(TxnKind.EXPENSE, "savings"), isDebt),
        )
        assertEquals(
            WordRule(secondAccountId = "cash"),
            quickCorrection(withdraw, saved(TxnKind.TRANSFER, "savings", "cash"), isDebt),
        )
    }

    @Test
    fun debtDirectionIsReadFromTheTransfer() {
        assertEquals(QuickKind.DEBT_OUT, saved(TxnKind.TRANSFER, "card", "debt-katya").quickMeaning(isDebt)?.kind)
        saved(TxnKind.TRANSFER, "debt-katya", "card").quickMeaning(isDebt).let {
            assertEquals(QuickKind.DEBT_IN, it?.kind)
            assertEquals("card", it?.accountId)
            assertEquals("debt-katya", it?.secondAccountId)
        }
    }

    @Test
    fun draftWithoutKeyWordTeachesNothing() {
        assertNull(quickCorrection(withdraw.copy(keyWord = null), saved(TxnKind.EXPENSE, "savings"), isDebt))
    }
}
