package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CalculatorUiTest {

    @Test
    fun fullScreenKeypadAddsUpAndReturnsTheSum() = runComposeUiTest {
        var result: Long? = null
        setContent { MaterialTheme { CalculatorDialog("", allowNegative = false, onDone = { minor, _ -> result = minor }, onDismiss = {}, fullScreen = true) } }
        listOf("1", "2", "+", "3", "0").forEach { onNodeWithText(it).performClick() }
        onNodeWithText("= 42", substring = true).assertExists() // перед «₽» — неразрывный пробел
        onNodeWithContentDescription("Готово").performClick()
        assertEquals(4_200L, result)
    }

    @Test
    fun fullScreenBackArrowCancels() = runComposeUiTest {
        var dismissed = 0
        setContent { MaterialTheme { CalculatorDialog("500", allowNegative = false, onDone = { _, _ -> }, onDismiss = { dismissed++ }, fullScreen = true) } }
        onNodeWithText("Введите сумму").assertExists()
        onNodeWithContentDescription("Отмена").performClick()
        assertEquals(1, dismissed)
    }
}
