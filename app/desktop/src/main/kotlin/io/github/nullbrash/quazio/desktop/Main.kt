package io.github.nullbrash.quazio.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.nullbrash.quazio.core.ui.QuazioApp

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Quazio") {
        // Версию передаёт сборка через -Dquazio.version; без неё — запуск мимо Gradle.
        QuazioApp(versionName = System.getProperty("quazio.version") ?: "dev")
    }
}
