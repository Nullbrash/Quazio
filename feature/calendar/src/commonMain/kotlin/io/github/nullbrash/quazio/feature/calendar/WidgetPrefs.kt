package io.github.nullbrash.quazio.feature.calendar

/**
 * Виджет-циферблат на рабочем столе — общие настройки для всех виджетов этого устройства
 * (решение пользователя: базовые — 12/24 часа, прозрачность круга, «осталось», кнопки;
 * подробная — цвета и общая прозрачность, [style]).
 */
class WidgetPrefs(private val store: KeyValueStore) {

    /** 24 часа или (по умолчанию) 12 «скользящих» — как виджет пользователя. Своё, не как во вкладке. */
    var dial24: Boolean
        get() = store.get(KEY_24) == "1"
        set(value) = store.put(KEY_24, if (value) "1" else "0")

    /** Цвета и общая прозрачность круга; не сохранены — вид по умолчанию. */
    var style: DialStyle
        get() {
            store.get(KEY_STYLE)?.takeIf { it.isNotEmpty() }?.let { return DialStyle.decode(it) }
            // До настройки цветов непрозрачность круга хранилась отдельно — она становится альфой фона.
            val old = store.get(KEY_OPACITY)?.toIntOrNull()?.coerceIn(0, 100) ?: return DialStyle()
            return DialStyle().let { it.copy(background = withAlpha(it.background, old * 255 / 100)) }
        }
        set(value) = store.put(KEY_STYLE, value.encode())

    /** Непрозрачность круга, 0…100 % — альфа цвета основного фона; по умолчанию — непрозрачный. */
    var circleOpacity: Int
        get() = (((style.background ushr 24) and 0xFF).toInt() * 100 + 127) / 255
        set(value) {
            val s = style
            style = s.copy(background = withAlpha(s.background, value.coerceIn(0, 100) * 255 / 100))
        }

    private fun withAlpha(color: Long, alpha: Int) = (alpha.toLong() shl 24) or (color and 0xFFFFFF)

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
        const val KEY_STYLE = "widget.style"
    }
}
