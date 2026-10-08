package io.github.nullbrash.quazio.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.accounts.DeviceClock
import io.github.nullbrash.quazio.feature.finance.FinanceService
import io.github.nullbrash.quazio.core.db.DesktopDatabase
import io.github.nullbrash.quazio.core.db.DpapiKeyStore
import io.github.nullbrash.quazio.core.lock.AppLock
import io.github.nullbrash.quazio.core.lock.PasswordVault
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.QuazioApp
import io.github.nullbrash.quazio.core.ui.appStateStore
import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.ical.LinkedCalendars
import java.nio.file.Path
import java.nio.file.Paths

/** Папка данных: `%LOCALAPPDATA%\Quazio`; при запуске из Gradle — своя (`quazio.dataDir`). */
private fun dataDir(): Path =
    System.getProperty("quazio.dataDir")?.let { Paths.get(it) }
        ?: Paths.get(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Quazio")

// Версию передаёт сборка через -Dquazio.version; без неё — запуск мимо Gradle.
private val appVersion: String = System.getProperty("quazio.version") ?: "dev"

// Один раз на процесс; первое обращение — из фонового потока оболочки.
private val services: AppServices by lazy {
    val dir = dataDir()
    val db = DesktopDatabase.open(dir.resolve("quazio.db"), DpapiKeyStore(dir.resolve("db.key")))
    val lock = AppLock(System::currentTimeMillis)
    val clock = DeviceClock(db, System::currentTimeMillis)
    SessionLockWatcher.start(onLocked = lock::lockNow)
    val state = db.appStateStore()
    val services = AppServices(
        accounts = AccountService(db, clock),
        finance = FinanceService(db, clock),
        vault = PasswordVault(db, System::currentTimeMillis),
        lock = lock,
        deviceName = System.getenv("COMPUTERNAME") ?: "ПК",
        platform = "windows",
        // На ПК пароль Quazio необязателен (решение пользователя): база и так под ключом Windows.
        passwordRequired = false,
        // Календари Google по секретным ссылкам — единственное, ради чего ПК-версия выходит в сеть.
        calendar = LinkedCalendars(state, IcalHttpFetcher(appVersion), System::currentTimeMillis),
        calendarPrefs = CalendarPrefs(state),
    )
    DevStress.seedIfRequested(services)
    services
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Quazio",
        state = rememberWindowState(width = 1000.dp, height = 700.dp),
    ) {
        val fileSaver = remember(window) { DesktopFileSaver(window) }
        LaunchedEffect(window) { DevStress.scrollIfRequested(window) }
        QuazioApp(
            versionName = appVersion,
            openServices = { services },
            fileSaver = fileSaver,
        )
    }
}
