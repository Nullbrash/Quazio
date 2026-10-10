package io.github.nullbrash.quazio.feature.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetPrefsTest {

    private class MemoryStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
    }

    @Test
    fun defaultsAreLikeTheUsersWidget() {
        val p = WidgetPrefs(MemoryStore())
        assertFalse(p.dial24) // 12 «скользящих» часов
        assertEquals(100, p.circleOpacity) // чёрный непрозрачный круг
        assertTrue(p.showUntilNext)
        assertTrue(p.showButtons)
        p.circleOpacity = 140
        assertEquals(100, p.circleOpacity)
        p.dial24 = true
        p.showButtons = false
        assertTrue(p.dial24)
        assertFalse(p.showButtons)
    }

    @Test
    fun styleDefaultsAreTheLookBeforeColours() {
        val s = WidgetPrefs(MemoryStore()).style
        assertEquals(DialStyle(), s)
        assertEquals(0xFF000000, s.background) // чёрный непрозрачный круг
        assertEquals(0xFFE53935, s.hand) // красная стрелка
        assertEquals(0xFF9E9E9E, s.sectorBase) // события без цвета — серые, как раньше
        assertEquals(100, s.opacity)
        // Новые элементы до настройки не рисуются.
        listOf(s.outerRing, s.middleRing, s.timeBoundary, s.sectorTimeBackground, s.sectorTime, s.durationArc, s.centerBackground, s.dayArcBackground)
            .forEach { assertEquals(DialStyle.CLEAR, it) }
    }

    @Test
    fun styleIsSavedAndReadBack() {
        val store = MemoryStore()
        val p = WidgetPrefs(store)
        val custom = DialStyle(outerRing = 0x80FF0000, hand = 0xFF00FF00, buttons = 0xFF000000, opacity = 60)
        p.style = custom
        assertEquals(custom, WidgetPrefs(store).style)
        // Незнакомые ключи (из будущих версий) и мусор не ломают чтение.
        assertEquals(DialStyle(hand = 0xFF00FF00), DialStyle.decode("hand=ff00ff00;future_key=12345678;opacity=abc;garbage"))
        assertEquals(DialStyle.COLORS.size, DialStyle.COLORS.map { it.key }.toSet().size) // ключи не повторяются
        assertEquals(20, DialStyle.COLORS.size) // все цвета образца
    }

    @Test
    fun oldCircleOpacityBecomesBackgroundAlpha() {
        val store = MemoryStore().apply { put("widget.circle_opacity", "70") }
        val p = WidgetPrefs(store)
        assertEquals(70 * 255 / 100, ((p.style.background ushr 24) and 0xFF).toInt())
        assertEquals(70, p.circleOpacity)
        p.circleOpacity = 40
        assertEquals(40, WidgetPrefs(store).circleOpacity)
        assertEquals(0x000000L, WidgetPrefs(store).style.background and 0xFFFFFF) // цвет фона не трогается
    }
}
