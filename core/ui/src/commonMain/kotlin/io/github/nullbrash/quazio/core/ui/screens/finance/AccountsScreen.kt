package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.SystemBack
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.fin_accounts_order
import io.github.nullbrash.quazio.core.ui.res.fin_accounts_title
import io.github.nullbrash.quazio.core.ui.res.fin_add_account
import io.github.nullbrash.quazio.core.ui.res.fin_in_total
import io.github.nullbrash.quazio.feature.finance.FinAccount
import io.github.nullbrash.quazio.feature.finance.formatMoney
import org.jetbrains.compose.resources.stringResource

/**
 * Все счета — отдельный экран (решение пользователя: на главном экране финансов карточек нет).
 * Ровно по две карточки в ряд; архивные — в конце (так их отдаёт база).
 */
@Composable
internal fun AccountsScreen(
    data: FinanceData,
    onBack: () -> Unit,
    onAccount: (FinAccount) -> Unit,
    onNewAccount: () -> Unit,
    onOrder: () -> Unit,
) {
    SystemBack(onBack = onBack)
    val tagsById = data.tags.associateBy { it.id }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(stringResource(Res.string.fin_accounts_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onOrder) { Text(stringResource(Res.string.fin_accounts_order)) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.fin_in_total, formatMoney(data.total)), style = MaterialTheme.typography.titleMedium)
            data.accounts.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { a -> AccountCard(a, a.personTagId?.let(tagsById::get), Modifier.weight(1f)) { onAccount(a) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            OutlinedButton(onClick = onNewAccount) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(Res.string.fin_add_account), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
