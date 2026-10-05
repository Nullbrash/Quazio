package io.github.nullbrash.quazio.feature.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Настоящее хранилище календарей Android (на эмуляторе): отдельный тестовый календарь
 * «на телефоне», создаётся и удаляется самим тестом — чужие календари не трогаются.
 */
@RunWith(AndroidJUnit4::class)
class AndroidCalendarSourceTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = AndroidCalendarSource(context)
    private lateinit var calendarId: String

    private val asSyncAdapter: Uri = Calendars.CONTENT_URI.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    @Before
    fun createTestCalendar() {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, "quazio-test")
            put(Calendars.CALENDAR_DISPLAY_NAME, "Тест Quazio")
            put(Calendars.CALENDAR_COLOR, 0xFF2E7D32.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, "Europe/Moscow")
        }
        calendarId = ContentUris.parseId(context.contentResolver.insert(asSyncAdapter, values)!!).toString()
    }

    @After
    fun deleteTestCalendar() {
        context.contentResolver.delete(asSyncAdapter, "${Calendars.ACCOUNT_NAME} = ?", arrayOf(ACCOUNT))
    }

    private fun day(n: Int) = BASE + n * DAY

    private fun rawEvents(): String {
        val proj = arrayOf("_id", "title", "rrule", "deleted", "dtstart", "dtend", "duration", "original_id", "exdate", "lastDate", "eventStatus", "allDay")
        return context.contentResolver.query(CalendarContract.Events.CONTENT_URI, proj, "calendar_id = ?", arrayOf(calendarId), null)!!.use { c ->
            buildList { while (c.moveToNext()) add(proj.indices.joinToString("|") { proj[it] + "=" + c.getString(it) }) }.toString()
        }
    }

    private fun eventsOfTestCalendar() = source.events(day(-1), day(10), setOf(calendarId))

    @Test
    fun testCalendarIsListedAsWritablePhoneCalendar() {
        val cal = source.calendars().single { it.id == calendarId }
        assertEquals("Тест Quazio", cal.name)
        assertEquals(CalendarKind.PHONE, cal.kind)
        assertTrue(cal.writable)
        assertTrue(cal.visibleInSystem)
    }

    @Test
    fun singleEventRoundTripWithReminders() {
        val id = source.create(EventDraft(calendarId, "Встреча", day(1), day(1) + HOUR, timeZone = "Europe/Moscow", reminderMinutes = listOf(60, 10)))
        val e = eventsOfTestCalendar().single()
        assertEquals("Встреча", e.title)
        assertEquals(day(1), e.start)
        assertEquals(day(1) + HOUR, e.end)
        assertEquals(false, e.recurring)
        assertEquals(listOf(10, 60), source.reminders(id))

        source.update(id, EventDraft(calendarId, "Встреча (перенос)", day(2), day(2) + 2 * HOUR, timeZone = "Europe/Moscow", reminderMinutes = listOf(30)))
        val moved = eventsOfTestCalendar().single()
        assertEquals("Встреча (перенос)", moved.title)
        assertEquals(day(2), moved.start)
        assertEquals(listOf(30), source.reminders(id))

        source.delete(id)
        assertTrue(eventsOfTestCalendar().isEmpty())
    }

    /** Как будто событие уже синхронизировано с Google: id синхронизации ставит только «синхронизатор». */
    private fun markSynced(eventId: String) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLong()).buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        context.contentResolver.update(uri, ContentValues().apply { put(CalendarContract.Events._SYNC_ID, "sync-$eventId") }, null, null)
    }

    private fun editOneAndCancelOne(synced: Boolean) {
        val id = source.create(
            EventDraft(calendarId, "Зарядка", day(1), day(1) + HOUR / 2, timeZone = "Europe/Moscow", rrule = "FREQ=DAILY;COUNT=4"),
        )
        if (synced) markSynced(id)
        val series = eventsOfTestCalendar()
        assertEquals(4, series.size)
        assertTrue(series.all { it.recurring && it.eventId == id })

        // Только это повторение — другое название и время; остальные как были.
        source.updateInstance(id, day(2), EventDraft(calendarId, "Зарядка (позже)", day(2) + HOUR, day(2) + HOUR * 3 / 2, timeZone = "Europe/Moscow"))
        // Одно повторение отменить.
        source.deleteInstance(id, day(3))

        val after = eventsOfTestCalendar()
        val view = after.map { it.title to (it.start - BASE) / HOUR } + " строки: " + rawEvents()
        assertEquals(listOf("Зарядка", "Зарядка (позже)", "Зарядка"), after.map { it.title }, "$view")
        assertEquals(listOf(day(1), day(2) + HOUR, day(4)), after.map { it.start }, "$view")

        source.delete(id)
        // Удалили серию — изменённое повторение (если было отдельным событием) остаётся: это уже своё событие.
        assertTrue(eventsOfTestCalendar().none { it.title == "Зарядка" })
    }

    @Test
    fun recurringEventNotYetSynced() = editOneAndCancelOne(synced = false)

    @Test
    fun recurringEventSyncedWithGoogle() = editOneAndCancelOne(synced = true)

    @Test
    fun allDayEventUsesUtcMidnights() {
        source.create(EventDraft(calendarId, "Отпуск", UTC_DAY, UTC_DAY + 3 * DAY, allDay = true, timeZone = "Europe/Moscow"))
        val e = source.events(UTC_DAY - DAY, UTC_DAY + 5 * DAY, setOf(calendarId)).single()
        assertTrue(e.allDay)
        assertEquals(UTC_DAY, e.start)
        assertEquals(UTC_DAY + 3 * DAY, e.end)
    }

    private companion object {
        const val ACCOUNT = "quazio-test@local"
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
        // 2026-11-02 10:00 МСК — будущее, чтобы не пересечься с прошлыми проверками.
        const val BASE = 1_793_602_800_000L
        // 2026-11-02 00:00 UTC.
        const val UTC_DAY = 1_793_577_600_000L
    }
}
