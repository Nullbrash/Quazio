package io.github.nullbrash.quazio.core.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.SystemBack
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.cal_add_reminder
import io.github.nullbrash.quazio.core.ui.res.cal_all_day
import io.github.nullbrash.quazio.core.ui.res.cal_calendar
import io.github.nullbrash.quazio.core.ui.res.cal_delete_confirm
import io.github.nullbrash.quazio.core.ui.res.cal_description
import io.github.nullbrash.quazio.core.ui.res.cal_dial
import io.github.nullbrash.quazio.core.ui.res.cal_duration
import io.github.nullbrash.quazio.core.ui.res.cal_edit_event
import io.github.nullbrash.quazio.core.ui.res.cal_location
import io.github.nullbrash.quazio.core.ui.res.cal_new_event
import io.github.nullbrash.quazio.core.ui.res.cal_repeat
import io.github.nullbrash.quazio.core.ui.res.cal_repeat_custom
import io.github.nullbrash.quazio.core.ui.res.cal_repeat_daily
import io.github.nullbrash.quazio.core.ui.res.cal_repeat_monthly
import io.github.nullbrash.quazio.core.ui.res.cal_repeat_none
import io.github.nullbrash.quazio.core.ui.res.cal_repeat_until
import io.github.nullbrash.quazio.core.ui.res.cal_repeat_weekdays
import io.github.nullbrash.quazio.core.ui.res.cal_repeat_weekly
import io.github.nullbrash.quazio.core.ui.res.cal_repeat_yearly
import io.github.nullbrash.quazio.core.ui.res.cal_rem_day
import io.github.nullbrash.quazio.core.ui.res.cal_rem_hour
import io.github.nullbrash.quazio.core.ui.res.cal_rem_min
import io.github.nullbrash.quazio.core.ui.res.cal_rem_start
import io.github.nullbrash.quazio.core.ui.res.cal_read_only_event
import io.github.nullbrash.quazio.core.ui.res.cal_rem_week
import io.github.nullbrash.quazio.core.ui.res.cal_save
import io.github.nullbrash.quazio.core.ui.res.cal_scope_all
import io.github.nullbrash.quazio.core.ui.res.cal_scope_one
import io.github.nullbrash.quazio.core.ui.res.cal_scope_title
import io.github.nullbrash.quazio.core.ui.res.cal_time_zone
import io.github.nullbrash.quazio.core.ui.res.cal_title_hint
import io.github.nullbrash.quazio.core.ui.res.cal_tomorrow
import io.github.nullbrash.quazio.core.ui.res.cal_until_forever
import io.github.nullbrash.quazio.core.ui.res.lock_done
import io.github.nullbrash.quazio.core.ui.screens.finance.ColorDot
import io.github.nullbrash.quazio.core.ui.screens.finance.colorOf
import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import io.github.nullbrash.quazio.feature.calendar.CalendarInfo
import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.CalendarSource
import io.github.nullbrash.quazio.feature.calendar.DialLayouts
import io.github.nullbrash.quazio.feature.calendar.DialMode
import io.github.nullbrash.quazio.feature.calendar.EventDraft
import io.github.nullbrash.quazio.feature.calendar.Repeat
import io.github.nullbrash.quazio.feature.calendar.RepeatKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val MIN = 60_000L
private const val STEP_MIN = 15

/** Напоминания Google на выбор: при начале, минуты, часы, день, неделя. */
private val REMINDER_PRESETS = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 10080)

@Composable
private fun reminderText(m: Int) = when {
    m == 0 -> stringResource(Res.string.cal_rem_start)
    m % 10080 == 0 -> stringResource(Res.string.cal_rem_week, m / 10080)
    m % 1440 == 0 -> stringResource(Res.string.cal_rem_day, m / 1440)
    m % 60 == 0 -> stringResource(Res.string.cal_rem_hour, m / 60)
    else -> stringResource(Res.string.cal_rem_min, m)
}

private val RepeatKind.label: StringResource
    get() = when (this) {
        RepeatKind.NONE -> Res.string.cal_repeat_none
        RepeatKind.DAILY -> Res.string.cal_repeat_daily
        RepeatKind.WEEKDAYS -> Res.string.cal_repeat_weekdays
        RepeatKind.WEEKLY -> Res.string.cal_repeat_weekly
        RepeatKind.MONTHLY -> Res.string.cal_repeat_monthly
        RepeatKind.YEARLY -> Res.string.cal_repeat_yearly
        RepeatKind.CUSTOM -> Res.string.cal_repeat_custom
    }

/**
 * Окно события по образцу Sectograph: название, «весь день», начало и конец, ползунок
 * длительности, циферблат-превью, цвет, календарь, повтор, напоминания Google, место, описание.
 * У повторяющегося — «только это / все» при сохранении и удалении.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EventEditor(
    source: CalendarSource,
    prefs: CalendarPrefs,
    calendars: List<CalendarInfo>,
    target: EventTarget,
    onClose: (changed: Boolean) -> Unit,
) {
    SystemBack { onClose(false) }
    val scope = rememberCoroutineScope()
    val zone = remember { TimeZone.currentSystemDefault() }
    val writable = prefs.shown(calendars).filter { it.writable }
    var loaded by remember { mutableStateOf(target.eventId == null) }
    var seriesStart by remember { mutableStateOf<Long?>(null) }

    var title by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(millisToLocal(target.newStart ?: 0L, zone)) }
    var durationMin by remember { mutableStateOf(60L) }
    var calendarId by remember { mutableStateOf(prefs.defaultFor(prefs.shown(calendars))?.id) }
    var repeat by remember { mutableStateOf(Repeat.NONE) }
    var reminders by remember { mutableStateOf(listOf<Int>()) }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    // Цвет события в Quazio не выбирается (у календарей свои цвета — решение пользователя);
    // назначенный в Google сохраняется при правке как был.
    var colorKey by remember { mutableStateOf<String?>(null) }
    var timeZoneId by remember { mutableStateOf(zone.id) }
    var error by remember { mutableStateOf<String?>(null) }
    var askScope by remember { mutableStateOf<(suspend (Boolean) -> Unit)?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(target) {
        val id = target.eventId ?: return@LaunchedEffect
        val d = withContext(Dispatchers.IO) { source.event(id) } ?: return@LaunchedEffect onClose(false)
        seriesStart = d.start
        val instance = target.instanceStart ?: d.start
        title = d.title
        allDay = d.allDay
        start = millisToLocal(instance, if (d.allDay) TimeZone.UTC else zone)
        durationMin = ((d.end - d.start) / MIN).coerceAtLeast(0)
        calendarId = d.calendarId
        repeat = Repeat.parse(d.rrule)
        reminders = d.reminderMinutes
        location = d.location
        description = d.description
        colorKey = d.colorKey
        timeZoneId = d.timeZone
        loaded = true
    }
    if (!loaded) return
    // Календарь только для просмотра (ПК — по ссылке; телефон — чужой или праздники): правку некуда записать.
    val calendar = calendars.firstOrNull { it.id == calendarId }
    if (target.eventId != null && calendar?.writable != true) {
        EventDetails(title, allDay, start, durationMin, zone, calendar, repeat, location, description, onClose = { onClose(false) })
        return
    }

    // Весь день — даты: в Android это полночь UTC; длительность — целые дни.
    fun draft(): EventDraft? {
        val cal = calendarId ?: return null
        val (s, e) = if (allDay) {
            val days = maxOf(1L, (durationMin + 1439) / 1440)
            val s = LocalDateTime(start.date, LocalTime(0, 0)).millis(TimeZone.UTC)
            s to s + days * 1440 * MIN
        } else {
            val s = start.millis(zone)
            s to s + durationMin * MIN
        }
        return EventDraft(cal, title.trim(), s, e, allDay, timeZoneId, location.trim(), description.trim(), repeat.toRrule(), reminders, colorKey)
    }

    fun save() {
        val d = draft() ?: return
        val id = target.eventId
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        id == null -> source.create(d)
                        !target.recurring -> source.update(id, d)
                        else -> return@runCatching false
                    }
                    true
                }
            }
            result.onSuccess { done ->
                if (done) onClose(true) else askScope = { onlyThis ->
                    withContext(Dispatchers.IO) {
                        if (onlyThis) source.updateInstance(id!!, target.instanceStart!!, d.copy(rrule = null))
                        else {
                            // «Все» — сдвиг повторения переносится на всю серию.
                            val shift = d.start - (target.instanceStart ?: d.start)
                            val base = seriesStart ?: d.start
                            source.update(id!!, d.copy(start = base + shift, end = base + shift + (d.end - d.start)))
                        }
                    }
                    onClose(true)
                }
            }.onFailure { error = it.message }
        }
    }

    fun delete() {
        val id = target.eventId ?: return
        if (target.recurring) {
            askScope = { onlyThis ->
                withContext(Dispatchers.IO) { if (onlyThis) source.deleteInstance(id, target.instanceStart!!) else source.delete(id) }
                onClose(true)
            }
        } else confirmDelete = true
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onClose(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(stringResource(if (target.eventId == null) Res.string.cal_new_event else Res.string.cal_edit_event), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (target.eventId != null) IconButton(onClick = ::delete) { Icon(Icons.Filled.Delete, contentDescription = null) }
            Button(onClick = ::save, enabled = calendarId != null, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(Res.string.cal_save)) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(title, { title = it }, placeholder = { Text(stringResource(Res.string.cal_title_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.titleLarge)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.cal_all_day), modifier = Modifier.weight(1f))
                        Switch(allDay, { allDay = it; if (it && durationMin < 1440) durationMin = 1440 else if (!it && durationMin >= 1440) durationMin = 60 })
                    }
                    val end = millisToLocal(start.millis(zone) + durationMin * MIN, zone)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { pick = "startDate" }) { Text(shortDate(start.date)) }
                        if (!allDay) OutlinedButton(onClick = { pick = "startTime" }) { Text(hm(start)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { pick = "endDate" }) { Text(shortDate(if (allDay) millisToLocal(start.millis(zone) + (durationMin - 1) * MIN, zone).date else end.date)) }
                        if (!allDay) OutlinedButton(onClick = { pick = "endTime" }) { Text(hm(end)) }
                    }
                }
                // Превью на циферблате — как в окне события Sectograph.
                if (!allDay) {
                    val s = start.millis(zone)
                    val preview = CalendarEvent("preview", calendarId.orEmpty(), title, s, s + durationMin * MIN, false, null,
                        calendars.firstOrNull { it.id == calendarId }?.color, null, false, s)
                    val dayStart = start.date.startMillis(zone)
                    DayDial(
                        DialLayouts.layout(DialMode.DAY_24, listOf(preview), s, dayStart, dayStart + 1440 * MIN, { t -> millisToLocal(t, zone).let { it.hour * 60 + it.minute } }),
                        centerTop = start.day.toString(), centerBottom = hoursMinutes(durationMin), untilNextText = null,
                        tomorrowText = stringResource(Res.string.cal_tomorrow), description = stringResource(Res.string.cal_dial),
                        modifier = Modifier.size(130.dp),
                        compact = true,
                    )
                }
            }
            if (!allDay) {
                Text(stringResource(Res.string.cal_duration, hoursMinutes(durationMin)), style = MaterialTheme.typography.bodySmall)
                // Шаг 15 минут, 0 … 4 ч; дальше — «4+»: длиннее задаётся концом события.
                Slider(
                    value = (durationMin / STEP_MIN).coerceAtMost(16).toFloat(),
                    onValueChange = { durationMin = it.toLong() * STEP_MIN },
                    valueRange = 0f..16f, steps = 15,
                )
            }

            HorizontalDivider()
            Picker(stringResource(Res.string.cal_calendar), writable.firstOrNull { it.id == calendarId }?.name ?: "—") { dismiss ->
                writable.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, leadingIcon = { ColorDot(c.color) }, onClick = { calendarId = c.id; dismiss() }) }
            }
            Picker(stringResource(Res.string.cal_repeat), stringResource(repeat.kind.label)) { dismiss ->
                val kinds = RepeatKind.entries.filter { it != RepeatKind.CUSTOM || repeat.kind == RepeatKind.CUSTOM }
                kinds.forEach { k -> DropdownMenuItem(text = { Text(stringResource(k.label)) }, onClick = { repeat = if (k == repeat.kind) repeat else Repeat(k, repeat.until); dismiss() }) }
            }
            if (repeat.kind != RepeatKind.NONE && repeat.kind != RepeatKind.CUSTOM) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.cal_repeat_until), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { pick = "until" }) { Text(repeat.until?.let { shortDate(it) } ?: stringResource(Res.string.cal_until_forever)) }
                    if (repeat.until != null) IconButton(onClick = { repeat = repeat.copy(until = null) }) { Icon(Icons.Filled.Close, null) }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reminders.sorted().forEach { m ->
                    InputChip(selected = false, onClick = { reminders = reminders - m }, label = { Text(reminderText(m)) }, trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) })
                }
                var open by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { open = true }) { Text(stringResource(Res.string.cal_add_reminder)) }
                    DropdownMenu(open, { open = false }) {
                        REMINDER_PRESETS.filter { it !in reminders }.forEach { m ->
                            DropdownMenuItem(text = { Text(reminderText(m)) }, onClick = { reminders = reminders + m; open = false })
                        }
                    }
                }
            }

            HorizontalDivider()
            OutlinedTextField(location, { location = it }, label = { Text(stringResource(Res.string.cal_location)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text(stringResource(Res.string.cal_description)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Text(stringResource(Res.string.cal_time_zone, timeZoneId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(48.dp))
        }
    }

    askScope?.let { action ->
        AlertDialog(
            onDismissRequest = { askScope = null },
            title = { Text(stringResource(Res.string.cal_scope_title)) },
            text = {
                Column {
                    TextButton(onClick = { askScope = null; scope.launch { runCatching { action(true) }.onFailure { error = it.message } } }) { Text(stringResource(Res.string.cal_scope_one)) }
                    TextButton(onClick = { askScope = null; scope.launch { runCatching { action(false) }.onFailure { error = it.message } } }) { Text(stringResource(Res.string.cal_scope_all)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askScope = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(Res.string.cal_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { source.delete(target.eventId!!) } }
                            .onSuccess { onClose(true) }.onFailure { error = it.message }
                    }
                }) { Text(stringResource(Res.string.lock_done)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }

    when (pick) {
        "startDate", "endDate", "until" -> {
            val initial = when (pick) {
                "until" -> repeat.until ?: start.date
                "endDate" -> millisToLocal(start.millis(zone) + maxOf(0, durationMin - (if (allDay) 1 else 0)) * MIN, zone).date
                else -> start.date
            }
            val state = rememberDatePickerState(initialSelectedDateMillis = LocalDateTime(initial, LocalTime(0, 0)).millis(TimeZone.UTC))
            DatePickerDialog(
                onDismissRequest = { pick = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { picked ->
                            val d: LocalDate = millisToLocal(picked, TimeZone.UTC).date
                            when (pick) {
                                "startDate" -> start = LocalDateTime(d, LocalTime(start.hour, start.minute))
                                "until" -> repeat = repeat.copy(until = d)
                                else -> {
                                    // Конец — меняется длительность; раньше начала не бывает.
                                    val endAt = if (allDay) LocalDateTime(d, LocalTime(0, 0)).millis(zone) + 1440 * MIN
                                    else LocalDateTime(d, millisToLocal(start.millis(zone) + durationMin * MIN, zone).time).millis(zone)
                                    durationMin = ((endAt - start.millis(zone)) / MIN).coerceAtLeast(if (allDay) 1440 else 0)
                                }
                            }
                        }
                        pick = null
                    }) { Text(stringResource(Res.string.lock_done)) }
                },
                dismissButton = { TextButton(onClick = { pick = null }) { Text(stringResource(Res.string.action_cancel)) } },
            ) { DatePicker(state) }
        }
        "startTime", "endTime" -> {
            val at = if (pick == "startTime") start else millisToLocal(start.millis(zone) + durationMin * MIN, zone)
            val state = rememberTimePickerState(initialHour = at.hour, initialMinute = at.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { pick = null },
                confirmButton = {
                    TextButton(onClick = {
                        if (pick == "startTime") start = LocalDateTime(start.date, LocalTime(state.hour, state.minute))
                        else {
                            val endAt = LocalDateTime(at.date, LocalTime(state.hour, state.minute)).millis(zone)
                            durationMin = ((endAt - start.millis(zone)) / MIN).coerceAtLeast(0)
                        }
                        pick = null
                    }) { Text(stringResource(Res.string.lock_done)) }
                },
                dismissButton = { TextButton(onClick = { pick = null }) { Text(stringResource(Res.string.action_cancel)) } },
                text = { TimePicker(state) },
            )
        }
    }
}

@Composable
private fun Picker(label: String, value: String, items: @Composable (dismiss: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }) { Text(value) }
            DropdownMenu(open, { open = false }) { items { open = false } }
        }
    }
}

@Composable
private fun EventDetails(
    title: String,
    allDay: Boolean,
    start: LocalDateTime,
    durationMin: Long,
    zone: TimeZone,
    calendar: CalendarInfo?,
    repeat: Repeat,
    location: String,
    description: String,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(stringResource(Res.string.cal_edit_event), style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title.ifBlank { "—" }, style = MaterialTheme.typography.headlineSmall)
            val when_ = if (allDay) {
                val days = maxOf(1L, (durationMin + 1439) / 1440)
                val last = millisToLocal(start.millis(TimeZone.UTC) + (days - 1) * 1440 * MIN, TimeZone.UTC).date
                if (last == start.date) longDate(start.date) + " · " + stringResource(Res.string.cal_all_day)
                else longDate(start.date) + " – " + longDate(last)
            } else {
                val end = millisToLocal(start.millis(zone) + durationMin * MIN, zone)
                if (end.date == start.date) "${longDate(start.date)}, ${hm(start)} – ${hm(end)}"
                else "${longDate(start.date)}, ${hm(start)} – ${longDate(end.date)}, ${hm(end)}"
            }
            Text(when_, style = MaterialTheme.typography.bodyLarge)
            if (repeat.kind != RepeatKind.NONE) Text(stringResource(repeat.kind.label), style = MaterialTheme.typography.bodyMedium)
            calendar?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(it.color)
                    Text(it.name, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (location.isNotBlank()) Text(stringResource(Res.string.cal_location) + ": " + location, style = MaterialTheme.typography.bodyMedium)
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.cal_read_only_event), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
