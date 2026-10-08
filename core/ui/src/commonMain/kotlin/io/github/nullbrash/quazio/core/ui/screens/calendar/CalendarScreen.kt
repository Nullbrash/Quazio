package io.github.nullbrash.quazio.core.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import io.github.nullbrash.quazio.core.ui.LocalCalendarRefresher
import io.github.nullbrash.quazio.core.ui.res.cal_refresh
import io.github.nullbrash.quazio.core.ui.res.cal_payment
import io.github.nullbrash.quazio.core.ui.res.cal_payment_waiting
import io.github.nullbrash.quazio.core.ui.QuazioIcons
import io.github.nullbrash.quazio.core.ui.screens.statusText
import io.github.nullbrash.quazio.feature.calendar.ical.LinkStatus
import io.github.nullbrash.quazio.feature.calendar.ical.LinkedCalendars
import kotlinx.coroutines.launch
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.nullbrash.quazio.core.ui.LocalCalendarAccess
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.cal_add_event
import io.github.nullbrash.quazio.core.ui.res.cal_all_day
import io.github.nullbrash.quazio.core.ui.res.cal_dial
import io.github.nullbrash.quazio.core.ui.res.cal_dial_12
import io.github.nullbrash.quazio.core.ui.res.cal_dial_24
import io.github.nullbrash.quazio.core.ui.res.cal_next_day
import io.github.nullbrash.quazio.core.ui.res.cal_no_events
import io.github.nullbrash.quazio.core.ui.res.cal_prev_day
import io.github.nullbrash.quazio.core.ui.res.cal_today
import io.github.nullbrash.quazio.core.ui.res.cal_tomorrow
import io.github.nullbrash.quazio.core.ui.res.cal_view_day
import io.github.nullbrash.quazio.core.ui.res.cal_view_month
import io.github.nullbrash.quazio.core.ui.res.cal_view_week
import io.github.nullbrash.quazio.core.ui.screens.CalendarSettingsSection
import io.github.nullbrash.quazio.core.ui.screens.finance.ColorDot
import io.github.nullbrash.quazio.core.ui.screens.finance.colorOf
import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import io.github.nullbrash.quazio.feature.calendar.CalendarInfo
import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.CalendarSource
import io.github.nullbrash.quazio.feature.calendar.DialLayouts
import io.github.nullbrash.quazio.feature.calendar.DialMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

internal enum class CalendarView { DAY, WEEK, MONTH }

/** Что открыть в окне события: новое (с началом [newStart]) или повторение существующего. */
internal data class EventTarget(val eventId: String?, val instanceStart: Long?, val recurring: Boolean, val newStart: Long?)

/** Память вкладки между переключениями разделов: открывается сразу, свежие события догружаются. */
class CalendarCache {
    internal var calendars: List<CalendarInfo> = emptyList()
    internal var events: List<CalendarEvent> = emptyList()
}

private const val HOUR_MS = 3_600_000L

/**
 * Календарь: день на циферблате + список, неделя, месяц. События — из календарей устройства
 * (своих Quazio не хранит); [jumpTo] — открыть месяц с этой датой (из финансов).
 *
 * В виде «день» показывается момент [cursor]: пока его не трогали, он идёт вместе с часами;
 * стрелку тянут по кругу — время пролистывается вперёд и назад, через полночь — на другой день.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(
    source: CalendarSource,
    prefs: CalendarPrefs,
    jumpTo: LocalDate? = null,
    onJumpHandled: () -> Unit = {},
    cache: CalendarCache = remember { CalendarCache() },
    /** Регулярные платежи на отрезок дат [from, toExclusive) — слой поверх календарей. */
    payments: ((LocalDate, LocalDate) -> List<CalendarEvent>)? = null,
    onOpenPayment: (String) -> Unit = {},
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val access = LocalCalendarAccess.current
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    val today = millisToLocal(now, zone).date
    // Из финансов — сразу месяц: иначе экран сначала рисовал день с циферблатом (была задержка).
    var view by remember { mutableStateOf(if (jumpTo != null) CalendarView.MONTH else CalendarView.DAY) }
    var date by remember { mutableStateOf(jumpTo ?: today) }
    var cursor by remember { mutableLongStateOf(now) }
    var live by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(prefs.enabled && access?.granted() != false) }
    var calendars by remember { mutableStateOf(cache.calendars) }
    var events by remember { mutableStateOf(cache.events) }
    var dial24 by remember { mutableStateOf(prefs.dial24) }
    var target by remember { mutableStateOf<EventTarget?>(null) }
    var reload by remember { mutableStateOf(0) }
    // ПК: календари по ссылкам загружаются по сети — при открытии, по расписанию и кнопкой.
    val refresher = LocalCalendarRefresher.current
    val revision = refresher?.revision?.collectAsState()?.value ?: 0
    val refreshing = refresher?.running?.collectAsState()?.value ?: false
    var linkProblem by remember { mutableStateOf<LinkStatus?>(null) }
    val refreshScope = rememberCoroutineScope()

    LaunchedEffect(jumpTo) {
        if (jumpTo != null) {
            date = jumpTo
            view = CalendarView.MONTH
            onJumpHandled()
        }
    }
    // Стрелка и «осталось» — раз в минуту; изменения из Google — при возвращении в приложение.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - Clock.System.now().toEpochMilliseconds() % 60_000)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }
    LaunchedEffect(now) { if (live) cursor = now }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { reload++ }
    LaunchedEffect(enabled) { if (enabled) refresher?.onCalendarOpened() }

    fun backToNow() {
        cursor = now
        live = true
        date = today
    }
    /** Другой день — то же время суток. Вид «12 / 24 ч» — один на все дни (решение пользователя). */
    fun openDay(d: LocalDate) {
        if (d == today) backToNow() else {
            val timeOfDay = now - today.startMillis(zone)
            cursor = d.startMillis(zone) + timeOfDay
            live = false
            date = d
        }
        view = CalendarView.DAY
    }

    val dayDate = millisToLocal(cursor, zone).date
    val sunday = prefs.weekStartsSunday
    // День — двое суток: окно 12 часов от стрелки заходит в завтрашний день.
    val (from, to) = when (view) {
        CalendarView.DAY -> dayDate.startMillis(zone) to dayDate.plus(DatePeriod(days = 2)).startMillis(zone)
        CalendarView.WEEK -> weekStart(date, sunday).let { it.startMillis(zone) to it.plus(DatePeriod(days = 7)).startMillis(zone) }
        CalendarView.MONTH -> monthGridStart(date, sunday).let { it.startMillis(zone) to it.plus(DatePeriod(days = 42)).startMillis(zone) }
    }
    LaunchedEffect(enabled, from, to, reload, revision) {
        if (!enabled) return@LaunchedEffect
        val (cals, evs) = withContext(Dispatchers.IO) {
            val cals = source.calendars()
            linkProblem = (source as? LinkedCalendars)?.let { l -> l.links().map { l.status(it.id) }.firstOrNull { it.problem != null } }
            val own = source.events(from, to, prefs.shown(cals).mapTo(HashSet()) { it.id })
            val extra = payments?.let { load ->
                runCatching { load(millisToLocal(from, zone).date, millisToLocal(to, zone).date.plus(DatePeriod(days = 1))) }.getOrDefault(emptyList())
            }.orEmpty()
            cals to own + extra
        }
        calendars = cals
        events = evs
        cache.calendars = cals
        cache.events = evs
    }

    if (!enabled) {
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            CalendarSettingsSection(source, prefs, onChanged = { enabled = prefs.enabled && access?.granted() != false })
        }
        return
    }

    target?.let { t ->
        EventEditor(source, prefs, calendars, t, onClose = { changed -> target = null; if (changed) reload++ })
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val titleBlock: @Composable () -> Unit = {
                if (view == CalendarView.DAY) {
                    // В «дне» стрелки переключения — внизу у циферблата (пожелание пользователя).
                    Text(title(dayDate, view, sunday), style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                } else {
                    IconButton(onClick = { date = shift(date, view, -1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
                    Text(title(date, view, sunday), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    IconButton(onClick = { date = shift(date, view, 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                }
            }
            val viewChips: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CalendarView.DAY to Res.string.cal_view_day, CalendarView.WEEK to Res.string.cal_view_week, CalendarView.MONTH to Res.string.cal_view_month)
                        .forEach { (v, label) ->
                            FilterChip(selected = view == v, onClick = { if (v == CalendarView.DAY) openDay(if (view == CalendarView.DAY) dayDate else date) else { date = if (view == CalendarView.DAY) dayDate else date; view = v } }, label = { Text(stringResource(label)) })
                        }
                }
            }
            val actions: @Composable () -> Unit = {
                if (refresher != null) {
                    if (refreshing) CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { refreshScope.launch { refresher.refresh() } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.cal_refresh))
                    }
                }
                TextButton(onClick = ::backToNow) { Text(stringResource(Res.string.cal_today)) }
            }
            // Широкое окно — дата и «День / Неделя / Месяц» в одну строку (пожелание пользователя);
            // на узком телефоне всё вместе не помещается — две строки.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= WIDE_HEADER) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        titleBlock()
                        Spacer(Modifier.size(24.dp))
                        viewChips()
                        Spacer(Modifier.weight(1f))
                        actions()
                    }
                } else {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            titleBlock()
                            Spacer(Modifier.weight(1f))
                            actions()
                        }
                        Box(Modifier.padding(horizontal = 16.dp)) { viewChips() }
                    }
                }
            }
            // Загрузка не удалась — видна прошлая; говорим об этом, а не молча показываем старое.
            linkProblem?.let {
                Text(statusText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
            val calendarName = calendars.associate { it.id to it.name }
            val open: (CalendarEvent) -> Unit = { e ->
                val p = e.payment
                if (p != null) onOpenPayment(p.recurringId) else target = EventTarget(e.eventId, e.instanceStart, e.recurring, null)
            }
            when (view) {
                CalendarView.DAY -> {
                    val full24 = dial24
                    DayView(
                        dayDate, today, cursor, live, zone, events, calendarName, full24,
                        onToggle = { dial24 = it; prefs.dial24 = it },
                        onShiftDay = { n -> openDay(dayDate.plus(DatePeriod(days = n))) },
                        // 12 часов — 2 минуты на градус, сутки — 4.
                        onDrag = { deg -> cursor += (deg * (if (full24) 4f else 2f) * 60_000f).toLong(); live = false },
                        onCenterTap = ::backToNow,
                        onOpen = open,
                    )
                }
                CalendarView.WEEK -> WeekView(weekStart(date, sunday), today, zone, events, onDay = ::openDay, onOpen = open)
                CalendarView.MONTH -> MonthView(date, today, zone, events, sunday, onDay = ::openDay)
            }
        }
        // Писать некуда (на ПК календари по ссылке — только просмотр) — кнопки нового события нет.
        if (prefs.shown(calendars).any { it.writable }) FloatingActionButton(
            onClick = {
                // Новое событие — ближайший полный час от показанного времени.
                val base = if (view == CalendarView.DAY) cursor else maxOf(now, date.startMillis(zone) + 9 * HOUR_MS)
                target = EventTarget(null, null, false, (base / HOUR_MS + 1) * HOUR_MS)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.cal_add_event)) }
    }
}

private fun shift(d: LocalDate, view: CalendarView, n: Int): LocalDate = when (view) {
    CalendarView.DAY -> d.plus(DatePeriod(days = n))
    CalendarView.WEEK -> d.plus(DatePeriod(days = 7 * n))
    CalendarView.MONTH -> d.plus(DatePeriod(months = n))
}

private fun title(d: LocalDate, view: CalendarView, sunday: Boolean): String = when (view) {
    CalendarView.DAY -> shortDate(d)
    CalendarView.WEEK -> {
        val a = weekStart(d, sunday)
        val b = a.plus(DatePeriod(days = 6))
        if (a.month == b.month) "${a.day}–${b.day} ${MONTHS_GEN[b.month.ordinal]}"
        else "${a.day} ${MONTHS_GEN[a.month.ordinal].take(3)}. – ${b.day} ${MONTHS_GEN[b.month.ordinal].take(3)}."
    }
    CalendarView.MONTH -> "${MONTHS_NOM[d.month.ordinal]} ${d.year}"
}

private fun monthGridStart(d: LocalDate, sunday: Boolean): LocalDate = weekStart(LocalDate(d.year, d.month, 1), sunday)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayView(
    date: LocalDate,
    today: LocalDate,
    cursor: Long,
    live: Boolean,
    zone: TimeZone,
    events: List<CalendarEvent>,
    calendarName: Map<String, String>,
    full24: Boolean,
    onToggle: (Boolean) -> Unit,
    onShiftDay: (Int) -> Unit,
    onDrag: (Float) -> Unit,
    onCenterTap: () -> Unit,
    onOpen: (CalendarEvent) -> Unit,
) {
    val dayEvents = events.filter { it.occursOn(date, zone) }.sortedWith(compareBy({ !it.allDay }, { it.start }))
    val computed = DialLayouts.layout(
        if (full24) DialMode.DAY_24 else DialMode.SLIDING_12,
        events, cursor, date.startMillis(zone), date.nextDay().startMillis(zone),
        minuteOfDay = { t -> millisToLocal(t, zone).let { it.hour * 60 + it.minute } },
    )
    // «Осталось до следующего» — только про настоящее «сейчас», не про пролистанное время.
    val layout = if (live) computed else computed.copy(untilNext = null)
    val at = millisToLocal(cursor, zone)
    val allDayText = stringResource(Res.string.cal_all_day)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        // Над циферблатом ничего не появляется и не исчезает: циферблат и кнопки дней всегда
        // на одном месте (события на весь день сдвигали их — нажимали не туда).
        item {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                DayDial(
                    layout = layout,
                    centerTop = hm(at),
                    centerBottom = if (date == today) "${date.day}.${date.month.ordinal + 1}" else "${WEEKDAYS_SHORT[date.dayOfWeek.ordinal]} ${date.day}.${date.month.ordinal + 1}",
                    untilNextText = layout.untilNext?.let { hoursMinutes(it.minutes) },
                    tomorrowText = stringResource(Res.string.cal_tomorrow),
                    description = stringResource(Res.string.cal_dial),
                    modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
                    onDrag = onDrag,
                    onCenterTap = onCenterTap,
                )
            }
        }
        // Переключение дней — слева и справа от «12 ч / 24 ч», над списком (пожелание пользователя).
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShiftDay(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(Res.string.cal_prev_day)) }
                Spacer(Modifier.weight(1f))
                FilterChip(selected = !full24, onClick = { onToggle(false) }, label = { Text(stringResource(Res.string.cal_dial_12)) })
                Spacer(Modifier.size(8.dp))
                FilterChip(selected = full24, onClick = { onToggle(true) }, label = { Text(stringResource(Res.string.cal_dial_24)) })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onShiftDay(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(Res.string.cal_next_day)) }
            }
        }
        item { HorizontalDivider() }
        // События на весь день — чипами в начале списка, под кнопками.
        val allDay = dayEvents.filter { it.allDay }
        if (allDay.isNotEmpty()) item {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(allDayText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp))
                allDay.forEach { e -> AssistChip(onClick = { onOpen(e) }, label = { Text(eventTitle(e), maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { EventMark(e) }) }
            }
        }
        if (dayEvents.isEmpty()) item {
            Text(stringResource(Res.string.cal_no_events), modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
        }
        dayEvents.filter { !it.allDay }.forEach { e ->
            item {
                EventRow(e, e.timeText(date, zone, allDayText), calendarName[e.calendarId], onClick = { onOpen(e) })
            }
        }
    }
}

@Composable
private fun EventRow(e: CalendarEvent, time: String, calendar: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        EventMark(e)
        Text(time, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp).widthIn(min = 92.dp))
        Column(Modifier.weight(1f)) {
            Text(eventTitle(e), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val extra = listOfNotNull(e.location, calendar).joinToString(" · ")
            if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WeekView(start: LocalDate, today: LocalDate, zone: TimeZone, events: List<CalendarEvent>, onDay: (LocalDate) -> Unit, onOpen: (CalendarEvent) -> Unit) {
    val allDayText = stringResource(Res.string.cal_all_day)
    val paymentText = stringResource(Res.string.cal_payment)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        // День — слева от своих событий, коротко «5 – Пн» (пожелание пользователя).
        (0 until 7).forEach { i ->
            val d = start.plus(DatePeriod(days = i))
            val dayEvents = events.filter { it.occursOn(d, zone) }.sortedWith(compareBy({ !it.allDay }, { it.start }))
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Text(
                        "${d.day} – ${WEEKDAYS_SHORT[d.dayOfWeek.ordinal]}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (d == today) FontWeight.Bold else null,
                        color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(WEEK_DAY_COLUMN).fillMaxHeight().clickable { onDay(d) }.padding(start = 16.dp, top = 12.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        if (dayEvents.isEmpty()) Text("—", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        dayEvents.forEach { e -> EventRow(e, if (e.payment != null) paymentText else e.timeText(d, zone, allDayText), null, onClick = { onOpen(e) }) }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MonthView(date: LocalDate, today: LocalDate, zone: TimeZone, events: List<CalendarEvent>, sunday: Boolean, onDay: (LocalDate) -> Unit) {
    val start = monthGridStart(date, sunday)
    val names = if (sunday) listOf(WEEKDAYS_SHORT.last()) + WEEKDAYS_SHORT.dropLast(1) else WEEKDAYS_SHORT
    // Клетка — по ширине, но не выше, чем помещается шесть недель: на широком окне ПК иначе обрезалось.
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        val cellHeight = minOf(maxWidth / 7 / 0.8f, (maxHeight - 24.dp) / 6)
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                names.forEach { n -> Text(n, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            (0 until 6).forEach { w ->
                Row(Modifier.fillMaxWidth()) {
                    (0 until 7).forEach { i ->
                        val d = start.plus(DatePeriod(days = w * 7 + i))
                        val dayEvents = events.filter { it.occursOn(d, zone) }
                        val isToday = d == today
                        val inMonth = d.month == date.month
                        val colors = MaterialTheme.colorScheme
                        // Каждое число — карточка с видимой границей, нажимается целиком (пожелание
                        // пользователя); свободное место в ней — под будущие сведения (платежи и т. п.).
                        Column(
                            Modifier.weight(1f).height(cellHeight).padding(2.dp)
                                .clip(CELL_SHAPE)
                                .background(if (inMonth) colors.surfaceContainer else colors.surface)
                                .border(if (isToday) 2.dp else 1.dp, if (isToday) colors.primary else colors.outlineVariant, CELL_SHAPE)
                                .clickable { onDay(d) }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(if (isToday) colors.primary else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    d.day.toString(),
                                    color = when {
                                        isToday -> MaterialTheme.colorScheme.onPrimary
                                        !inMonth -> MaterialTheme.colorScheme.outline
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                dayEvents.take(4).forEach { e -> Box(Modifier.size(6.dp).clip(CircleShape).background(colorOf(e.color))) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CELL_SHAPE = RoundedCornerShape(8.dp)

/** Точка цвета календаря; у платежа — значок денег. */
@Composable
private fun EventMark(e: CalendarEvent) {
    if (e.payment != null) Icon(QuazioIcons.Cash, contentDescription = null, tint = colorOf(e.color), modifier = Modifier.size(16.dp))
    else ColorDot(e.color)
}

/** Неподтверждённый платёж (режим «спрашивать») — с пометкой. */
@Composable
private fun eventTitle(e: CalendarEvent): String {
    val title = e.title.ifBlank { "—" }
    return if (e.payment?.waiting == true) "$title · ${stringResource(Res.string.cal_payment_waiting)}" else title
}

private val WIDE_HEADER = 600.dp
private val WEEK_DAY_COLUMN = 84.dp
