package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
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
import kotlin.test.assertTrue

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
        // Счета — на своём экране: кнопка «Счета» → экран «Счета» (две карточки в ряд) → «Порядок счетов».
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Счета")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasText("Копилка")).fetchSemanticsNodes().isEmpty()) // на главном карточек нет
        onNodeWithText("Счета").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Копилка")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Порядок счетов").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Архивные", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        // По имени: Карта, Копилка, Наличные → «Наличные» выше дважды.
        onAllNodesWithContentDescription("Выше")[2].performClick()
        onAllNodesWithContentDescription("Выше")[1].performClick()
        waitUntil(timeoutMillis = 10_000) { names() == listOf("Наличные", "Карта", "Копилка") }
    }

    @Test
    fun accountIsDraggedByHandle() = runComposeUiTest {
        finance.createAccount(accountId, "Карта", FinAccountType.CARD)
        finance.createAccount(accountId, "Копилка", FinAccountType.SAVINGS)
        setContent { MaterialTheme { AccountsOrderScreen(services, accountId, finance.accounts(accountId)) {} } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Наличные")).fetchSemanticsNodes().isNotEmpty() }
        // Карта, Копилка, Наличные: тянем «Наличные» за ≡ на две строки вверх.
        val rowHeight = onNodeWithTag("drag-Карта").fetchSemanticsNode().size.height.toFloat()
        onNodeWithTag("drag-Наличные").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, -rowHeight * 2.4f / 20)) }
            up()
        }
        waitUntil(timeoutMillis = 10_000) { names() == listOf("Наличные", "Карта", "Копилка") }
    }

    @Test
    fun accountIsDraggedAfterLongPressOnRow() = runComposeUiTest {
        finance.createAccount(accountId, "Карта", FinAccountType.CARD)
        finance.createAccount(accountId, "Копилка", FinAccountType.SAVINGS)
        setContent { MaterialTheme { AccountsOrderScreen(services, accountId, finance.accounts(accountId)) {} } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Карта")).fetchSemanticsNodes().isNotEmpty() }
        val rowHeight = onNodeWithTag("drag-Карта").fetchSemanticsNode().size.height.toFloat()
        // Долгое нажатие на название «Карта», затем вниз на две строки — как пальцем на телефоне.
        onAllNodes(hasText("Карта"))[0].performTouchInput { // [1] — тип счёта «Карта»
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(20) { moveBy(Offset(0f, rowHeight * 2.4f / 20)) }
            up()
        }
        waitUntil(timeoutMillis = 10_000) { names() == listOf("Копилка", "Наличные", "Карта") }
    }
}
