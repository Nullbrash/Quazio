package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.LocalReminderPlatform
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.action_delete
import io.github.nullbrash.quazio.core.ui.res.cal_done
import io.github.nullbrash.quazio.core.ui.res.rem_calendars
import io.github.nullbrash.quazio.core.ui.res.rem_desktop
import io.github.nullbrash.quazio.core.ui.res.rem_desktop_hint
import io.github.nullbrash.quazio.core.ui.res.rem_end
import io.github.nullbrash.quazio.core.ui.res.rem_end_minutes
import io.github.nullbrash.quazio.core.ui.res.rem_full_screen_allow
import io.github.nullbrash.quazio.core.ui.res.rem_full_screen_needed
import io.github.nullbrash.quazio.core.ui.res.rem_kind_alarm
import io.github.nullbrash.quazio.core.ui.res.rem_kind_notification
import io.github.nullbrash.quazio.core.ui.res.rem_no_permission
import io.github.nullbrash.quazio.core.ui.res.rem_payments
import io.github.nullbrash.quazio.core.ui.res.rem_payments_hint
import io.github.nullbrash.quazio.core.ui.res.rem_persistent
import io.github.nullbrash.quazio.core.ui.res.rem_persistent_hint
import io.github.nullbrash.quazio.core.ui.res.rem_profile
import io.github.nullbrash.quazio.core.ui.res.rem_profile_add
import io.github.nullbrash.quazio.core.ui.res.rem_profile_name
import io.github.nullbrash.quazio.core.ui.res.rem_profiles
import io.github.nullbrash.quazio.core.ui.res.rem_profiles_hint
import io.github.nullbrash.quazio.core.ui.res.rem_ramp
import io.github.nullbrash.quazio.core.ui.res.rem_ramp_off
import io.github.nullbrash.quazio.core.ui.res.rem_ramp_seconds
import io.github.nullbrash.quazio.core.ui.res.rem_snooze
import io.github.nullbrash.quazio.core.ui.res.rem_snooze_minutes
import io.github.nullbrash.quazio.core.ui.res.rem_sound
import io.github.nullbrash.quazio.core.ui.res.rem_sound_default
import io.github.nullbrash.quazio.core.ui.res.rem_summary
import io.github.nullbrash.quazio.core.ui.res.rem_summary_hint
import io.github.nullbrash.quazio.core.ui.res.rem_time
import io.github.nullbrash.quazio.core.ui.res.rem_title
import io.github.nullbrash.quazio.core.ui.res.rem_vibrate
import io.github.nullbrash.quazio.core.ui.screens.finance.ColorDot
import io.github.nullbrash.quazio.core.ui.screens.finance.LabeledPicker
import io.github.nullbrash.quazio.core.ui.screens.finance.MenuOption
import io.github.nullbrash.quazio.feature.calendar.CalendarInfo
import io.github.nullbrash.quazio.feature.reminders.ReminderKind
import io.github.nullbrash.quazio.feature.reminders.ReminderProfile
import io.github.nullbrash.quazio.feature.reminders.ReminderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource

/**
 * «Настройки → Напоминания»: какие напоминания и с каким профилем; профили — как у
 * будильников AMdroid (решение пользователя), пока с малым набором настроек.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RemindersSection(services: AppServices) {
    val reminders = services.reminders ?: return
    val platform = LocalReminderPlatform.current
    val scope = rememberCoroutineScope()
    val s = reminders.settings
    var profiles by remember { mutableStateOf<List<ReminderProfile>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<ReminderProfile?>(null) }
    var pickTime by remember { mutableStateOf<String?>(null) }
    var pickCalendars by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    // Значения настроек — в состоянии экрана, чтобы переключатели сразу отражали нажатие.
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(reload) { profiles = withContext(Dispatchers.IO) { reminders.profiles.all() } }

    fun changed(block: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { block() }
            tick++
            platform?.reschedule()
        }
    }
    fun enable(block: () -> Unit) {
        scope.launch {
            val ok = platform?.ensureNotifications() ?: true
            denied = !ok
            changed(block)
        }
    }
    val profileName: (String) -> String = { id -> profiles.firstOrNull { it.id == id }?.name ?: "—" }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.rem_title), style = MaterialTheme.typography.titleMedium)
        BackgroundWarning()
        key(tick) {
            ReminderRow(stringResource(Res.string.rem_payments), stringResource(Res.string.rem_payments_hint), s.paymentsEnabled,
                { on -> if (on) enable { s.paymentsEnabled = true } else changed { s.paymentsEnabled = false } }) {
                TimeButton(s.paymentsTime) { pickTime = "payments" }
                ProfilePicker(profiles, profileName(s.paymentsProfile)) { id -> changed { s.paymentsProfile = id } }
            }
            ReminderRow(stringResource(Res.string.rem_summary), stringResource(Res.string.rem_summary_hint), s.summaryEnabled,
                { on -> if (on) enable { s.summaryEnabled = true } else changed { s.summaryEnabled = false } }) {
                TimeButton(s.summaryTime) { pickTime = "summary" }
                ProfilePicker(profiles, profileName(s.summaryProfile)) { id -> changed { s.summaryProfile = id } }
            }
            if (services.calendar != null) {
                ReminderRow(stringResource(Res.string.rem_end), null, s.eventEndEnabled,
                    { on -> if (on) enable { s.eventEndEnabled = true } else changed { s.eventEndEnabled = false } }) {
                    LabeledPicker(stringResource(Res.string.rem_end_minutes), stringResource(Res.string.rem_snooze_minutes, s.eventEndMinutes)) { dismiss ->
                        listOf(5, 10, 15, 30).forEach { m -> MenuOption(stringResource(Res.string.rem_snooze_minutes, m)) { changed { s.eventEndMinutes = m }; dismiss() } }
                    }
                    Column {
                        Text(" ", style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick = { pickCalendars = true }) { Text(stringResource(Res.string.rem_calendars)) }
                    }
                    ProfilePicker(profiles, profileName(s.eventEndProfile)) { id -> changed { s.eventEndProfile = id } }
                }
            }
            if (platform?.phone == true) {
                ReminderRow(stringResource(Res.string.rem_persistent), stringResource(Res.string.rem_persistent_hint), s.persistentEnabled,
                    { on -> if (on) enable { s.persistentEnabled = true } else changed { s.persistentEnabled = false } }) {}
            } else {
                ReminderRow(stringResource(Res.string.rem_desktop), stringResource(Res.string.rem_desktop_hint), s.desktopEnabled,
                    { on -> changed { s.desktopEnabled = on } }) {}
            }
        }
        if (denied) Text(stringResource(Res.string.rem_no_permission), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

        Text(stringResource(Res.string.rem_profiles), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(Res.string.rem_profiles_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        profiles.forEach { p ->
            Row(Modifier.fillMaxWidth().clickable { editing = p }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name)
                    Text(stringResource(if (p.kind == ReminderKind.ALARM) Res.string.rem_kind_alarm else Res.string.rem_kind_notification),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedButton(onClick = { editing = ReminderProfile("", "", ReminderKind.NOTIFICATION) }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(Res.string.rem_profile_add), modifier = Modifier.padding(start = 8.dp))
        }
    }

    editing?.let { p ->
        ProfileDialog(p, onDone = { saved ->
            editing = null
            scope.launch {
                withContext(Dispatchers.IO) { reminders.profiles.save(saved) }
                reload++
                platform?.reschedule()
            }
        }, onDelete = {
            editing = null
            scope.launch {
                withContext(Dispatchers.IO) { reminders.profiles.delete(p.id) }
                reload++
                platform?.reschedule()
            }
        }, onCancel = { editing = null })
    }
    pickTime?.let { which ->
        TimeDialog(if (which == "payments") s.paymentsTime else s.summaryTime, onDone = { t ->
            pickTime = null
            changed { if (which == "payments") s.paymentsTime = t else s.summaryTime = t }
        }, onCancel = { pickTime = null })
    }
    if (pickCalendars) {
        EventEndCalendarsDialog(services, reminders, onDone = { pickCalendars = false; tick++; platform?.reschedule() })
    }
}

@Composable
private fun key(k: Any, content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderRow(title: String, hint: String?, on: Boolean, onToggle: (Boolean) -> Unit, details: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f))
            Switch(on, onToggle)
        }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (on) FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) { details() }
    }
}

@Composable
private fun TimeButton(t: LocalTime, onClick: () -> Unit) {
    Column {
        Text(stringResource(Res.string.rem_time), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = onClick) { Text(t.toString().take(5)) }
    }
}

@Composable
private fun ProfilePicker(profiles: List<ReminderProfile>, current: String, onPick: (String) -> Unit) {
    LabeledPicker(stringResource(Res.string.rem_profile), current) { dismiss ->
        profiles.forEach { p -> MenuOption(p.name) { onPick(p.id); dismiss() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDone: (LocalTime) -> Unit, onCancel: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onCancel,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onDone(LocalTime(state.hour, state.minute)) }) { Text(stringResource(Res.string.cal_done)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileDialog(initial: ReminderProfile, onDone: (ReminderProfile) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    val platform = LocalReminderPlatform.current
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf(initial) }
    val alarm = p.kind == ReminderKind.ALARM
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (initial.builtin) initial.name else stringResource(Res.string.rem_profile)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!initial.builtin) {
                    OutlinedTextField(p.name, { p = p.copy(name = it) }, label = { Text(stringResource(Res.string.rem_profile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !alarm, onClick = { p = p.copy(kind = ReminderKind.NOTIFICATION) }, label = { Text(stringResource(Res.string.rem_kind_notification)) })
                        FilterChip(selected = alarm, onClick = { p = p.copy(kind = ReminderKind.ALARM, rampSeconds = if (p.rampSeconds == 0) 30 else p.rampSeconds) }, label = { Text(stringResource(Res.string.rem_kind_alarm)) })
                    }
                }
                if (platform?.phone == true) {
                    Column {
                        Text(stringResource(Res.string.rem_sound), style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick = {
                            scope.launch { platform.pickSound(p.soundUri, alarm)?.let { uri -> p = p.copy(soundUri = uri.ifEmpty { null }) } }
                        }) { Text(platform.soundName(p.soundUri, alarm) ?: stringResource(Res.string.rem_sound_default)) }
                    }
                }
                if (alarm) {
                    LabeledPicker(stringResource(Res.string.rem_ramp), if (p.rampSeconds == 0) stringResource(Res.string.rem_ramp_off) else stringResource(Res.string.rem_ramp_seconds, p.rampSeconds)) { dismiss ->
                        listOf(0, 10, 30, 60, 120).forEach { sec ->
                            MenuOption(if (sec == 0) stringResource(Res.string.rem_ramp_off) else stringResource(Res.string.rem_ramp_seconds, sec)) { p = p.copy(rampSeconds = sec); dismiss() }
                        }
                    }
                    if (platform?.phone == true && !platform.canFullScreen()) {
                        Text(stringResource(Res.string.rem_full_screen_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { platform.openFullScreenSettings() }) { Text(stringResource(Res.string.rem_full_screen_allow)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.rem_vibrate), modifier = Modifier.weight(1f))
                    Switch(p.vibrate, { p = p.copy(vibrate = it) })
                }
                LabeledPicker(stringResource(Res.string.rem_snooze), stringResource(Res.string.rem_snooze_minutes, p.snoozeMinutes)) { dismiss ->
                    listOf(5, 10, 15, 30).forEach { m -> MenuOption(stringResource(Res.string.rem_snooze_minutes, m)) { p = p.copy(snoozeMinutes = m); dismiss() } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(p) }) { Text(stringResource(Res.string.cal_done)) } },
        dismissButton = {
            Row {
                if (initial.id.isNotEmpty() && !initial.builtin) TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete)) }
                TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

/** Для каких календарей напоминать «до конца события» (по умолчанию — все показываемые). */
@Composable
private fun EventEndCalendarsDialog(services: AppServices, reminders: ReminderService, onDone: () -> Unit) {
    val prefs = services.calendarPrefs
    var list by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    val chosen = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(Unit) {
        val cals = withContext(Dispatchers.IO) { runCatching { services.calendar?.calendars().orEmpty() }.getOrDefault(emptyList()) }
            .filter { prefs?.isShown(it) == true }
        list = cals
        cals.forEach { chosen[it.id] = reminders.settings.eventEndFor(it, shown = true) }
    }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(Res.string.rem_calendars)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                list.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { chosen[c.id] = chosen[c.id] != true }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(chosen[c.id] == true, { chosen[c.id] = it })
                        ColorDot(c.color)
                        Text(c.name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { reminders.settings.eventEndChoices = chosen.toMap() }
                    onDone()
                }
            }) { Text(stringResource(Res.string.cal_done)) }
        },
    )
}
