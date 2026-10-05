package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.SystemBack
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.fin_accounts_order
import io.github.nullbrash.quazio.core.ui.res.fin_accounts_order_hint
import io.github.nullbrash.quazio.core.ui.res.fin_drag
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
 * Ручной порядок счетов. Перетаскивание (решение пользователя: обязательно): на телефоне —
 * долгим нажатием на строку, мышью — за «≡» сразу; стрелки — запасной способ.
 * Порядок сохраняется после каждого сдвига стрелкой и по отпусканию строки.
 * Архивные — всегда в конце, здесь не показаны.
 */
@Composable
internal fun AccountsOrderScreen(services: AppServices, accountId: String, initial: List<FinAccount>, onBack: () -> Unit) {
    SystemBack(onBack = onBack)
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf(initial.filter { !it.archived }) }
    // Быстрые сдвиги: сохранения строго по очереди, иначе в базе мог бы остаться промежуточный порядок.
    val saving = remember { Mutex() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }

    fun persist() {
        val ids = order.map { it.id }
        scope.launch { saving.withLock { withContext(Dispatchers.IO) { services.finance.reorderAccounts(accountId, ids) } } }
    }

    fun swap(from: Int, to: Int) {
        order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    fun move(from: Int, to: Int) {
        if (to !in order.indices) return
        swap(from, to)
        persist()
    }

    // Строка едет за пальцем; перешла середину соседней — меняются местами, сдвиг пересчитывается.
    fun dragBy(dy: Float) {
        val id = draggingId ?: return
        dragOffset += dy
        var i = order.indexOfFirst { it.id == id }
        while (dragOffset > 0 && i < order.lastIndex) {
            val next = heights[order[i + 1].id] ?: break
            if (dragOffset < next / 2f) break
            swap(i, i + 1); dragOffset -= next; i++
        }
        while (dragOffset < 0 && i > 0) {
            val prev = heights[order[i - 1].id] ?: break
            if (-dragOffset < prev / 2f) break
            swap(i, i - 1); dragOffset += prev; i--
        }
    }

    fun endDrag() {
        if (draggingId == null) return
        draggingId = null
        dragOffset = 0f
        persist()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Text(stringResource(Res.string.fin_accounts_order), style = MaterialTheme.typography.titleLarge)
        }
        Text(
            stringResource(Res.string.fin_accounts_order_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            order.forEachIndexed { i, account ->
                key(account.id) {
                    val dragging = account.id == draggingId
                    Column(
                        Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragging) dragOffset else 0f
                                shadowElevation = if (dragging) 12f else 0f
                            }
                            .background(if (dragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                            .onSizeChanged { heights[account.id] = it.height }
                            .pointerInput(account.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingId = account.id; dragOffset = 0f },
                                    onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                                    onDragEnd = ::endDrag,
                                    onDragCancel = ::endDrag,
                                )
                            },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .testTag("drag-${account.name}")
                                    .pointerInput(account.id) {
                                        detectDragGestures(
                                            onDragStart = { draggingId = account.id; dragOffset = 0f },
                                            onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                                            onDragEnd = ::endDrag,
                                            onDragCancel = ::endDrag,
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Menu, contentDescription = stringResource(Res.string.fin_drag)) }
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
    }
}
