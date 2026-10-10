package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RecoveryCodeUiTest {

    @Test
    fun tapOnLabelMarksCodeSaved() = runComposeUiTest {
        var done = 0
        setContent { MaterialTheme { RecoveryCodeDialog("ABCD-EFGH") { done++ } } }
        onNodeWithText("Готово").assertIsNotEnabled()
        onNodeWithText("Я сохранил код").performClick() // по подписи, не по квадратику
        onNodeWithText("Готово").assertIsEnabled().performClick()
        assertEquals(1, done)
    }
}
