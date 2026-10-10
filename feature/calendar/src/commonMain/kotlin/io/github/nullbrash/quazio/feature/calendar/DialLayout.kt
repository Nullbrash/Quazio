package io.github.nullbrash.quazio.feature.calendar

/**
 * Раскладка дня на циферблате — как у виджета Sectograph пользователя. Чистая логика без
 * рисования: углы в градусах от «12 часов» по часовой стрелке.
 *
 * 12 часов, «скользящий»: окно — ближайшие 12 часов от «сейчас», циферблат — обычные часы
 * (12 сверху), подписаны часы, которые впереди. 24 часа: сутки целиком, полночь сверху.
 */
enum class DialMode(val spanMinutes: Int) { SLIDING_12(720), DAY_24(1440) }

data class DialSector(
    val event: CalendarEvent,
    /** Видимая часть события (обрезана окном циферблата), мс UTC. */
    val from: Long,
    val to: Long,
    val startAngle: Float,
    val sweep: Float,
    /** Пересекающиеся события — на разных «дорожках» (0 — внешняя). */
    val lane: Int,
    /**
     * Сколько дорожек в его группе пересекающихся событий: сектор толщиной в полосу / [lanes].
     * Одно пересечение в обед не делает тонкими все сектора дня (нашлось в перегруженном дне).
     */
    val lanes: Int = 1,
)

data class HourLabel(val hour: Int, val angle: Float)

data class DialLayout(
    val mode: DialMode,
    val windowStart: Long,
    val windowEnd: Long,
    val sectors: List<DialSector>,
    val labels: List<HourLabel>,
    val lanes: Int,
    /** Стрелка «сейчас» — если сейчас внутри окна. */
    val nowAngle: Float?,
    /** До ближайшего события в окне: дуга от «сейчас» до его начала и сколько минут осталось. */
    val untilNext: UntilNext?,
    /** Где начинается завтра (12-часовой режим, окно через полночь) — угол полуночи. */
    val tomorrowAngle: Float?,
    /**
     * Где кончается показанное время (линия границы): у 12 «скользящих» — «сейчас + 12 ч», то
     * есть под стрелкой; у 24 часов — полночь.
     */
    val boundaryAngle: Float = 0f,
)

data class UntilNext(val fromAngle: Float, val sweep: Float, val minutes: Long, val event: CalendarEvent)

object DialLayouts {

    private const val MINUTE = 60_000L

    /**
     * @param minuteOfDay местное время дня (0..1439) для момента; передаётся снаружи —
     * логике не нужен часовой пояс.
     * @param dayStart / dayEnd — границы суток для 24-часового режима (местная полночь).
     */
    fun layout(
        mode: DialMode,
        events: List<CalendarEvent>,
        now: Long,
        dayStart: Long,
        dayEnd: Long,
        minuteOfDay: (Long) -> Int,
        maxLanes: Int = 3,
    ): DialLayout {
        val span = mode.spanMinutes
        val (windowStart, windowEnd) = when (mode) {
            DialMode.SLIDING_12 -> now to now + span * MINUTE
            DialMode.DAY_24 -> dayStart to dayEnd
        }
        fun angleOf(t: Long): Float = (minuteOfDay(t) % span) * 360f / span

        // События на весь день по кругу не рисуются — у них нет времени (показываются списком).
        val timed = events.filter { !it.allDay && it.end > windowStart && it.start < windowEnd && it.end > it.start }
            .sortedWith(compareBy({ it.start }, { -it.end }))
        // Группы пересекающихся событий (касание концами — не пересечение): дорожки — внутри группы.
        val sectors = ArrayList<DialSector>()
        var maxUsed = 1
        var group = ArrayList<DialSector>()
        var groupEnd = Long.MIN_VALUE
        var laneEnds = ArrayList<Long>()
        fun closeGroup() {
            val n = laneEnds.size.coerceAtLeast(1)
            maxUsed = maxOf(maxUsed, n)
            group.forEach { sectors += it.copy(lanes = n) }
            group = ArrayList()
            laneEnds = ArrayList()
        }
        for (e in timed) {
            val from = maxOf(e.start, windowStart)
            val to = minOf(e.end, windowEnd)
            if (group.isNotEmpty() && from >= groupEnd) closeGroup()
            var lane = laneEnds.indexOfFirst { it <= from }
            if (lane < 0) {
                lane = if (laneEnds.size < maxLanes) laneEnds.size.also { laneEnds += 0L } else laneEnds.indices.minBy { laneEnds[it] }
            }
            laneEnds[lane] = maxOf(laneEnds[lane], to)
            groupEnd = if (group.isEmpty()) to else maxOf(groupEnd, to)
            group += DialSector(e, from, to, angleOf(from), ((to - from) / MINUTE) * 360f / span, lane)
        }
        closeGroup()

        val labels = when (mode) {
            // Часы впереди: следующий полный час и ещё 11 — подписаны часами суток (16, 17 … 1, 2).
            DialMode.SLIDING_12 -> {
                val firstHour = (now / (60 * MINUTE) + 1) * 60 * MINUTE
                (0 until 12).map { i ->
                    val t = firstHour + i * 60 * MINUTE
                    HourLabel(minuteOfDay(t) / 60, angleOf(t))
                }
            }
            DialMode.DAY_24 -> (0 until 24).map { h -> HourLabel(h, h * 15f) }
        }

        val nowInside = now in windowStart until windowEnd
        val next = if (nowInside) timed.firstOrNull { it.start >= now && it.start < windowEnd } else null
        val untilNext = next?.let {
            val minutes = (it.start - now) / MINUTE
            UntilNext(angleOf(now), minutes * 360f / span, minutes, it)
        }

        val tomorrowAngle = if (mode == DialMode.SLIDING_12) {
            // Полночь внутри окна: минута суток «сейчас» больше, чем у конца окна.
            if (minuteOfDay(windowEnd - MINUTE) < minuteOfDay(now)) 0f else null
        } else null

        return DialLayout(
            mode, windowStart, windowEnd, sectors, labels, maxUsed,
            if (nowInside) angleOf(now) else null, untilNext, tomorrowAngle,
            boundaryAngle = angleOf(windowEnd),
        )
    }
}
