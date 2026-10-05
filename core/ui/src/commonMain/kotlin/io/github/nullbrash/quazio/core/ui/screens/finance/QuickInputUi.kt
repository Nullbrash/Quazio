package io.github.nullbrash.quazio.core.ui.screens.finance

import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.txn_bad_amount
import io.github.nullbrash.quazio.core.ui.res.txn_need_account
import io.github.nullbrash.quazio.core.ui.res.txn_need_debt
import io.github.nullbrash.quazio.core.ui.res.txn_need_to_account
import io.github.nullbrash.quazio.engine.quickinput.QuickDraft
import io.github.nullbrash.quazio.engine.quickinput.QuickKind
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.TransactionDraft
import io.github.nullbrash.quazio.feature.finance.TxnKind
import io.github.nullbrash.quazio.feature.finance.TxnSource
import io.github.nullbrash.quazio.feature.finance.quickCorrection
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource

/** Содержимое окна операции до сохранения: из сохранённой операции, из быстрого ввода или из полей окна. */
internal data class EditorPrefill(
    val kind: EditorKind = EditorKind.EXPENSE,
    /** Знак суммы: −1 / +1 (у перевода не важен). */
    val sign: Int = -1,
    /** Модуль суммы в копейках. */
    val amountMinor: Long? = null,
    val finAccountId: String? = null,
    /** Перевод — «куда», долг — счёт-долг. */
    val secondAccountId: String? = null,
    val categoryId: String? = null,
    val merchant: String = "",
    val description: String = "",
    val tagIds: Set<String> = emptySet(),
    val dateTime: LocalDateTime,
    val note: String = "",
)

internal sealed interface DraftCheck {
    data class Ok(val draft: TransactionDraft) : DraftCheck
    data class Bad(val error: StringResource) : DraftCheck
}

/** Поля окна → операция для сохранения (или что не заполнено). */
internal fun EditorPrefill.check(id: String?, zone: TimeZone, isDebt: (String?) -> Boolean): DraftCheck {
    val abs = amountMinor?.takeIf { it > 0 } ?: return DraftCheck.Bad(Res.string.txn_bad_amount)
    val mine = finAccountId ?: return DraftCheck.Bad(Res.string.txn_need_account)
    val at = toMillis(dateTime, zone)
    val draft = when (kind) {
        EditorKind.INCOME, EditorKind.EXPENSE -> TransactionDraft(
            id = id, kind = if (kind == EditorKind.INCOME) TxnKind.INCOME else TxnKind.EXPENSE, amountMinor = abs,
            finAccountId = mine, categoryId = categoryId, merchantName = merchant, tagIds = tagIds,
            occurredAt = at, timeZone = zone.id, description = description, note = note,
        )
        EditorKind.TRANSFER -> {
            val to = secondAccountId?.takeIf { it != mine } ?: return DraftCheck.Bad(Res.string.txn_need_to_account)
            TransactionDraft(id = id, kind = TxnKind.TRANSFER, amountMinor = abs, finAccountId = mine, toFinAccountId = to,
                tagIds = tagIds, occurredAt = at, timeZone = zone.id, description = description, note = note)
        }
        EditorKind.DEBT -> {
            val debt = secondAccountId?.takeIf { isDebt(it) } ?: return DraftCheck.Bad(Res.string.txn_need_debt)
            // «−» — со своего счёта на долг, «+» — с долга на свой счёт.
            val (from, to) = if (sign < 0) mine to debt else debt to mine
            TransactionDraft(id = id, kind = TxnKind.TRANSFER, amountMinor = abs, finAccountId = from, toFinAccountId = to,
                tagIds = tagIds, occurredAt = at, timeZone = zone.id, description = description, note = note)
        }
        EditorKind.ADJUSTMENT -> TransactionDraft(
            id = id, kind = TxnKind.ADJUSTMENT, amountMinor = sign * abs, finAccountId = mine, tagIds = tagIds,
            occurredAt = at, timeZone = zone.id, description = description, note = note,
        )
    }
    return DraftCheck.Ok(draft)
}

/** Сохранённая операция → поля окна. Перевод со счётом-долгом показывается как «Долг». */
internal fun TransactionDraft.toPrefill(isDebt: (String?) -> Boolean): EditorPrefill {
    val base = EditorPrefill(
        amountMinor = kotlin.math.abs(amountMinor), categoryId = categoryId, merchant = merchantName.orEmpty(),
        description = description, tagIds = tagIds, dateTime = localDateTime(occurredAt, timeZone), note = note,
    )
    return when {
        kind == TxnKind.TRANSFER && isDebt(toFinAccountId) ->
            base.copy(kind = EditorKind.DEBT, sign = -1, finAccountId = finAccountId, secondAccountId = toFinAccountId)
        kind == TxnKind.TRANSFER && isDebt(finAccountId) ->
            base.copy(kind = EditorKind.DEBT, sign = 1, finAccountId = toFinAccountId, secondAccountId = finAccountId)
        else -> base.copy(
            kind = when (kind) {
                TxnKind.INCOME -> EditorKind.INCOME
                TxnKind.EXPENSE -> EditorKind.EXPENSE
                TxnKind.TRANSFER -> EditorKind.TRANSFER
                TxnKind.ADJUSTMENT -> EditorKind.ADJUSTMENT
            },
            sign = when (kind) {
                TxnKind.INCOME -> 1
                TxnKind.ADJUSTMENT -> if (amountMinor < 0) -1 else 1
                else -> -1
            },
            finAccountId = finAccountId,
            secondAccountId = toFinAccountId,
        )
    }
}

/** Черновик быстрого ввода → поля окна. Время не названо: сегодня — сейчас, другой день — полдень. */
internal fun QuickDraft.toPrefill(tagIds: Set<String>, zone: TimeZone): EditorPrefill {
    val now = localDateTime(nowMillis(), zone.id)
    val at = LocalDateTime(date, time ?: if (date == now.date) LocalTime(now.hour, now.minute) else LocalTime(12, 0))
    val (kind, sign) = when (kind) {
        QuickKind.EXPENSE -> EditorKind.EXPENSE to -1
        QuickKind.INCOME -> EditorKind.INCOME to 1
        QuickKind.TRANSFER -> EditorKind.TRANSFER to -1
        QuickKind.DEBT_OUT -> EditorKind.DEBT to -1
        QuickKind.DEBT_IN -> EditorKind.DEBT to 1
    }
    return EditorPrefill(
        kind = kind, sign = sign, amountMinor = amountMinor, finAccountId = accountId, secondAccountId = secondAccountId,
        categoryId = categoryId, merchant = merchant.orEmpty(), description = description, tagIds = tagIds, dateTime = at,
    )
}

/** Сохранить черновик быстрого ввода и запомнить, что пользователь в нём поправил. */
internal fun saveQuickDraft(services: AppServices, data: FinanceData, original: QuickDraft, draft: TransactionDraft) {
    val finance = services.finance
    finance.saveTransaction(data.accountId, draft.copy(source = TxnSource.QUICK_INPUT))
    val isDebt = { id: String -> data.accounts.any { it.id == id && it.type == FinAccountType.DEBT } }
    quickCorrection(original, draft, isDebt)?.let { finance.rememberQuickWord(data.accountId, original.keyWord!!, it) }
}
