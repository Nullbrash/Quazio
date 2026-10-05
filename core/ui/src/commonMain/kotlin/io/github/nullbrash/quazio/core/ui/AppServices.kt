package io.github.nullbrash.quazio.core.ui

import io.github.nullbrash.quazio.core.accounts.AccountService

/**
 * Службы, которые оболочке даёт платформа. Создаются один раз на процесс и
 * только в основном процессе; открытие базы — дорогое, оболочка зовёт его в фоне.
 */
class AppServices(
    val accounts: AccountService,
    val deviceName: String,
    val platform: String,
)
