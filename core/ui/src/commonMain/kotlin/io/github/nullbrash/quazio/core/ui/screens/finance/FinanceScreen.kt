package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.model.Money
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.fin_add_account
import io.github.nullbrash.quazio.core.ui.res.fin_add_txn
import io.github.nullbrash.quazio.core.ui.res.fin_archived
import io.github.nullbrash.quazio.core.ui.res.fin_categories
import io.github.nullbrash.quazio.core.ui.res.fin_empty_month
import io.github.nullbrash.quazio.core.ui.res.fin_expense
import io.github.nullbrash.quazio.core.ui.res.fin_in_total
import io.github.nullbrash.quazio.core.ui.res.fin_income
import io.github.nullbrash.quazio.core.ui.res.fin_net
import io.github.nullbrash.quazio.core.ui.res.fin_no_category
import io.github.nullbrash.quazio.feature.finance.CategoryNode
import io.github.nullbrash.quazio.feature.finance.FinAccount
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.Tag
import io.github.nullbrash.quazio.feature.finance.Totals
import io.github.nullbrash.quazio.feature.finance.Transaction
import io.github.nullbrash.quazio.feature.finance.TxnKind
import io.github.nullbrash.quazio.feature.finance.formatMoney
import io.github.nullbrash.quazio.feature.finance.monthRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

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
}

@Composable
fun FinanceScreen(services: AppServices) {
    var month by remember { mutableStateOf(currentMonth()) }
    var reload by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<FinanceData?>(null) }
    var view by remember { mutableStateOf<FinanceView>(FinanceView.Main) }
    var accountDialog by remember { mutableStateOf<FinAccount?>(null) }
    var newAccountDialog by remember { mutableStateOf(false) }

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
        FinanceView.Main -> Box(Modifier.fillMaxSize()) {
            FinanceMain(
                data = d,
                month = month,
                onMonth = { month = it },
                onTransaction = { view = FinanceView.Editor(it.id) },
                onAccount = { accountDialog = it },
                onNewAccount = { newAccountDialog = true },
                onCategories = { view = FinanceView.Categories },
            )
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
    onAccount: (FinAccount) -> Unit,
    onNewAccount: () -> Unit,
    onCategories: () -> Unit,
) {
    val colors = data.categories.associate { it.id to it.color }
    val byDay = data.transactions.groupBy { localDateTime(it.occurredAt, it.timeZone).date }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(month.previous()) }) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null) }
                Text(monthTitle(month), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { onMonth(month.next()) }) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCategories) { Text(stringResource(Res.string.fin_categories)) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TotalCell(stringResource(Res.string.fin_income), formatMoney(data.totals.income), INCOME_COLOR)
                TotalCell(stringResource(Res.string.fin_expense), formatMoney(data.totals.expense), null)
                TotalCell(stringResource(Res.string.fin_net), formatMoney(data.totals.net), null)
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.accounts, key = { it.id }) { account -> AccountCard(account) { onAccount(account) } }
                item { AssistChip(onClick = onNewAccount, label = { Text(stringResource(Res.string.fin_add_account)) }, leadingIcon = { Icon(Icons.Filled.Add, null) }) }
            }
            Text(
                stringResource(Res.string.fin_in_total, formatMoney(data.total)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HorizontalDivider(Modifier.padding(top = 8.dp))
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
            items(dayTxns, key = { it.id }) { t -> TransactionRow(t, colors[t.categoryId]) { onTransaction(t) } }
        }
    }
}

@Composable
private fun TotalCell(label: String, value: String, color: Color?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AccountCard(account: FinAccount, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(account.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (account.type == FinAccountType.DEBT) {
                // Долг: знак говорит, кто кому должен — «+» вам вернут, «−» вернёте вы.
                val toMe = !account.balance.isNegative
                Text(
                    (if (toMe && account.balance.minor > 0) "+" else "") + formatMoney(account.balance),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (toMe) INCOME_COLOR else MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    formatMoney(account.balance),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (account.balance.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (account.archived) Text(stringResource(Res.string.fin_archived), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TransactionRow(t: Transaction, categoryColor: Long?, onClick: () -> Unit) {
    val noCategory = stringResource(Res.string.fin_no_category)
    val kindLabel = stringResource(t.kind.label)
    val title = t.merchantName ?: t.description.ifBlank { null } ?: t.categoryName ?: kindLabel
    val account = if (t.kind == TxnKind.TRANSFER) "${t.finAccountName} → ${t.toFinAccountName}" else t.finAccountName
    val category = when (t.kind) {
        TxnKind.EXPENSE, TxnKind.INCOME -> t.categoryName ?: noCategory
        else -> kindLabel
    }
    val subtitle = listOfNotNull(category.takeIf { it != title }, account, t.tags.joinToString { it.name }.ifBlank { null }).joinToString(" · ")
    val (amountText, amountColor) = when (t.kind) {
        TxnKind.EXPENSE -> "−" + formatMoney(t.amount) to null
        TxnKind.INCOME -> "+" + formatMoney(t.amount) to INCOME_COLOR
        TxnKind.TRANSFER -> formatMoney(t.amount) to null
        TxnKind.ADJUSTMENT -> (if (t.amount.isNegative) "" else "+") + formatMoney(t.amount) to null
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
