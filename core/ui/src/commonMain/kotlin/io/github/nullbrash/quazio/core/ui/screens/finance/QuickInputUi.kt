package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.model.Money
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.adj_balance
import io.github.nullbrash.quazio.core.ui.res.fin_no_category
import io.github.nullbrash.quazio.core.ui.res.quick_adjust
import io.github.nullbrash.quazio.core.ui.res.quick_balance
import io.github.nullbrash.quazio.core.ui.res.quick_balance_ok
import io.github.nullbrash.quazio.core.ui.res.quick_balance_unknown
import io.github.nullbrash.quazio.core.ui.res.quick_save
import io.github.nullbrash.quazio.core.ui.res.quick_skipped_hint
import io.github.nullbrash.quazio.core.ui.res.quick_title
import io.github.nullbrash.quazio.core.ui.res.quick_total_bad
import io.github.nullbrash.quazio.core.ui.res.quick_total_ok
import io.github.nullbrash.quazio.core.ui.res.txn_bad_amount
import io.github.nullbrash.quazio.core.ui.res.txn_need_account
import io.github.nullbrash.quazio.core.ui.res.txn_need_debt
import io.github.nullbrash.quazio.core.ui.res.txn_need_to_account
import io.github.nullbrash.quazio.engine.quickinput.QuickDraft
import io.github.nullbrash.quazio.engine.quickinput.QuickKind
import io.github.nullbrash.quazio.engine.quickinput.QuickLine
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.TransactionDraft
import io.github.nullbrash.quazio.feature.finance.TxnKind
import io.github.nullbrash.quazio.feature.finance.TxnSource
import io.github.nullbrash.quazio.feature.finance.formatMoney
import io.github.nullbrash.quazio.feature.finance.quickCorrection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

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

private class ReviewOp(val original: QuickDraft, prefill: EditorPrefill) {
    var prefill by mutableStateOf(prefill)
    var include by mutableStateOf(true)
}

/** Список черновиков поста: поправить или выкинуть каждый, сверки, корректировка остатка, «Сохранить все». */
@Composable
internal fun QuickReviewScreen(
    services: AppServices,
    data: FinanceData,
    lines: List<QuickLine>,
    prefills: List<EditorPrefill?>,
    onClose: (saved: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val zone = remember { currentZone() }
    val ops = remember { lines.mapIndexed { i, line -> (line as? QuickLine.Operation)?.let { ReviewOp(it.draft, prefills[i]!!) } } }
    val adjust = remember { mutableStateMapOf<Int, Boolean>() }
    var editing by remember { mutableStateOf<Int?>(null) }
    val errors = remember { mutableStateListOf<Int>() }
    var saveError by remember { mutableStateOf<String?>(null) }
    fun isDebt(id: String?) = data.accounts.any { it.id == id && it.type == FinAccountType.DEBT }
    val accountName = data.accounts.associate { it.id to it.name }
    val categoryPath = data.categories.associate { it.id to it.path }

    editing?.let { index ->
        val op = ops[index]!!
        TransactionEditor(
            services = services, data = data, txnId = null, prefill = op.prefill,
            onDraft = { draft ->
                op.prefill = draft.toPrefill(::isDebt)
                errors.remove(index)
                editing = null
            },
            onClose = { editing = null },
        )
        return
    }

    val checked = ops.map { op -> op?.takeIf { it.include }?.prefill?.check(null, zone, ::isDebt) }
    val valid = checked.mapNotNull { (it as? DraftCheck.Ok)?.draft }
    fun predicted(accountId: String): Long {
        val current = data.accounts.firstOrNull { it.id == accountId }?.balance?.minor ?: 0
        return current + valid.sumOf { d ->
            when (d.kind) {
                TxnKind.EXPENSE -> if (d.finAccountId == accountId) -d.amountMinor else 0
                TxnKind.INCOME -> if (d.finAccountId == accountId) d.amountMinor else 0
                TxnKind.TRANSFER -> (if (d.toFinAccountId == accountId) d.amountMinor else 0) - (if (d.finAccountId == accountId) d.amountMinor else 0)
                TxnKind.ADJUSTMENT -> if (d.finAccountId == accountId) d.amountMinor else 0
            }
        }
    }
    val count = checked.count { it != null }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onClose(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(stringResource(Res.string.quick_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(
                enabled = count > 0 || adjust.values.any { it },
                modifier = Modifier.padding(end = 8.dp),
                onClick = {
                    errors.clear()
                    checked.forEachIndexed { i, c -> if (c is DraftCheck.Bad) errors += i }
                    if (errors.isNotEmpty()) return@Button
                    scope.launch {
                        val adjustText = getString(Res.string.adj_balance)
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                ops.forEachIndexed { i, op ->
                                    val draft = (checked[i] as? DraftCheck.Ok)?.draft ?: return@forEachIndexed
                                    saveQuickDraft(services, data, op!!.original, draft)
                                }
                                lines.forEachIndexed { i, line ->
                                    if (line is QuickLine.Balance && line.accountId != null && adjust[i] == true) {
                                        services.finance.adjustBalanceTo(data.accountId, line.accountId!!, line.amountMinor, adjustText, nowMillis(), zone.id)
                                    }
                                }
                            }
                        }
                        result.onSuccess { onClose(true) }.onFailure { saveError = it.message }
                    }
                },
            ) { Text(stringResource(Res.string.quick_save, count)) }
        }
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (lines.any { it is QuickLine.Skipped }) {
            Text(
                stringResource(Res.string.quick_skipped_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(lines) { i, line ->
                when (line) {
                    is QuickLine.Operation -> {
                        val op = ops[i]!!
                        OperationRow(
                            source = line.text, prefill = op.prefill, include = op.include,
                            error = (checked[i] as? DraftCheck.Bad)?.error,
                            accountName = accountName, categoryPath = categoryPath,
                            onInclude = { op.include = it },
                            onEdit = { editing = i },
                        )
                    }
                    is QuickLine.Total -> {
                        val text = if (line.matches) stringResource(Res.string.quick_total_ok, formatMoney(Money.rub(line.expectedMinor)))
                        else stringResource(Res.string.quick_total_bad, formatMoney(Money.rub(line.expectedMinor)), formatMoney(Money.rub(line.actualMinor)))
                        Text(
                            text, color = if (line.matches) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    is QuickLine.Balance -> BalanceRow(
                        line = line, accountName = line.accountId?.let { accountName[it] },
                        predicted = line.accountId?.let { predicted(it) }, adjust = adjust[i] == true,
                        onAdjust = { adjust[i] = it },
                    )
                    is QuickLine.Skipped -> Text(
                        line.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun OperationRow(
    source: String,
    prefill: EditorPrefill,
    include: Boolean,
    error: StringResource?,
    accountName: Map<String, String>,
    categoryPath: Map<String, String>,
    onInclude: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = include, onCheckedChange = onInclude)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            val amount = prefill.amountMinor?.let { formatMoney(Money.rub(if (prefill.kind == EditorKind.TRANSFER) it else it * prefill.sign)) } ?: "—"
            Text("${dateText(prefill.dateTime)} · ${stringResource(prefill.kind.label)} · $amount", style = MaterialTheme.typography.bodyLarge)
            val from = prefill.finAccountId?.let { accountName[it] } ?: "?"
            val second = prefill.secondAccountId?.let { accountName[it] } ?: "?"
            val where = when (prefill.kind) {
                EditorKind.TRANSFER -> "$from → $second"
                EditorKind.DEBT -> if (prefill.sign < 0) "$from → $second" else "$second → $from"
                EditorKind.ADJUSTMENT -> from
                else -> "$from · " + (prefill.categoryId?.let { categoryPath[it] } ?: stringResource(Res.string.fin_no_category))
            }
            val extra = listOf(prefill.merchant, prefill.description).filter { it.isNotBlank() }.joinToString(" · ")
            Text(if (extra.isEmpty()) where else "$where · $extra", style = MaterialTheme.typography.bodyMedium)
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun BalanceRow(line: QuickLine.Balance, accountName: String?, predicted: Long?, adjust: Boolean, onAdjust: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val record = formatMoney(Money.rub(line.amountMinor))
        if (accountName == null || predicted == null) {
            Text(stringResource(Res.string.quick_balance_unknown, record))
            return
        }
        Text(stringResource(Res.string.quick_balance, record, accountName, formatMoney(Money.rub(predicted))))
        val diff = line.amountMinor - predicted
        if (diff == 0L) {
            Text(stringResource(Res.string.quick_balance_ok), color = MaterialTheme.colorScheme.primary)
        } else {
            val signed = (if (diff > 0) "+" else "") + formatMoney(Money.rub(diff))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onAdjust(!adjust) }) {
                Checkbox(checked = adjust, onCheckedChange = onAdjust)
                Text(stringResource(Res.string.quick_adjust, signed))
            }
        }
    }
}
