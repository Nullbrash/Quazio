package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.nullbrash.quazio.core.accounts.Account
import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.accounts_add
import io.github.nullbrash.quazio.core.ui.res.accounts_create
import io.github.nullbrash.quazio.core.ui.res.accounts_name
import io.github.nullbrash.quazio.core.ui.res.accounts_title
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** Список аккаунтов: выбор текущего и добавление. Обращения к базе — в фоне. */
@Composable
fun AccountsSection(service: AccountService) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var currentId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    suspend fun reload() {
        val (list, current) = withContext(Dispatchers.IO) { service.accounts() to service.current().id }
        accounts = list
        currentId = current
    }

    LaunchedEffect(service) { reload() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.accounts_title), style = MaterialTheme.typography.titleMedium)
        accounts.forEach { account ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            withContext(Dispatchers.IO) { service.switchTo(account.id) }
                            reload()
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = account.id == currentId, onClick = null)
                Text(account.name, modifier = Modifier.padding(start = 8.dp))
            }
        }
        TextButton(onClick = { adding = true }) { Text(stringResource(Res.string.accounts_add)) }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(Res.string.accounts_add)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 50) name = it },
                    label = { Text(stringResource(Res.string.accounts_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        adding = false
                        scope.launch {
                            withContext(Dispatchers.IO) { service.create(name) }
                            reload()
                        }
                    },
                ) { Text(stringResource(Res.string.accounts_create)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}
