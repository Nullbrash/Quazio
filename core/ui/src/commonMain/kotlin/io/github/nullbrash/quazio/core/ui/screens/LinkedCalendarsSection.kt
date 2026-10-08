package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.LocalCalendarRefresher
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.action_delete
import io.github.nullbrash.quazio.core.ui.res.cal_enable
import io.github.nullbrash.quazio.core.ui.res.cal_link_add
import io.github.nullbrash.quazio.core.ui.res.cal_link_add_button
import io.github.nullbrash.quazio.core.ui.res.cal_link_default_name
import io.github.nullbrash.quazio.core.ui.res.cal_link_delete_confirm
import io.github.nullbrash.quazio.core.ui.res.cal_link_error_http
import io.github.nullbrash.quazio.core.ui.res.cal_link_error_not_calendar
import io.github.nullbrash.quazio.core.ui.res.cal_link_error_not_found
import io.github.nullbrash.quazio.core.ui.res.cal_link_error_not_https
import io.github.nullbrash.quazio.core.ui.res.cal_link_error_offline
import io.github.nullbrash.quazio.core.ui.res.cal_link_error_too_large
import io.github.nullbrash.quazio.core.ui.res.cal_link_guide_title
import io.github.nullbrash.quazio.core.ui.res.cal_link_open_settings
import io.github.nullbrash.quazio.core.ui.res.cal_link_other
import io.github.nullbrash.quazio.core.ui.res.cal_link_secret_note
import io.github.nullbrash.quazio.core.ui.res.cal_link_step1
import io.github.nullbrash.quazio.core.ui.res.cal_link_step2
import io.github.nullbrash.quazio.core.ui.res.cal_link_step3
import io.github.nullbrash.quazio.core.ui.res.cal_link_step4
import io.github.nullbrash.quazio.core.ui.res.cal_link_step5
import androidx.compose.ui.platform.LocalUriHandler
import io.github.nullbrash.quazio.core.ui.res.cal_link_name
import io.github.nullbrash.quazio.core.ui.res.cal_link_never
import io.github.nullbrash.quazio.core.ui.res.cal_link_updated
import io.github.nullbrash.quazio.core.ui.res.cal_link_url
import io.github.nullbrash.quazio.core.ui.res.cal_links_intro
import io.github.nullbrash.quazio.core.ui.res.cal_title
import io.github.nullbrash.quazio.core.ui.res.cal_turn_off
import io.github.nullbrash.quazio.core.ui.res.cal_week_sunday
import io.github.nullbrash.quazio.core.ui.screens.calendar.MONTHS_GEN
import io.github.nullbrash.quazio.core.ui.screens.calendar.hm
import io.github.nullbrash.quazio.core.ui.screens.calendar.millisToLocal
import io.github.nullbrash.quazio.core.ui.screens.finance.ColorDot
import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.ical.FetchException
import io.github.nullbrash.quazio.feature.calendar.ical.FetchProblem
import io.github.nullbrash.quazio.feature.calendar.ical.IcalLink
import io.github.nullbrash.quazio.feature.calendar.ical.LinkStatus
import io.github.nullbrash.quazio.feature.calendar.ical.LinkedCalendars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * ПК: календари Google по секретным ссылкам iCal — только просмотр. Сама ссылка после
 * добавления не показывается: по ней читается весь календарь.
 */
@Composable
internal fun LinkedCalendarsSection(links: LinkedCalendars, prefs: CalendarPrefs, onChanged: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val refresher = LocalCalendarRefresher.current
    val revision = refresher?.revision?.collectAsState()?.value ?: 0
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var list by remember { mutableStateOf<List<Pair<IcalLink, LinkStatus>>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<IcalLink?>(null) }

    LaunchedEffect(reload, revision) {
        list = withContext(Dispatchers.IO) { links.links().map { it to links.status(it.id) } }
    }
    fun changed() {
        reload++
        refresher?.changed()
        onChanged()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.cal_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.cal_links_intro), style = MaterialTheme.typography.bodyMedium)
        if (enabled) {
            list.forEach { (link, status) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(link.color)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(link.name)
                        Text(
                            statusText(status),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { deleting = link }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.action_delete)) }
                }
            }
        }
        if (enabled || list.isEmpty()) {
            OutlinedButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(Res.string.cal_link_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (enabled) {
            var sunday by remember { mutableStateOf(prefs.weekStartsSunday) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.cal_week_sunday), modifier = Modifier.weight(1f))
                Switch(sunday, { sunday = it; prefs.weekStartsSunday = it })
            }
            // Выключено — ни одного обращения к сети (ссылки и прошлая загрузка остаются).
            TextButton(onClick = { prefs.enabled = false; enabled = false; changed() }) { Text(stringResource(Res.string.cal_turn_off)) }
        } else if (list.isNotEmpty()) {
            Button(onClick = { prefs.enabled = true; enabled = true; changed() }) { Text(stringResource(Res.string.cal_enable)) }
        }
    }

    if (adding) {
        AddLinkDialog(
            links = links,
            onAdded = {
                adding = false
                prefs.enabled = true
                enabled = true
                changed()
            },
            onCancel = { adding = false },
        )
    }
    deleting?.let { link ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(Res.string.cal_link_delete_confirm, link.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        withContext(Dispatchers.IO) { links.remove(link.id) }
                        changed()
                    }
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun AddLinkDialog(links: LinkedCalendars, onAdded: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun add() {
        busy = true
        error = null
        scope.launch {
            val fallback = getString(Res.string.cal_link_default_name)
            val result = withContext(Dispatchers.IO) { runCatching { links.add(url, name, fallback) } }
            busy = false
            result.onSuccess { onAdded() }.onFailure { e ->
                error = when (e) {
                    is FetchException -> problemText(e.problem, e.httpCode)
                    is IllegalArgumentException -> getString(Res.string.cal_link_error_not_https)
                    else -> getString(Res.string.cal_link_error_offline)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text(stringResource(Res.string.cal_link_add)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinkGuide()
                OutlinedTextField(url, { url = it; error = null }, label = { Text(stringResource(Res.string.cal_link_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.cal_link_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = ::add, enabled = !busy && url.isNotBlank()) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(Res.string.cal_link_add_button))
            }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/**
 * Пошагово, где взять ссылку (пожелание пользователя). Кнопка открывает настройки Google в
 * браузере — это браузер, а не сеть Quazio.
 */
@Composable
private fun LinkGuide() {
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.cal_link_guide_title), style = MaterialTheme.typography.titleSmall)
        Step(1, stringResource(Res.string.cal_link_step1))
        OutlinedButton(onClick = { runCatching { uri.openUri(GOOGLE_CALENDAR_SETTINGS) } }, modifier = Modifier.padding(start = 24.dp)) {
            Text(stringResource(Res.string.cal_link_open_settings))
        }
        Step(2, stringResource(Res.string.cal_link_step2))
        Step(3, stringResource(Res.string.cal_link_step3))
        Step(4, stringResource(Res.string.cal_link_step4))
        Step(5, stringResource(Res.string.cal_link_step5))
        Text(stringResource(Res.string.cal_link_secret_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(Res.string.cal_link_other), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row {
        Text("$n.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val GOOGLE_CALENDAR_SETTINGS = "https://calendar.google.com/calendar/r/settings"

@Composable
internal fun statusText(status: LinkStatus): String {
    val at = status.fetchedAt?.let { formatMoment(it) }
    val updated = at?.let { stringResource(Res.string.cal_link_updated, it) } ?: stringResource(Res.string.cal_link_never)
    val problem = status.problem ?: return updated
    return problemTextNow(problem, status.httpCode) + " · " + updated
}

private fun formatMoment(millis: Long): String {
    val t = millisToLocal(millis, TimeZone.currentSystemDefault())
    return "${t.day} ${MONTHS_GEN[t.month.ordinal].take(3)}., ${hm(t)}"
}

@Composable
private fun problemTextNow(problem: FetchProblem, code: Int?): String = when (problem) {
    FetchProblem.OFFLINE -> stringResource(Res.string.cal_link_error_offline)
    FetchProblem.NOT_FOUND -> stringResource(Res.string.cal_link_error_not_found)
    FetchProblem.HTTP -> stringResource(Res.string.cal_link_error_http, code ?: 0)
    FetchProblem.NOT_CALENDAR -> stringResource(Res.string.cal_link_error_not_calendar)
    FetchProblem.TOO_LARGE -> stringResource(Res.string.cal_link_error_too_large)
}

private suspend fun problemText(problem: FetchProblem, code: Int?): String = when (problem) {
    FetchProblem.OFFLINE -> getString(Res.string.cal_link_error_offline)
    FetchProblem.NOT_FOUND -> getString(Res.string.cal_link_error_not_found)
    FetchProblem.HTTP -> getString(Res.string.cal_link_error_http, code ?: 0)
    FetchProblem.NOT_CALENDAR -> getString(Res.string.cal_link_error_not_calendar)
    FetchProblem.TOO_LARGE -> getString(Res.string.cal_link_error_too_large)
}
