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
}
