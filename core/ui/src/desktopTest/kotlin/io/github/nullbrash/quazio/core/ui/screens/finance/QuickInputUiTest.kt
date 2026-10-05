package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
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
import io.github.nullbrash.quazio.core.ui.IncomingText
import io.github.nullbrash.quazio.core.ui.LocalIncomingText
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.FinanceService
import io.github.nullbrash.quazio.feature.finance.TxnKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Текст «В учёт Quazio» (меню выделенного текста / «Поделиться» на Android) → черновик
 * или список черновиков → сохранение в настоящую базу (в памяти), без окна.
 */
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
    private val incoming = IncomingText()

    @AfterTest
    fun close() = driver.close()

    private fun all() = finance.transactions(accountId, Long.MIN_VALUE, Long.MAX_VALUE)

    @Test
    fun phraseOpensFilledDraftAndSavesIt() = runComposeUiTest {
        incoming.offer("такси 450")
        setContent { CompositionLocalProvider(LocalIncomingText provides incoming) { MaterialTheme { FinanceScreen(services) } } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Черновик")).fetchSemanticsNodes().isNotEmpty() }
        assertNull(incoming.text.value) // разобран один раз
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
        setContent { CompositionLocalProvider(LocalIncomingText provides incoming) { MaterialTheme { FinanceScreen(services) } } }
        // Текст пришёл, когда экран уже открыт (второе «В учёт Quazio»).
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Копилка", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        incoming.offer("Забрал:\n14.07: - 2 000р.\n18.07: - 500р.\n\n= - 2 500р.\n\nОстаток: 47 500р.")
        waitUntil("текст принят экраном", timeoutMillis = 10_000) { incoming.text.value == null }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Сохранить (2)")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("совпадает со строками выше", substring = true).assertExists()
        onNodeWithText("Совпадает с вашим остатком", substring = true).assertExists()
        onNodeWithText("Сохранить (2)").performClick()
        waitUntil(timeoutMillis = 10_000) { all().size == 2 }

        assertTrue(all().all { it.kind == TxnKind.TRANSFER && it.finAccountId == piggy })
        assertEquals(4_750_000, finance.accounts(accountId).first { it.id == piggy }.balance.minor)
    }

    @Test
    fun postWithoutSavingsAccountCreatesItAndFixesBalance() = runComposeUiTest {
        // Пост в стиле пользователя (суммы изменены); копилки в Quazio ещё нет.
        incoming.offer("Забрал 1 200р.\n- 1 200р.\n\nЗа 1-4-ое по 500р не брал\n+ 2 000р.\n\n= + 800р.\n\nОстаток: 61 000р.")
        setContent { CompositionLocalProvider(LocalIncomingText provides incoming) { MaterialTheme { FinanceScreen(services) } } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Нашлось в тексте", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("1 операция", substring = true).assertExists()
        onNodeWithText("вы пишете, что деньги не брали", substring = true).assertExists()
        onNodeWithText("совпадает со строками выше", substring = true).assertExists()
        onNodeWithText("на каком счёте", substring = true).assertExists()

        // «Откуда» не выбран — сохранить нельзя; выбираем, создав «Накопления» прямо из карточки.
        onAllNodes(hasText("выбрать счёт"))[0].performClick()
        onNodeWithText("+ Создать счёт «Накопления»").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Поправить баланс", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Поправить баланс", substring = true).performClick()
        onNodeWithText("Сохранить (1)").performClick()
        waitUntil(timeoutMillis = 10_000) { all().size == 2 } // перевод + поправка баланса

        val byName = finance.accounts(accountId).associateBy { it.name }
        assertEquals(6_100_000, byName.getValue("Накопления").balance.minor)
        assertEquals(120_000, byName.getValue("Наличные").balance.minor)
    }

    @Test
    fun textWithoutOperationsSaysSo() = runComposeUiTest {
        incoming.offer("Всем спасибо, всем пока!")
        setContent { CompositionLocalProvider(LocalIncomingText provides incoming) { MaterialTheme { FinanceScreen(services) } } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Операций не нашлось", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(all().isEmpty())
    }
}
