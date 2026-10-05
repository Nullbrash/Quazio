package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.nullbrash.quazio.core.ui.res.quick_balance_ask
import io.github.nullbrash.quazio.core.ui.res.quick_balance_fix
import io.github.nullbrash.quazio.core.ui.res.quick_balance_same
import io.github.nullbrash.quazio.core.ui.res.quick_balance_will
import io.github.nullbrash.quazio.core.ui.res.quick_check_bad
import io.github.nullbrash.quazio.core.ui.res.quick_check_ok
import io.github.nullbrash.quazio.core.ui.res.quick_create_savings
import io.github.nullbrash.quazio.core.ui.res.quick_edit
import io.github.nullbrash.quazio.core.ui.res.quick_found
import io.github.nullbrash.quazio.core.ui.res.quick_make_op
import io.github.nullbrash.quazio.core.ui.res.quick_ops
import io.github.nullbrash.quazio.core.ui.res.quick_pick_account
import io.github.nullbrash.quazio.core.ui.res.quick_save
import io.github.nullbrash.quazio.core.ui.res.quick_savings_name
import io.github.nullbrash.quazio.core.ui.res.quick_skip_date
import io.github.nullbrash.quazio.core.ui.res.quick_skip_header
import io.github.nullbrash.quazio.core.ui.res.quick_skip_negated
import io.github.nullbrash.quazio.core.ui.res.quick_skip_unknown
import io.github.nullbrash.quazio.core.ui.res.quick_title
import io.github.nullbrash.quazio.core.ui.res.quick_word_account
import io.github.nullbrash.quazio.core.ui.res.quick_word_debt
import io.github.nullbrash.quazio.core.ui.res.quick_word_from
import io.github.nullbrash.quazio.core.ui.res.quick_word_to
import io.github.nullbrash.quazio.engine.quickinput.QuickDraft
import io.github.nullbrash.quazio.engine.quickinput.QuickKind
import io.github.nullbrash.quazio.engine.quickinput.QuickLine
import io.github.nullbrash.quazio.engine.quickinput.SkipReason
import io.github.nullbrash.quazio.feature.finance.FinAccount
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.TxnKind
import io.github.nullbrash.quazio.feature.finance.formatMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Создать счёт «Накопления» и передать его id. */
private typealias CreateSavings = (onCreated: (String) -> Unit) -> Unit

private class ReviewOp(val original: QuickDraft, prefill: EditorPrefill) {
    var prefill by mutableStateOf(prefill)
    var include by mutableStateOf(true)
}

/**
 * Что нашлось в тексте: черновики операций (счёт выбирается прямо в карточке, нажатие —
 * окно операции), пропущенные строки с причиной, проверка итога «=», остаток по записи
 * со сравнением и поправкой баланса. Ничего не сохраняется до «Сохранить».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickReviewScreen(
    services: AppServices,
    data: FinanceData,
    lines: List<QuickLine>,
    prefills: List<EditorPrefill?>,
    onClose: (changed: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val zone = remember { currentZone() }
    val now = remember { localDateTime(nowMillis(), zone.id) }
    // По индексам строк; пропущенная строка получает операцию, если её «сделали операцией».
    val ops = remember {
        mutableStateListOf<ReviewOp?>().apply {
            addAll(lines.mapIndexed { i, line -> (line as? QuickLine.Operation)?.let { ReviewOp(it.draft, prefills[i]!!) } })
        }
    }
    // Счета меняются, если «Накопления» создали прямо отсюда.
    var accounts by remember { mutableStateOf(data.accounts.filter { !it.archived }) }
    var accountCreated by remember { mutableStateOf(false) }
    val balanceAccount = remember { mutableStateMapOf<Int, String>() }
    val adjust = remember { mutableStateMapOf<Int, Boolean>() }
    var editing by remember { mutableStateOf<Int?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val current = data.copy(accounts = accounts)
    fun isDebt(id: String?) = accounts.any { it.id == id && it.type == FinAccountType.DEBT }
    val own = accounts.filter { it.type != FinAccountType.DEBT }
    val debts = accounts.filter { it.type == FinAccountType.DEBT }
    val categoryPath = data.categories.associate { it.id to it.path }
    val savingsName = stringResource(Res.string.quick_savings_name)
    val createSavings: CreateSavings? = if (accounts.any { it.type == FinAccountType.SAVINGS }) null else { onCreated ->
        scope.launch {
            val (id, list) = withContext(Dispatchers.IO) {
                val id = services.finance.createAccount(data.accountId, savingsName, FinAccountType.SAVINGS)
                id to services.finance.accounts(data.accountId).filter { !it.archived }
            }
            accounts = list
            accountCreated = true
            onCreated(id)
        }
    }

    /** Пропущенная строка → окно операции с тем, что из неё известно: сумма, знак, текст. */
    fun fromSkipped(line: QuickLine.Skipped): EditorPrefill {
        val signed = line.signedMinor
        val income = signed != null && signed > 0
        val now = localDateTime(nowMillis(), zone.id)
        return EditorPrefill(
            kind = if (income) EditorKind.INCOME else EditorKind.EXPENSE, sign = if (income) 1 else -1,
            amountMinor = signed?.let { kotlin.math.abs(it) }, finAccountId = own.firstOrNull()?.id,
            description = line.text.lineSequence().first().trim().trimEnd('.'),
            dateTime = LocalDateTime(now.date, LocalTime(now.hour, now.minute)),
        )
    }

    editing?.let { index ->
        val existing = ops[index]
        TransactionEditor(
            services = services, data = current, txnId = null,
            prefill = existing?.prefill ?: fromSkipped(lines[index] as QuickLine.Skipped),
            onDraft = { draft ->
                val p = draft.toPrefill(::isDebt)
                if (existing != null) existing.prefill = p
                // Сделано вручную, не разбором: запоминать для слова нечего (keyWord = null).
                else ops[index] = ReviewOp(QuickDraft(QuickKind.EXPENSE, 1, now.date), p)
                editing = null
            },
            onClose = { editing = null },
        )
        return
    }

    val checked = ops.map { op -> op?.takeIf { it.include }?.prefill?.check(null, zone, ::isDebt) }
    val valid = checked.mapNotNull { (it as? DraftCheck.Ok)?.draft }
    val count = checked.count { it != null }
    /** О каком счёте «Остаток»: выбран здесь, понят из текста или счёт последней операции выше. */
    fun accountOfBalance(i: Int, line: QuickLine.Balance): String? = balanceAccount[i] ?: line.accountId
        ?: (i - 1 downTo 0).firstNotNullOfOrNull { ops[it]?.prefill?.finAccountId }
    fun predicted(accountId: String): Long {
        val now = accounts.firstOrNull { it.id == accountId }?.balance?.minor ?: 0
        return now + valid.sumOf { d ->
            when (d.kind) {
                TxnKind.EXPENSE -> if (d.finAccountId == accountId) -d.amountMinor else 0
                TxnKind.INCOME -> if (d.finAccountId == accountId) d.amountMinor else 0
                TxnKind.TRANSFER -> (if (d.toFinAccountId == accountId) d.amountMinor else 0) - (if (d.finAccountId == accountId) d.amountMinor else 0)
                TxnKind.ADJUSTMENT -> if (d.finAccountId == accountId) d.amountMinor else 0
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onClose(accountCreated) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(stringResource(Res.string.quick_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(
                enabled = count > 0 && checked.none { it is DraftCheck.Bad },
                modifier = Modifier.padding(end = 8.dp),
                onClick = {
                    scope.launch {
                        val adjustText = getString(Res.string.adj_balance)
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                ops.forEachIndexed { i, op ->
                                    val draft = (checked[i] as? DraftCheck.Ok)?.draft ?: return@forEachIndexed
                                    saveQuickDraft(services, current, op!!.original, draft)
                                }
                                lines.forEachIndexed { i, line ->
                                    if (line !is QuickLine.Balance || adjust[i] != true) return@forEachIndexed
                                    val account = accountOfBalance(i, line) ?: return@forEachIndexed
                                    services.finance.adjustBalanceTo(data.accountId, account, line.amountMinor, adjustText, nowMillis(), zone.id)
                                }
                            }
                        }
                        result.onSuccess { onClose(true) }.onFailure { saveError = it.message }
                    }
                },
            ) { Text(stringResource(Res.string.quick_save, count)) }
        }
        Text(
            stringResource(Res.string.quick_found, pluralStringResource(Res.plurals.quick_ops, ops.count { it != null }, ops.count { it != null })),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(lines) { i, line ->
                val op = ops[i]
                when {
                    op != null -> {
                        OperationCard(
                            source = line.text, prefill = op.prefill, include = op.include,
                            own = own, debts = debts, categoryPath = categoryPath, createSavings = createSavings,
                            onInclude = { op.include = it },
                            onChange = { op.prefill = it },
                            onEdit = { editing = i },
                        )
                    }
                    line is QuickLine.Total -> {
                        val written = line.text.trim()
                        val ok = line.matches
                        Text(
                            if (ok) stringResource(Res.string.quick_check_ok, written)
                            else stringResource(Res.string.quick_check_bad, written, signed(line.actualMinor)),
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    line is QuickLine.Balance -> {
                        val account = accountOfBalance(i, line)
                        BalanceCard(
                            amount = line.amountMinor, account = accounts.firstOrNull { it.id == account },
                            predicted = account?.let { predicted(it) }, own = own, createSavings = createSavings,
                            adjust = adjust[i] == true, onAdjust = { adjust[i] = it }, onAccount = { balanceAccount[i] = it },
                        )
                    }
                    line is QuickLine.Skipped -> Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(line.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            stringResource(
                                when (line.reason) {
                                    SkipReason.NEGATED -> Res.string.quick_skip_negated
                                    SkipReason.HEADER -> Res.string.quick_skip_header
                                    SkipReason.DATE -> Res.string.quick_skip_date
                                    SkipReason.UNKNOWN -> Res.string.quick_skip_unknown
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Решение разбора можно оспорить: строка становится операцией, заполненной из текста.
                        TextButton(onClick = { editing = i }) { Text(stringResource(Res.string.quick_make_op)) }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

private fun signed(minor: Long) = (if (minor > 0) "+" else "") + formatMoney(Money.rub(minor))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OperationCard(
    source: String,
    prefill: EditorPrefill,
    include: Boolean,
    own: List<FinAccount>,
    debts: List<FinAccount>,
    categoryPath: Map<String, String>,
    createSavings: CreateSavings?,
    onInclude: (Boolean) -> Unit,
    onChange: (EditorPrefill) -> Unit,
    onEdit: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Checkbox(checked = include, onCheckedChange = onInclude)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            val amount = prefill.amountMinor?.let { formatMoney(Money.rub(it)) } ?: "—"
            Text("${stringResource(prefill.kind.label)} $amount · ${dayMonth(prefill.dateTime)}", style = MaterialTheme.typography.titleMedium)
            val pickFirst: (String) -> Unit = { onChange(prefill.copy(finAccountId = it)) }
            val pickSecond: (String) -> Unit = { onChange(prefill.copy(secondAccountId = it)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                when (prefill.kind) {
                    EditorKind.TRANSFER -> {
                        Word(stringResource(Res.string.quick_word_from))
                        AccountChip(prefill.finAccountId, own.filter { it.id != prefill.secondAccountId }, createSavings, pickFirst)
                        Word(stringResource(Res.string.quick_word_to))
                        AccountChip(prefill.secondAccountId, own.filter { it.id != prefill.finAccountId }, null, pickSecond)
                    }
                    EditorKind.DEBT -> {
                        Word(stringResource(Res.string.quick_word_account))
                        AccountChip(prefill.finAccountId, own, null, pickFirst)
                        Word(stringResource(Res.string.quick_word_debt))
                        AccountChip(prefill.secondAccountId, debts, null, pickSecond)
                    }
                    else -> {
                        Word(stringResource(Res.string.quick_word_account))
                        AccountChip(prefill.finAccountId, own, createSavings, pickFirst)
                        if (prefill.kind == EditorKind.EXPENSE || prefill.kind == EditorKind.INCOME) {
                            Word("· " + (prefill.categoryId?.let { categoryPath[it] } ?: stringResource(Res.string.fin_no_category)))
                        }
                    }
                }
            }
            val extra = listOf(prefill.merchant, prefill.description).filter { it.isNotBlank() }.joinToString(" · ")
            if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onEdit) { Text(stringResource(Res.string.quick_edit)) }
    }
}

@Composable
private fun Word(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 14.dp))
}

/** Счёт прямо в карточке: не выбран — красным «выбрать счёт»; в списке может быть «+ Создать „Накопления“». */
@Composable
private fun AccountChip(selected: String?, options: List<FinAccount>, createSavings: CreateSavings?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = options.firstOrNull { it.id == selected }?.name
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(name ?: stringResource(Res.string.quick_pick_account)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            colors = if (name == null) AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error) else AssistChipDefaults.assistChipColors(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { a ->
                DropdownMenuItem(text = { Text(a.name) }, onClick = { open = false; onPick(a.id) })
            }
            if (createSavings != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.quick_create_savings)) },
                    onClick = { open = false; createSavings(onPick) },
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(
    amount: Long,
    account: FinAccount?,
    predicted: Long?,
    own: List<FinAccount>,
    createSavings: CreateSavings?,
    adjust: Boolean,
    onAdjust: (Boolean) -> Unit,
    onAccount: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.quick_balance_ask, formatMoney(Money.rub(amount))), modifier = Modifier.weight(1f, fill = false))
            AccountChip(account?.id, own, createSavings, onAccount)
        }
        if (account == null || predicted == null) return@Column
        Text(stringResource(Res.string.quick_balance_will, account.name, formatMoney(Money.rub(predicted))), style = MaterialTheme.typography.bodyMedium)
        val diff = amount - predicted
        if (diff == 0L) {
            Text(stringResource(Res.string.quick_balance_same), color = MaterialTheme.colorScheme.primary)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onAdjust(!adjust) }) {
                Checkbox(checked = adjust, onCheckedChange = onAdjust)
                Text(stringResource(Res.string.quick_balance_fix, signed(diff)))
            }
        }
    }
}
