package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_close
import io.github.nullbrash.quazio.core.ui.res.fin_no_category
import io.github.nullbrash.quazio.core.ui.res.tag_create
import io.github.nullbrash.quazio.core.ui.res.tag_search
import io.github.nullbrash.quazio.core.ui.res.txn_category
import io.github.nullbrash.quazio.feature.finance.CategoryNode
import io.github.nullbrash.quazio.feature.finance.Tag
import io.github.nullbrash.quazio.feature.finance.TagKind
import org.jetbrains.compose.resources.stringResource

/** Подпись + кнопка с текущим значением, по нажатию — выпадающий список. */
@Composable
internal fun LabeledPicker(label: String, value: String, menu: @Composable (dismiss: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { open = true }) { Text(value) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
        }
    }
}

@Composable
internal fun MenuOption(text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick)
}

/** Выбор категории из дерева: отступ по глубине, цвет группы; «Без категории» — сверху. */
@Composable
internal fun CategoryPickerDialog(categories: List<CategoryNode>, selectedId: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.txn_category)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                item { CategoryLine(null, stringResource(Res.string.fin_no_category), 0, selectedId == null) { onPick(null) } }
                val guides = treeGuides(categories)
                items(categories, key = { it.id }) { c ->
                    CategoryTreeRow(c, guides[c.id], Modifier.clickable { onPick(c.id) }) {
                        RadioButton(selected = c.id == selectedId, onClick = null)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) } },
    )
}

@Composable
private fun CategoryLine(color: Long?, name: String, depth: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = (depth * 20).dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        ColorDot(color)
        Spacer(Modifier.width(8.dp))
        Text(name, style = if (depth == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Метки: поиск по существующим (отметить/снять) и создание новой нужного вида —
 * тема, человек или организация.
 */
@Composable
internal fun TagPickerDialog(
    tags: List<Tag>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onCreate: (String, TagKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val matches = tags.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column {
                FinField(query, { query = it }, stringResource(Res.string.tag_search), Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(matches, key = { it.id }) { tag ->
                        Row(Modifier.fillMaxWidth().clickable { onToggle(tag.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = tag.id in selected, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(tag.name, modifier = Modifier.weight(1f))
                            Text(stringResource(tag.kind.label), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (q.isNotEmpty()) {
                        TagKind.entries.filter { k -> tags.none { it.kind == k && it.name.equals(q, ignoreCase = true) } }.forEach { k ->
                            item(key = "create-$k") {
                                TextButton(onClick = { onCreate(q, k); query = "" }) {
                                    Text(stringResource(Res.string.tag_create, q) + " · " + stringResource(k.label))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) } },
    )
}
