package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import io.github.nullbrash.quazio.feature.finance.TxnKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Быстрый ввод целиком: поле → черновик / список черновиков → сохранение в настоящую базу (в памяти). */
@OptIn(ExperimentalTestApi::class)
class QuickInputUiTest {

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

    private fun all() = finance.transactions(accountId, Long.MIN_VALUE, Long.MAX_VALUE)

    @Test
    fun phraseOpensFilledDraftAndSavesIt() = runComposeUiTest {
        setContent { MaterialTheme { FinanceScreen(services) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasSetTextAction()).performTextInput("такси 450")
        onNodeWithContentDescription("Разобрать").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(androidx.compose.ui.test.hasText("Черновик")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Сохранить").performClick()
        waitUntil(timeoutMillis = 10_000) { all().isNotEmpty() }

        val t = all().single()
        assertEquals(TxnKind.EXPENSE, t.kind)
        assertEquals(45_000, t.amount.minor)
        assertEquals("$accountId:c:transport.taxi", t.categoryId)
        assertEquals("quickinput", db.financeQueries.txnById(t.id).executeAsOne().source)
    }

    @Test
    fun postShowsDraftListAndSavesAll() = runComposeUiTest {
        val piggy = finance.createAccount(accountId, "Копилка", FinAccountType.SAVINGS, openingBalanceMinor = 5_000_000)
        setContent { MaterialTheme { FinanceScreen(services) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasSetTextAction()).performTextInput("Забрал:\n14.07: - 2 000р.\n18.07: - 500р.\n\n= - 2 500р.\n\nОстаток: 47 500р.")
        onNodeWithContentDescription("Разобрать").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(androidx.compose.ui.test.hasText("Сохранить (2)")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("— сходится", substring = true).assertExists() // в суммах неразрывные пробелы
        onNodeWithText("Сходится").assertExists()
        onNodeWithText("Сохранить (2)").performClick()
        waitUntil(timeoutMillis = 10_000) { all().size == 2 }

        assertTrue(all().all { it.kind == TxnKind.TRANSFER && it.finAccountId == piggy })
        assertEquals(4_750_000, finance.accounts(accountId).first { it.id == piggy }.balance.minor)
    }
}
