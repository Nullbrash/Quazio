package io.github.nullbrash.quazio.core.ui.screens.calendar

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.test.runComposeUiTest
import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import io.github.nullbrash.quazio.feature.calendar.CalendarInfo
import io.github.nullbrash.quazio.feature.calendar.CalendarKind
import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.CalendarSource
import io.github.nullbrash.quazio.feature.calendar.EventDraft
import io.github.nullbrash.quazio.feature.calendar.KeyValueStore
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Вкладка «Календарь» на поддельном календаре устройства: день, месяц, окно события. */
@OptIn(ExperimentalTestApi::class)
class CalendarScreenUiTest {

    private val zone = TimeZone.currentSystemDefault()
    private val h = 3_600_000L
    private val now = Clock.System.now().toEpochMilliseconds()
    private val todayStart = millisToLocal(now, zone).date.startMillis(zone)
    private val utcToday = millisToLocal(now, zone).date.startMillis(TimeZone.UTC)

    private val store = object : KeyValueStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
    }
    private val prefs = CalendarPrefs(store).also { it.enabled = true; it.defaultCalendarId = "1" }

    private val google = CalendarInfo("1", "Личный", "me@example.com", CalendarKind.GOOGLE, 0xFF7986CB, true, true, true)

    private inner class FakeSource : CalendarSource {
        val events = mutableListOf(
            CalendarEvent("10", "1", "Планёрка", todayStart + 23 * h, todayStart + 23 * h + h / 2, false, zone.id, null, null, false, todayStart + 23 * h),
            CalendarEvent("11", "1", "Зарядка", todayStart + 7 * h, todayStart + 7 * h + h / 2, false, zone.id, null, null, true, todayStart + 7 * h),
            // Весь день у Android — полночь UTC, а не местного времени.
            CalendarEvent("12", "1", "Отпуск", utcToday, utcToday + 24 * h, true, "UTC", null, null, false, utcToday),
            CalendarEvent("13", "1", "Завтрашнее", todayStart + 24 * h + 12 * h, todayStart + 24 * h + 13 * h, false, zone.id, null, null, false, todayStart + 36 * h),
        )
        val created = mutableListOf<EventDraft>()
        val instanceEdits = mutableListOf<Pair<Long, EventDraft>>()

        override fun calendars() = listOf(google)
        override fun events(from: Long, to: Long, calendarIds: Set<String>) = events.filter { it.end > from && it.start < to && it.calendarId in calendarIds }
        override fun create(draft: EventDraft): String { created += draft; return "new" }
        override fun update(eventId: String, draft: EventDraft) = Unit
        override fun updateInstance(eventId: String, instanceStart: Long, draft: EventDraft) { instanceEdits += instanceStart to draft }
        override fun delete(eventId: String) = Unit
        override fun deleteInstance(eventId: String, instanceStart: Long) = Unit
        override fun reminders(eventId: String) = emptyList<Int>()
        override fun event(eventId: String): EventDraft? = when (eventId) {
            "11" -> EventDraft("1", "Зарядка", todayStart - 10 * 24 * h + 7 * h, todayStart - 10 * 24 * h + 7 * h + h / 2, timeZone = zone.id, rrule = "FREQ=DAILY")
            else -> null
        }
    }

    @Test
    fun dayShowsEventsDialAndAllDay() = runComposeUiTest {
        val source = FakeSource()
        setContent { MaterialTheme { CalendarScreen(source, prefs) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Зарядка")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Планёрка").assertExists()
        onNodeWithText("Отпуск").assertExists() // весь день — чипом над циферблатом
        onNodeWithContentDescription("Циферблат дня").assertExists()
        onNodeWithText("12 ч").assertExists()
    }

    @Test
    fun newEventGoesToTheCalendarForNewEvents() = runComposeUiTest {
        val source = FakeSource()
        setContent { MaterialTheme { CalendarScreen(source, prefs) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Зарядка")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Новое событие").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Сохранить")).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(hasSetTextAction())[0].performTextInput("Врач")
        onNodeWithText("Сохранить").performClick()
        waitUntil(timeoutMillis = 10_000) { source.created.isNotEmpty() }

        val d = source.created.single()
        assertEquals("Врач", d.title)
        assertEquals("1", d.calendarId)
        assertEquals(h, d.end - d.start) // по умолчанию 1 час
        assertEquals(0L, d.start % h) // ближайший полный час
        assertNull(d.rrule)
    }

    @Test
    fun editingOneRepetitionAsksScope() = runComposeUiTest {
        val source = FakeSource()
        setContent { MaterialTheme { CalendarScreen(source, prefs) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Зарядка")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Зарядка").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Каждый день")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Сохранить").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Только это событие")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Только это событие").performClick()
        waitUntil(timeoutMillis = 10_000) { source.instanceEdits.isNotEmpty() }

        val (instance, d) = source.instanceEdits.single()
        assertEquals(todayStart + 7 * h, instance)
        assertEquals(todayStart + 7 * h, d.start) // время этого повторения, а не начала серии
        assertNull(d.rrule)
    }

    @Test
    fun draggingTheHandScrollsTimeAndCenterTapReturns() = runComposeUiTest {
        val source = FakeSource()
        setContent { MaterialTheme { CalendarScreen(source, prefs) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Зарядка")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasText("Завтрашнее")).fetchSemanticsNodes().isEmpty())
        // Два полных оборота по часовой: в 12-часовом режиме — сутки вперёд.
        onNodeWithContentDescription("Циферблат дня").performTouchInput {
            val c = center
            val r = width * 0.35f
            fun at(deg: Double) = Offset(c.x + r * sin(deg * PI / 180).toFloat(), c.y - r * cos(deg * PI / 180).toFloat())
            down(at(0.0))
            for (d in 5..720 step 5) moveTo(at(d.toDouble()))
            up()
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Завтрашнее")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasText("Зарядка")).fetchSemanticsNodes().isEmpty())
        // Нажатие на центр — снова «сейчас».
        onNodeWithContentDescription("Циферблат дня").performTouchInput { click(center) }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Зарядка")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun dayButtonsUnderTheDialSwitchDays() = runComposeUiTest {
        val source = FakeSource()
        setContent { MaterialTheme { CalendarScreen(source, prefs) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Зарядка")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Следующий день").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Завтрашнее")).fetchSemanticsNodes().isNotEmpty() }
        // Вид «12 / 24 ч» — один на все дни: при смене дня сам не меняется (замечание пользователя).
        onNodeWithText("12 ч").assertIsSelected()
        onNodeWithText("24 ч").assertIsNotSelected()
        onNodeWithContentDescription("Предыдущий день").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Зарядка")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun monthDayOpensDayView() = runComposeUiTest {
        val source = FakeSource()
        setContent { MaterialTheme { CalendarScreen(source, prefs) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Месяц")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Месяц").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Пн")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasContentDescription("Циферблат дня")).fetchSemanticsNodes().isEmpty()) // в месяце циферблата нет
        // Нажатие на число в сетке месяца — этот день с циферблатом.
        onAllNodes(hasText(millisToLocal(now, zone).date.day.toString()))[0].performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasContentDescription("Циферблат дня")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasText("Пн")).fetchSemanticsNodes().isEmpty()) // сетки месяца больше нет
    }
}
