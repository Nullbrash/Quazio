package io.github.nullbrash.quazio.feature.finance

import io.github.nullbrash.quazio.engine.quickinput.QuickDraft
import io.github.nullbrash.quazio.engine.quickinput.QuickKind
import io.github.nullbrash.quazio.engine.quickinput.WordRule

/** Смысл сохранённой операции в терминах быстрого ввода: вид, свой счёт, второй счёт, категория. */
data class QuickMeaning(val kind: QuickKind, val accountId: String?, val secondAccountId: String?, val categoryId: String?)

/** Долг хранится переводом: на счёт-долг — деньги ушли от меня, со счёта-долга — пришли. */
fun TransactionDraft.quickMeaning(isDebt: (String) -> Boolean): QuickMeaning? = when (kind) {
    TxnKind.EXPENSE -> QuickMeaning(QuickKind.EXPENSE, finAccountId, null, categoryId)
    TxnKind.INCOME -> QuickMeaning(QuickKind.INCOME, finAccountId, null, categoryId)
    TxnKind.TRANSFER -> when {
        toFinAccountId != null && isDebt(toFinAccountId) -> QuickMeaning(QuickKind.DEBT_OUT, finAccountId, toFinAccountId, null)
        isDebt(finAccountId) -> QuickMeaning(QuickKind.DEBT_IN, toFinAccountId, finAccountId, null)
        else -> QuickMeaning(QuickKind.TRANSFER, finAccountId, toFinAccountId, null)
    }
    TxnKind.ADJUSTMENT -> null
}

/**
 * Что пользователь поправил в черновике — это и запоминается для слова черновика
 * (решение пользователя: «забрал» поправили на расход — дальше так же).
 * null — запоминать нечего.
 */
fun quickCorrection(original: QuickDraft, saved: TransactionDraft, isDebt: (String) -> Boolean): WordRule? {
    if (original.keyWord == null) return null
    val m = saved.quickMeaning(isDebt) ?: return null
    val kindChanged = m.kind != original.kind
    val rule = WordRule(
        kind = m.kind.takeIf { kindChanged },
        categoryId = m.categoryId?.takeIf { it != original.categoryId },
        accountId = m.accountId?.takeIf { it != original.accountId },
        // У расхода и дохода второго счёта нет — стирать запомненное «куда» незачем.
        secondAccountId = m.secondAccountId?.takeIf { it != original.secondAccountId },
    )
    return rule.takeIf { it != WordRule() }
}
