package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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
import io.github.nullbrash.quazio.feature.finance.FinanceService
import io.github.nullbrash.quazio.feature.finance.RecurringDraft
import io.github.nullbrash.quazio.feature.finance.RecurringMode
import io.github.nullbrash.quazio.feature.finance.TransactionDraft
import io.github.nullbrash.quazio.feature.finance.TxnKind
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Регулярные платежи в финансах: создание с подкатегорией, «ждут подтверждения», «сделать регулярной». */
@OptIn(ExperimentalTestApi::class)
class RecurringUiTest {

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
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val telecom get() = "$accountId:c:online.telecom"

    @AfterTest
    fun close() = driver.close()

    private fun cash() = finance.accounts(accountId).first().id
    private fun all() = finance.transactions(accountId, Long.MIN_VALUE, Long.MAX_VALUE)

    @Test
    fun createPaymentWithNewSubcategory() = runComposeUiTest {
        setContent { MaterialTheme { FinanceScreen(services) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Счета")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Ещё").performClick()
        onNodeWithText("Регулярные платежи").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Регулярный платёж")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Регулярный платёж").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Новый регулярный платёж")).fetchSemanticsNodes().isNotEmpty() }

        onAllNodes(hasSetTextAction())[0].performTextInput("МТС")
        // Есть «Связь и интернет», подкатегории «МТС» нет — кнопка создаёт её одним нажатием.
        onNodeWithText("Без категории").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Связь и интернет")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Связь и интернет").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Создать подкатегорию «МТС» в «Связь и интернет»")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Создать подкатегорию «МТС» в «Связь и интернет»").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Создать подкатегорию", substring = true)).fetchSemanticsNodes().isEmpty() }

        onNodeWithText("0 ₽").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Введите сумму")).fetchSemanticsNodes().isNotEmpty() }
        listOf("6", "5", "0").forEach { onNodeWithText(it).performClick() }
        onNodeWithContentDescription("Готово").performClick()
        onNodeWithText("Сохранить").performClick()
        waitUntil(timeoutMillis = 10_000) { finance.recurring.all(accountId).isNotEmpty() }

        val r = finance.recurring.all(accountId).single()
        assertEquals("МТС", r.name)
        assertEquals(65_000, r.amountMinor)
        assertEquals(RecurringMode.ASK, r.mode) // по умолчанию — спрашивать
        val sub = finance.categories(accountId).single { it.id == r.categoryId }
        assertEquals("МТС", sub.name)
        assertEquals(telecom, sub.parentId)
    }

    @Test
    fun pendingPaymentIsRecordedFromMainScreen() = runComposeUiTest {
        val id = finance.recurring.save(accountId, RecurringDraft(
            name = "Интернет", kind = TxnKind.EXPENSE, amountMinor = 70_000, finAccountId = cash(), categoryId = telecom,
            rrule = "FREQ=MONTHLY", startDate = today.minus(DatePeriod(days = 1)),
        ))
        setContent { MaterialTheme { FinanceScreen(services) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Ждут подтверждения")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Записать").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Сохранить")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Сохранить").performClick()
        waitUntil(timeoutMillis = 10_000) { all().isNotEmpty() }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Ждут подтверждения")).fetchSemanticsNodes().isEmpty() }

        val t = all().single()
        assertEquals("$id|${today.minus(DatePeriod(days = 1))}", t.id)
        assertEquals(70_000, t.amount.minor)
    }

    @Test
    fun autoPaymentIsRecordedOnOpen() = runComposeUiTest {
        finance.recurring.save(accountId, RecurringDraft(
            name = "Хостинг", kind = TxnKind.EXPENSE, amountMinor = 30_000, finAccountId = cash(),
            rrule = "FREQ=MONTHLY", startDate = today, mode = RecurringMode.AUTO,
        ))
        setContent { MaterialTheme { FinanceScreen(services) } }
        waitUntil(timeoutMillis = 10_000) { all().isNotEmpty() }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Хостинг", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasText("Ждут подтверждения")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun makeRecurringFromOperation() = runComposeUiTest {
        val at = System.currentTimeMillis()
        finance.saveTransaction(accountId, TransactionDraft(kind = TxnKind.EXPENSE, amountMinor = 39_900, finAccountId = cash(),
            categoryId = "$accountId:c:online.subscriptions", occurredAt = at, timeZone = TimeZone.currentSystemDefault().id, description = "Музыка"))
        setContent { MaterialTheme { FinanceScreen(services) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Музыка", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Музыка", substring = true).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Сделать регулярной")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Сделать регулярной").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Новый регулярный платёж")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Сохранить").performClick()
        waitUntil(timeoutMillis = 10_000) { finance.recurring.all(accountId).isNotEmpty() }

        val r = finance.recurring.all(accountId).single()
        assertEquals("Музыка", r.name)
        assertEquals(39_900, r.amountMinor)
        assertEquals("$accountId:c:online.subscriptions", r.categoryId)
        assertEquals(today.plus(DatePeriod(months = 1)), r.startDate) // первый — через месяц после операции
    }
}
