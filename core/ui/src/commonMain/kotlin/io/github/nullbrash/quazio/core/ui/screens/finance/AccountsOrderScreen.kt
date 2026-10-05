package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.SystemBack
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.fin_accounts_order
import io.github.nullbrash.quazio.core.ui.res.fin_accounts_order_hint
import io.github.nullbrash.quazio.core.ui.res.fin_move_down
import io.github.nullbrash.quazio.core.ui.res.fin_move_up
import io.github.nullbrash.quazio.feature.finance.FinAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * Ручной порядок счетов (пожелание пользователя). Стрелки, а не перетаскивание: одинаково
 * надёжно пальцем и мышью. Каждый сдвиг сохраняется сразу. Архивные — всегда в конце, здесь не показаны.
 */
@Composable
internal fun AccountsOrderScreen(services: AppServices, accountId: String, initial: List<FinAccount>, onBack: () -> Unit) {
    SystemBack(onBack = onBack)
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf(initial.filter { !it.archived }) }
    // Быстрые нажатия: сохранения строго по очереди, иначе в базе мог бы остаться промежуточный порядок.
    val saving = remember { Mutex() }

    fun move(from: Int, to: Int) {
        if (to !in order.indices) return
        order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
        val ids = order.map { it.id }
        scope.launch { saving.withLock { withContext(Dispatchers.IO) { services.finance.reorderAccounts(accountId, ids) } } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(stringResource(Res.string.fin_accounts_order), style = MaterialTheme.typography.titleLarge)
        }
        Text(
            stringResource(Res.string.fin_accounts_order_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(order, key = { _, a -> a.id }) { i, account ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(account.name, style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(account.type.label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { move(i, i - 1) }, enabled = i > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(Res.string.fin_move_up))
                    }
                    IconButton(onClick = { move(i, i + 1) }, enabled = i < order.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(Res.string.fin_move_down))
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
