package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.model.Money
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.SystemBack
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.action_delete
import io.github.nullbrash.quazio.core.ui.res.fin_no_category
import io.github.nullbrash.quazio.core.ui.res.lock_done
import io.github.nullbrash.quazio.core.ui.res.rec_add
import io.github.nullbrash.quazio.core.ui.res.rec_auto
import io.github.nullbrash.quazio.core.ui.res.rec_auto_hint
import io.github.nullbrash.quazio.core.ui.res.rec_custom
import io.github.nullbrash.quazio.core.ui.res.rec_delete_confirm
import io.github.nullbrash.quazio.core.ui.res.rec_edit
import io.github.nullbrash.quazio.core.ui.res.rec_empty
import io.github.nullbrash.quazio.core.ui.res.rec_finished
import io.github.nullbrash.quazio.core.ui.res.rec_half_year
import io.github.nullbrash.quazio.core.ui.res.rec_make_sub
import io.github.nullbrash.quazio.core.ui.res.rec_mode_ask
import io.github.nullbrash.quazio.core.ui.res.rec_mode_auto
import io.github.nullbrash.quazio.core.ui.res.rec_monthly
import io.github.nullbrash.quazio.core.ui.res.rec_name
import io.github.nullbrash.quazio.core.ui.res.rec_need_name
import io.github.nullbrash.quazio.core.ui.res.rec_new
import io.github.nullbrash.quazio.core.ui.res.rec_next
import io.github.nullbrash.quazio.core.ui.res.rec_next_date
import io.github.nullbrash.quazio.core.ui.res.rec_no_end
import io.github.nullbrash.quazio.core.ui.res.rec_paused
import io.github.nullbrash.quazio.core.ui.res.rec_pending_title
import io.github.nullbrash.quazio.core.ui.res.rec_record
import io.github.nullbrash.quazio.core.ui.res.rec_skip
import io.github.nullbrash.quazio.core.ui.res.rec_quarterly
import io.github.nullbrash.quazio.core.ui.res.rec_repeat
import io.github.nullbrash.quazio.core.ui.res.rec_short_last
import io.github.nullbrash.quazio.core.ui.res.rec_short_last_feb
import io.github.nullbrash.quazio.core.ui.res.rec_short_need
import io.github.nullbrash.quazio.core.ui.res.rec_short_next
import io.github.nullbrash.quazio.core.ui.res.rec_short_next_feb
import io.github.nullbrash.quazio.core.ui.res.rec_short_skip
import io.github.nullbrash.quazio.core.ui.res.rec_short_skip_feb
import io.github.nullbrash.quazio.core.ui.res.rec_short_title
import io.github.nullbrash.quazio.core.ui.res.rec_short_title_feb
import io.github.nullbrash.quazio.core.ui.res.rec_title
import io.github.nullbrash.quazio.core.ui.res.rec_until
import io.github.nullbrash.quazio.core.ui.res.rec_weekly
import io.github.nullbrash.quazio.core.ui.res.rec_yearly
import io.github.nullbrash.quazio.core.ui.res.txn_account
import io.github.nullbrash.quazio.core.ui.res.txn_amount
import io.github.nullbrash.quazio.core.ui.res.txn_bad_amount
import io.github.nullbrash.quazio.core.ui.res.txn_category
import io.github.nullbrash.quazio.core.ui.res.txn_from_account
import io.github.nullbrash.quazio.core.ui.res.txn_need_account
import io.github.nullbrash.quazio.core.ui.res.txn_need_to_account
import io.github.nullbrash.quazio.core.ui.res.txn_save
import io.github.nullbrash.quazio.core.ui.res.txn_to_account
import io.github.nullbrash.quazio.feature.finance.CategoryKind
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.Occurrence
import io.github.nullbrash.quazio.feature.finance.formatAmountForEdit
import io.github.nullbrash.quazio.feature.finance.Recurring
import io.github.nullbrash.quazio.feature.finance.RecurringDraft
import io.github.nullbrash.quazio.feature.finance.RecurringMode
import io.github.nullbrash.quazio.feature.finance.ShortMonth
import io.github.nullbrash.quazio.feature.finance.TransactionDraft
import io.github.nullbrash.quazio.feature.finance.TxnKind
import io.github.nullbrash.quazio.feature.finance.formatMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Повтор платежа на выбор. Всё остальное (пришло с другого устройства) — «свой», правило не трогаем. */
internal enum class PaymentRepeat(val label: StringResource, val months: Int?) {
    WEEKLY(Res.string.rec_weekly, null),
    MONTHLY(Res.string.rec_monthly, 1),
    QUARTERLY(Res.string.rec_quarterly, 3),
    HALF_YEAR(Res.string.rec_half_year, 6),
    YEARLY(Res.string.rec_yearly, 12),
}

/** Бывают ли месяцы (у ежегодного — годы) без числа первого платежа: 29–31-е, 29 февраля. */
internal fun PaymentRepeat.hasShortMonths(start: LocalDate): Boolean = when (this) {
    PaymentRepeat.WEEKLY -> false
    PaymentRepeat.YEARLY -> start.month.ordinal == 1 && start.day == 29
    else -> start.day > 28
}

/**
 * RRULE повтора от даты первого платежа. Короткий месяц — как выбрал пользователь: «последний
 * день» и «1-е следующего» — правилом BYMONTHDAY=28..d;BYSETPOS=-1 (сдвиг на 1-е делает сервис),
 * «пропустить» — обычное правило (так по RFC 5545).
 */
internal fun PaymentRepeat.rrule(start: LocalDate, short: ShortMonth?): String {
    if (this == PaymentRepeat.WEEKLY) return "FREQ=WEEKLY"
    val n = months!!
    val base = if (n == 12) "FREQ=YEARLY" else "FREQ=MONTHLY" + if (n > 1) ";INTERVAL=$n" else ""
    if (!hasShortMonths(start) || short == null || short == ShortMonth.SKIP) return base
    val days = (28..start.day).joinToString(",")
    return if (n == 12) "$base;BYMONTH=2;BYMONTHDAY=$days;BYSETPOS=-1" else "$base;BYMONTHDAY=$days;BYSETPOS=-1"
}

internal fun paymentRepeatOf(rrule: String, start: LocalDate, short: ShortMonth?): PaymentRepeat? =
    PaymentRepeat.entries.firstOrNull { it.rrule(start, short) == rrule }

internal fun shortDate(d: LocalDate) = dayMonth(LocalDateTime(d, LocalTime(0, 0)))

@Composable
internal fun RecurringListScreen(
    services: AppServices,
    data: FinanceData,
    onBack: () -> Unit,
    onOpen: (String?) -> Unit,
) {
    SystemBack(onBack = onBack)
    var list by remember { mutableStateOf<List<Pair<Recurring, LocalDate?>>?>(null) }
    LaunchedEffect(Unit) {
        list = withContext(Dispatchers.IO) { services.finance.recurring.let { r -> r.all(data.accountId).map { it to r.next(it) } } }
    }
    val categories = data.categories.associateBy { it.id }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(stringResource(Res.string.rec_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { onOpen(null) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(Res.string.rec_add), modifier = Modifier.padding(start = 4.dp))
            }
        }
        val items = list ?: return@Column
        if (items.isEmpty()) Text(stringResource(Res.string.rec_empty), modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.first.id }) { (r, next) ->
                val repeat = paymentRepeatOf(r.rrule, r.startDate, r.shortMonth)?.label ?: Res.string.rec_custom
                val status = when {
                    r.paused -> stringResource(Res.string.rec_paused)
                    next == null -> stringResource(Res.string.rec_finished)
                    else -> stringResource(Res.string.rec_next, shortDate(next))
                }
                Row(Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(categories[r.categoryId]?.color)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${stringResource(repeat)} · $status · ${stringResource(if (r.mode == RecurringMode.AUTO) Res.string.rec_mode_auto else Res.string.rec_mode_ask)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val sign = when (r.kind) { TxnKind.INCOME -> "+"; TxnKind.EXPENSE -> "−"; else -> "" }
                    Text(sign + formatMoney(Money.rub(r.amountMinor)), style = MaterialTheme.typography.bodyLarge, color = if (r.kind == TxnKind.INCOME) INCOME_COLOR else MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider()
            }
        }
    }
}

/**
 * Окно регулярного платежа. [from] — «Сделать регулярной» из операции: поля из неё, первый
 * платёж — через месяц. Категория подсказывается по названию (подкатегория предпочитается);
 * нет подходящей подкатегории — кнопка создать её одним нажатием (решение пользователя: ничего
 * не создаётся само).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RecurringEditor(
    services: AppServices,
    data: FinanceData,
    recurringId: String?,
    from: TransactionDraft?,
    onClose: (changed: Boolean) -> Unit,
) {
    SystemBack { onClose(false) }
    val scope = rememberCoroutineScope()
    val finance = services.finance
    val accountId = data.accountId
    val zone = remember { currentZone() }
    val today = remember { localDateTime(nowMillis(), zone.id).date }
    val ownAccounts = data.accounts.filter { !it.archived && it.type != FinAccountType.DEBT }
    val allAccounts = data.accounts.filter { !it.archived }

    var loaded by remember { mutableStateOf(recurringId == null) }
    var name by remember { mutableStateOf(from?.let { it.description.ifBlank { it.merchantName.orEmpty() } }.orEmpty().ifBlank { from?.categoryId?.let { id -> data.categories.firstOrNull { it.id == id }?.name }.orEmpty() }) }
    var kind by remember { mutableStateOf(from?.kind?.takeIf { it != TxnKind.ADJUSTMENT } ?: TxnKind.EXPENSE) }
    var amountMinor by remember { mutableStateOf(from?.amountMinor) }
    var finAccountId by remember { mutableStateOf(from?.finAccountId ?: ownAccounts.firstOrNull()?.id) }
    var toAccountId by remember { mutableStateOf(from?.toFinAccountId) }
    var categoryId by remember { mutableStateOf(from?.categoryId) }
    var categories by remember { mutableStateOf(data.categories) }
    var startDate by remember {
        mutableStateOf(from?.let { localDateTime(it.occurredAt, it.timeZone).date.plus(DatePeriod(months = 1)) } ?: today)
    }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var repeat by remember { mutableStateOf<PaymentRepeat?>(PaymentRepeat.MONTHLY) }
    var customRule by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var remindDays by remember { mutableStateOf(1) }
    var shortMonth by remember { mutableStateOf<ShortMonth?>(null) }
    var touchedCategory by remember { mutableStateOf(from?.categoryId != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCalc by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(recurringId) {
        val id = recurringId ?: return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { finance.recurring.byId(id) } ?: return@LaunchedEffect onClose(false)
        name = r.name
        kind = r.kind
        amountMinor = r.amountMinor
        finAccountId = r.finAccountId
        toAccountId = r.toFinAccountId
        categoryId = r.categoryId
        startDate = r.startDate
        endDate = r.endDate
        shortMonth = r.shortMonth
        repeat = paymentRepeatOf(r.rrule, r.startDate, r.shortMonth)
        customRule = if (repeat == null) r.rrule else null
        auto = r.mode == RecurringMode.AUTO
        paused = r.paused
        remindDays = r.remindDays
        touchedCategory = true
        loaded = true
    }
    // Подсказка категории по названию — пока пользователь сам её не выбрал.
    LaunchedEffect(name, kind, loaded) {
        if (!loaded || touchedCategory || kind == TxnKind.TRANSFER || name.isBlank()) return@LaunchedEffect
        delay(400)
        val suggested = withContext(Dispatchers.IO) { finance.suggestCategory(accountId, name, kind == TxnKind.INCOME, today) }
        if (suggested != null && !touchedCategory) categoryId = suggested
    }
    if (!loaded) return

    val categoryKind = if (kind == TxnKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val node = categories.firstOrNull { it.id == categoryId }

    val needsShort = repeat?.hasShortMonths(startDate) == true

    fun save() {
        scope.launch {
            error = when {
                name.isBlank() -> getString(Res.string.rec_need_name)
                needsShort && shortMonth == null -> getString(Res.string.rec_short_need)
                amountMinor == null || amountMinor!! <= 0 -> getString(Res.string.txn_bad_amount)
                finAccountId == null -> getString(Res.string.txn_need_account)
                kind == TxnKind.TRANSFER && (toAccountId == null || toAccountId == finAccountId) -> getString(Res.string.txn_need_to_account)
                else -> null
            }
            if (error != null) return@launch
            val draft = RecurringDraft(
                id = recurringId, name = name, kind = kind, amountMinor = amountMinor!!, finAccountId = finAccountId!!,
                toFinAccountId = toAccountId, categoryId = categoryId,
                rrule = repeat?.rrule(startDate, shortMonth) ?: customRule ?: PaymentRepeat.MONTHLY.rrule(startDate, shortMonth),
                startDate = startDate, endDate = endDate, mode = if (auto) RecurringMode.AUTO else RecurringMode.ASK,
                remindDays = remindDays, paused = paused,
                shortMonth = if (needsShort || repeat == null) shortMonth else null,
            )
            val result = withContext(Dispatchers.IO) { runCatching { finance.recurring.save(accountId, draft) } }
            result.onSuccess { onClose(true) }.onFailure { error = it.message }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onClose(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(stringResource(if (recurringId == null) Res.string.rec_new else Res.string.rec_edit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (recurringId != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(Res.string.action_delete)) }
            Button(onClick = ::save, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(Res.string.txn_save)) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FinField(name, { name = it; error = null }, stringResource(Res.string.rec_name), Modifier.fillMaxWidth(), onSubmit = ::save)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TxnKind.EXPENSE, TxnKind.INCOME, TxnKind.TRANSFER).forEach { k ->
                    FilterChip(selected = kind == k, onClick = {
                        if (kind != k) { kind = k; categoryId = null; touchedCategory = false }
                    }, label = { Text(stringResource(k.label)) })
                }
            }
            Column {
                Text(stringResource(Res.string.txn_amount), style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { showCalc = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(amountMinor?.let { formatMoney(Money.rub(it)) } ?: "0 ₽", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledPicker(stringResource(if (kind == TxnKind.TRANSFER) Res.string.txn_from_account else Res.string.txn_account), ownAccounts.firstOrNull { it.id == finAccountId }?.name ?: "—") { dismiss ->
                    ownAccounts.forEach { a -> MenuOption(a.name) { finAccountId = a.id; dismiss() } }
                }
                if (kind == TxnKind.TRANSFER) {
                    // Перевод — и на долг: «каждый месяц отдаю маме».
                    LabeledPicker(stringResource(Res.string.txn_to_account), allAccounts.firstOrNull { it.id == toAccountId }?.name ?: "—") { dismiss ->
                        allAccounts.filter { it.id != finAccountId }.forEach { a -> MenuOption(a.name) { toAccountId = a.id; dismiss() } }
                    }
                } else {
                    Column {
                        Text(stringResource(Res.string.txn_category), style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick = { pickCategory = true }) {
                            if (node != null) ColorDot(node.color, Modifier.padding(end = 8.dp))
                            Text(node?.path ?: stringResource(Res.string.fin_no_category))
                        }
                    }
                }
            }
            // «Сеть» выбрана, а подкатегории «МТС» нет — создать её одним нажатием.
            val clean = name.trim()
            val canMakeSub = kind != TxnKind.TRANSFER && clean.isNotEmpty() && node != null && !node.name.equals(clean, ignoreCase = true) &&
                categories.none { it.parentId == node.id && it.name.equals(clean, ignoreCase = true) }
            if (canMakeSub && node != null) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val id = finance.addCategory(accountId, node.id, clean, categoryKind)
                                id to finance.categories(accountId)
                            }
                        }
                        result.onSuccess { (id, all) -> categories = all; categoryId = id; touchedCategory = true }.onFailure { error = it.message }
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(Res.string.rec_make_sub, clean, node.name), modifier = Modifier.padding(start = 8.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledPicker(stringResource(Res.string.rec_repeat), stringResource(repeat?.label ?: Res.string.rec_custom)) { dismiss ->
                    PaymentRepeat.entries.forEach { p -> MenuOption(stringResource(p.label)) { repeat = p; dismiss() } }
                }
                Column {
                    Text(stringResource(Res.string.rec_next_date), style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { pickDate = "start" }) { Text(dateText(LocalDateTime(startDate, LocalTime(0, 0)))) }
                }
                Column {
                    Text(stringResource(Res.string.rec_until), style = MaterialTheme.typography.labelLarge)
                    Row {
                        OutlinedButton(onClick = { pickDate = "end" }) {
                            Text(endDate?.let { dateText(LocalDateTime(it, LocalTime(0, 0))) } ?: stringResource(Res.string.rec_no_end))
                        }
                        if (endDate != null) TextButton(onClick = { endDate = null }) { Text("×") }
                    }
                }
            }

            // Куда платёж 29–31-го в месяце без этого числа — спрашиваем явно, без выбора по умолчанию.
            if (needsShort) {
                val feb = repeat == PaymentRepeat.YEARLY
                Column {
                    Text(
                        if (feb) stringResource(Res.string.rec_short_title_feb) else stringResource(Res.string.rec_short_title, startDate.day),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    listOf(
                        ShortMonth.LAST to if (feb) Res.string.rec_short_last_feb else Res.string.rec_short_last,
                        ShortMonth.NEXT to if (feb) Res.string.rec_short_next_feb else Res.string.rec_short_next,
                        ShortMonth.SKIP to if (feb) Res.string.rec_short_skip_feb else Res.string.rec_short_skip,
                    ).forEach { (option, label) ->
                        Row(Modifier.fillMaxWidth().clickable { shortMonth = option; error = null }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = shortMonth == option, onClick = { shortMonth = option; error = null })
                            Text(stringResource(label))
                        }
                    }
                }
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.rec_auto), modifier = Modifier.weight(1f))
                    Switch(auto, { auto = it })
                }
                Text(stringResource(Res.string.rec_auto_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (recurringId != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.rec_paused), modifier = Modifier.weight(1f))
                    Switch(paused, { paused = it })
                }
            }
        }
    }

    if (showCalc) {
        CalculatorDialog(
            initial = amountMinor?.let { formatAmountForEdit(it) }.orEmpty(),
            allowNegative = false,
            onDone = { minor, _ -> amountMinor = kotlin.math.abs(minor); error = null; showCalc = false },
            onDismiss = { showCalc = false },
        )
    }
    if (pickCategory) {
        CategoryPickerDialog(
            categories = categories.filter { it.kind == categoryKind },
            selectedId = categoryId,
            onPick = { categoryId = it; touchedCategory = true; pickCategory = false },
            onDismiss = { pickCategory = false },
        )
    }
    pickDate?.let { which ->
        val initial = if (which == "end") endDate ?: startDate else startDate
        val state = rememberDatePickerState(initialSelectedDateMillis = toMillis(LocalDateTime(initial, LocalTime(0, 0)), TimeZone.UTC))
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        val d = localDateTime(picked, "UTC").date
                        if (which == "end") endDate = maxOf(d, startDate) else { startDate = d; if (endDate != null && endDate!! < d) endDate = d }
                    }
                    pickDate = null
                }) { Text(stringResource(Res.string.lock_done)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = null }) { Text(stringResource(Res.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(Res.string.rec_delete_confirm, name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { finance.recurring.delete(recurringId!!) }
                        onClose(true)
                    }
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** Блок «Ждут подтверждения» на главном экране финансов. */
@Composable
internal fun PendingPayments(
    pending: List<Occurrence>,
    onRecord: (Occurrence) -> Unit,
    onSkip: (Occurrence) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(Res.string.rec_pending_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        pending.forEach { o ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(o.recurring.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${shortDate(o.date)} · ${formatMoney(Money.rub(o.recurring.amountMinor))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onSkip(o) }) { Text(stringResource(Res.string.rec_skip)) }
                Button(onClick = { onRecord(o) }) { Text(stringResource(Res.string.rec_record)) }
            }
        }
    }
    HorizontalDivider()
}
