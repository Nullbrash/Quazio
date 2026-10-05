package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.accounts.Account
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.accounts_add
import io.github.nullbrash.quazio.core.ui.res.accounts_create
import io.github.nullbrash.quazio.core.ui.res.accounts_delete
import io.github.nullbrash.quazio.core.ui.res.accounts_delete_confirm
import io.github.nullbrash.quazio.core.ui.res.accounts_delete_last
import io.github.nullbrash.quazio.core.ui.res.accounts_edit
import io.github.nullbrash.quazio.core.ui.res.accounts_name
import io.github.nullbrash.quazio.core.ui.res.accounts_save
import io.github.nullbrash.quazio.core.ui.res.accounts_title
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.action_delete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** Список аккаунтов: выбор текущего, добавление, переименование и удаление. Обращения к базе — в фоне. */
@Composable
fun AccountsSection(services: AppServices) {
    val service = services.accounts
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var currentId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var deleting by remember { mutableStateOf<Account?>(null) }

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
                Text(account.name, modifier = Modifier.padding(start = 8.dp).weight(1f))
                IconButton(onClick = { editing = account }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.accounts_edit))
                }
            }
        }
        TextButton(onClick = { adding = true }) { Text(stringResource(Res.string.accounts_add)) }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        val submit = {
            if (name.isNotBlank()) {
                adding = false
                scope.launch {
                    withContext(Dispatchers.IO) { service.create(name) }
                    reload()
                }
            }
        }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(Res.string.accounts_add)) },
            text = { NameField(name, onChange = { name = it }, onSubmit = submit) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { submit() }) {
                    Text(stringResource(Res.string.accounts_create))
                }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }

    editing?.let { account ->
        var name by remember(account.id) { mutableStateOf(account.name) }
        val canDelete = accounts.size > 1
        val submit = {
            if (name.isNotBlank()) {
                editing = null
                scope.launch {
                    withContext(Dispatchers.IO) { service.rename(account.id, name) }
                    reload()
                }
            }
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(Res.string.accounts_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NameField(name, onChange = { name = it }, onSubmit = submit)
                    TextButton(
                        enabled = canDelete,
                        onClick = { editing = null; deleting = account },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(Res.string.accounts_delete)) }
                    if (!canDelete) {
                        Text(stringResource(Res.string.accounts_delete_last), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { submit() }) {
                    Text(stringResource(Res.string.accounts_save))
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }

    deleting?.let { account ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.accounts_delete)) },
            text = { Text(stringResource(Res.string.accounts_delete_confirm, account.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launch {
                            withContext(Dispatchers.IO) { service.delete(account.id) { services.finance.purgeAccountData(it) } }
                            reload()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun NameField(name: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    OutlinedTextField(
        value = name,
        onValueChange = { if (it.length <= 50) onChange(it) },
        label = { Text(stringResource(Res.string.accounts_name)) },
        singleLine = true,
        // «Готово» на клавиатуре телефона и Enter на ПК — то же, что кнопка подтверждения.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.onPreviewKeyEvent { e ->
            val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
            if (enter && e.type == KeyEventType.KeyDown) { onSubmit(); true } else false
        },
    )
}
