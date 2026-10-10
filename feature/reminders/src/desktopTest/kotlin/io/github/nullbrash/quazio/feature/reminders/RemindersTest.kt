package io.github.nullbrash.quazio.feature.reminders

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.accounts.DeviceClock
import io.github.nullbrash.quazio.core.db.QuazioDatabase
import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import io.github.nullbrash.quazio.feature.calendar.CalendarInfo
import io.github.nullbrash.quazio.feature.calendar.CalendarKind
import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.CalendarSource
import io.github.nullbrash.quazio.feature.calendar.EventDraft
import io.github.nullbrash.quazio.feature.calendar.KeyValueStore
import io.github.nullbrash.quazio.feature.finance.FinanceService
import io.github.nullbrash.quazio.feature.finance.RecurringDraft
import io.github.nullbrash.quazio.feature.finance.TxnKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemindersTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { QuazioDatabase.Schema.create(it) }
    private val db = QuazioDatabase(driver)
    private val clock = DeviceClock(db, System::currentTimeMillis)
    private val accountId = AccountService(db, clock).initialize("ПК", "windows", "Я").id
    private val finance = FinanceService(db, clock).also { it.ensureDefaults(accountId) }
    private val moscow = TimeZone.of("Europe/Moscow")
    private val store = object : KeyValueStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }
    private val prefs = CalendarPrefs(store).also { it.enabled = true }
    private val work = CalendarInfo("1", "Работа", "me", CalendarKind.GOOGLE, 0, true, true, true)
    private val events = mutableListOf<CalendarEvent>()
    private val source = object : CalendarSource {
        override fun calendars() = listOf(work)
        override fun events(from: Long, to: Long, calendarIds: Set<String>) = events.filter { it.end > from && it.start < to && it.calendarId in calendarIds }
        override fun create(draft: EventDraft) = ""
        override fun update(eventId: String, draft: EventDraft) = Unit
        override fun updateInstance(eventId: String, instanceStart: Long, draft: EventDraft) = Unit
        override fun delete(eventId: String) = Unit
        override fun deleteInstance(eventId: String, instanceStart: Long) = Unit
        override fun reminders(eventId: String) = emptyList<Int>()
        override fun event(eventId: String): EventDraft? = null
    }
    private val settings = ReminderSettings(store)
    private val reminders = ReminderService(ProfileStore(db), settings, store, finance.recurring, { accountId }, source, prefs, zone = { moscow })

    @AfterTest
    fun close() = driver.close()

    private fun at(s: String) = LocalDateTime.parse(s).toInstant(moscow).toEpochMilliseconds()
    private fun cash() = finance.accounts(accountId).first().id

    private fun internet(remindDays: Int = 1) = finance.recurring.save(accountId, RecurringDraft(
        name = "Интернет", kind = TxnKind.EXPENSE, amountMinor = 70_000, finAccountId = cash(),
        rrule = "FREQ=MONTHLY", startDate = LocalDate.parse("2026-10-15"), remindDays = remindDays,
    ))

    @Test
    fun defaultsFollowUserDecisions() {
        assertTrue(settings.paymentsEnabled)
        assertEquals("10:00", settings.paymentsTime.toString())
        assertEquals(false, settings.summaryEnabled)
        assertEquals("08:00", settings.summaryTime.toString())
        assertEquals(false, settings.eventEndEnabled)
        assertEquals(10, settings.eventEndMinutes)
        assertEquals(false, settings.persistentEnabled)
        assertTrue(settings.desktopEnabled)
        assertTrue(settings.paymentsShowAmount)
        val profiles = reminders.profiles.all()
        assertEquals(listOf("notification" to ReminderKind.NOTIFICATION, "alarm" to ReminderKind.ALARM), profiles.map { it.id to it.kind })
    }

    @Test
    fun paymentRemindsDayBeforeAndOnTheDayUntilRecorded() {
        val id = internet()
        val list = reminders.upcoming(at("2026-10-13T00:00"), at("2026-10-16T00:00")).filterIsInstance<Reminder.Payment>()
        assertEquals(listOf(at("2026-10-14T10:00") to 1, at("2026-10-15T10:00") to 0), list.map { it.at to it.daysBefore })
        assertTrue(list.all { it.ask && it.name == "Интернет" && it.amountMinor == 70_000L })

        finance.recurring.record(accountId, id, LocalDate.parse("2026-10-15"),
            finance.recurring.defaultTxn(finance.recurring.byId(id)!!, LocalDate.parse("2026-10-15"), moscow.id))
        assertTrue(reminders.upcoming(at("2026-10-13T00:00"), at("2026-10-16T00:00")).isEmpty()) // записан — не напоминаем

        settings.paymentsEnabled = false
        internet(remindDays = 0)
        assertTrue(reminders.upcoming(at("2026-11-13T00:00"), at("2026-11-16T00:00")).isEmpty())
    }

    @Test
    fun noAccountYetMeansNothingToRemind() {
        // Первый запуск: фоновая проверка напоминаний успела раньше, чем оболочка создала аккаунт.
        val early = ReminderService(ProfileStore(db), settings, store, finance.recurring, { null }, source, prefs, zone = { moscow })
        internet()
        assertTrue(early.due(at("2026-10-15T10:01")).isEmpty())
        assertTrue(early.summary(LocalDate.parse("2026-10-15")).payments.isEmpty())
    }

    @Test
    fun dueShowsOnceAndSnoozeComesBack() {
        internet(remindDays = 0)
        reminders.due(at("2026-10-01T00:00")) // первый запуск — точка отсчёта
        val now = at("2026-10-15T10:01")
        val due = reminders.due(now)
        assertEquals(listOf("pay|"), due.map { it.key.take(4) })
        reminders.markShown(due, now)
        assertTrue(reminders.due(now + 60_000).isEmpty()) // не дважды

        reminders.snooze(due.single(), 10, now)
        assertEquals(now + 10 * 60_000, reminders.nextAt(now))
        val again = reminders.due(now + 10 * 60_000)
        assertEquals(1, again.size)
        reminders.markShown(again, now + 10 * 60_000)
        // Показанное отложенное ещё находится по ключу — для кнопок уведомления (нашлось на эмуляторе).
        val found = reminders.find(again.single().key, now + 10 * 60_000) as Reminder.Payment
        assertEquals("Интернет" to 70_000L, found.name to found.amountMinor) // сумма переживает «Отложить»
        assertTrue(reminders.due(now + 11 * 60_000).isEmpty())
        assertEquals(at("2026-11-15T10:00"), reminders.nextAt(now + 11 * 60_000)) // следующий месяц
        settings.paymentsEnabled = false
        assertEquals(now + 11 * 60_000 + 7 * 86_400_000L, reminders.nextAt(now + 11 * 60_000)) // нечего ждать — пересчёт через неделю
    }

    @Test
    fun firstRunDoesNotFloodWithThePast() {
        internet(remindDays = 0)
        // Quazio обновили днём, а платёж был утром — до первого запуска напоминаний; не показываем.
        assertTrue(reminders.due(at("2026-10-15T15:00")).isEmpty())
    }

    @Test
    fun eventEndOnlyForTimedEventsWhenEnabled() {
        events += CalendarEvent("e1", "1", "Встреча", at("2026-10-15T14:00"), at("2026-10-15T15:00"), false, moscow.id, null, null, false, at("2026-10-15T14:00"))
        events += CalendarEvent("e2", "1", "Отпуск", at("2026-10-15T00:00"), at("2026-10-16T00:00"), true, "UTC", null, null, false, at("2026-10-15T00:00"))
        assertTrue(reminders.upcoming(at("2026-10-15T00:00"), at("2026-10-16T00:00")).none { it is Reminder.EventEnd })
        settings.eventEndEnabled = true
        val end = reminders.upcoming(at("2026-10-15T00:00"), at("2026-10-16T00:00")).filterIsInstance<Reminder.EventEnd>().single()
        assertEquals(at("2026-10-15T14:50"), end.at)
        assertEquals("Встреча", end.title)
        // Календарь снят галочкой — не напоминаем.
        settings.eventEndChoices = mapOf("1" to false)
        assertTrue(reminders.upcoming(at("2026-10-15T00:00"), at("2026-10-16T00:00")).none { it is Reminder.EventEnd })
    }

    @Test
    fun summaryAndNowNext() {
        settings.summaryEnabled = true
        internet(remindDays = 0)
        events += CalendarEvent("e1", "1", "Встреча", at("2026-10-15T14:00"), at("2026-10-15T15:00"), false, moscow.id, null, null, false, at("2026-10-15T14:00"))
        val sum = reminders.upcoming(at("2026-10-15T00:00"), at("2026-10-15T09:00")).filterIsInstance<Reminder.Summary>().single()
        assertEquals(at("2026-10-15T08:00"), sum.at)
        val day = reminders.summary(LocalDate.parse("2026-10-15"))
        assertEquals(listOf("Встреча"), day.events.map { it.title })
        assertEquals(listOf("Интернет"), day.payments.map { it.recurring.name })

        val nn = reminders.nowNext(at("2026-10-15T14:30"))
        assertEquals("Встреча", nn.current?.title)
        assertEquals(at("2026-10-15T15:00"), reminders.nextBoundary(at("2026-10-15T14:30")))
    }

    @Test
    fun profilesCanBeAddedEditedAndDeletedButBuiltinsStay() {
        val p = reminders.profiles.save(ReminderProfile("", "Громкий", ReminderKind.ALARM, rampSeconds = 60, snoozeMinutes = 5))
        assertTrue(reminders.profiles.all().any { it.id == p.id && it.rampSeconds == 60 })
        reminders.profiles.delete(p.id)
        reminders.profiles.delete(ProfileStore.ALARM)
        assertEquals(listOf("notification", "alarm"), reminders.profiles.all().map { it.id })
        assertEquals(ProfileStore.NOTIFICATION, reminders.profiles.byId("нет такого").id) // удалённый → «Уведомление»
    }
}
