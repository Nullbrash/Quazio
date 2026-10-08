package io.github.nullbrash.quazio.feature.reminders

import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.CalendarSource
import io.github.nullbrash.quazio.feature.calendar.KeyValueStore
import io.github.nullbrash.quazio.feature.finance.Occurrence
import io.github.nullbrash.quazio.feature.finance.OccurrenceState
import io.github.nullbrash.quazio.feature.finance.RecurringMode
import io.github.nullbrash.quazio.feature.finance.RecurringService
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Что напомнить. [key] — один и тот же у одного напоминания: по нему не показываем дважды. */
sealed interface Reminder {
    val key: String
    val at: Long
    val profileId: String

    /** Регулярный платёж: накануне ([daysBefore] > 0) и в день (у срока с разбросом — в первый день). */
    data class Payment(
        override val key: String, override val at: Long, override val profileId: String,
        val recurringId: String, val date: LocalDate, val name: String, val ask: Boolean, val daysBefore: Int,
    ) : Reminder

    /** Утренняя сводка дня; содержимое собирается в момент показа ([ReminderService.summary]). */
    data class Summary(override val key: String, override val at: Long, override val profileId: String, val date: LocalDate) : Reminder

    /** «Через N минут заканчивается событие». */
    data class EventEnd(
        override val key: String, override val at: Long, override val profileId: String,
        val title: String, val end: Long, val minutes: Int,
    ) : Reminder
}

/** Утренняя сводка: события дня, платежи дня и сколько ждут подтверждения. */
data class DaySummary(val events: List<CalendarEvent>, val payments: List<Occurrence>, val pendingCount: Int)

/** «Что сейчас / что дальше» для постоянного уведомления. */
data class NowNext(val current: CalendarEvent?, val next: CalendarEvent?)

/**
 * Напоминания Quazio — только о том, о чём не знает Google (о его событиях напоминает он
 * сам): платежи, сводка дня, «до конца события». Считает, что и когда показать; показывает
 * платформа (Android — точные будильники системы, ПК — уведомления Windows, пока окно открыто).
 * Методы блокирующие — не из главного потока.
 */
class ReminderService(
    val profiles: ProfileStore,
    val settings: ReminderSettings,
    private val store: KeyValueStore,
    private val recurring: RecurringService,
    private val accountId: () -> String,
    private val calendar: CalendarSource?,
    private val calendarPrefs: CalendarPrefs?,
    private val calendarReadable: () -> Boolean = { true },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {

    /** Напоминания, чей момент в [from, to), по порядку. */
    fun upcoming(from: Long, to: Long): List<Reminder> {
        val z = zone()
        val firstDay = Instant.fromEpochMilliseconds(from).toLocalDateTime(z).date.minus(DatePeriod(days = 1))
        val lastDay = Instant.fromEpochMilliseconds(to).toLocalDateTime(z).date.plus(DatePeriod(days = 1))
        val out = ArrayList<Reminder>()
        val s = settings
        if (s.paymentsEnabled) {
            val maxBefore = 7
            val time = s.paymentsTime
            recurring.occurrences(accountId(), firstDay, lastDay.plus(DatePeriod(days = maxBefore + 1)), firstDay)
                .filter { it.state == OccurrenceState.PLANNED || it.state == OccurrenceState.PENDING }
                .forEach { o ->
                    val r = o.recurring
                    val days = if (r.remindDays > 0) listOf(r.remindDays, 0) else listOf(0)
                    days.forEach { before ->
                        val at = millis(o.date.minus(DatePeriod(days = before)), time, z)
                        out += Reminder.Payment("pay|${r.id}|${o.date}|$before", at, s.paymentsProfile, r.id, o.date, r.name,
                            ask = r.mode == RecurringMode.ASK, daysBefore = before)
                    }
                }
        }
        if (s.summaryEnabled) {
            var d = firstDay
            while (d <= lastDay) {
                out += Reminder.Summary("sum|$d", millis(d, s.summaryTime, z), s.summaryProfile, d)
                d = d.plus(DatePeriod(days = 1))
            }
        }
        if (s.eventEndEnabled) {
            val minutes = s.eventEndMinutes
            val ms = minutes * 60_000L
            events(from + ms - DAY_MS, to + ms + DAY_MS) { cal, shown -> s.eventEndFor(cal, shown) }
                .filter { !it.allDay && it.end - it.start > ms }
                .forEach { e ->
                    out += Reminder.EventEnd("end|${e.eventId}|${e.instanceStart}", e.end - ms, s.eventEndProfile, e.title, e.end, minutes)
                }
        }
        return out.filter { it.at in from until to }.sortedBy { it.at }
    }

    /**
     * Что показать сейчас: наступившее (не раньше чем [CATCH_UP_MS] назад — телефон мог быть
     * выключен), ещё не показанное, и отложенное, чьё время пришло.
     */
    fun due(now: Long): List<Reminder> {
        val since = maxOf(now - CATCH_UP_MS, startedAt(now))
        val fired = fired()
        val planned = upcoming(since, now + 1).filter { it.key !in fired }
        return planned + snoozedDue(now)
    }

    /**
     * Когда проснуться в следующий раз: ближайшее напоминание или отложенное. Дальше
     * [LOOKAHEAD_MS] не ищем — но и не засыпаем навсегда: через [RECHECK_MS] пересчитать
     * (ежегодный платёж, изменения из Google).
     */
    fun nextAt(now: Long): Long {
        val fired = fired()
        val planned = upcoming(now + 1, now + LOOKAHEAD_MS).firstOrNull { it.key !in fired }?.at
        val snoozed = snoozed().minOfOrNull { it.at }
        return listOfNotNull(planned, snoozed).minOrNull() ?: (now + RECHECK_MS)
    }

    /** Показано — больше не показывать (на 3 суток помнятся ключи; дальше в расчёт и не попадут). */
    fun markShown(reminders: List<Reminder>, now: Long) {
        if (reminders.isEmpty()) return
        val keep = fired().filterValues { it > now - KEEP_MS } + reminders.associate { it.key to now }
        store.put(KEY_FIRED, keep.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        val snoozedKeys = reminders.map { it.key }.toSet()
        saveSnoozed(snoozed().filter { it.key !in snoozedKeys || it.at > now })
        // Показанные помним недолго: кнопки уведомления («Отложить», «Записать») ищут их по ключу,
        // а отложенное после показа из списка отложенных уже убрано.
        store.put(KEY_RECENT, (recent().filter { it.key !in snoozedKeys } + reminders).takeLast(RECENT_COUNT).joinToString("\n", transform = ::encode))
    }

    /** «Отложить»: то же напоминание через [minutes] минут (ключ — с пометкой, чтобы не путать с исходным). */
    fun snooze(reminder: Reminder, minutes: Int, now: Long) {
        val at = now + minutes * 60_000L
        val again = when (reminder) {
            is Reminder.Payment -> reminder.copy(key = snoozeKey(reminder.key, at), at = at)
            is Reminder.Summary -> reminder.copy(key = snoozeKey(reminder.key, at), at = at)
            is Reminder.EventEnd -> reminder.copy(key = snoozeKey(reminder.key, at), at = at)
        }
        saveSnoozed(snoozed() + again)
    }

    /** Найти напоминание по ключу (для кнопок в уведомлении): среди отложенных и недавних. */
    fun find(key: String, now: Long): Reminder? =
        snoozed().firstOrNull { it.key == key } ?: recent().firstOrNull { it.key == key }
            ?: upcoming(now - CATCH_UP_MS, now + LOOKAHEAD_MS).firstOrNull { it.key == key }

    private fun recent(): List<Reminder> = store.get(KEY_RECENT).orEmpty().lines().mapNotNull(::decode)

    fun summary(date: LocalDate): DaySummary {
        val z = zone()
        val from = millis(date, LocalTime(0, 0), z)
        val to = millis(date.plus(DatePeriod(days = 1)), LocalTime(0, 0), z)
        val events = events(from, to) { _, shown -> shown }.sortedWith(compareBy({ !it.allDay }, { it.start }))
        val payments = recurring.occurrences(accountId(), date, date.plus(DatePeriod(days = 1)), date)
            .filter { it.state == OccurrenceState.PLANNED || it.state == OccurrenceState.PENDING }
        return DaySummary(events, payments, recurring.pending(accountId(), date).size)
    }

    /** Текущее и следующее событие (со временем) — для постоянного уведомления. */
    fun nowNext(now: Long): NowNext {
        val list = events(now - DAY_MS, now + DAY_MS) { _, shown -> shown }.filter { !it.allDay }.sortedBy { it.start }
        return NowNext(list.firstOrNull { it.start <= now && it.end > now }, list.firstOrNull { it.start > now })
    }

    /** Когда «сейчас / дальше» поменяется: конец текущего или начало следующего. */
    fun nextBoundary(now: Long): Long? {
        val nn = nowNext(now)
        return listOfNotNull(nn.current?.end, nn.next?.start).minOrNull()
    }

    private fun events(from: Long, to: Long, pick: (io.github.nullbrash.quazio.feature.calendar.CalendarInfo, Boolean) -> Boolean): List<CalendarEvent> {
        val source = calendar ?: return emptyList()
        val prefs = calendarPrefs ?: return emptyList()
        if (!prefs.enabled || !calendarReadable()) return emptyList()
        return runCatching {
            val ids = source.calendars().filter { pick(it, prefs.isShown(it)) }.mapTo(HashSet()) { it.id }
            if (ids.isEmpty()) emptyList() else source.events(from, to, ids)
        }.getOrDefault(emptyList())
    }

    /** Первый запуск напоминаний: прошлое до него не показываем пачкой. */
    private fun startedAt(now: Long): Long = store.get(KEY_SINCE)?.toLongOrNull() ?: now.also { store.put(KEY_SINCE, it.toString()) }

    private fun fired(): Map<String, Long> = store.get(KEY_FIRED).orEmpty().lines().filter { '\t' in it }
        .associate { it.substringBefore('\t') to (it.substringAfter('\t').toLongOrNull() ?: 0L) }

    private fun snoozedDue(now: Long) = snoozed().filter { it.at <= now }

    private fun snoozed(): List<Reminder> = store.get(KEY_SNOOZED).orEmpty().lines().mapNotNull(::decode)

    private fun saveSnoozed(list: List<Reminder>) = store.put(KEY_SNOOZED, list.joinToString("\n", transform = ::encode))

    private fun snoozeKey(key: String, at: Long) = key.substringBefore("#") + "#" + at

    private fun encode(r: Reminder): String = when (r) {
        is Reminder.Payment -> listOf("P", r.key, r.at, r.profileId, r.recurringId, r.date, clean(r.name), r.ask, r.daysBefore)
        is Reminder.Summary -> listOf("S", r.key, r.at, r.profileId, r.date)
        is Reminder.EventEnd -> listOf("E", r.key, r.at, r.profileId, clean(r.title), r.end, r.minutes)
    }.joinToString("\t")

    private fun decode(line: String): Reminder? = runCatching {
        val f = line.split('\t')
        when (f[0]) {
            "P" -> Reminder.Payment(f[1], f[2].toLong(), f[3], f[4], LocalDate.parse(f[5]), f[6], f[7].toBoolean(), f[8].toInt())
            "S" -> Reminder.Summary(f[1], f[2].toLong(), f[3], LocalDate.parse(f[4]))
            "E" -> Reminder.EventEnd(f[1], f[2].toLong(), f[3], f[4], f[5].toLong(), f[6].toInt())
            else -> null
        }
    }.getOrNull()

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    private fun millis(d: LocalDate, t: LocalTime, z: TimeZone) = LocalDateTime(d, t).toInstant(z).toEpochMilliseconds()

    companion object {
        private const val DAY_MS = 86_400_000L
        const val CATCH_UP_MS = 12 * 3_600_000L
        private const val LOOKAHEAD_MS = 35 * DAY_MS
        private const val RECHECK_MS = 7 * DAY_MS
        private const val KEEP_MS = 3 * DAY_MS
        private const val KEY_FIRED = "reminders.fired"
        private const val KEY_SNOOZED = "reminders.snoozed"
        private const val KEY_SINCE = "reminders.since"
        private const val KEY_RECENT = "reminders.recent"
        private const val RECENT_COUNT = 30
    }
}
