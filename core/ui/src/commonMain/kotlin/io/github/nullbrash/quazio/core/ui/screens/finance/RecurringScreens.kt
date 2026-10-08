package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import io.github.nullbrash.quazio.core.ui.res.rec_overdue
import io.github.nullbrash.quazio.core.ui.res.rec_expected_until
import io.github.nullbrash.quazio.core.ui.res.rec_suggest_short
import io.github.nullbrash.quazio.core.ui.res.rec_suggest_later
import io.github.nullbrash.quazio.core.ui.res.rec_suggest_apply
import io.github.nullbrash.quazio.core.ui.res.rec_suggest
import io.github.nullbrash.quazio.core.ui.res.rec_confirm_no
import io.github.nullbrash.quazio.core.ui.res.rec_confirm_yes
import io.github.nullbrash.quazio.core.ui.res.rec_confirm_question
import io.github.nullbrash.quazio.core.ui.res.rec_confirm_text
import io.github.nullbrash.quazio.core.ui.res.rec_confirm_title
import io.github.nullbrash.quazio.core.ui.res.rec_preview
import io.github.nullbrash.quazio.core.ui.res.rec_window_auto_off
import io.github.nullbrash.quazio.core.ui.res.rec_window_range
import io.github.nullbrash.quazio.core.ui.res.rec_window_one
import io.github.nullbrash.quazio.core.ui.res.rec_window
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
import io.github.nullbrash.quazio.feature.finance.RecurringService
import io.github.nullbrash.quazio.feature.finance.WindowSuggestion
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

/** RRULE повтора; 29–31-е в коротких месяцах сервис сам переносит вперёд «по счёту дней». */
internal fun PaymentRepeat.rrule(): String = when (this) {
    PaymentRepeat.WEEKLY -> "FREQ=WEEKLY"
    PaymentRepeat.YEARLY -> "FREQ=YEARLY"
    else -> "FREQ=MONTHLY" + if (months!! > 1) ";INTERVAL=$months" else ""
}

internal fun paymentRepeatOf(rrule: String): PaymentRepeat? = PaymentRepeat.entries.firstOrNull { it.rrule() == rrule }

internal fun shortDate(d: LocalDate) = dayMonth(LocalDateTime(d, LocalTime(0, 0)))

/** «20–25 октября», «29 января – 1 февраля», один день — «20 октября». */
internal fun rangeText(a: LocalDate, b: LocalDate): String = when {
    a == b -> shortDate(a)
    a.month == b.month && a.year == b.year -> "${a.day}–${shortDate(b)}"
    else -> "${shortDate(a)} – ${shortDate(b)}"
}

/** «21–24» — срок числами (для подсказки и списка). */
internal fun daysText(a: LocalDate, b: LocalDate) = if (a == b) "${a.day}" else "${a.day}–${b.day}"

@Composable
internal fun RecurringListScreen(
    services: AppServices,
    data: FinanceData,
    onBack: () -> Unit,
    onOpen: (String?) -> Unit,
) {
    SystemBack(onBack = onBack)
    var list by remember { mutableStateOf<List<Triple<Recurring, LocalDate?, WindowSuggestion?>>?>(null) }
    LaunchedEffect(Unit) {
        list = withContext(Dispatchers.IO) {
            services.finance.recurring.let { s -> s.all(data.accountId).map { Triple(it, s.next(it), s.suggestWindow(data.accountId, it)) } }
        }
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
            items(items, key = { it.first.id }) { (r, next, suggestion) ->
                val repeat = paymentRepeatOf(r.rrule)?.label ?: Res.string.rec_custom
                val status = when {
                    r.paused -> stringResource(Res.string.rec_paused)
                    next == null -> stringResource(Res.string.rec_finished)
                    else -> stringResource(Res.string.rec_next, rangeText(next, next.plus(DatePeriod(days = r.windowDays))))
                }
                Row(Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(categories[r.categoryId]?.color)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${stringResource(repeat)} · $status · ${stringResource(if (r.mode == RecurringMode.AUTO) Res.string.rec_mode_auto else Res.string.rec_mode_ask)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        suggestion?.let { sg ->
                            Text(
                                stringResource(Res.string.rec_suggest_short, daysText(r.startDate.plus(DatePeriod(days = sg.fromOffset)), r.startDate.plus(DatePeriod(days = sg.toOffset)))),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            )
                        }
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
    var windowDays by remember { mutableStateOf(0) }
    var suggestion by remember { mutableStateOf<WindowSuggestion?>(null) }
    var preview by remember { mutableStateOf<List<LocalDate>>(emptyList()) }
    var confirmShift by remember { mutableStateOf<RecurringDraft?>(null) }
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
        windowDays = r.windowDays
        repeat = paymentRepeatOf(r.rrule)
        suggestion = withContext(Dispatchers.IO) { finance.recurring.suggestWindow(accountId, r) }
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
    val rule = repeat?.rrule() ?: customRule ?: PaymentRepeat.MONTHLY.rrule()
    // Как лягут ближайшие платежи — до сохранения (перенос 29–31-го виден сразу).
    LaunchedEffect(rule, startDate, endDate, windowDays, finAccountId, loaded) {
        val account = finAccountId ?: return@LaunchedEffect
        if (!loaded) return@LaunchedEffect
        preview = withContext(Dispatchers.IO) {
            runCatching {
                finance.recurring.preview(RecurringDraft(name = "-", kind = kind, amountMinor = 1, finAccountId = account, rrule = rule,
                    startDate = startDate, endDate = endDate, windowDays = windowDays), PREVIEW_COUNT)
            }.getOrDefault(emptyList())
        }
    }
    if (!loaded) return

    val categoryKind = if (kind == TxnKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val node = categories.firstOrNull { it.id == categoryId }

    fun store(draft: RecurringDraft) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { finance.recurring.save(accountId, draft) } }
            result.onSuccess { onClose(true) }.onFailure { error = it.message }
        }
    }

    fun save() {
        scope.launch {
            error = when {
                name.isBlank() -> getString(Res.string.rec_need_name)
                amountMinor == null || amountMinor!! <= 0 -> getString(Res.string.txn_bad_amount)
                finAccountId == null -> getString(Res.string.txn_need_account)
                kind == TxnKind.TRANSFER && (toAccountId == null || toAccountId == finAccountId) -> getString(Res.string.txn_need_to_account)
                else -> null
            }
            if (error != null) return@launch
            val draft = RecurringDraft(
                id = recurringId, name = name, kind = kind, amountMinor = amountMinor!!, finAccountId = finAccountId!!,
                toFinAccountId = toAccountId, categoryId = categoryId, rrule = rule,
                startDate = startDate, endDate = endDate, mode = if (auto && windowDays == 0) RecurringMode.AUTO else RecurringMode.ASK,
                remindDays = remindDays, paused = paused, windowDays = windowDays,
            )
            // Есть перенесённые даты (29–31-е) — сначала показать, как лягут, и спросить (решение пользователя).
            val ahead = withContext(Dispatchers.IO) { runCatching { finance.recurring.preview(draft, CONFIRM_COUNT) }.getOrDefault(emptyList()) }
            if (repeat != PaymentRepeat.WEEKLY && ahead.any { it.day != startDate.day }) confirmShift = draft else store(draft)
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

            // Разброс: деньги приходят «в 20-х» — срок «с 20 по 25» (пожелание пользователя).
            LabeledPicker(stringResource(Res.string.rec_window), windowLabel(startDate, windowDays)) { dismiss ->
                (0..RecurringService.MAX_WINDOW).forEach { n -> MenuOption(windowLabel(startDate, n)) { windowDays = n; dismiss() } }
            }
            if (preview.isNotEmpty()) {
                Text(
                    stringResource(Res.string.rec_preview, preview.joinToString(", ") { rangeText(it, it.plus(DatePeriod(days = windowDays))) }),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            suggestion?.let { sg ->
                val a = startDate.plus(DatePeriod(days = sg.fromOffset))
                val b = startDate.plus(DatePeriod(days = sg.toOffset))
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(stringResource(Res.string.rec_suggest, daysText(a, b), sg.basedOn), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { finance.recurring.byId(recurringId!!)?.let { finance.recurring.applySuggestion(accountId, it, sg); finance.recurring.byId(recurringId) } }
                                if (r != null) { startDate = r.startDate; windowDays = r.windowDays }
                                suggestion = null
                            }
                        }) { Text(stringResource(Res.string.rec_suggest_apply)) }
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { finance.recurring.byId(recurringId!!)?.let { finance.recurring.dismissSuggestion(accountId, it) } }
                                suggestion = null
                            }
                        }) { Text(stringResource(Res.string.rec_suggest_later)) }
                    }
                }
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.rec_auto), modifier = Modifier.weight(1f))
                    // С разбросом день прихода неизвестен — записать самому нельзя (решение пользователя).
                    Switch(auto && windowDays == 0, { auto = it }, enabled = windowDays == 0)
                }
                Text(
                    stringResource(if (windowDays > 0) Res.string.rec_window_auto_off else Res.string.rec_auto_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        // «До» — через год от первого платежа: подтверждение без правки не делает платёж разовым.
        val initial = if (which == "end") endDate ?: startDate.plus(DatePeriod(years = 1)) else startDate
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
    confirmShift?.let { draft ->
        AlertDialog(
            onDismissRequest = { confirmShift = null },
            title = { Text(stringResource(Res.string.rec_confirm_title)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.rec_confirm_text, startDate.day))
                    preview.forEach { d ->
                        val moved = d.day != startDate.day
                        Text("• " + rangeText(d, d.plus(DatePeriod(days = windowDays))) + if (moved) "  ←" else "",
                            color = if (moved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    Text(stringResource(Res.string.rec_confirm_question), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { confirmShift = null; store(draft) }) { Text(stringResource(Res.string.rec_confirm_yes)) } },
            dismissButton = { TextButton(onClick = { confirmShift = null }) { Text(stringResource(Res.string.rec_confirm_no)) } },
        )
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
    val today = remember { localDateTime(nowMillis(), currentZone().id).date }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(Res.string.rec_pending_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        pending.forEach { o ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(o.recurring.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val term = when {
                        o.overdue(today) -> stringResource(Res.string.rec_overdue)
                        o.windowEnd > o.date -> stringResource(Res.string.rec_expected_until, o.windowEnd.day)
                        else -> null
                    }
                    Text(
                        listOfNotNull(shortDate(o.date), term, formatMoney(Money.rub(o.recurring.amountMinor))).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (o.overdue(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onSkip(o) }) { Text(stringResource(Res.string.rec_skip)) }
                Button(onClick = { onRecord(o) }) { Text(stringResource(Res.string.rec_record)) }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun windowLabel(start: LocalDate, days: Int): String =
    if (days == 0) stringResource(Res.string.rec_window_one) else stringResource(Res.string.rec_window_range, start.day, start.plus(DatePeriod(days = days)).day)

private const val PREVIEW_COUNT = 6
private const val CONFIRM_COUNT = 12
