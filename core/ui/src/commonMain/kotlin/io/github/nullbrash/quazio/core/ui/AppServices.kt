package io.github.nullbrash.quazio.core.ui

import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.lock.AppLock
import io.github.nullbrash.quazio.core.lock.PasswordVault
import io.github.nullbrash.quazio.feature.finance.FinanceService

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
)
