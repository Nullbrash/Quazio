package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.acc_archived
import io.github.nullbrash.quazio.core.ui.res.acc_credit_limit
import io.github.nullbrash.quazio.core.ui.res.acc_delete
import io.github.nullbrash.quazio.core.ui.res.acc_edit
import io.github.nullbrash.quazio.core.ui.res.acc_has_txns
import io.github.nullbrash.quazio.core.ui.res.acc_in_total
import io.github.nullbrash.quazio.core.ui.res.acc_name
import io.github.nullbrash.quazio.core.ui.res.acc_new
import io.github.nullbrash.quazio.core.ui.res.acc_opening
import io.github.nullbrash.quazio.core.ui.res.acc_person
import io.github.nullbrash.quazio.core.ui.res.acc_type
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.action_save
import io.github.nullbrash.quazio.core.ui.res.txn_bad_amount
import io.github.nullbrash.quazio.feature.finance.FinAccount
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.Tag
import io.github.nullbrash.quazio.feature.finance.TagKind
import io.github.nullbrash.quazio.feature.finance.formatAmountForEdit
import io.github.nullbrash.quazio.feature.finance.parseAmountMinor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** Новый или существующий счёт ([account] = null — новый). */
@Composable
internal fun AccountEditor(services: AppServices, accountId: String, account: FinAccount?, tags: List<Tag>, onClose: (changed: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var type by remember { mutableStateOf(account?.type ?: FinAccountType.CARD) }
    var opening by remember { mutableStateOf(account?.let { "" } ?: "0") }
    var limit by remember { mutableStateOf(account?.creditLimit?.minor?.let(::formatAmountForEdit).orEmpty()) }
    var person by remember { mutableStateOf(tags.firstOrNull { it.id == account?.personTagId }?.name.orEmpty()) }
    var includeInTotal by remember { mutableStateOf(account?.includeInTotal ?: type.defaultIncludeInTotal) }
    var touchedInclude by remember { mutableStateOf(account != null) }
    var archived by remember { mutableStateOf(account?.archived ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    val badAmount = stringResource(Res.string.txn_bad_amount)
    val hasTxns = stringResource(Res.string.acc_has_txns)

    // Начальный остаток существующего счёта подгружаем отдельно: в списке — уже текущий баланс.
    androidx.compose.runtime.LaunchedEffect(account?.id) {
        val a = account ?: return@LaunchedEffect
        opening = withContext(Dispatchers.IO) { services.finance.openingBalance(a.id) }.let(::formatAmountForEdit)
    }

    val save: () -> Unit = save@{
        val openingMinor = parseAmountMinor(opening.ifBlank { "0" }) ?: run { error = badAmount; return@save }
        val limitMinor = if (type == FinAccountType.CREDIT_CARD && limit.isNotBlank()) parseAmountMinor(limit) ?: run { error = badAmount; return@save } else null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val personTagId = if (type == FinAccountType.DEBT && person.isNotBlank())
                        services.finance.createTag(accountId, person, TagKind.PERSON).id else null
                    if (account == null) {
                        services.finance.createAccount(accountId, name, type, openingMinor, limitMinor, includeInTotal, personTagId)
                    } else {
                        services.finance.updateAccount(account.id, name, type, openingMinor, limitMinor, includeInTotal, personTagId, archived)
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
                FinField(opening, { opening = it; error = null }, stringResource(Res.string.acc_opening), Modifier.fillMaxWidth(), KeyboardType.Decimal, onSubmit = save)
                if (type == FinAccountType.CREDIT_CARD) {
                    FinField(limit, { limit = it; error = null }, stringResource(Res.string.acc_credit_limit), Modifier.fillMaxWidth(), KeyboardType.Decimal, onSubmit = save)
                }
                if (type == FinAccountType.DEBT) {
                    FinField(person, { person = it }, stringResource(Res.string.acc_person), Modifier.fillMaxWidth(), onSubmit = save)
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
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
