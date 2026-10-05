package io.github.nullbrash.quazio.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.nullbrash.quazio.core.ui.QuazioApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Quazio",
        state = rememberWindowState(width = 1000.dp, height = 700.dp),
    ) {
        // Версию передаёт сборка через -Dquazio.version; без неё — запуск мимо Gradle.
        QuazioApp(versionName = System.getProperty("quazio.version") ?: "dev")
    }
}
