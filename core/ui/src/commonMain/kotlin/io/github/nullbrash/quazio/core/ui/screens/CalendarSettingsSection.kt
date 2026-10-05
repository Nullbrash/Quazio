package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.LocalCalendarAccess
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.cal_change
import io.github.nullbrash.quazio.core.ui.res.cal_choose
import io.github.nullbrash.quazio.core.ui.res.cal_default_hint
import io.github.nullbrash.quazio.core.ui.res.cal_next
import io.github.nullbrash.quazio.core.ui.res.cal_default_for_new
import io.github.nullbrash.quazio.core.ui.res.cal_denied
import io.github.nullbrash.quazio.core.ui.res.cal_done
import io.github.nullbrash.quazio.core.ui.res.cal_enable
import io.github.nullbrash.quazio.core.ui.res.cal_intro
import io.github.nullbrash.quazio.core.ui.res.cal_new_events_in
import io.github.nullbrash.quazio.core.ui.res.cal_no_writable
import io.github.nullbrash.quazio.core.ui.res.cal_read_only
import io.github.nullbrash.quazio.core.ui.res.cal_setup_hint
import io.github.nullbrash.quazio.core.ui.res.cal_setup_title
import io.github.nullbrash.quazio.core.ui.res.cal_shown_count
import io.github.nullbrash.quazio.core.ui.res.cal_title
import io.github.nullbrash.quazio.core.ui.res.cal_turn_off
import io.github.nullbrash.quazio.core.ui.screens.finance.ColorDot
import io.github.nullbrash.quazio.feature.calendar.CalendarInfo
import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.CalendarSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private enum class CalendarDialog { NONE, SHOWN, DEFAULT }

/**
 * Календари телефона: включение (разрешение — только здесь), при первом включении — обязательно
 * два окна по очереди: «что показывать», затем «куда записывать новые события» (два отдельных
 * списка — пожелание пользователя); потом каждое меняется своей кнопкой.
 */
@Composable
internal fun CalendarSettingsSection(source: CalendarSource, prefs: CalendarPrefs) {
    val scope = rememberCoroutineScope()
    val access = LocalCalendarAccess.current
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>?>(null) }
    var denied by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(CalendarDialog.NONE) }
    // Первое включение: выбор «что показывать» держим до второго окна — до «Готово» ничего не сохраняется.
    var pendingChoices by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(enabled, reload) {
        calendars = if (enabled && access?.granted() != false) withContext(Dispatchers.IO) { source.calendars() } else null
    }

    fun open(which: CalendarDialog) {
        scope.launch {
            val ok = access?.request() ?: true
            denied = !ok
            if (!ok) return@launch
            calendars = withContext(Dispatchers.IO) { source.calendars() }
            dialog = which
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.cal_title), style = MaterialTheme.typography.titleMedium)
        if (!enabled) {
            Text(stringResource(Res.string.cal_intro), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { open(CalendarDialog.SHOWN) }) { Text(stringResource(Res.string.cal_enable)) }
        } else {
            val list = calendars.orEmpty()
            val shown = prefs.shown(list)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.cal_shown_count, shown.size, list.size), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { open(CalendarDialog.SHOWN) }) { Text(stringResource(Res.string.cal_change)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.cal_new_events_in, prefs.defaultFor(shown)?.name ?: stringResource(Res.string.cal_no_writable)),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { open(CalendarDialog.DEFAULT) }) { Text(stringResource(Res.string.cal_choose)) }
            }
            TextButton(onClick = { prefs.enabled = false; enabled = false }) { Text(stringResource(Res.string.cal_turn_off)) }
        }
        if (denied) Text(stringResource(Res.string.cal_denied), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    val list = calendars ?: return
    val firstRun = !enabled
    when (dialog) {
        CalendarDialog.NONE -> Unit
        CalendarDialog.SHOWN -> ShownCalendarsDialog(
            calendars = list,
            initial = list.associate { it.id to prefs.isShown(it) },
            confirm = stringResource(if (firstRun) Res.string.cal_next else Res.string.cal_done),
            onDone = { choices ->
                if (firstRun) {
                    pendingChoices = choices
                    dialog = CalendarDialog.DEFAULT
                } else {
                    prefs.choices = choices
                    dialog = CalendarDialog.NONE
                    reload++
                }
            },
            onCancel = { dialog = CalendarDialog.NONE; pendingChoices = null },
        )
        CalendarDialog.DEFAULT -> {
            val choices = pendingChoices ?: list.associate { it.id to prefs.isShown(it) }
            val candidates = list.filter { it.writable && choices[it.id] == true }
            val current = prefs.defaultFor(candidates)?.id
            DefaultCalendarDialog(
                candidates = candidates,
                initial = current,
                onDone = { defaultId ->
                    if (firstRun) {
                        prefs.choices = choices
                        prefs.enabled = true
                        enabled = true
                    }
                    prefs.defaultCalendarId = defaultId
                    pendingChoices = null
                    dialog = CalendarDialog.NONE
                    reload++
                },
                onCancel = { dialog = CalendarDialog.NONE; pendingChoices = null },
            )
        }
    }
}

/** Какие календари показывать (по умолчанию — как видно в системе). */
@Composable
internal fun ShownCalendarsDialog(
    calendars: List<CalendarInfo>,
    initial: Map<String, Boolean>,
    confirm: String,
    onDone: (Map<String, Boolean>) -> Unit,
    onCancel: () -> Unit,
) {
    val shown = remember { mutableStateMapOf<String, Boolean>().apply { putAll(initial) } }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.cal_setup_title)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.cal_setup_hint), style = MaterialTheme.typography.bodySmall)
                calendars.groupBy { it.accountName }.forEach { (account, group) ->
                    Text(account, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    group.forEach { cal ->
                        Row(
                            Modifier.fillMaxWidth().clickable { shown[cal.id] = shown[cal.id] != true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = shown[cal.id] == true, onCheckedChange = { shown[cal.id] = it })
                            ColorDot(cal.color)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(cal.name)
                                if (!cal.writable) Text(stringResource(Res.string.cal_read_only), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(calendars.associate { it.id to (shown[it.id] == true) }) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/**
 * Один календарь для новых событий — из показываемых и доступных для записи: событие в
 * скрытом календаре «пропало» бы с циферблата.
 */
@Composable
internal fun DefaultCalendarDialog(
    candidates: List<CalendarInfo>,
    initial: String?,
    onDone: (String?) -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.cal_default_for_new)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.cal_default_hint), style = MaterialTheme.typography.bodySmall)
                if (candidates.isEmpty()) Text(stringResource(Res.string.cal_no_writable), style = MaterialTheme.typography.bodySmall)
                candidates.forEach { cal ->
                    Row(Modifier.fillMaxWidth().clickable { selected = cal.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == cal.id, onClick = { selected = cal.id })
                        ColorDot(cal.color)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(cal.name)
                            Text(cal.accountName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(selected) }) { Text(stringResource(Res.string.cal_done)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
