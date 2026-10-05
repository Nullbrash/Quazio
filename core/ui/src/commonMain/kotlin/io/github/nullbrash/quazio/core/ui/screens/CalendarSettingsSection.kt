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
import androidx.compose.material3.HorizontalDivider
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

/**
 * Календари телефона: включение (разрешение — только здесь), обязательный список «что
 * показывать» при первом включении и выбор одного календаря для новых событий.
 */
@Composable
internal fun CalendarSettingsSection(source: CalendarSource, prefs: CalendarPrefs) {
    val scope = rememberCoroutineScope()
    val access = LocalCalendarAccess.current
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>?>(null) }
    var denied by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(enabled, reload) {
        calendars = if (enabled && access?.granted() != false) withContext(Dispatchers.IO) { source.calendars() } else null
    }

    fun openSetup() {
        scope.launch {
            val ok = access?.request() ?: true
            denied = !ok
            if (!ok) return@launch
            calendars = withContext(Dispatchers.IO) { source.calendars() }
            setup = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.cal_title), style = MaterialTheme.typography.titleMedium)
        if (!enabled) {
            Text(stringResource(Res.string.cal_intro), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = ::openSetup) { Text(stringResource(Res.string.cal_enable)) }
        } else {
            val list = calendars.orEmpty()
            val shown = prefs.shown(list)
            Text(stringResource(Res.string.cal_shown_count, shown.size, list.size))
            Text(stringResource(Res.string.cal_new_events_in, prefs.defaultFor(shown)?.name ?: stringResource(Res.string.cal_no_writable)))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = ::openSetup) { Text(stringResource(Res.string.cal_change)) }
                TextButton(onClick = { prefs.enabled = false; enabled = false }) { Text(stringResource(Res.string.cal_turn_off)) }
            }
        }
        if (denied) Text(stringResource(Res.string.cal_denied), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    val list = calendars
    if (setup && list != null) {
        CalendarSetupDialog(
            calendars = list,
            prefs = prefs,
            onDone = { choices, defaultId ->
                prefs.choices = choices
                prefs.defaultCalendarId = defaultId
                prefs.enabled = true
                setup = false
                enabled = true
                reload++
            },
            onCancel = { setup = false },
        )
    }
}

/**
 * Обязательный при первом включении список (решение пользователя): галочки «показывать»
 * (по умолчанию — как видно в системе) и один календарь для новых событий.
 */
@Composable
internal fun CalendarSetupDialog(
    calendars: List<CalendarInfo>,
    prefs: CalendarPrefs,
    onDone: (choices: Map<String, Boolean>, defaultId: String?) -> Unit,
    onCancel: () -> Unit,
) {
    val shown = remember { mutableStateMapOf<String, Boolean>().apply { calendars.forEach { put(it.id, prefs.isShown(it)) } } }
    var defaultId by remember { mutableStateOf(prefs.defaultFor(prefs.shown(calendars))?.id) }
    val writableShown = calendars.filter { it.writable && shown[it.id] == true }
    if (writableShown.none { it.id == defaultId }) defaultId = writableShown.firstOrNull()?.id

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
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(Res.string.cal_default_for_new), style = MaterialTheme.typography.labelLarge)
                if (writableShown.isEmpty()) Text(stringResource(Res.string.cal_no_writable), style = MaterialTheme.typography.bodySmall)
                writableShown.forEach { cal ->
                    Row(Modifier.fillMaxWidth().clickable { defaultId = cal.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = defaultId == cal.id, onClick = { defaultId = cal.id })
                        ColorDot(cal.color)
                        Text(cal.name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(calendars.associate { it.id to (shown[it.id] == true) }, defaultId) }) {
                Text(stringResource(Res.string.cal_done))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
