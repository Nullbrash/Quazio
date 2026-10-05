package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.action_save
import io.github.nullbrash.quazio.core.ui.res.cat_add
import io.github.nullbrash.quazio.core.ui.res.cat_add_child
import io.github.nullbrash.quazio.core.ui.res.cat_delete
import io.github.nullbrash.quazio.core.ui.res.cat_delete_confirm
import io.github.nullbrash.quazio.core.ui.res.cat_name
import io.github.nullbrash.quazio.core.ui.res.cat_rename
import io.github.nullbrash.quazio.core.ui.res.fin_categories
import io.github.nullbrash.quazio.core.ui.res.fin_expense
import io.github.nullbrash.quazio.core.ui.res.fin_income
import io.github.nullbrash.quazio.feature.finance.CategoryKind
import io.github.nullbrash.quazio.feature.finance.CategoryNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private sealed interface CategoryDialog {
    data class Add(val parent: CategoryNode?) : CategoryDialog
    data class Rename(val node: CategoryNode) : CategoryDialog
    data class Delete(val node: CategoryNode) : CategoryDialog
}

/** Дерево категорий: добавить (в корень или подкатегорию), переименовать, удалить. */
@Composable
internal fun CategoriesScreen(services: AppServices, accountId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var reload by remember { mutableIntStateOf(0) }
    var nodes by remember { mutableStateOf(emptyList<CategoryNode>()) }
    var dialog by remember { mutableStateOf<CategoryDialog?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(kind, reload) { nodes = withContext(Dispatchers.IO) { services.finance.categories(accountId, kind) } }

    fun act(block: () -> Unit) = scope.launch {
        val r = withContext(Dispatchers.IO) { runCatching(block) }
        r.onSuccess { dialog = null; error = null; reload++ }.onFailure { error = it.message }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(stringResource(Res.string.fin_categories), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { dialog = CategoryDialog.Add(null) }) { Text(stringResource(Res.string.cat_add)) }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == CategoryKind.EXPENSE, onClick = { kind = CategoryKind.EXPENSE }, label = { Text(stringResource(Res.string.fin_expense)) })
            FilterChip(selected = kind == CategoryKind.INCOME, onClick = { kind = CategoryKind.INCOME }, label = { Text(stringResource(Res.string.fin_income)) })
        }
        val guides = remember(nodes) { treeGuides(nodes) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(nodes, key = { it.id }) { node ->
                CategoryTreeRow(node, guides[node.id], Modifier.clickable { dialog = CategoryDialog.Rename(node) }) {
                    IconButton(onClick = { dialog = CategoryDialog.Add(node) }) { Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.cat_add_child)) }
                }
            }
        }
    }

    when (val d = dialog) {
        is CategoryDialog.Add -> NameDialog(
            title = d.parent?.let { "${stringResource(Res.string.cat_add_child)}: ${it.name}" } ?: stringResource(Res.string.cat_add),
            initial = "",
            error = error,
            onDismiss = { dialog = null; error = null },
            onSave = { name -> act { services.finance.addCategory(accountId, d.parent?.id, name, kind) } },
        )
        is CategoryDialog.Rename -> NameDialog(
            title = stringResource(Res.string.cat_rename),
            initial = d.node.name,
            error = error,
            onDismiss = { dialog = null; error = null },
            onSave = { name -> act { services.finance.updateCategory(d.node.id, name, d.node.parentId, null) } },
            extra = { TextButton(onClick = { dialog = CategoryDialog.Delete(d.node) }) { Text(stringResource(Res.string.cat_delete), color = MaterialTheme.colorScheme.error) } },
        )
        is CategoryDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            text = { Text(stringResource(Res.string.cat_delete_confirm, d.node.name)) },
            confirmButton = { TextButton(onClick = { act { services.finance.deleteCategory(d.node.id) } }) { Text(stringResource(Res.string.cat_delete)) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    extra: @Composable () -> Unit = {},
) {
    var name by remember { mutableStateOf(initial) }
    val submit = { if (name.isNotBlank()) onSave(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FinField(name, { name = it }, stringResource(Res.string.cat_name), Modifier.fillMaxWidth(), onSubmit = submit)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                extra()
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = submit) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
