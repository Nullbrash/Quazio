package io.github.nullbrash.quazio.feature.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders

/**
 * Календари телефона через системное хранилище (`CalendarContract`), как у Sectograph:
 * события Google сюда синхронизирует сам Google, Quazio их только читает и правит.
 * Повторения считает Android (таблица `Instances`).
 */
class AndroidCalendarSource(context: Context) : CalendarSource {

    private val appContext = context.applicationContext
    private val cr: ContentResolver get() = appContext.contentResolver

    override fun calendars(): List<CalendarInfo> {
        val projection = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR, Calendars.VISIBLE, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.IS_PRIMARY,
            Calendars.OWNER_ACCOUNT,
        )
        return cr.query(Calendars.CONTENT_URI, projection, null, null, null).use { c ->
            c ?: return emptyList()
            buildList {
                while (c.moveToNext()) {
                    val accountName = c.str(2).orEmpty()
                    val accountType = c.str(3).orEmpty()
                    val kind = when {
                        accountType == GOOGLE_ACCOUNT_TYPE -> CalendarKind.GOOGLE
                        accountType.equals(CalendarContract.ACCOUNT_TYPE_LOCAL, ignoreCase = true) -> CalendarKind.PHONE
                        else -> CalendarKind.OTHER
                    }
                    // IS_PRIMARY бывает пустым: тогда основной — тот, чей владелец совпадает с аккаунтом.
                    val primary = if (c.isNull(7)) c.str(8) == accountName else c.getInt(7) == 1
                    add(
                        CalendarInfo(
                            id = c.getLong(0).toString(),
                            name = c.str(1) ?: accountName,
                            accountName = accountName,
                            kind = kind,
                            color = c.getInt(4).toLong() and 0xFFFFFFFFL,
                            visibleInSystem = c.getInt(5) == 1,
                            writable = c.getInt(6) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                            primary = primary,
                        ),
                    )
                }
            }
        }
    }

    override fun events(from: Long, to: Long, calendarIds: Set<String>): List<CalendarEvent> {
        if (calendarIds.isEmpty()) return emptyList()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
            Instances.ALL_DAY, Instances.EVENT_TIMEZONE, Instances.DISPLAY_COLOR, Instances.EVENT_LOCATION,
            Instances.RRULE, Instances.RDATE,
        )
        val ids = calendarIds.toList()
        val selection = "${Instances.CALENDAR_ID} IN (${ids.joinToString(",") { "?" }})"
        return cr.query(uri, projection, selection, ids.toTypedArray(), "${Instances.BEGIN} ASC").use { c ->
            c ?: return emptyList()
            buildList {
                while (c.moveToNext()) {
                    val begin = c.getLong(3)
                    add(
                        CalendarEvent(
                            eventId = c.getLong(0).toString(),
                            calendarId = c.getLong(1).toString(),
                            title = c.str(2).orEmpty(),
                            start = begin,
                            end = c.getLong(4),
                            allDay = c.getInt(5) == 1,
                            timeZone = c.str(6),
                            color = if (c.isNull(7)) null else c.getInt(7).toLong() and 0xFFFFFFFFL,
                            location = c.str(8)?.takeIf { it.isNotBlank() },
                            recurring = !c.str(9).isNullOrBlank() || !c.str(10).isNullOrBlank(),
                            instanceStart = begin,
                        ),
                    )
                }
            }
        }
    }

    override fun create(draft: EventDraft): String {
        val values = eventValues(draft).apply { put(Events.CALENDAR_ID, draft.calendarId.toLong()) }
        val uri = requireNotNull(cr.insert(Events.CONTENT_URI, values)) { "Календарь не принял событие" }
        val id = ContentUris.parseId(uri)
        setReminders(id, draft.reminderMinutes)
        return id.toString()
    }

    override fun update(eventId: String, draft: EventDraft) {
        val id = eventId.toLong()
        val values = eventValues(draft).apply { put(Events.CALENDAR_ID, draft.calendarId.toLong()) }
        cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values, null, null)
        setReminders(id, draft.reminderMinutes)
    }

    override fun updateInstance(eventId: String, instanceStart: Long, draft: EventDraft) {
        if (!isSynced(eventId)) {
            // Событие ещё не видел Google (или календарь «на телефоне»): исключения Android для
            // таких ломают всю серию — повторение убирается датой в EXDATE, правка — отдельным событием.
            addExdate(eventId, instanceStart)
            create(draft.copy(rrule = null))
            return
        }
        // Исключение из серии: у Android — отдельное событие, ссылающееся на исходное повторение.
        // Конец у исключения Android задать не даёт («can't overwrite dtend») — только длительность.
        val values = ContentValues().apply {
            put(Events.ORIGINAL_INSTANCE_TIME, instanceStart)
            put(Events.STATUS, Events.STATUS_CONFIRMED)
            put(Events.TITLE, draft.title)
            put(Events.DESCRIPTION, draft.description)
            put(Events.EVENT_LOCATION, draft.location)
            put(Events.DTSTART, draft.start)
            put(Events.ALL_DAY, if (draft.allDay) 1 else 0)
            put(Events.EVENT_TIMEZONE, if (draft.allDay) "UTC" else draft.timeZone)
            put(Events.DURATION, duration(draft))
            put(Events.HAS_ALARM, if (draft.reminderMinutes.isEmpty()) 0 else 1)
        }
        val uri = requireNotNull(cr.insert(exceptionUri(eventId), values)) { "Календарь не принял изменение повторения" }
        setReminders(ContentUris.parseId(uri), draft.reminderMinutes)
    }

    override fun delete(eventId: String) {
        cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId.toLong()), null, null)
    }

    override fun deleteInstance(eventId: String, instanceStart: Long) {
        if (!isSynced(eventId)) {
            addExdate(eventId, instanceStart)
            return
        }
        val values = ContentValues().apply {
            put(Events.ORIGINAL_INSTANCE_TIME, instanceStart)
            put(Events.STATUS, Events.STATUS_CANCELED)
        }
        requireNotNull(cr.insert(exceptionUri(eventId), values)) { "Календарь не отменил повторение" }
    }

    override fun reminders(eventId: String): List<Int> =
        cr.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES), "${Reminders.EVENT_ID} = ?", arrayOf(eventId), null).use { c ->
            c ?: return emptyList()
            buildList { while (c.moveToNext()) add(c.getInt(0)) }.sorted()
        }

    /** Есть ли у события id синхронизации (его знает Google) — только тогда работают исключения Android. */
    private fun isSynced(eventId: String): Boolean =
        cr.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId.toLong()), arrayOf(Events._SYNC_ID), null, null, null).use { c ->
            c != null && c.moveToFirst() && !c.str(0).isNullOrEmpty()
        }

    /** Убрать одно повторение из серии: дата добавляется в EXDATE (так делает и календарь AOSP). */
    private fun addExdate(eventId: String, instanceStart: Long) {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId.toLong())
        val values = cr.query(uri, arrayOf(Events.EXDATE, Events.ALL_DAY, Events.DTSTART, Events.RRULE, Events.DURATION, Events.EVENT_TIMEZONE), null, null, null).use { c ->
            requireNotNull(c?.takeIf { it.moveToFirst() }) { "Нет события $eventId" }
            val allDay = c.getInt(1) == 1
            val time = java.time.Instant.ofEpochMilli(instanceStart).atZone(java.time.ZoneOffset.UTC)
            val stamp = if (allDay) EXDATE_DAY.format(time) else EXDATE_TIME.format(time)
            ContentValues().apply {
                put(Events.EXDATE, listOfNotNull(c.str(0)?.takeIf { it.isNotBlank() }, stamp).joinToString(","))
                // Повторения хранилище пересчитывает, только когда в изменении есть начало, правило
                // и длительность — одного EXDATE мало: передаём их заново, без изменений.
                put(Events.DTSTART, c.getLong(2))
                put(Events.RRULE, c.str(3))
                put(Events.DURATION, c.str(4))
                put(Events.EVENT_TIMEZONE, c.str(5))
            }
        }
        cr.update(uri, values, null, null)
    }

    override fun event(eventId: String): EventDraft? {
        val projection = arrayOf(
            Events.CALENDAR_ID, Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION, Events.DTSTART, Events.DTEND,
            Events.DURATION, Events.ALL_DAY, Events.EVENT_TIMEZONE, Events.RRULE, Events.EVENT_COLOR_KEY,
        )
        val draft = cr.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId.toLong()), projection, null, null, null).use { c ->
            if (c == null || !c.moveToFirst()) return null
            val start = c.getLong(4)
            // У повторяющегося событий конца нет — только длительность.
            val end = if (c.isNull(5)) start + (parseDuration(c.str(6)) ?: 0L) else c.getLong(5)
            EventDraft(
                calendarId = c.getLong(0).toString(),
                title = c.str(1).orEmpty(),
                start = start,
                end = maxOf(end, start),
                allDay = c.getInt(7) == 1,
                timeZone = c.str(8) ?: java.util.TimeZone.getDefault().id,
                location = c.str(3).orEmpty(),
                description = c.str(2).orEmpty(),
                rrule = c.str(9)?.takeIf { it.isNotBlank() },
                colorKey = c.str(10)?.takeIf { it.isNotBlank() },
            )
        }
        return draft.copy(reminderMinutes = reminders(eventId))
    }

    override fun eventColors(calendarId: String): List<EventColor> {
        val (account, type) = cr.query(
            ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId.toLong()),
            arrayOf(Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE), null, null, null,
        ).use { c -> if (c == null || !c.moveToFirst()) return emptyList() else c.str(0) to c.str(1) }
        return cr.query(
            CalendarContract.Colors.CONTENT_URI,
            arrayOf(CalendarContract.Colors.COLOR_KEY, CalendarContract.Colors.COLOR),
            "${CalendarContract.Colors.ACCOUNT_NAME} = ? AND ${CalendarContract.Colors.ACCOUNT_TYPE} = ? AND ${CalendarContract.Colors.COLOR_TYPE} = ?",
            arrayOf(account.orEmpty(), type.orEmpty(), CalendarContract.Colors.TYPE_EVENT.toString()),
            null,
        ).use { c ->
            c ?: return emptyList()
            buildList { while (c.moveToNext()) add(EventColor(c.getString(0), c.getInt(1).toLong() and 0xFFFFFFFFL)) }
        }
    }

    private fun exceptionUri(eventId: String) = ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, eventId.toLong())

    private fun eventValues(d: EventDraft) = ContentValues().apply {
        put(Events.TITLE, d.title)
        put(Events.DESCRIPTION, d.description)
        put(Events.EVENT_LOCATION, d.location)
        put(Events.DTSTART, d.start)
        put(Events.ALL_DAY, if (d.allDay) 1 else 0)
        // У событий на весь день Android требует пояс UTC и полночь UTC.
        put(Events.EVENT_TIMEZONE, if (d.allDay) "UTC" else d.timeZone)
        put(Events.HAS_ALARM, if (d.reminderMinutes.isEmpty()) 0 else 1)
        // Цвет — ключом из палитры календаря: Google понимает только свои 11 цветов.
        if (d.colorKey != null) put(Events.EVENT_COLOR_KEY, d.colorKey) else { putNull(Events.EVENT_COLOR_KEY); putNull(Events.EVENT_COLOR) }
        if (d.rrule != null) {
            // Повторяющееся событие задаётся длительностью, а не концом — иначе Android его отвергнет.
            put(Events.RRULE, d.rrule)
            put(Events.DURATION, duration(d))
            putNull(Events.DTEND)
        } else {
            put(Events.DTEND, d.end)
            putNull(Events.RRULE)
            putNull(Events.DURATION)
        }
    }

    private fun duration(d: EventDraft): String =
        if (d.allDay) "P${maxOf(1, (d.end - d.start) / DAY_MS)}D" else "P${(d.end - d.start) / 1000}S"

    private fun setReminders(eventId: Long, minutes: List<Int>) {
        cr.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        minutes.distinct().forEach { m ->
            cr.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, eventId)
                put(Reminders.MINUTES, m)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            })
        }
    }

    private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private val EXDATE_TIME = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        private val EXDATE_DAY = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        fun hasPermission(context: Context): Boolean =
            PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
}
