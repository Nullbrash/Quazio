package io.github.nullbrash.quazio.feature.reminders

import io.github.nullbrash.quazio.feature.calendar.CalendarInfo
import io.github.nullbrash.quazio.feature.calendar.KeyValueStore
import kotlinx.datetime.LocalTime

/**
 * Какие напоминания включены и с каким профилем — настройки этого устройства. Значения по
 * умолчанию — решения пользователя (фаза 5): платежи — включены, 10:00; утренняя сводка —
 * выключена, 8:00; «до конца события» — выключено, 10 минут; постоянное уведомление —
 * выключено; уведомления на ПК — включены; сумма в уведомлениях о платежах — показывается
 * (можно скрыть: уведомление видно на заблокированном экране).
 */
class ReminderSettings(private val store: KeyValueStore) {

    var paymentsEnabled by flag("reminders.payments.enabled", true)
    var paymentsTime by time("reminders.payments.time", LocalTime(10, 0))
    var paymentsProfile by text("reminders.payments.profile", ProfileStore.NOTIFICATION)
    var paymentsShowAmount by flag("reminders.payments.amount", true)

    var summaryEnabled by flag("reminders.summary.enabled", false)
    var summaryTime by time("reminders.summary.time", LocalTime(8, 0))
    var summaryProfile by text("reminders.summary.profile", ProfileStore.NOTIFICATION)

    var eventEndEnabled by flag("reminders.event_end.enabled", false)
    var eventEndMinutes by number("reminders.event_end.minutes", 10)
    var eventEndProfile by text("reminders.event_end.profile", ProfileStore.NOTIFICATION)

    /** Явный выбор календарей «напоминать / нет» по id; не выбранные — как показываются в календаре. */
    var eventEndChoices: Map<String, Boolean>
        get() = store.get(KEY_END_CALENDARS).orEmpty().split(',').filter { ':' in it }
            .associate { it.substringBefore(':') to (it.substringAfter(':') == "1") }
        set(value) = store.put(KEY_END_CALENDARS, value.entries.joinToString(",") { "${it.key}:${if (it.value) 1 else 0}" })

    fun eventEndFor(calendar: CalendarInfo, shown: Boolean): Boolean = eventEndChoices[calendar.id] ?: shown

    var persistentEnabled by flag("reminders.persistent.enabled", false)
    var desktopEnabled by flag("reminders.desktop.enabled", true)

    private fun flag(key: String, default: Boolean) = Prop({ store.get(key)?.let { it == "1" } ?: default }, { store.put(key, if (it) "1" else "0") })
    private fun text(key: String, default: String) = Prop({ store.get(key)?.takeIf { it.isNotEmpty() } ?: default }, { store.put(key, it) })
    private fun number(key: String, default: Int) = Prop({ store.get(key)?.toIntOrNull() ?: default }, { store.put(key, it.toString()) })
    private fun time(key: String, default: LocalTime) =
        Prop({ store.get(key)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: default }, { store.put(key, it.toString()) })

    private class Prop<T>(val read: () -> T, val write: (T) -> Unit) {
        operator fun getValue(owner: Any?, property: kotlin.reflect.KProperty<*>): T = read()
        operator fun setValue(owner: Any?, property: kotlin.reflect.KProperty<*>, value: T) = write(value)
    }

    private companion object {
        const val KEY_END_CALENDARS = "reminders.event_end.calendars"
    }
}
