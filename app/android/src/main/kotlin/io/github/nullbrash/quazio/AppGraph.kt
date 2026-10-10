package io.github.nullbrash.quazio

import android.content.Context
import android.os.Build
import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.accounts.DeviceClock
import io.github.nullbrash.quazio.feature.finance.FinanceService
import io.github.nullbrash.quazio.core.db.AndroidDatabase
import io.github.nullbrash.quazio.core.db.AndroidKeystoreKeyStore
import io.github.nullbrash.quazio.core.lock.AppLock
import io.github.nullbrash.quazio.core.lock.PasswordVault
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.appStateStore
import io.github.nullbrash.quazio.feature.calendar.AndroidCalendarSource
import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.WidgetPrefs
import io.github.nullbrash.quazio.feature.reminders.ProfileStore
import io.github.nullbrash.quazio.feature.reminders.ReminderService
import io.github.nullbrash.quazio.feature.reminders.ReminderSettings

/**
 * Службы основного процесса — лениво, при первом обращении (не в Application:
 * процесс поднимается и ради фоновых частей, запуск должен оставаться лёгким).
 * Процесс клавиатуры `:ime` сюда не обращается — основная база в нём не открывается.
 */
object AppGraph {

    @Volatile private var services: AppServices? = null

    fun services(context: Context): AppServices =
        services ?: synchronized(this) { services ?: create(context.applicationContext).also { services = it } }

    private fun create(context: Context): AppServices {
        val db = AndroidDatabase.open(context, AndroidKeystoreKeyStore(context))
        val clock = DeviceClock(db, System::currentTimeMillis)
        val state = db.appStateStore()
        val accounts = AccountService(db, clock)
        val finance = FinanceService(db, clock)
        val calendar = AndroidCalendarSource(context)
        val calendarPrefs = CalendarPrefs(state)
        return AppServices(
            accounts = accounts,
            finance = finance,
            vault = PasswordVault(db, System::currentTimeMillis),
            lock = AppLock(System::currentTimeMillis),
            deviceName = Build.MODEL,
            platform = "android",
            // Телефон без блокировки экрана: финансы закрыты, пока не задан пароль Quazio.
            passwordRequired = true,
            calendar = calendar,
            calendarPrefs = calendarPrefs,
            // Напоминания читают календари в фоне — только если доступ дан.
            widgetPrefs = WidgetPrefs(state),
            reminders = ReminderService(ProfileStore(db), ReminderSettings(state), state, finance.recurring,
                { accounts.currentOrNull()?.id }, calendar, calendarPrefs, calendarReadable = { AndroidCalendarSource.hasPermission(context) }),
        )
    }
}
