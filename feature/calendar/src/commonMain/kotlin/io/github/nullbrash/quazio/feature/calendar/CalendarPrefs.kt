package io.github.nullbrash.quazio.feature.calendar

/** Хранилище настроек устройства (в Quazio — таблица `app_state`). */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String) = put(key, "")
}

/**
 * Какие календари показывать и куда записывать новые события (решение пользователя:
 * по умолчанию — видимые в системе; при первом включении — обязательный список;
 * для новых событий — один выбранный календарь). Настройки устройства: id календарей
 * на другом устройстве другие, поэтому не синхронизируются.
 */
class CalendarPrefs(private val store: KeyValueStore) {

    /** Пользователь включил календари телефона и прошёл первый список. */
    var enabled: Boolean
        get() = store.get(KEY_ENABLED) == "1"
        set(value) = store.put(KEY_ENABLED, if (value) "1" else "0")

    /**
     * Явный выбор пользователя «показывать / нет» по id. Хранится только выбор, а не список
     * видимых: новый календарь (подписались на чей-то) показывается так, как видим в системе.
     */
    var choices: Map<String, Boolean>
        get() = store.get(KEY_CHOICES).orEmpty().split(',').filter { ':' in it }
            .associate { it.substringBefore(':') to (it.substringAfter(':') == "1") }
        set(value) = store.put(KEY_CHOICES, value.entries.joinToString(",") { "${it.key}:${if (it.value) 1 else 0}" })

    var defaultCalendarId: String?
        get() = store.get(KEY_DEFAULT)?.takeIf { it.isNotEmpty() }
        set(value) = store.put(KEY_DEFAULT, value.orEmpty())

    /** Циферблат в приложении: 24 часа или (по умолчанию) 12 «скользящих», как на виджете. */
    var dial24: Boolean
        get() = store.get(KEY_DIAL_24) == "1"
        set(value) = store.put(KEY_DIAL_24, if (value) "1" else "0")

    /** Неделя с воскресенья; по умолчанию — с понедельника (решение пользователя). */
    var weekStartsSunday: Boolean
        get() = store.get(KEY_WEEK_SUNDAY) == "1"
        set(value) = store.put(KEY_WEEK_SUNDAY, if (value) "1" else "0")

    fun isShown(calendar: CalendarInfo): Boolean = choices[calendar.id] ?: calendar.visibleInSystem

    fun shown(calendars: List<CalendarInfo>): List<CalendarInfo> = calendars.filter(::isShown)

    /**
     * Календарь для новых событий: выбранный, если он ещё есть и в него можно писать;
     * иначе основной Google, иначе любой доступный для записи.
     */
    fun defaultFor(calendars: List<CalendarInfo>): CalendarInfo? {
        val writable = calendars.filter { it.writable }
        return writable.firstOrNull { it.id == defaultCalendarId }
            ?: writable.firstOrNull { it.kind == CalendarKind.GOOGLE && it.primary }
            ?: writable.firstOrNull { it.kind == CalendarKind.GOOGLE }
            ?: writable.firstOrNull()
    }

    private companion object {
        const val KEY_ENABLED = "calendar.enabled"
        const val KEY_CHOICES = "calendar.choices"
        const val KEY_DEFAULT = "calendar.default"
        const val KEY_DIAL_24 = "calendar.dial_24"
        const val KEY_WEEK_SUNDAY = "calendar.week_sunday"
    }
}
