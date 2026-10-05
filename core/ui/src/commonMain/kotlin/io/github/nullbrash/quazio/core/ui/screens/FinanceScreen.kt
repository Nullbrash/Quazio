package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.finance_account
import io.github.nullbrash.quazio.core.ui.res.finance_placeholder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** Заглушка раздела финансов — наполняется в фазе 4 этапа 1. */
@Composable
fun FinanceScreen(accounts: AccountService) {
    val accountName by produceState<String?>(null, accounts) {
        value = withContext(Dispatchers.IO) { accounts.current().name }
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        accountName?.let { Text(stringResource(Res.string.finance_account, it), style = MaterialTheme.typography.titleMedium) }
        Text(stringResource(Res.string.finance_placeholder), style = MaterialTheme.typography.bodyLarge)
    }
}
