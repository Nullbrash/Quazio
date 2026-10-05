package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.model.Money
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.acc_archived
import io.github.nullbrash.quazio.core.ui.res.acc_balance
import io.github.nullbrash.quazio.core.ui.res.acc_balance_hint
import io.github.nullbrash.quazio.core.ui.res.acc_balance_hint_debt
import io.github.nullbrash.quazio.core.ui.res.acc_counterparty_none
import io.github.nullbrash.quazio.core.ui.res.acc_counterparty_returns
import io.github.nullbrash.quazio.core.ui.res.acc_counterparty_waits
import io.github.nullbrash.quazio.core.ui.res.acc_credit_limit
import io.github.nullbrash.quazio.core.ui.res.acc_delete
import io.github.nullbrash.quazio.core.ui.res.acc_edit
import io.github.nullbrash.quazio.core.ui.res.acc_has_txns
import io.github.nullbrash.quazio.core.ui.res.acc_in_total
import io.github.nullbrash.quazio.core.ui.res.acc_name
import io.github.nullbrash.quazio.core.ui.res.acc_new
import io.github.nullbrash.quazio.core.ui.res.acc_type
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.action_save
import io.github.nullbrash.quazio.core.ui.res.adj_balance
import io.github.nullbrash.quazio.core.ui.res.adj_opening
import io.github.nullbrash.quazio.core.ui.res.txn_bad_amount
import io.github.nullbrash.quazio.feature.finance.FinAccount
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.Tag
import io.github.nullbrash.quazio.feature.finance.TagKind
import io.github.nullbrash.quazio.feature.finance.formatAmountForEdit
import io.github.nullbrash.quazio.feature.finance.formatMoney
import io.github.nullbrash.quazio.feature.finance.parseAmountMinor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Новый или существующий счёт ([account] = null — новый).
 * «Баланс» — сколько на счёте сейчас: при создании он записывается первой операцией
 * («Начальный остаток»), при правке — корректировкой на разницу (решение пользователя).
 * У долга знак баланса — направление: «+» вам вернут, «−» вернёте вы.
 */
@Composable
internal fun AccountEditor(services: AppServices, accountId: String, account: FinAccount?, tags: List<Tag>, onClose: (changed: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var type by remember { mutableStateOf(account?.type ?: FinAccountType.CARD) }
    var balanceText by remember { mutableStateOf(formatAmountForEdit(account?.balance?.minor ?: 0)) }
    var showCalc by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf(account?.creditLimit?.minor?.let(::formatAmountForEdit).orEmpty()) }
    val existingCounterparty = tags.firstOrNull { it.id == account?.personTagId }
    var counterparty by remember { mutableStateOf(existingCounterparty?.name.orEmpty()) }
    var counterpartyKind by remember { mutableStateOf(existingCounterparty?.kind ?: TagKind.PERSON) }
    // Люди и организации — те же метки, что у операций: подсказываются отовсюду.
    val counterparties = tags.filter { it.kind == TagKind.PERSON || it.kind == TagKind.ORGANIZATION }
    var includeInTotal by remember { mutableStateOf(account?.includeInTotal ?: type.defaultIncludeInTotal) }
    var touchedInclude by remember { mutableStateOf(account != null) }
    var archived by remember { mutableStateOf(account?.archived ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    val badAmount = stringResource(Res.string.txn_bad_amount)
    val hasTxns = stringResource(Res.string.acc_has_txns)
    val balanceMinor = parseAmountMinor(balanceText)

    val save: () -> Unit = save@{
        val target = balanceMinor ?: run { error = badAmount; return@save }
        val limitMinor = if (type == FinAccountType.CREDIT_CARD && limit.isNotBlank()) parseAmountMinor(limit) ?: run { error = badAmount; return@save } else null
        scope.launch {
            val openingText = getString(Res.string.adj_opening)
            val adjustText = getString(Res.string.adj_balance)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val f = services.finance
                    val personTagId = if (type == FinAccountType.DEBT && counterparty.isNotBlank()) {
                        counterparties.firstOrNull { it.name.equals(counterparty.trim(), ignoreCase = true) }?.id
                            ?: f.createTag(accountId, counterparty, counterpartyKind).id
                    } else null
                    val zone = currentZone().id
                    if (account == null) {
                        val id = f.createAccount(accountId, name, type, 0, limitMinor, includeInTotal, personTagId)
                        f.adjustBalanceTo(accountId, id, target, openingText, nowMillis(), zone)
                    } else {
                        f.updateAccount(account.id, name, type, f.openingBalance(account.id), limitMinor, includeInTotal, personTagId, archived)
                        f.adjustBalanceTo(accountId, account.id, target, adjustText, nowMillis(), zone)
                    }
                }
            }
            result.onSuccess { onClose(true) }.onFailure { error = it.message }
        }
    }

    AlertDialog(
        onDismissRequest = { onClose(false) },
        title = { Text(stringResource(if (account == null) Res.string.acc_new else Res.string.acc_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FinField(name, { name = it; error = null }, stringResource(Res.string.acc_name), Modifier.fillMaxWidth(), onSubmit = save)
                LabeledPicker(stringResource(Res.string.acc_type), stringResource(type.label)) { dismiss ->
                    FinAccountType.entries.forEach { t ->
                        MenuOption(stringResource(t.label)) {
                            type = t
                            if (!touchedInclude) includeInTotal = t.defaultIncludeInTotal
                            dismiss()
                        }
                    }
                }

                Column {
                    Text(stringResource(Res.string.acc_balance), style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { showCalc = true }, modifier = Modifier.fillMaxWidth()) {
                        val shown = balanceMinor?.let { m ->
                            (if (type == FinAccountType.DEBT && m > 0) "+" else "") + formatMoney(Money.rub(m))
                        } ?: balanceText
                        Text(shown, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        stringResource(if (type == FinAccountType.DEBT) Res.string.acc_balance_hint_debt else Res.string.acc_balance_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (type == FinAccountType.CREDIT_CARD) {
                    FinField(limit, { limit = it; error = null }, stringResource(Res.string.acc_credit_limit), Modifier.fillMaxWidth(), KeyboardType.Decimal, onSubmit = save)
                }
                if (type == FinAccountType.DEBT) {
                    // Подпись поля — по знаку баланса: «Кто вернёт» / «Кому вернуть» / «С кем».
                    val label = when {
                        (balanceMinor ?: 0) > 0 -> stringResource(Res.string.acc_counterparty_returns)
                        (balanceMinor ?: 0) < 0 -> stringResource(Res.string.acc_counterparty_waits)
                        else -> stringResource(Res.string.acc_counterparty_none)
                    }
                    FinField(counterparty, { counterparty = it }, label, Modifier.fillMaxWidth(), onSubmit = save)
                    val q = counterparty.trim()
                    val known = counterparties.firstOrNull { it.name.equals(q, ignoreCase = true) }
                    val hints = counterparties.filter { q.isNotEmpty() && it.name.contains(q, ignoreCase = true) && it != known }.take(5)
                    if (hints.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            hints.forEach { h -> SuggestionChip(onClick = { counterparty = h.name; counterpartyKind = h.kind }, label = { Text(h.name) }) }
                        }
                    }
                    if (q.isNotEmpty() && known == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(TagKind.PERSON, TagKind.ORGANIZATION).forEach { k ->
                                FilterChip(selected = counterpartyKind == k, onClick = { counterpartyKind = k }, label = { Text(stringResource(k.label)) })
                            }
                        }
                    }
                }
                SwitchRow(stringResource(Res.string.acc_in_total), includeInTotal) { includeInTotal = it; touchedInclude = true }
                if (account != null) SwitchRow(stringResource(Res.string.acc_archived), archived) { archived = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (account != null) {
                    TextButton(onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                if (services.finance.hasTransactions(account.id)) null else runCatching { services.finance.deleteAccount(account.id) }
                            }
                            if (result == null) error = hasTxns else result.onSuccess { onClose(true) }.onFailure { error = it.message }
                        }
                    }) { Text(stringResource(Res.string.acc_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = save) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { TextButton(onClick = { onClose(false) }) { Text(stringResource(Res.string.action_cancel)) } },
    )

    if (showCalc) {
        CalculatorDialog(
            initial = balanceText.takeIf { it != "0" }.orEmpty(),
            allowNegative = true,
            onDone = { balanceText = formatAmountForEdit(it); error = null; showCalc = false },
            onDismiss = { showCalc = false },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
