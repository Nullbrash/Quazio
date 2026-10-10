package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
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
import io.github.nullbrash.quazio.core.ui.LocalReminderPlatform
import io.github.nullbrash.quazio.core.ui.ReminderPlatform
import io.github.nullbrash.quazio.core.ui.appStateStore
import io.github.nullbrash.quazio.feature.finance.FinanceService
import io.github.nullbrash.quazio.feature.reminders.ProfileStore
import io.github.nullbrash.quazio.feature.reminders.ReminderKind
import io.github.nullbrash.quazio.feature.reminders.ReminderService
import io.github.nullbrash.quazio.feature.reminders.ReminderSettings
import io.github.nullbrash.quazio.core.ui.reminderText
import io.github.nullbrash.quazio.feature.reminders.Reminder
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** «Настройки → Напоминания»: значения по умолчанию, включение сводки, свой профиль. */
@OptIn(ExperimentalTestApi::class)
class RemindersUiTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { QuazioDatabase.Schema.create(it) }
    private val db = QuazioDatabase(driver)
    private val clock = DeviceClock(db, System::currentTimeMillis)
    private val accounts = AccountService(db, clock)
    private val accountId = accounts.initialize("ПК", "windows", "Я").id
    private val finance = FinanceService(db, clock).also { it.ensureDefaults(accountId) }
    private val state = db.appStateStore()
    private val reminders = ReminderService(ProfileStore(db), ReminderSettings(state), state, finance.recurring, { accountId }, null, null)
    private val services = AppServices(
        accounts = accounts, finance = finance, vault = PasswordVault(db, System::currentTimeMillis),
        lock = AppLock(System::currentTimeMillis), deviceName = "ПК", platform = "windows", passwordRequired = false,
        reminders = reminders,
    )
    private var reschedules = 0
    private val platform = object : ReminderPlatform {
        override val phone = false
        override fun reschedule() { reschedules++ }
    }

    @AfterTest
    fun close() = driver.close()

    @Test
    fun summaryIsOffByDefaultAndCanBeTurnedOn() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalReminderPlatform provides platform) { MaterialTheme { RemindersSection(services) } } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Утренняя сводка дня")).fetchSemanticsNodes().isNotEmpty() }
        // Платежи включены — видно время 10:00; сводка выключена — её времени не видно.
        onNodeWithText("10:00").assertExists()
        assertTrue(onAllNodesWithText("08:00").fetchSemanticsNodes().isEmpty())
        onNodeWithText("Уведомления Windows").assertExists() // на ПК — вместо постоянного уведомления

        // Переключатель сводки — третий (после платежей и «показывать сумму»).
        onAllNodes(isToggleable())[2].performClick()
        waitUntil(timeoutMillis = 10_000) { reminders.settings.summaryEnabled }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("08:00")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(reschedules > 0)
    }

    @Test
    fun ownAlarmProfileIsAdded() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalReminderPlatform provides platform) { MaterialTheme { RemindersSection(services) } } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Будильник")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Новый профиль").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Название профиля")).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasSetTextAction()).performTextInput("Громкий")
        onAllNodesWithText("Будильник на весь экран").onLast().performClick() // в окне профиля (позади — список)
        onNodeWithText("Готово").performClick()
        waitUntil(timeoutMillis = 10_000) { reminders.profiles.all().size == 3 }
        val p = reminders.profiles.all().last()
        assertEquals("Громкий" to ReminderKind.ALARM, p.name to p.kind)
        assertEquals(30, p.rampSeconds) // будильник — с нарастанием по умолчанию
    }

    @Test
    fun backgroundRestrictionIsShownWithSettingsButton() = runComposeUiTest {
        var opened = 0
        val restricted = object : ReminderPlatform {
            override val phone = true
            override fun reschedule() {}
            override fun backgroundRestricted() = true
            override fun openBackgroundSettings() { opened++ }
        }
        setContent { CompositionLocalProvider(LocalReminderPlatform provides restricted) { MaterialTheme { RemindersSection(services) } } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Открыть настройки")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Открыть настройки").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun noWarningWithoutRestriction() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalReminderPlatform provides platform) { MaterialTheme { RemindersSection(services) } } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Утренняя сводка дня")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodesWithText("Открыть настройки").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun paymentTextShowsAmountUnlessHidden() = kotlinx.coroutines.runBlocking {
        val r = Reminder.Payment("k", 0, "notification", "rec", LocalDate.parse("2026-10-15"), "Интернет", true, 1, 70_000)
        assertEquals("Завтра платёж: Интернет, 700 ₽", reminderText(r, reminders).first)
        reminders.settings.paymentsShowAmount = false
        assertEquals("Завтра платёж: Интернет", reminderText(r, reminders).first)
    }
}
