package io.github.nullbrash.quazio.feature.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarPrefsTest {

    private class MemoryStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
    }

    private fun cal(id: String, kind: CalendarKind, visible: Boolean = true, writable: Boolean = true, primary: Boolean = false) =
        CalendarInfo(id, "Календарь $id", "acc", kind, 0xFF000000, visible, writable, primary)

    private val google = cal("1", CalendarKind.GOOGLE, primary = true)
    private val holidays = cal("2", CalendarKind.GOOGLE, writable = false)
    private val phone = cal("3", CalendarKind.PHONE)
    private val hiddenInSystem = cal("4", CalendarKind.OTHER, visible = false)
    private val all = listOf(google, holidays, phone, hiddenInSystem)

    @Test
    fun byDefaultShowsWhatTheSystemShows() {
        val prefs = CalendarPrefs(MemoryStore())
        assertFalse(prefs.enabled)
        assertEquals(listOf(google, holidays, phone), prefs.shown(all))
    }

    @Test
    fun userChoiceWinsAndNewCalendarsFollowTheSystem() {
        val store = MemoryStore()
        val prefs = CalendarPrefs(store)
        prefs.choices = mapOf("2" to false, "4" to true)
        // Перечитываем — выбор сохранён в хранилище, а не только в памяти.
        val again = CalendarPrefs(store)
        assertEquals(listOf(google, phone, hiddenInSystem), again.shown(all))
        val newOne = cal("9", CalendarKind.GOOGLE)
        assertTrue(again.isShown(newOne))
    }

    @Test
    fun defaultCalendarFallsBackWhenTheChosenOneIsGone() {
        val prefs = CalendarPrefs(MemoryStore())
        assertEquals(google, prefs.defaultFor(all)) // основной Google
        prefs.defaultCalendarId = "3"
        assertEquals(phone, prefs.defaultFor(all))
        // Календарь удалили (отписались) — снова основной Google, а не пустота.
        assertEquals(google, prefs.defaultFor(all - phone))
        // В «праздники» писать нельзя — его не предложим, даже если выбран.
        prefs.defaultCalendarId = "2"
        assertEquals(google, prefs.defaultFor(all))
        assertNull(prefs.defaultFor(listOf(holidays)))
    }
}
