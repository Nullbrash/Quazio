package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    /**
     * «Сохранить»: если даты сдвигаются (первый платёж 29–31-го — тесты идут по сегодняшней дате),
     * окно сначала спрашивает «Внести так?».
     */
    private fun androidx.compose.ui.test.ComposeUiTest.saveConfirmingShift() {
        onNodeWithText("Сохранить").performClick()
        waitUntil(timeoutMillis = 10_000) { finance.recurring.all(accountId).isNotEmpty() || onAllNodes(hasText("Внести так")).fetchSemanticsNodes().isNotEmpty() }
        if (finance.recurring.all(accountId).isEmpty()) onNodeWithText("Внести так").performClick()
        waitUntil(timeoutMillis = 10_000) { finance.recurring.all(accountId).isNotEmpty() }
    }

    private fun data(): FinanceData {
        val accs = finance.accounts(accountId)
        return FinanceData(accountId, accs, finance.total(accs), finance.totals(accountId, 0, 0), emptyList(),
            finance.categories(accountId), finance.tags(accountId), calculatorOnNew = false)
    }

    private fun operation(y: Int, m: Int, d: Int, name: String) = TimeZone.currentSystemDefault().let { zone ->
        TransactionDraft(kind = TxnKind.INCOME, amountMinor = 2_000_000, finAccountId = cash(),
            occurredAt = toMillis(kotlinx.datetime.LocalDateTime(y, m, d, 12, 0), zone), timeZone = zone.id, description = name)
    }

    @Test
    fun shiftedDatesAreShownAndConfirmed() = runComposeUiTest {
        // «Сделать регулярной» с операции 31 декабря: первый — 31 января, в феврале 31-го нет → 3 марта.
        // Даты — в будущем от сегодняшнего: окно показывает ближайшие платежи с сегодняшнего дня.
        var closed = false
        setContent { MaterialTheme { RecurringEditor(services, data(), null, operation(2030, 12, 31, "Аренда")) { closed = true } } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Ближайшие платежи", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("31 января, 3 марта, 31 марта", substring = true).assertExists()

        onNodeWithText("Сохранить").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Платежи лягут так")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Нет, изменить").performClick() // назад в окно — ничего не сохранено
        assertTrue(finance.recurring.all(accountId).isEmpty())

        onNodeWithText("Сохранить").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Внести так")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Внести так").performClick()
        waitUntil(timeoutMillis = 10_000) { closed }
        val dates = finance.recurring.occurrences(accountId, kotlinx.datetime.LocalDate(2031, 1, 1), kotlinx.datetime.LocalDate(2031, 4, 2),
            kotlinx.datetime.LocalDate(2031, 1, 1)).map { it.date.toString() }
        assertEquals(listOf("2031-01-31", "2031-03-03", "2031-03-31"), dates)
    }

    @Test
    fun windowTermDisablesAutoAndIsSaved() = runComposeUiTest {
        var closed = false
        setContent { MaterialTheme { RecurringEditor(services, data(), null, operation(2030, 12, 20, "С аренды")) { closed = true } } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("только в этот день")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("только в этот день").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("с 20 по 25")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("с 20 по 25").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("С разбросом срока", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("20–25 января", substring = true).assertExists()
        onNodeWithText("Сохранить").performClick()
        waitUntil(timeoutMillis = 10_000) { closed }
        val r = finance.recurring.all(accountId).single()
        assertEquals(5, r.windowDays)
        assertEquals(RecurringMode.ASK, r.mode)
    }

    @Test
    fun windowPaymentWaitsWithItsTerm() = runComposeUiTest {
        finance.recurring.save(accountId, RecurringDraft(name = "С аренды", kind = TxnKind.INCOME, amountMinor = 2_000_000, finAccountId = cash(),
            rrule = "FREQ=MONTHLY", startDate = today.minus(DatePeriod(days = 1)), windowDays = 5))
        val until = today.minus(DatePeriod(days = 1)).plus(DatePeriod(days = 5)).day
        setContent { MaterialTheme { FinanceScreen(services) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("ожидается до $until-го", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun termSuggestionIsOfferedAndApplied() = runComposeUiTest {
        val id = finance.recurring.save(accountId, RecurringDraft(name = "С аренды", kind = TxnKind.INCOME, amountMinor = 2_000_000, finAccountId = cash(),
            rrule = "FREQ=MONTHLY", startDate = kotlinx.datetime.LocalDate(2026, 5, 20), windowDays = 5))
        val zone = TimeZone.currentSystemDefault()
        listOf(5 to 21, 6 to 23, 7 to 24, 8 to 23).forEach { (m, day) ->
            val nominal = kotlinx.datetime.LocalDate(2026, m, 20)
            finance.recurring.record(accountId, id, nominal, finance.recurring.defaultTxn(finance.recurring.byId(id)!!, nominal, zone.id)
                .copy(occurredAt = toMillis(kotlinx.datetime.LocalDateTime(2026, m, day, 12, 0), zone)))
        }
        setContent { MaterialTheme { RecurringEditor(services, data(), id, null) {} } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Обычно приходит 21–24-го (по последним 4)", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Изменить срок").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { finance.recurring.byId(id)!!.windowDays == 3 }
        assertEquals(kotlinx.datetime.LocalDate(2026, 5, 21), finance.recurring.byId(id)!!.startDate)
    }

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
        saveConfirmingShift()

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
        saveConfirmingShift()

        val r = finance.recurring.all(accountId).single()
        assertEquals("Музыка", r.name)
        assertEquals(39_900, r.amountMinor)
        assertEquals("$accountId:c:online.subscriptions", r.categoryId)
        assertEquals(today.plus(DatePeriod(months = 1)), r.startDate) // первый — через месяц после операции
    }
}
