package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.clickable
import io.github.nullbrash.quazio.core.ui.res.fin_accounts_title
import io.github.nullbrash.quazio.core.ui.QuazioIcons
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.model.Money
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.LocalFileSaver
import io.github.nullbrash.quazio.core.ui.LocalIncomingText
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.fin_add_account
import io.github.nullbrash.quazio.core.ui.res.fin_add_txn
import io.github.nullbrash.quazio.core.ui.res.fin_archived
import io.github.nullbrash.quazio.core.ui.res.fin_categories
import io.github.nullbrash.quazio.core.ui.res.fin_debt_return_to_org
import io.github.nullbrash.quazio.core.ui.res.fin_debt_return_to_person
import io.github.nullbrash.quazio.core.ui.res.fin_debt_returns
import io.github.nullbrash.quazio.core.ui.res.fin_tags
import io.github.nullbrash.quazio.core.ui.res.txn_debt
import io.github.nullbrash.quazio.core.ui.res.fin_empty_month
import io.github.nullbrash.quazio.core.ui.res.fin_expense
import io.github.nullbrash.quazio.core.ui.res.fin_export_csv
import io.github.nullbrash.quazio.core.ui.res.fin_export_done
import io.github.nullbrash.quazio.core.ui.res.fin_export_failed
import io.github.nullbrash.quazio.core.ui.res.fin_more
import io.github.nullbrash.quazio.core.ui.res.quick_none
import io.github.nullbrash.quazio.engine.quickinput.QuickDraft
import io.github.nullbrash.quazio.engine.quickinput.QuickLine
import io.github.nullbrash.quazio.engine.quickinput.QuickParser
import io.github.nullbrash.quazio.core.ui.res.fin_in_total
import io.github.nullbrash.quazio.core.ui.res.fin_income
import io.github.nullbrash.quazio.core.ui.res.fin_net
import io.github.nullbrash.quazio.core.ui.res.fin_no_category
import io.github.nullbrash.quazio.feature.finance.CategoryNode
import io.github.nullbrash.quazio.feature.finance.FinAccount
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.Tag
import io.github.nullbrash.quazio.feature.finance.TagKind
import io.github.nullbrash.quazio.feature.finance.Totals
import io.github.nullbrash.quazio.feature.finance.Transaction
import io.github.nullbrash.quazio.feature.finance.TxnKind
import io.github.nullbrash.quazio.feature.finance.formatMoney
import io.github.nullbrash.quazio.feature.finance.monthRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString

/** Всё, что показывает раздел за выбранный месяц (грузится в фоне одним заходом). */
internal data class FinanceData(
    val accountId: String,
    val accounts: List<FinAccount>,
    val total: Money,
    val totals: Totals,
    val transactions: List<Transaction>,
    val categories: List<CategoryNode>,
    val tags: List<Tag>,
    val calculatorOnNew: Boolean,
)

private sealed interface FinanceView {
    data object Main : FinanceView
    data class Editor(val txnId: String?) : FinanceView
    data object Categories : FinanceView
    data object Tags : FinanceView
    data object AccountsOrder : FinanceView
    data object Accounts : FinanceView
    /** Одна операция из быстрого ввода — сразу окно операции с заполненными полями. */
    data class QuickEditor(val original: QuickDraft, val prefill: EditorPrefill) : FinanceView
    /** Пост из нескольких строк — список черновиков. */
    data class QuickReview(val lines: List<QuickLine>, val prefills: List<EditorPrefill?>) : FinanceView
}

@Composable
fun FinanceScreen(services: AppServices, onOpenCalendar: ((kotlinx.datetime.LocalDate) -> Unit)? = null) {
    var month by remember { mutableStateOf(currentMonth()) }
    var reload by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<FinanceData?>(null) }
    var view by remember { mutableStateOf<FinanceView>(FinanceView.Main) }
    var accountDialog by remember { mutableStateOf<FinAccount?>(null) }
    var newAccountDialog by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val incoming = LocalIncomingText.current
    val incomingText = incoming?.text?.collectAsState()?.value
    val fileSaver = LocalFileSaver.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(month, reload) {
        data = withContext(Dispatchers.IO) {
            val finance = services.finance
            val accountId = services.accounts.current().id
            finance.ensureDefaults(accountId)
            val (from, to) = monthRange(month, currentZone())
            val accounts = finance.accounts(accountId)
            FinanceData(
                accountId = accountId,
                accounts = accounts,
                total = finance.total(accounts),
                totals = finance.totals(accountId, from, to),
                transactions = finance.transactions(accountId, from, to),
                categories = finance.categories(accountId),
                tags = finance.tags(accountId),
                calculatorOnNew = finance.openCalculatorOnNew,
            )
        }
    }
    val d = data ?: return

    // Текст «В учёт Quazio»: одна операция — окно-черновик, несколько строк — список черновиков.
    suspend fun openQuick(text: String) {
        val zone = currentZone()
        val today = localDateTime(nowMillis(), zone.id).date
        val (lines, prefills) = withContext(Dispatchers.IO) {
            val lines = QuickParser.parse(text, services.finance.quickVocabulary(d.accountId), today)
            lines to lines.map { line ->
                (line as? QuickLine.Operation)?.draft?.let { it.toPrefill(services.finance.tagIdsFor(d.accountId, it.tags), zone) }
            }
        }
        val ops = lines.filterIsInstance<QuickLine.Operation>()
        view = when {
            ops.isEmpty() -> { exportMessage = getString(Res.string.quick_none); FinanceView.Main }
            lines.size == 1 -> FinanceView.QuickEditor(ops.single().draft, prefills.single()!!)
            else -> FinanceView.QuickReview(lines, prefills)
        }
    }
    LaunchedEffect(incomingText) {
        val text = incomingText ?: return@LaunchedEffect
        incoming?.consume()
        // consume() меняет ключ этого эффекта и отменил бы его — разбор идёт в области экрана.
        scope.launch { openQuick(text) }
    }

    when (val v = view) {
        is FinanceView.Editor -> TransactionEditor(
            services = services,
            data = d,
            txnId = v.txnId,
            onClose = { changed ->
                view = FinanceView.Main
                if (changed) reload++
            },
        )
        FinanceView.Categories -> CategoriesScreen(services, d.accountId) {
            view = FinanceView.Main
            reload++
        }
        FinanceView.Tags -> TagsScreen(services, d.accountId) {
            view = FinanceView.Main
            reload++
        }
        FinanceView.AccountsOrder -> AccountsOrderScreen(services, d.accountId, d.accounts) {
            view = FinanceView.Accounts
            reload++
        }
        FinanceView.Accounts -> AccountsScreen(
            data = d,
            onBack = { view = FinanceView.Main },
            onAccount = { accountDialog = it },
            onNewAccount = { newAccountDialog = true },
            onOrder = { view = FinanceView.AccountsOrder },
        )
        is FinanceView.QuickEditor -> TransactionEditor(
            services = services, data = d, txnId = null, prefill = v.prefill,
            onDraft = { draft ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { saveQuickDraft(services, d, v.original, draft) } }
                    result.onSuccess { view = FinanceView.Main; reload++ }
                        .onFailure { exportMessage = it.message; view = FinanceView.Main }
                }
            },
            onClose = { view = FinanceView.Main },
        )
        is FinanceView.QuickReview -> QuickReviewScreen(services, d, v.lines, v.prefills) { saved ->
            view = FinanceView.Main
            if (saved) reload++
        }
        FinanceView.Main -> Box(Modifier.fillMaxSize()) {
            FinanceMain(
                data = d,
                month = month,
                onMonth = { month = it },
                onTransaction = { view = FinanceView.Editor(it.id) },
                onAccounts = { view = FinanceView.Accounts },
                onCategories = { view = FinanceView.Categories },
                onTags = { view = FinanceView.Tags },
                onMonthClick = onOpenCalendar?.let { open -> { open(kotlinx.datetime.LocalDate(month.year, month.month, 1)) } },
                onExport = fileSaver?.let { saver ->
                    {
                        scope.launch {
                            exportMessage = try {
                                val csv = withContext(Dispatchers.IO) { services.finance.exportCsv(d.accountId) }
                                val name = "quazio-operations-${localDateTime(nowMillis(), currentZone().id).date}.csv"
                                if (saver.save(name, csv.encodeToByteArray())) getString(Res.string.fin_export_done) else null
                            } catch (e: Exception) {
                                getString(Res.string.fin_export_failed, e.message ?: e.toString())
                            }
                        }
                    }
                },
            )
            exportMessage?.let { message ->
                LaunchedEffect(message) {
                    delay(4_000)
                    exportMessage = null
                }
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                ) { Text(message, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            }
            ExtendedFloatingActionButton(
                onClick = { view = FinanceView.Editor(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.fin_add_txn)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (newAccountDialog || accountDialog != null) {
        AccountEditor(
            services = services,
            accountId = d.accountId,
            account = accountDialog,
            tags = d.tags,
            onClose = { changed ->
                newAccountDialog = false
                accountDialog = null
                if (changed) reload++
            },
        )
    }
}

@Composable
private fun FinanceMain(
    data: FinanceData,
    month: io.github.nullbrash.quazio.feature.finance.YearMonth,
    onMonth: (io.github.nullbrash.quazio.feature.finance.YearMonth) -> Unit,
    onTransaction: (Transaction) -> Unit,
    onAccounts: () -> Unit,
    onCategories: () -> Unit,
    onTags: () -> Unit,
    onMonthClick: (() -> Unit)?,
    onExport: (() -> Unit)?,
) {
    val colors = data.categories.associate { it.id to it.color }
    val debtIds = data.accounts.filter { it.type == FinAccountType.DEBT }.mapTo(HashSet()) { it.id }
    val byDay = data.transactions.groupBy { localDateTime(it.occurredAt, it.timeZone).date }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        // Счета — на своём экране (решение пользователя): здесь только сколько всего и вход туда.
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Плашка — чтобы было видно, что это кнопка (пожелание пользователя).
                FilledTonalButton(onClick = onAccounts, modifier = Modifier.padding(vertical = 8.dp).height(48.dp)) {
                    Icon(QuazioIcons.Wallet, contentDescription = null)
                    Text(stringResource(Res.string.fin_accounts_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.weight(1f))
                // Редкие действия — в меню.
                Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.fin_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.fin_categories)) }, onClick = { menu = false; onCategories() })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.fin_tags)) }, onClick = { menu = false; onTags() })
                        if (onExport != null) {
                            DropdownMenuItem(text = { Text(stringResource(Res.string.fin_export_csv)) }, onClick = { menu = false; onExport() })
                        }
                    }
                }
            }
            HorizontalDivider()
        }
        // Месяц — по центру, над тем, что к нему относится (пожелание пользователя): нажатие ближе к середине.
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(month.previous()) }) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null) }
                // Нажатие на месяц — этот месяц в календаре.
                Text(
                    monthTitle(month), style = MaterialTheme.typography.titleLarge,
                    modifier = if (onMonthClick != null) Modifier.clickable(onClick = onMonthClick) else Modifier,
                )
                IconButton(onClick = { onMonth(month.next()) }) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null) }
            }
        }
        item {
            // Итоги месяца — три равные плитки во всю ширину, без промежутков (пожелание пользователя).
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(IntrinsicSize.Min)) {
                TotalTile(stringResource(Res.string.fin_income), formatMoney(data.totals.income), INCOME_COLOR, Modifier.weight(1f))
                TotalTile(stringResource(Res.string.fin_expense), formatMoney(data.totals.expense), null, Modifier.weight(1f))
                TotalTile(stringResource(Res.string.fin_net), formatMoney(data.totals.net), null, Modifier.weight(1f))
            }
        }
        if (data.transactions.isEmpty()) {
            item {
                Text(stringResource(Res.string.fin_empty_month), modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        byDay.forEach { (_, dayTxns) ->
            val first = localDateTime(dayTxns.first().occurredAt, dayTxns.first().timeZone)
            val dayExpense = dayTxns.filter { it.kind == TxnKind.EXPENSE }.fold(Money.rub(0)) { acc, t -> acc + t.amount }
            item(key = "day-${first.date}") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                    Text(dayTitle(first), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (dayExpense.minor > 0) Text("−" + formatMoney(dayExpense), style = MaterialTheme.typography.labelLarge)
                }
            }
            items(dayTxns, key = { it.id }) { t -> TransactionRow(t, colors[t.categoryId], debtIds) { onTransaction(t) } }
        }
    }
}

@Composable
private fun TotalTile(label: String, value: String, color: Color?, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outline)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun AccountCard(account: FinAccount, counterparty: Tag?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Одинаковый размер у всех карточек (пожелание пользователя): длинное — с многоточием.
    OutlinedCard(onClick = onClick, modifier = modifier.height(ACCOUNT_CARD_HEIGHT)) {
        // Иконка по типу счёта над названием; всё по центру (пожелание пользователя).
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(account.type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(account.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (account.type == FinAccountType.DEBT) {
                // Долг: знак говорит, кто кому должен — «+» вам вернут, «−» вернёте вы.
                val toMe = !account.balance.isNegative
                // Имя впереди — деньги вам («Катя вернёт»), «Верну» впереди — от вас («Верну: Катя»).
                // Имя не склоняем: автоматически это ненадёжно; организации — в кавычках.
                if (counterparty != null && account.balance.minor != 0L) {
                    val org = counterparty.kind == TagKind.ORGANIZATION
                    val shown = if (org) "«${counterparty.name}»" else counterparty.name
                    val res = when {
                        toMe -> Res.string.fin_debt_returns
                        org -> Res.string.fin_debt_return_to_org
                        else -> Res.string.fin_debt_return_to_person
                    }
                    Text(stringResource(res, shown), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    (if (toMe && account.balance.minor > 0) "+" else "") + formatMoney(account.balance),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (toMe) INCOME_COLOR else MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    formatMoney(account.balance),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (account.balance.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (account.archived) Text(stringResource(Res.string.fin_archived), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private val ACCOUNT_CARD_HEIGHT = 120.dp

internal val FinAccountType.icon: ImageVector
    get() = when (this) {
        FinAccountType.CARD -> QuazioIcons.Card
        FinAccountType.SAVINGS -> QuazioIcons.Savings
        FinAccountType.CASH -> QuazioIcons.Cash
        FinAccountType.EWALLET -> QuazioIcons.Wallet
        FinAccountType.SITE_BALANCE -> QuazioIcons.Globe
        FinAccountType.BONUS -> Icons.Filled.Star
        FinAccountType.CREDIT_CARD -> QuazioIcons.Bank
        FinAccountType.DEBT -> Icons.Filled.Person
    }

@Composable
private fun TransactionRow(t: Transaction, categoryColor: Long?, debtIds: Set<String>, onClick: () -> Unit) {
    val noCategory = stringResource(Res.string.fin_no_category)
    // Перевод с участием счёта-долга показываем как «Долг» со знаком: «+» — деньги к вам.
    val debtIn = t.kind == TxnKind.TRANSFER && t.finAccountId in debtIds
    val debtOut = t.kind == TxnKind.TRANSFER && t.toFinAccountId in debtIds
    val kindLabel = stringResource(if (debtIn || debtOut) Res.string.txn_debt else t.kind.label)
    val title = t.merchantName ?: t.description.ifBlank { null } ?: t.categoryName ?: kindLabel
    val account = if (t.kind == TxnKind.TRANSFER) "${t.finAccountName} → ${t.toFinAccountName}" else t.finAccountName
    val category = when (t.kind) {
        TxnKind.EXPENSE, TxnKind.INCOME -> t.categoryName ?: noCategory
        else -> kindLabel
    }
    val subtitle = listOfNotNull(category.takeIf { it != title }, account, t.tags.joinToString { it.name }.ifBlank { null }).joinToString(" · ")
    val (amountText, amountColor) = when {
        debtIn -> "+" + formatMoney(t.amount) to INCOME_COLOR
        debtOut -> "−" + formatMoney(t.amount) to null
        else -> when (t.kind) {
        TxnKind.EXPENSE -> "−" + formatMoney(t.amount) to null
        TxnKind.INCOME -> "+" + formatMoney(t.amount) to INCOME_COLOR
        TxnKind.TRANSFER -> formatMoney(t.amount) to null
        TxnKind.ADJUSTMENT -> (if (t.amount.isNegative) "" else "+") + formatMoney(t.amount) to null
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(if (t.kind == TxnKind.EXPENSE || t.kind == TxnKind.INCOME) categoryColor else null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(amountText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = amountColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

internal val INCOME_COLOR = Color(0xFF2E9D4F)
