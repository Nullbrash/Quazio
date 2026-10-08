package io.github.nullbrash.quazio.feature.calendar

/**
 * Календари Quazio своих событий не хранят (решение пользователя): события живут в
 * календарях устройства (на Android — системное хранилище, туда их синхронизирует Google),
 * Quazio их показывает и правит через [CalendarSource].
 */
enum class CalendarKind { GOOGLE, PHONE, OTHER }

data class CalendarInfo(
    /** Id в источнике (на Android — `_ID` системного календаря); на другом устройстве не совпадает. */
    val id: String,
    val name: String,
    /** Аккаунт (почта Google, «Мой телефон»): показывается пользователю, не хранится. */
    val accountName: String,
    val kind: CalendarKind,
    /** ARGB. */
    val color: Long,
    /** Виден ли в системе (галочка в «Календаре» телефона) — так Quazio показывает по умолчанию. */
    val visibleInSystem: Boolean,
    /** Можно ли создавать и править события (свой календарь, а не чужой или праздники). */
    val writable: Boolean,
    /** Основной календарь аккаунта. */
    val primary: Boolean,
)

/** Одно повторение события на дату. У разовых событий [instanceStart] = [start]. */
data class CalendarEvent(
    val eventId: String,
    val calendarId: String,
    val title: String,
    /** Начало и конец, миллисекунды UTC. У событий на весь день — полночь UTC дат начала и конца. */
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val timeZone: String?,
    val color: Long?,
    val location: String?,
    /** Есть правило повтора — правка спрашивает «только это / все». */
    val recurring: Boolean,
    /** Исходное начало этого повторения — по нему правится или отменяется одно повторение. */
    val instanceStart: Long,
    /** Не событие календаря, а регулярный платёж из финансов (слой поверх календарей). */
    val payment: PaymentMark? = null,
)

/** Платёж в календаре: только название и значок, сумма — в финансах под замком (решение пользователя). */
data class PaymentMark(val recurringId: String, val waiting: Boolean)

/** Что записать в календарь. Напоминания — самого календаря (Google сам их и покажет). */
data class EventDraft(
    val calendarId: String,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean = false,
    val timeZone: String,
    val location: String = "",
    val description: String = "",
    /** RFC 5545 без «RRULE:», например «FREQ=WEEKLY;BYDAY=MO,WE». null — разовое. */
    val rrule: String? = null,
    /** За сколько минут напоминать (у Google — уведомлением). */
    val reminderMinutes: List<Int> = emptyList(),
    /**
     * Ключ цвета события из палитры аккаунта (так его хранит Google); null — цвет календаря.
     * Quazio цвет не выбирает, но при правке передаёт назад как был — чтобы не стереть.
     */
    val colorKey: String? = null,
) {
    init {
        require(end >= start) { "Конец события раньше начала" }
    }
}

/**
 * Календари: Android — `CalendarContract`, ПК — ссылки iCal (`LinkedCalendars`, только чтение).
 * Вызывать не из главного потока: это запросы к хранилищу.
 */
interface CalendarSource {
    fun calendars(): List<CalendarInfo>

    /** Повторения событий, пересекающие [from, to), только из [calendarIds]. */
    fun events(from: Long, to: Long, calendarIds: Set<String>): List<CalendarEvent>

    /** Новое событие; возвращает его id. */
    fun create(draft: EventDraft): String

    /** Изменить событие целиком (у повторяющегося — все повторения). */
    fun update(eventId: String, draft: EventDraft)

    /** Изменить одно повторение: остальные остаются как были. */
    fun updateInstance(eventId: String, instanceStart: Long, draft: EventDraft)

    fun delete(eventId: String)

    /** Отменить одно повторение. */
    fun deleteInstance(eventId: String, instanceStart: Long)

    fun reminders(eventId: String): List<Int>

    /** Событие целиком для окна правки: у повторяющегося — начало и длительность всей серии. */
    fun event(eventId: String): EventDraft?

}
