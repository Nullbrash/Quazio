package io.github.nullbrash.quazio.core.ui

import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.lock.AppLock
import io.github.nullbrash.quazio.core.lock.PasswordVault
import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.CalendarSource
import io.github.nullbrash.quazio.feature.calendar.WidgetPrefs
import io.github.nullbrash.quazio.feature.finance.FinanceService
import io.github.nullbrash.quazio.feature.reminders.ReminderService

/**
 * Службы, которые оболочке даёт платформа. Создаются один раз на процесс и
 * только в основном процессе; открытие базы — дорогое, оболочка зовёт его в фоне.
 */
class AppServices(
    val accounts: AccountService,
    val finance: FinanceService,
    val vault: PasswordVault,
    val lock: AppLock,
    val deviceName: String,
    val platform: String,
    /** Без блокировки экрана пароль обязателен (телефон) или нет (ПК — решение пользователя). */
    val passwordRequired: Boolean,
    /** Календари: Android — устройства, ПК — Google по ссылкам iCal (`LinkedCalendars`: свои настройки, сеть). */
    val calendar: CalendarSource? = null,
    val calendarPrefs: CalendarPrefs? = null,
    /** Напоминания: платежи, сводка дня, «до конца события» (показывает платформа). */
    val reminders: ReminderService? = null,
    /** Настройки виджета-циферблата (Android); null — виджета на этой платформе нет. */
    val widgetPrefs: WidgetPrefs? = null,
)
