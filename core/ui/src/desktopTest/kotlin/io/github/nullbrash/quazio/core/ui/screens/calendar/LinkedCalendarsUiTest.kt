package io.github.nullbrash.quazio.core.ui.screens.calendar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import io.github.nullbrash.quazio.core.ui.CalendarRefresher
import io.github.nullbrash.quazio.core.ui.LocalCalendarRefresher
import io.github.nullbrash.quazio.core.ui.screens.CalendarSettingsSection
import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.KeyValueStore
import io.github.nullbrash.quazio.feature.calendar.ical.FetchException
import io.github.nullbrash.quazio.feature.calendar.ical.FetchProblem
import io.github.nullbrash.quazio.feature.calendar.ical.LinkedCalendars
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/** ПК: календари Google по ссылке — добавление в настройках, показ, «Обновить», только просмотр. */
@OptIn(ExperimentalTestApi::class)
class LinkedCalendarsUiTest {

    private val zone = TimeZone.currentSystemDefault()
    private val today = millisToLocal(Clock.System.now().toEpochMilliseconds(), zone).date
    private val ymd = "${today.year}${(today.month.ordinal + 1).toString().padStart(2, '0')}${today.day.toString().padStart(2, '0')}"

    // «Плавающее» время (без пояса) — время того, кто смотрит: событие всегда сегодня в 10:00.
    private val ics = """
        BEGIN:VCALENDAR
        X-WR-CALNAME:Семья
        BEGIN:VEVENT
        UID:dentist@example.com
        DTSTART:${ymd}T100000
        DTEND:${ymd}T110000
        SUMMARY:Стоматолог
        LOCATION:Клиника на углу
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    private val store = object : KeyValueStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }
    private val prefs = CalendarPrefs(store)
    private var answer: () -> String = { ics }
    private var fetches = 0
    private val links = LinkedCalendars(store, { fetches++; answer() }, System::currentTimeMillis)
    private val refresher = CalendarRefresher(links, prefs)

    @Test
    fun addLinkInSettings() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalCalendarRefresher provides refresher) { MaterialTheme { CalendarSettingsSection(links, prefs) } } }
        onNodeWithText("Добавить календарь Google").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Секретный адрес в формате iCal")).fetchSemanticsNodes().isNotEmpty() }

        // Ссылка не та — понятная ошибка, ничего не сохранено.
        answer = { throw FetchException(FetchProblem.NOT_FOUND, 404) }
        onAllNodes(hasSetTextAction())[0].performTextInput("https://calendar.google.com/calendar/ical/x/private-old/basic.ics")
        onNodeWithText("Добавить").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Ссылка не работает", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(links.links().isEmpty())

        answer = { ics }
        onNodeWithText("Добавить").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Семья")).fetchSemanticsNodes().isNotEmpty() } // имя — из календаря
        assertTrue(prefs.enabled)
        onNodeWithText("обновлено", substring = true).assertExists()
        // Секретная ссылка после добавления на экране не показывается.
        assertTrue(onAllNodes(hasText("private-old", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun calendarShowsLinkedEventsReadOnlyWithRefresh() = runComposeUiTest {
        links.add("https://calendar.google.com/calendar/ical/x/private-s/basic.ics", "", "Календарь")
        prefs.enabled = true
        val before = fetches
        setContent { CompositionLocalProvider(LocalCalendarRefresher provides refresher) { MaterialTheme { CalendarScreen(links, prefs) } } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Стоматолог")).fetchSemanticsNodes().isNotEmpty() }
        waitUntil("загрузка при открытии", timeoutMillis = 10_000) { fetches == before + 1 }
        // Писать некуда — кнопки нового события нет.
        assertTrue(onAllNodes(hasContentDescription("Новое событие")).fetchSemanticsNodes().isEmpty())

        // Сеть пропала: «Обновить» — видна прошлая загрузка и строка об ошибке.
        answer = { throw FetchException(FetchProblem.OFFLINE) }
        onNodeWithContentDescription("Обновить").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("проверьте интернет", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(before + 2, fetches)
        onNodeWithText("Стоматолог").assertExists()

        // Событие — только просмотр.
        onNodeWithText("Стоматолог").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Только просмотр", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Место: Клиника на углу").assertExists()
        onNodeWithText("10:00 – 11:00", substring = true).assertExists()
        assertTrue(onAllNodes(hasText("Сохранить")).fetchSemanticsNodes().isEmpty())
    }
}
