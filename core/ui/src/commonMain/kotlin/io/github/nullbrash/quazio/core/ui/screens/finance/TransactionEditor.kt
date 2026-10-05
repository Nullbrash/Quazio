package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.model.Money
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.SystemBack
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.calc_on_new
import io.github.nullbrash.quazio.core.ui.res.fin_no_category
import io.github.nullbrash.quazio.core.ui.res.lock_done
import io.github.nullbrash.quazio.core.ui.res.txn_account
import io.github.nullbrash.quazio.core.ui.res.txn_add_tag
import io.github.nullbrash.quazio.core.ui.res.txn_amount
import io.github.nullbrash.quazio.core.ui.res.txn_category
import io.github.nullbrash.quazio.core.ui.res.txn_debt
import io.github.nullbrash.quazio.core.ui.res.txn_debt_sign_hint
import io.github.nullbrash.quazio.core.ui.res.txn_debt_with
import io.github.nullbrash.quazio.core.ui.res.txn_delete
import io.github.nullbrash.quazio.core.ui.res.txn_delete_confirm
import io.github.nullbrash.quazio.core.ui.res.txn_description
import io.github.nullbrash.quazio.core.ui.res.txn_description_hint
import io.github.nullbrash.quazio.core.ui.res.txn_edit
import io.github.nullbrash.quazio.core.ui.res.txn_from_account
import io.github.nullbrash.quazio.core.ui.res.txn_merchant
import io.github.nullbrash.quazio.core.ui.res.txn_new
import io.github.nullbrash.quazio.core.ui.res.txn_new_debt
import io.github.nullbrash.quazio.core.ui.res.txn_note
import io.github.nullbrash.quazio.core.ui.res.txn_note_hint
import io.github.nullbrash.quazio.core.ui.res.txn_save
import io.github.nullbrash.quazio.core.ui.res.txn_tags
import io.github.nullbrash.quazio.core.ui.res.txn_to_account
import io.github.nullbrash.quazio.feature.finance.CategoryKind
import io.github.nullbrash.quazio.feature.finance.FinAccount
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.Tag
import io.github.nullbrash.quazio.feature.finance.TagKind
import io.github.nullbrash.quazio.feature.finance.TransactionDraft
import io.github.nullbrash.quazio.feature.finance.TxnKind
import io.github.nullbrash.quazio.feature.finance.formatAmountForEdit
import io.github.nullbrash.quazio.feature.finance.formatMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import io.github.nullbrash.quazio.core.ui.res.quick_draft
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Вид операции в окне. «Долг» хранится как перевод между своим счётом и счётом-долгом:
 * «−» — деньги ушли от вас (дали в долг / вернули свой), «+» — пришли (вам вернули / заняли).
 * Порядок — доход первым (пожелание пользователя).
 */
internal enum class EditorKind(val label: StringResource) {
    INCOME(TxnKind.INCOME.label),
    EXPENSE(TxnKind.EXPENSE.label),
    TRANSFER(TxnKind.TRANSFER.label),
    DEBT(Res.string.txn_debt),
    ADJUSTMENT(TxnKind.ADJUSTMENT.label),
}

/**
 * Окно операции по образцу Money Manager пользователя.
 * [prefill] — заполненные поля (черновик быстрого ввода). С [onDraft] «Сохранить» не пишет
 * в базу, а отдаёт операцию вызывающему: он сохранит её сам или вернёт в список черновиков.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TransactionEditor(
    services: AppServices,
    data: FinanceData,
    txnId: String?,
    onClose: (changed: Boolean) -> Unit,
    prefill: EditorPrefill? = null,
    onDraft: ((TransactionDraft) -> Unit)? = null,
) {
    SystemBack { onClose(false) }
    val scope = rememberCoroutineScope()
    val finance = services.finance
    val accountId = data.accountId
    val zone = remember { currentZone() }
    var accounts by remember { mutableStateOf(data.accounts.filter { !it.archived }) }
    val ownAccounts = accounts.filter { it.type != FinAccountType.DEBT }
    val debtAccounts = accounts.filter { it.type == FinAccountType.DEBT }

    var loaded by remember { mutableStateOf(txnId == null) }
    val start = prefill ?: EditorPrefill(finAccountId = ownAccounts.firstOrNull()?.id, dateTime = localDateTime(nowMillis(), zone.id))
    var kind by remember { mutableStateOf(start.kind) }
    /** Модуль суммы в копейках; знак — отдельно ([sign]). */
    var amountMinor by remember { mutableStateOf(start.amountMinor) }
    var sign by remember { mutableStateOf(start.sign) }
    var finAccountId by remember { mutableStateOf(start.finAccountId) }
    var secondAccountId by remember { mutableStateOf(start.secondAccountId) }
    var categoryId by remember { mutableStateOf(start.categoryId) }
    var merchant by remember { mutableStateOf(start.merchant) }
    var description by remember { mutableStateOf(start.description) }
    var tagIds by remember { mutableStateOf(start.tagIds) }
    var tags by remember { mutableStateOf(data.tags) }
    var dateTime by remember { mutableStateOf(start.dateTime) }
    var note by remember { mutableStateOf(start.note) }
    var error by remember { mutableStateOf<String?>(null) }
    // Что пользователь (или разбор быстрого ввода) уже выбрал — автозаполнение это не трогает.
    var touchedAccount by remember { mutableStateOf(txnId != null || prefill?.finAccountId != null) }
    var touchedCategory by remember { mutableStateOf(txnId != null || prefill?.categoryId != null) }
    var touchedTags by remember { mutableStateOf(txnId != null || prefill?.tagIds?.isNotEmpty() == true) }
    var merchantHints by remember { mutableStateOf(emptyList<String>()) }
    var descriptionHints by remember { mutableStateOf(emptyList<String>()) }
    var pickCategory by remember { mutableStateOf(false) }
    var pickTags by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var newDebt by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var calcOnNew by remember { mutableStateOf(data.calculatorOnNew) }
    var showCalc by remember { mutableStateOf(txnId == null && prefill == null && data.calculatorOnNew) }

    fun isDebt(id: String?) = accounts.firstOrNull { it.id == id }?.type == FinAccountType.DEBT

    LaunchedEffect(txnId) {
        if (txnId == null) return@LaunchedEffect
        val p = withContext(Dispatchers.IO) { finance.transaction(txnId) }?.toPrefill(::isDebt) ?: return@LaunchedEffect onClose(false)
        kind = p.kind
        sign = p.sign
        finAccountId = p.finAccountId
        secondAccountId = p.secondAccountId
        amountMinor = p.amountMinor
        categoryId = p.categoryId
        merchant = p.merchant
        description = p.description
        tagIds = p.tagIds
        dateTime = p.dateTime
        note = p.note
        loaded = true
    }

    // Подсказки и автозаполнение — с небольшой задержкой после ввода.
    LaunchedEffect(merchant, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(300)
        merchantHints = if (merchant.isBlank()) emptyList()
        else withContext(Dispatchers.IO) { finance.merchantSuggestions(accountId, merchant) }.filter { !it.equals(merchant.trim(), true) }
    }
    LaunchedEffect(description, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(300)
        descriptionHints = if (description.isBlank()) emptyList()
        else withContext(Dispatchers.IO) { finance.descriptionSuggestions(accountId, description) }.filter { !it.equals(description.trim(), true) }
    }
    LaunchedEffect(merchant, description, loaded) {
        if (!loaded || txnId != null) return@LaunchedEffect
        delay(500)
        val s = withContext(Dispatchers.IO) { finance.suggest(accountId, merchant, description) } ?: return@LaunchedEffect
        if (!touchedCategory && categoryId == null && (s.kind == TxnKind.EXPENSE || s.kind == TxnKind.INCOME)) {
            kind = if (s.kind == TxnKind.INCOME) EditorKind.INCOME else EditorKind.EXPENSE
            sign = if (s.kind == TxnKind.INCOME) 1 else -1
            categoryId = s.categoryId
        }
        if (!touchedAccount && ownAccounts.any { it.id == s.finAccountId }) finAccountId = s.finAccountId
        if (!touchedTags && tagIds.isEmpty()) tagIds = s.tagIds
    }

    if (!loaded) return

    val signed = kind != EditorKind.TRANSFER
    fun setKind(k: EditorKind) {
        kind = k
        error = null
        when (k) {
            EditorKind.INCOME -> sign = 1
            EditorKind.EXPENSE, EditorKind.DEBT -> sign = -1
            else -> Unit
        }
        if (k == EditorKind.DEBT && !isDebt(secondAccountId)) secondAccountId = debtAccounts.firstOrNull()?.id
        if (k == EditorKind.TRANSFER && (isDebt(secondAccountId) || secondAccountId == finAccountId)) secondAccountId = null
    }
    fun flipSign() {
        sign = -sign
        if (kind == EditorKind.INCOME || kind == EditorKind.EXPENSE) kind = if (sign > 0) EditorKind.INCOME else EditorKind.EXPENSE
    }

    val save: () -> Unit = save@{
        val fields = EditorPrefill(kind, sign, amountMinor, finAccountId, secondAccountId, categoryId, merchant, description, tagIds, dateTime, note)
        val draft = when (val c = fields.check(txnId, zone, ::isDebt)) {
            is DraftCheck.Bad -> { scope.launch { error = getString(c.error) }; return@save }
            is DraftCheck.Ok -> c.draft
        }
        if (onDraft != null) return@save onDraft(draft)
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { finance.saveTransaction(accountId, draft) } }
            result.onSuccess { onClose(true) }.onFailure { error = it.message }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onClose(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(stringResource(if (onDraft != null) Res.string.quick_draft else if (txnId == null) Res.string.txn_new else Res.string.txn_edit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (txnId != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(Res.string.txn_delete)) }
            Button(onClick = save, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(Res.string.txn_save)) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { setKind(k) }, label = { Text(stringResource(k.label)) }) }
            }

            // Дата и время — над суммой, как в Money Manager.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }) { Text(dateText(dateTime)) }
                OutlinedButton(onClick = { pickTime = true }) { Text(timeText(dateTime)) }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.txn_amount), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    // Настройка прямо здесь, чтобы не ходить в «Настройки» (пожелание пользователя).
                    Text(stringResource(Res.string.calc_on_new), style = MaterialTheme.typography.labelMedium)
                    Switch(
                        checked = calcOnNew,
                        onCheckedChange = { on ->
                            calcOnNew = on
                            scope.launch { withContext(Dispatchers.IO) { finance.openCalculatorOnNew = on } }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (signed) OutlinedButton(onClick = { flipSign() }) { Text(if (sign > 0) "+" else "−", style = MaterialTheme.typography.titleLarge) }
                    OutlinedButton(onClick = { showCalc = true }, modifier = Modifier.weight(1f)) {
                        val text = amountMinor?.let { (if (signed) (if (sign > 0) "+" else "−") else "") + formatMoney(Money.rub(it)) } ?: "0 ₽"
                        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (kind == EditorKind.DEBT) Text(stringResource(Res.string.txn_debt_sign_hint), style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            // Счёт и второе поле — в одну строку.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val firstLabel = if (kind == EditorKind.TRANSFER) Res.string.txn_from_account else Res.string.txn_account
                LabeledPicker(stringResource(firstLabel), ownAccounts.firstOrNull { it.id == finAccountId }?.name ?: "—") { dismiss ->
                    ownAccounts.forEach { a -> MenuOption(a.name) { finAccountId = a.id; touchedAccount = true; dismiss() } }
                }
                when (kind) {
                    EditorKind.INCOME, EditorKind.EXPENSE -> {
                        val node = data.categories.firstOrNull { it.id == categoryId }
                        Column {
                            Text(stringResource(Res.string.txn_category), style = MaterialTheme.typography.labelLarge)
                            OutlinedButton(onClick = { pickCategory = true }) {
                                if (node != null) ColorDot(node.color, Modifier.padding(end = 8.dp))
                                Text(node?.path ?: stringResource(Res.string.fin_no_category))
                            }
                        }
                    }
                    EditorKind.TRANSFER -> LabeledPicker(stringResource(Res.string.txn_to_account), ownAccounts.firstOrNull { it.id == secondAccountId }?.name ?: "—") { dismiss ->
                        ownAccounts.filter { it.id != finAccountId }.forEach { a -> MenuOption(a.name) { secondAccountId = a.id; dismiss() } }
                    }
                    EditorKind.DEBT -> LabeledPicker(stringResource(Res.string.txn_debt_with), debtAccounts.firstOrNull { it.id == secondAccountId }?.name ?: "—") { dismiss ->
                        debtAccounts.forEach { a -> MenuOption(a.name) { secondAccountId = a.id; dismiss() } }
                        MenuOption(stringResource(Res.string.txn_new_debt)) { newDebt = true; dismiss() }
                    }
                    EditorKind.ADJUSTMENT -> Unit
                }
            }

            if (kind == EditorKind.INCOME || kind == EditorKind.EXPENSE) {
                FinField(merchant, { merchant = it }, stringResource(Res.string.txn_merchant), Modifier.fillMaxWidth(), onSubmit = save)
                Hints(merchantHints) { merchant = it; merchantHints = emptyList() }
            }
            Column {
                FinField(description, { description = it }, stringResource(Res.string.txn_description), Modifier.fillMaxWidth(), onSubmit = save)
                Text(stringResource(Res.string.txn_description_hint), style = MaterialTheme.typography.bodySmall)
            }
            Hints(descriptionHints) { description = it; descriptionHints = emptyList() }

            Column {
                Text(stringResource(Res.string.txn_tags), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.filter { it.id in tagIds }.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { tagIds = tagIds - tag.id; touchedTags = true },
                            label = { Text("${tag.name} · ${stringResource(tag.kind.label)}") },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                        )
                    }
                    SuggestionChip(onClick = { pickTags = true }, label = { Text(stringResource(Res.string.txn_add_tag)) })
                }
            }
            Column {
                FinField(note, { note = it }, stringResource(Res.string.txn_note), Modifier.fillMaxWidth(), singleLine = false)
                Text(stringResource(Res.string.txn_note_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showCalc) {
        CalculatorDialog(
            initial = amountMinor?.let { (if (signed && sign < 0) "−" else "") + formatAmountForEdit(it) }.orEmpty(),
            allowNegative = false,
            allowSign = signed,
            onDone = { minor, explicit ->
                amountMinor = kotlin.math.abs(minor)
                // «+5 000» / «−5 000» — знак выбирает направление (у дохода/расхода — и сам вид).
                val newSign = explicit ?: if (minor < 0) -1 else null
                if (signed && newSign != null && newSign != sign) flipSign()
                error = null
                showCalc = false
            },
            onDismiss = { showCalc = false },
        )
    }
    if (pickCategory) {
        CategoryPickerDialog(
            categories = data.categories.filter { it.kind == if (kind == EditorKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE },
            selectedId = categoryId,
            onPick = { categoryId = it; touchedCategory = true; pickCategory = false },
            onDismiss = { pickCategory = false },
        )
    }
    if (pickTags) {
        TagPickerDialog(
            tags = tags,
            selected = tagIds,
            onToggle = { id -> tagIds = if (id in tagIds) tagIds - id else tagIds + id; touchedTags = true },
            onCreate = { name, tagKind ->
                scope.launch {
                    val tag: Tag = withContext(Dispatchers.IO) { finance.createTag(accountId, name, tagKind) }
                    if (tags.none { it.id == tag.id }) tags = tags + tag
                    tagIds = tagIds + tag.id
                    touchedTags = true
                }
            },
            onDismiss = { pickTags = false },
        )
    }
    if (newDebt) {
        NewDebtDialog(
            counterparties = tags.filter { it.kind == TagKind.PERSON || it.kind == TagKind.ORGANIZATION },
            onDismiss = { newDebt = false },
            onCreate = { name, tagKind ->
                newDebt = false
                scope.launch {
                    val created: FinAccount = withContext(Dispatchers.IO) {
                        val tag = finance.createTag(accountId, name, tagKind)
                        val id = finance.createAccount(accountId, tag.name, FinAccountType.DEBT, personTagId = tag.id)
                        finance.accounts(accountId).first { it.id == id }
                    }
                    accounts = accounts + created
                    secondAccountId = created.id
                }
            },
        )
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = toMillis(LocalDateTime(dateTime.date, LocalTime(0, 0)), kotlinx.datetime.TimeZone.UTC))
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utcMidnight ->
                        val picked: LocalDate = localDateTime(utcMidnight, "UTC").date
                        dateTime = LocalDateTime(picked, dateTime.time)
                    }
                    pickDate = false
                }) { Text(stringResource(Res.string.lock_done)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(Res.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = dateTime.hour, initialMinute = dateTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    dateTime = LocalDateTime(dateTime.date, LocalTime(state.hour, state.minute))
                    pickTime = false
                }) { Text(stringResource(Res.string.lock_done)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(Res.string.action_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
    if (confirmDelete && txnId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(Res.string.txn_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { finance.deleteTransaction(txnId) }
                        onClose(true)
                    }
                }) { Text(stringResource(Res.string.txn_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hints(hints: List<String>, onPick: (String) -> Unit) {
    if (hints.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        hints.take(5).forEach { h -> SuggestionChip(onClick = { onPick(h) }, label = { Text(h) }) }
    }
}
