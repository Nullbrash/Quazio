package io.github.nullbrash.quazio.feature.calendar

/**
 * Цвета секторов на циферблате. Дела одного цвета (почти все — из одного календаря Google)
 * вплотную или внахлёст сливались бы в одну дугу; по настройке пользователя у следующего —
 * чуть светлее или темнее. Только отображение: в календаре цвет не меняется.
 */
object DialColors {

    /**
     * Цвет каждого сектора (по порядку [sectors]); [distinguish] — настройка «различать соседние»;
     * [fallback] — у события без цвета; [allFallback] — все сектора цветом [fallback].
     */
    fun of(sectors: List<DialSector>, distinguish: Boolean, fallback: Long = 0xFF9E9E9E, allFallback: Boolean = false): List<Long> {
        val base = sectors.map { if (allFallback) fallback else it.event.color ?: fallback }
        if (!distinguish) return base
        val result = base.toMutableList()
        val order = sectors.indices.sortedWith(compareBy({ sectors[it].from }, { sectors[it].lane }))
        val done = ArrayList<Int>()
        for (i in order) {
            val s = sectors[i]
            // Соседи — уже раскрашенные, которые касаются концами или пересекаются по времени.
            val near = done.filter { j -> sectors[j].to >= s.from - TOUCH_MS && sectors[j].from <= s.to + TOUCH_MS }.map { result[it] }.toSet()
            result[i] = variants(base[i]).firstOrNull { it !in near } ?: base[i]
            done += i
        }
        return result
    }

    /** Свой цвет, потом темнее, светлее, ещё темнее, ещё светлее. */
    private fun variants(c: Long) = listOf(c, mix(c, 0xFF000000, 0.28f), mix(c, 0xFFFFFFFF, 0.32f), mix(c, 0xFF000000, 0.5f), mix(c, 0xFFFFFFFF, 0.55f))

    private fun mix(c: Long, to: Long, k: Float): Long {
        fun ch(v: Long, shift: Int) = ((v shr shift) and 0xFF).toInt()
        fun m(shift: Int) = (ch(c, shift) + (ch(to, shift) - ch(c, shift)) * k).toInt().coerceIn(0, 255).toLong()
        return (c and 0xFF000000) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }

    private const val TOUCH_MS = 60_000L
}
