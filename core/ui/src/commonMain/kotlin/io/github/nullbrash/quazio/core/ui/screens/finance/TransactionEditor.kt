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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.calc_on_new
import io.github.nullbrash.quazio.core.ui.res.fin_no_category
import io.github.nullbrash.quazio.core.ui.res.lock_done
import io.github.nullbrash.quazio.core.ui.res.txn_account
import io.github.nullbrash.quazio.core.ui.res.txn_add_tag
import io.github.nullbrash.quazio.core.ui.res.txn_amount
import io.github.nullbrash.quazio.core.ui.res.txn_amount_adjustment
import io.github.nullbrash.quazio.core.ui.res.txn_bad_amount
import io.github.nullbrash.quazio.core.ui.res.txn_category
import io.github.nullbrash.quazio.core.ui.res.txn_delete
import io.github.nullbrash.quazio.core.ui.res.txn_delete_confirm
import io.github.nullbrash.quazio.core.ui.res.txn_description
import io.github.nullbrash.quazio.core.ui.res.txn_edit
import io.github.nullbrash.quazio.core.ui.res.txn_merchant
import io.github.nullbrash.quazio.core.ui.res.txn_need_account
import io.github.nullbrash.quazio.core.ui.res.txn_need_to_account
import io.github.nullbrash.quazio.core.ui.res.txn_new
import io.github.nullbrash.quazio.core.ui.res.txn_note
import io.github.nullbrash.quazio.core.ui.res.txn_save
import io.github.nullbrash.quazio.core.ui.res.txn_tags
import io.github.nullbrash.quazio.core.ui.res.txn_to_account
import io.github.nullbrash.quazio.feature.finance.CategoryKind
import io.github.nullbrash.quazio.feature.finance.Tag
import io.github.nullbrash.quazio.feature.finance.TransactionDraft
import io.github.nullbrash.quazio.feature.finance.TxnKind
import io.github.nullbrash.quazio.feature.finance.formatAmountForEdit
import io.github.nullbrash.quazio.feature.finance.parseAmountMinor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource

/** Окно операции по образцу Wallet пользователя: тип, сумма, счёт, категория, магазин, описание, метки, дата, заметка. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TransactionEditor(services: AppServices, data: FinanceData, txnId: String?, onClose: (changed: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val finance = services.finance
    val accountId = data.accountId
    val zone = remember { currentZone() }
    val activeAccounts = data.accounts.filter { !it.archived }

    var loaded by remember { mutableStateOf(txnId == null) }
    var kind by remember { mutableStateOf(TxnKind.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var finAccountId by remember { mutableStateOf(activeAccounts.firstOrNull()?.id) }
    var toFinAccountId by remember { mutableStateOf<String?>(null) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var merchant by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tagIds by remember { mutableStateOf(setOf<String>()) }
    var tags by remember { mutableStateOf(data.tags) }
    var dateTime by remember { mutableStateOf(localDateTime(nowMillis(), zone.id)) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Что пользователь уже выбрал сам — автозаполнение это не трогает.
    var touchedAccount by remember { mutableStateOf(txnId != null) }
    var touchedCategory by remember { mutableStateOf(txnId != null) }
    var touchedTags by remember { mutableStateOf(txnId != null) }
    var merchantHints by remember { mutableStateOf(emptyList<String>()) }
    var descriptionHints by remember { mutableStateOf(emptyList<String>()) }
    var pickCategory by remember { mutableStateOf(false) }
    var pickTags by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Новая операция начинается с суммы — калькулятор сразу (как в Wallet по нажатию на сумму).
    var calcOnNew by remember { mutableStateOf(data.calculatorOnNew) }
    var showCalc by remember { mutableStateOf(txnId == null && data.calculatorOnNew) }

    LaunchedEffect(txnId) {
        if (txnId == null) return@LaunchedEffect
        val d = withContext(Dispatchers.IO) { finance.transaction(txnId) } ?: return@LaunchedEffect onClose(false)
        kind = d.kind
        amountText = formatAmountForEdit(d.amountMinor)
        finAccountId = d.finAccountId
        toFinAccountId = d.toFinAccountId
        categoryId = d.categoryId
        merchant = d.merchantName.orEmpty()
        description = d.description
        tagIds = d.tagIds
        dateTime = localDateTime(d.occurredAt, d.timeZone)
        note = d.note
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
            kind = s.kind
            categoryId = s.categoryId
        }
        if (!touchedAccount && activeAccounts.any { it.id == s.finAccountId }) finAccountId = s.finAccountId
        if (!touchedTags && tagIds.isEmpty()) tagIds = s.tagIds
    }

    if (!loaded) return

    val badAmount = stringResource(Res.string.txn_bad_amount)
    val needAccount = stringResource(Res.string.txn_need_account)
    val needToAccount = stringResource(Res.string.txn_need_to_account)
    val save: () -> Unit = save@{
        val minor = parseAmountMinor(amountText)
        val okAmount = minor != null && (if (kind == TxnKind.ADJUSTMENT) minor != 0L else minor > 0)
        if (!okAmount) { error = badAmount; return@save }
        val from = finAccountId ?: run { error = needAccount; return@save }
        if (kind == TxnKind.TRANSFER && (toFinAccountId == null || toFinAccountId == from)) { error = needToAccount; return@save }
        val draft = TransactionDraft(
            id = txnId, kind = kind, amountMinor = minor!!, finAccountId = from, toFinAccountId = toFinAccountId,
            categoryId = categoryId, merchantName = merchant, tagIds = tagIds, occurredAt = toMillis(dateTime, zone),
            timeZone = zone.id, description = description, note = note,
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { finance.saveTransaction(accountId, draft) } }
            result.onSuccess { onClose(true) }.onFailure { error = it.message }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onClose(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(stringResource(if (txnId == null) Res.string.txn_new else Res.string.txn_edit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (txnId != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(Res.string.txn_delete)) }
            Button(onClick = save, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(Res.string.txn_save)) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TxnKind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k; error = null }, label = { Text(stringResource(k.label)) })
                }
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (kind == TxnKind.ADJUSTMENT) Res.string.txn_amount_adjustment else Res.string.txn_amount), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
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
                OutlinedButton(onClick = { showCalc = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(amountText.ifEmpty { "0" } + " ₽", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            LabeledPicker(stringResource(Res.string.txn_account), activeAccounts.firstOrNull { it.id == finAccountId }?.name ?: "—") { dismiss ->
                activeAccounts.forEach { a -> MenuOption(a.name) { finAccountId = a.id; touchedAccount = true; dismiss() } }
            }
            if (kind == TxnKind.TRANSFER) {
                LabeledPicker(stringResource(Res.string.txn_to_account), activeAccounts.firstOrNull { it.id == toFinAccountId }?.name ?: "—") { dismiss ->
                    activeAccounts.filter { it.id != finAccountId }.forEach { a -> MenuOption(a.name) { toFinAccountId = a.id; dismiss() } }
                }
            }
            if (kind == TxnKind.EXPENSE || kind == TxnKind.INCOME) {
                val node = data.categories.firstOrNull { it.id == categoryId }
                Column {
                    Text(stringResource(Res.string.txn_category), style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { pickCategory = true }) {
                        if (node != null) ColorDot(node.color, Modifier.padding(end = 8.dp))
                        Text(node?.path ?: stringResource(Res.string.fin_no_category))
                    }
                }
            }

            FinField(merchant, { merchant = it }, stringResource(Res.string.txn_merchant), Modifier.fillMaxWidth(), onSubmit = save)
            Hints(merchantHints) { merchant = it; merchantHints = emptyList() }
            FinField(description, { description = it }, stringResource(Res.string.txn_description), Modifier.fillMaxWidth(), onSubmit = save)
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }) { Text(dateText(dateTime)) }
                OutlinedButton(onClick = { pickTime = true }) { Text(timeText(dateTime)) }
            }
            FinField(note, { note = it }, stringResource(Res.string.txn_note), Modifier.fillMaxWidth(), singleLine = false)
        }
    }

    if (showCalc) {
        CalculatorDialog(
            initial = amountText,
            allowNegative = kind == TxnKind.ADJUSTMENT,
            onDone = { amountText = formatAmountForEdit(it); error = null; showCalc = false },
            onDismiss = { showCalc = false },
        )
    }
    if (pickCategory) {
        CategoryPickerDialog(
            categories = data.categories.filter { it.kind == if (kind == TxnKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE },
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
