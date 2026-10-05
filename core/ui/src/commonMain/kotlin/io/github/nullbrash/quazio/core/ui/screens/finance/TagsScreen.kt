package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.SystemBack
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.action_save
import io.github.nullbrash.quazio.core.ui.res.fin_tags
import io.github.nullbrash.quazio.core.ui.res.tag_delete
import io.github.nullbrash.quazio.core.ui.res.tag_delete_confirm
import io.github.nullbrash.quazio.core.ui.res.tag_empty
import io.github.nullbrash.quazio.core.ui.res.tag_name
import io.github.nullbrash.quazio.core.ui.res.txn_debt_with
import io.github.nullbrash.quazio.core.ui.res.txn_new_debt
import io.github.nullbrash.quazio.feature.finance.Tag
import io.github.nullbrash.quazio.feature.finance.TagKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** Метки по видам: переименовать, сменить вид, удалить (с операций исчезнет, операции останутся). */
@Composable
internal fun TagsScreen(services: AppServices, accountId: String, onBack: () -> Unit) {
    SystemBack(onBack = onBack)
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var tags by remember { mutableStateOf(emptyList<Tag>()) }
    var editing by remember { mutableStateOf<Tag?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) { tags = withContext(Dispatchers.IO) { services.finance.tags(accountId) } }

    fun act(block: () -> Unit) = scope.launch {
        val r = withContext(Dispatchers.IO) { runCatching(block) }
        r.onSuccess { editing = null; deleting = null; error = null; reload++ }.onFailure { error = it.message }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(stringResource(Res.string.fin_tags), style = MaterialTheme.typography.titleLarge)
        }
        if (tags.isEmpty()) Text(stringResource(Res.string.tag_empty), modifier = Modifier.padding(24.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            TagKind.entries.forEach { kind ->
                val group = tags.filter { it.kind == kind }
                if (group.isEmpty()) return@forEach
                item(key = "h-$kind") {
                    Text(stringResource(kind.label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                }
                items(group, key = { it.id }) { tag ->
                    Text(
                        tag.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { editing = tag }.padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    editing?.let { tag ->
        var name by remember(tag.id) { mutableStateOf(tag.name) }
        var kind by remember(tag.id) { mutableStateOf(tag.kind) }
        val submit = { if (name.isNotBlank()) act { services.finance.updateTag(tag.id, name, kind) } }
        AlertDialog(
            onDismissRequest = { editing = null; error = null },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinField(name, { name = it }, stringResource(Res.string.tag_name), Modifier.fillMaxWidth(), onSubmit = submit)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(stringResource(k.label)) }) }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { deleting = tag; editing = null }) { Text(stringResource(Res.string.tag_delete), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = submit) { Text(stringResource(Res.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editing = null; error = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(Res.string.tag_delete_confirm, tag.name)) },
            confirmButton = { TextButton(onClick = { act { services.finance.deleteTag(tag.id) } }) { Text(stringResource(Res.string.tag_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** Новый долг прямо из окна операции: с кем (подсказки из людей и организаций) и вид. */
@Composable
internal fun NewDebtDialog(counterparties: List<Tag>, onDismiss: () -> Unit, onCreate: (String, TagKind) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(TagKind.PERSON) }
    val q = name.trim()
    val known = counterparties.firstOrNull { it.name.equals(q, ignoreCase = true) }
    val submit = { if (q.isNotEmpty()) onCreate(q, known?.kind ?: kind) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.txn_new_debt)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FinField(name, { name = it }, stringResource(Res.string.txn_debt_with), Modifier.fillMaxWidth(), onSubmit = submit)
                val hints = counterparties.filter { q.isNotEmpty() && it.name.contains(q, ignoreCase = true) && it != known }.take(5)
                if (hints.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        hints.forEach { h -> SuggestionChip(onClick = { name = h.name; kind = h.kind }, label = { Text(h.name) }) }
                    }
                }
                if (q.isNotEmpty() && known == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(TagKind.PERSON, TagKind.ORGANIZATION).forEach { k ->
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(stringResource(k.label)) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = q.isNotEmpty(), onClick = submit) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
