package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.accounts.DeviceClock
import io.github.nullbrash.quazio.core.db.QuazioDatabase
import io.github.nullbrash.quazio.core.lock.AppLock
import io.github.nullbrash.quazio.core.lock.PasswordVault
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.FinanceService
import kotlin.test.AfterTest
import kotlin.test.Test

/** «⋮ → Порядок счетов»: стрелки двигают счёт, порядок сразу сохраняется в базе. */
@OptIn(ExperimentalTestApi::class)
class AccountsOrderUiTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { QuazioDatabase.Schema.create(it) }
    private val db = QuazioDatabase(driver)
    private val clock = DeviceClock(db, System::currentTimeMillis)
    private val accounts = AccountService(db, clock)
    private val accountId = accounts.initialize("ПК", "windows", "Я").id
    private val finance = FinanceService(db, clock).also { it.ensureDefaults(accountId) }
    private val services = AppServices(
        accounts = accounts, finance = finance, vault = PasswordVault(db, System::currentTimeMillis),
        lock = AppLock(System::currentTimeMillis), deviceName = "ПК", platform = "windows", passwordRequired = false,
    )

    @AfterTest
    fun close() = driver.close()

    private fun names() = finance.accounts(accountId).map { it.name }

    @Test
    fun arrowsMoveAccountAndOrderIsSaved() = runComposeUiTest {
        finance.createAccount(accountId, "Карта", FinAccountType.CARD)
        finance.createAccount(accountId, "Копилка", FinAccountType.SAVINGS)
        setContent { MaterialTheme { FinanceScreen(services) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Копилка", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Ещё").performClick()
        onNodeWithText("Порядок счетов").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Архивные", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        // По имени: Карта, Копилка, Наличные → «Наличные» выше дважды.
        onAllNodesWithContentDescription("Выше")[2].performClick()
        onAllNodesWithContentDescription("Выше")[1].performClick()
        waitUntil(timeoutMillis = 10_000) { names() == listOf("Наличные", "Карта", "Копилка") }
    }
}
