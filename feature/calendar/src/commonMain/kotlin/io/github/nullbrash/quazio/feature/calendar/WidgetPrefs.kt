package io.github.nullbrash.quazio.feature.calendar

/**
 * Виджет-циферблат на рабочем столе — общие настройки для всех виджетов этого устройства
 * (решение пользователя: базовые — 12/24 часа, прозрачность круга, «осталось», кнопки;
 * подробная настройка «как у пользователя» — следующим шагом).
 */
class WidgetPrefs(private val store: KeyValueStore) {

    /** 24 часа или (по умолчанию) 12 «скользящих» — как виджет пользователя. Своё, не как во вкладке. */
    var dial24: Boolean
        get() = store.get(KEY_24) == "1"
        set(value) = store.put(KEY_24, if (value) "1" else "0")

    /** Непрозрачность круга, 0…100 %; по умолчанию — чёрный непрозрачный. */
    var circleOpacity: Int
        get() = store.get(KEY_OPACITY)?.toIntOrNull()?.coerceIn(0, 100) ?: 100
        set(value) = store.put(KEY_OPACITY, value.coerceIn(0, 100).toString())

    var showUntilNext: Boolean
        get() = store.get(KEY_UNTIL) != "0"
        set(value) = store.put(KEY_UNTIL, if (value) "1" else "0")

    var showButtons: Boolean
        get() = store.get(KEY_BUTTONS) != "0"
        set(value) = store.put(KEY_BUTTONS, if (value) "1" else "0")

    private companion object {
        const val KEY_24 = "widget.dial_24"
        const val KEY_OPACITY = "widget.circle_opacity"
        const val KEY_UNTIL = "widget.until_next"
        const val KEY_BUTTONS = "widget.buttons"
    }
}
