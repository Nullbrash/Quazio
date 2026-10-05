package io.github.nullbrash.quazio.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.db.DesktopDatabase
import io.github.nullbrash.quazio.core.db.DpapiKeyStore
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.QuazioApp
import java.nio.file.Path
import java.nio.file.Paths

/** Папка данных: `%LOCALAPPDATA%\Quazio`; при запуске из Gradle — своя (`quazio.dataDir`). */
private fun dataDir(): Path =
    System.getProperty("quazio.dataDir")?.let { Paths.get(it) }
        ?: Paths.get(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Quazio")

// Один раз на процесс; первое обращение — из фонового потока оболочки.
private val services: AppServices by lazy {
    val dir = dataDir()
    val db = DesktopDatabase.open(dir.resolve("quazio.db"), DpapiKeyStore(dir.resolve("db.key")))
    AppServices(
        accounts = AccountService(db, System::currentTimeMillis),
        deviceName = System.getenv("COMPUTERNAME") ?: "ПК",
        platform = "windows",
    )
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Quazio",
        state = rememberWindowState(width = 1000.dp, height = 700.dp),
    ) {
        // Версию передаёт сборка через -Dquazio.version; без неё — запуск мимо Gradle.
        QuazioApp(versionName = System.getProperty("quazio.version") ?: "dev", openServices = { services })
    }
}
