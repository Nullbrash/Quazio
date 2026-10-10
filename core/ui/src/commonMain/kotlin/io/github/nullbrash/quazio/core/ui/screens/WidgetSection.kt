package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import io.github.nullbrash.quazio.core.ui.LocalWidgetUpdater
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.widget_24
import io.github.nullbrash.quazio.core.ui.res.widget_buttons
import io.github.nullbrash.quazio.core.ui.res.widget_hint
import io.github.nullbrash.quazio.core.ui.res.widget_opacity
import io.github.nullbrash.quazio.core.ui.res.widget_title
import io.github.nullbrash.quazio.core.ui.res.widget_until
import io.github.nullbrash.quazio.feature.calendar.WidgetPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** «Настройки → Виджет на рабочем столе»: базовые настройки (решение пользователя), общие для всех виджетов. */
@Composable
internal fun WidgetSection(prefs: WidgetPrefs) {
    val update = LocalWidgetUpdater.current ?: return
    val scope = rememberCoroutineScope()
    var dial24 by remember { mutableStateOf(prefs.dial24) }
    var opacity by remember { mutableStateOf(prefs.circleOpacity.toFloat()) }
    var until by remember { mutableStateOf(prefs.showUntilNext) }
    var buttons by remember { mutableStateOf(prefs.showButtons) }
    fun save(block: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { block() }
            update()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.widget_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.widget_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SwitchRow(stringResource(Res.string.widget_24), dial24) { dial24 = it; save { prefs.dial24 = it } }
        Text(stringResource(Res.string.widget_opacity, opacity.toInt()))
        // Ступенями по 10 %, сохраняется по отпусканию — не перерисовывать виджет на каждый пиксель.
        Slider(opacity, { opacity = it }, valueRange = 0f..100f, steps = 9, onValueChangeFinished = { save { prefs.circleOpacity = opacity.toInt() } })
        SwitchRow(stringResource(Res.string.widget_until), until) { until = it; save { prefs.showUntilNext = it } }
        SwitchRow(stringResource(Res.string.widget_buttons), buttons) { buttons = it; save { prefs.showButtons = it } }
    }
}

@Composable
private fun SwitchRow(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(on, onChange)
    }
}
