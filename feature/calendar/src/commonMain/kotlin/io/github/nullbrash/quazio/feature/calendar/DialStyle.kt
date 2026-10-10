package io.github.nullbrash.quazio.feature.calendar

/**
 * Вид виджета-циферблата: цвета (ARGB) по группам образца пользователя и общая прозрачность.
 * В общем коде, а не в рисовальщике Android — чтобы тот же круг мог нарисовать и другой
 * рисовальщик (умные часы). Прозрачный цвет (альфа 0) — элемент не рисуется. Значения по
 * умолчанию — вид виджета до настройки; новые элементы по умолчанию прозрачные.
 */
data class DialStyle(
    // Фон виджета
    val outerRing: Long = CLEAR,
    val middleRing: Long = CLEAR,
    val background: Long = 0xFF000000,
    // Стрелки
    val hand: Long = 0xFFE53935,
    val timeBoundary: Long = CLEAR,
    // Секторы
    /** Цвет сектора у события без своего цвета. */
    val sectorBase: Long = 0xFF9E9E9E,
    val sectorTimeBackground: Long = CLEAR,
    val eventText: Long = WHITE,
    val sectorTime: Long = CLEAR,
    val durationArc: Long = CLEAR,
    // Дуга ожидания события («осталось»)
    val waitingArc: Long = WHITE,
    val waitingText: Long = WHITE,
    // Центральная область
    val centerBackground: Long = CLEAR,
    val centerText: Long = WHITE,
    /** Кольцо «завтра» — граница завтрашнего дня. */
    val dayArc: Long = ORANGE,
    val dayArcBackground: Long = CLEAR,
    val dayText: Long = ORANGE,
    // Нумерация и деления
    val hourTicks: Long = 0x46FFFFFF,
    val hourNumbers: Long = WHITE,
    // Другое
    /** Фон кнопок «календарь» и «+». */
    val buttons: Long = 0x99000000,
    /** Общая непрозрачность всего виджета, 0…100 %. */
    val opacity: Int = 100,
) {

    /** «ключ=AARRGGBB;…»: незнакомые ключи пропускаются, недостающие — по умолчанию (новые версии). */
    fun encode(): String = (COLORS.map { "${it.key}=${hex(it.get(this))}" } + "opacity=$opacity").joinToString(";")

    /** Одно поле цвета — для хранения и экрана настройки. */
    class ColorField(val key: String, val get: (DialStyle) -> Long, val set: (DialStyle, Long) -> DialStyle)

    companion object {
        const val CLEAR = 0x00000000L
        const val WHITE = 0xFFFFFFFFL
        const val ORANGE = 0xFFFF9800L

        val COLORS: List<ColorField> = listOf(
            ColorField("outer_ring", { it.outerRing }) { s, c -> s.copy(outerRing = c) },
            ColorField("middle_ring", { it.middleRing }) { s, c -> s.copy(middleRing = c) },
            ColorField("background", { it.background }) { s, c -> s.copy(background = c) },
            ColorField("hand", { it.hand }) { s, c -> s.copy(hand = c) },
            ColorField("time_boundary", { it.timeBoundary }) { s, c -> s.copy(timeBoundary = c) },
            ColorField("sector_base", { it.sectorBase }) { s, c -> s.copy(sectorBase = c) },
            ColorField("sector_time_background", { it.sectorTimeBackground }) { s, c -> s.copy(sectorTimeBackground = c) },
            ColorField("event_text", { it.eventText }) { s, c -> s.copy(eventText = c) },
            ColorField("sector_time", { it.sectorTime }) { s, c -> s.copy(sectorTime = c) },
            ColorField("duration_arc", { it.durationArc }) { s, c -> s.copy(durationArc = c) },
            ColorField("waiting_arc", { it.waitingArc }) { s, c -> s.copy(waitingArc = c) },
            ColorField("waiting_text", { it.waitingText }) { s, c -> s.copy(waitingText = c) },
            ColorField("center_background", { it.centerBackground }) { s, c -> s.copy(centerBackground = c) },
            ColorField("center_text", { it.centerText }) { s, c -> s.copy(centerText = c) },
            ColorField("day_arc", { it.dayArc }) { s, c -> s.copy(dayArc = c) },
            ColorField("day_arc_background", { it.dayArcBackground }) { s, c -> s.copy(dayArcBackground = c) },
            ColorField("day_text", { it.dayText }) { s, c -> s.copy(dayText = c) },
            ColorField("hour_ticks", { it.hourTicks }) { s, c -> s.copy(hourTicks = c) },
            ColorField("hour_numbers", { it.hourNumbers }) { s, c -> s.copy(hourNumbers = c) },
            ColorField("buttons", { it.buttons }) { s, c -> s.copy(buttons = c) },
        )

        fun decode(text: String?): DialStyle {
            var style = DialStyle()
            text.orEmpty().split(';').forEach { part ->
                val key = part.substringBefore('=', "")
                val value = part.substringAfter('=', "")
                if (key == "opacity") value.toIntOrNull()?.let { style = style.copy(opacity = it.coerceIn(0, 100)) }
                else COLORS.firstOrNull { it.key == key }?.let { f -> value.toLongOrNull(16)?.let { style = f.set(style, it and 0xFFFFFFFFL) } }
            }
            return style
        }

        private fun hex(c: Long) = (c and 0xFFFFFFFFL).toString(16).padStart(8, '0')
    }
}
