package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.nullbrash.quazio.core.ui.CalendarAccess
import io.github.nullbrash.quazio.core.ui.LocalCalendarAccess
import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import io.github.nullbrash.quazio.feature.calendar.CalendarInfo
import io.github.nullbrash.quazio.feature.calendar.CalendarKind
import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.CalendarSource
import io.github.nullbrash.quazio.feature.calendar.EventDraft
import io.github.nullbrash.quazio.feature.calendar.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** «Настройки → Календарь»: обязательный список при первом включении и календарь для новых событий. */
@OptIn(ExperimentalTestApi::class)
class CalendarSettingsUiTest {

    private val store = object : KeyValueStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
    }
    private val prefs = CalendarPrefs(store)

    private val google = CalendarInfo("1", "Личный", "me@example.com", CalendarKind.GOOGLE, 0xFF7986CB, true, true, true)
    private val holidays = CalendarInfo("2", "Праздники России", "me@example.com", CalendarKind.GOOGLE, 0xFF0B8043, true, false, false)
    private val phone = CalendarInfo("3", "Мой календарь", "Телефон", CalendarKind.PHONE, 0xFFF4511E, true, true, false)

    private val source = object : CalendarSource {
        override fun calendars() = listOf(google, holidays, phone)
        override fun events(from: Long, to: Long, calendarIds: Set<String>) = emptyList<CalendarEvent>()
        override fun create(draft: EventDraft) = error("не нужно")
        override fun update(eventId: String, draft: EventDraft) = Unit
        override fun updateInstance(eventId: String, instanceStart: Long, draft: EventDraft) = Unit
        override fun delete(eventId: String) = Unit
        override fun deleteInstance(eventId: String, instanceStart: Long) = Unit
        override fun reminders(eventId: String) = emptyList<Int>()
    }

    private fun access(allow: Boolean) = object : CalendarAccess {
        var asked = false
        override fun granted() = asked && allow
        override suspend fun request(): Boolean { asked = true; return allow }
    }

    @Test
    fun firstEnableShowsMandatoryListAndDefaultCalendar() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalCalendarAccess provides access(allow = true)) {
                MaterialTheme { CalendarSettingsSection(source, prefs) }
            }
        }
        onNodeWithText("Включить").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Какие календари показывать")).fetchSemanticsNodes().isNotEmpty() }
        // Праздники — только просмотр: в выборе «куда записывать» их нет.
        onNodeWithText("только просмотр").assertExists()
        onNodeWithText("Праздники России").performClick() // снять галочку
        // Два отдельных окна (пожелание пользователя): сначала «что показывать» → «Далее».
        onNodeWithText("Далее").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Календарь новых событий")).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(prefs.enabled) // до «Готово» ничего не сохранено
        // Во втором окне только показываемые и доступные для записи: праздников нет.
        assertTrue(onAllNodes(hasText("Праздники России")).fetchSemanticsNodes().isEmpty())
        onNodeWithText("Мой календарь").performClick()
        onNodeWithText("Готово").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Показываю календарей", substring = true)).fetchSemanticsNodes().isNotEmpty() }

        assertTrue(prefs.enabled)
        assertEquals(mapOf("1" to true, "2" to false, "3" to true), prefs.choices)
        assertEquals("3", prefs.defaultCalendarId)
        onNodeWithText("Показываю календарей: 2 из 3").assertExists()
        onNodeWithText("Календарь новых событий: Мой календарь").assertExists()

        // Потом каждое меняется своей кнопкой: «Выбрать» — только второе окно.
        onNodeWithText("Выбрать").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Календарь новых событий")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasText("Какие календари показывать")).fetchSemanticsNodes().isEmpty())
        onNodeWithText("Личный").performClick()
        onNodeWithText("Готово").performClick()
        waitUntil(timeoutMillis = 10_000) { prefs.defaultCalendarId == "1" }
        assertEquals(mapOf("1" to true, "2" to false, "3" to true), prefs.choices) // список показа не тронут
    }

    @Test
    fun deniedPermissionLeavesCalendarOff() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalCalendarAccess provides access(allow = false)) {
                MaterialTheme { CalendarSettingsSection(source, prefs) }
            }
        }
        onNodeWithText("Включить").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Без доступа к календарям", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(prefs.enabled)
    }
}
