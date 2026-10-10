package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import io.github.nullbrash.quazio.core.ui.LocalWidgetUpdater
import io.github.nullbrash.quazio.feature.calendar.DialStyle
import io.github.nullbrash.quazio.feature.calendar.KeyValueStore
import io.github.nullbrash.quazio.feature.calendar.WidgetPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WidgetColorsUiTest {

    private class MemoryStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
    }

    private val prefs = WidgetPrefs(MemoryStore())
    private var updates = 0
    private val green = 0xFF33B679

    private fun androidx.compose.ui.test.ComposeUiTest.show() = setContent {
        CompositionLocalProvider(LocalWidgetUpdater provides { updates++ }) {
            MaterialTheme { WidgetColorsScreen(prefs, calendarColors = listOf(green), onClose = {}) }
        }
    }

    @Test
    fun usedColourIsPickedAndSaved() = runComposeUiTest {
        show()
        onNodeWithText("Стрелка времени").performClick()
        onNodeWithText("Уже используются").assertExists()
        // Цвета других строк идут первыми, цвет календаря — после них.
        val index = (DialStyle.COLORS.map { it.get(DialStyle()) and 0xFFFFFFFFL }.filter { (it ushr 24) != 0L }.distinct()).size
        onNodeWithTag("used-$index").performClick()
        onNodeWithText("Готово").performClick()
        waitUntil(timeoutMillis = 10_000) { prefs.style.hand == green }
        assertTrue(updates > 0)
    }

    @Test
    fun centreOfTheWheelIsWhite() = runComposeUiTest {
        show()
        onNodeWithText("Основной фон круга").performClick()
        onNodeWithTag("color-wheel").performTouchInput { click(center) }
        onNodeWithText("Готово").performClick()
        // Яркость и непрозрачность у чёрного фона были 0/100 % — тап по кругу поднимает яркость.
        waitUntil(timeoutMillis = 10_000) { prefs.style.background == 0xFFFFFFFF }
    }

    @Test
    fun allSectorsBaseAndReset() = runComposeUiTest {
        show()
        onNodeWithText("Все сектора базовым цветом").performScrollTo()
        onAllToggleables().first().performClick()
        waitUntil(timeoutMillis = 10_000) { prefs.style.allSectorsBase }
        onNodeWithContentDescription("Вернуть исходные цвета").performClick() // в верхней панели
        onNodeWithText("Готово").performClick()
        waitUntil(timeoutMillis = 10_000) { prefs.style == DialStyle() }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllToggleables() =
        onAllNodes(isToggleable()).fetchSemanticsNodes().indices.map { onAllNodes(isToggleable())[it] }

    @Test
    fun hsvRoundTrip() {
        listOf(0xFF000000, 0xFFFFFFFF, 0xFFE53935, 0x80FF9800, 0xFF8E24AA, 0x46FFFFFF, 0xFF039BE5).forEach { c ->
            assertEquals(c, Hsva.of(c).argb(), "цвет ${c.toString(16)}")
        }
    }

    /** Подставной буфер обмена: тест не трогает настоящий буфер компьютера. */
    @Suppress("DEPRECATION")
    private class FakeClipboard(var text: String? = null) : androidx.compose.ui.platform.ClipboardManager {
        override fun getText() = text?.let { androidx.compose.ui.text.AnnotatedString(it) }
        override fun setText(annotatedString: androidx.compose.ui.text.AnnotatedString) { text = annotatedString.text }
    }

    @Suppress("DEPRECATION")
    @Test
    fun copyPasteThroughClipboard() = runComposeUiTest {
        val clip = FakeClipboard("Купить молоко")
        setContent {
            CompositionLocalProvider(LocalWidgetUpdater provides { updates++ }, androidx.compose.ui.platform.LocalClipboardManager provides clip) {
                MaterialTheme { WidgetColorsScreen(prefs, calendarColors = emptyList(), onClose = {}) }
            }
        }
        onNodeWithContentDescription("Вставить настройки").performClick()
        onNodeWithText("В буфере обмена нет настроек виджета.").assertExists() // чужой текст не применяется
        val mine = DialStyle(hand = 0xFF00FF00, opacity = 80)
        clip.text = mine.share()
        onNodeWithContentDescription("Вставить настройки").performClick()
        waitUntil(timeoutMillis = 10_000) { prefs.style == mine }
        clip.text = null
        onNodeWithContentDescription("Копировать настройки").performClick()
        assertEquals(mine.share(), clip.text)
    }
}
