package io.github.nullbrash.quazio.feature.finance

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.accounts.DeviceClock
import io.github.nullbrash.quazio.core.db.QuazioDatabase
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Регулярные платежи на настоящей базе в памяти; суммы и названия выдуманные. */
class RecurringTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { QuazioDatabase.Schema.create(it) }
    private val db = QuazioDatabase(driver)
    private var wall = 1_790_000_000_000L
    private val clock = DeviceClock(db) { wall++ }
    private val accountId = AccountService(db, clock).initialize("ПК", "windows", "Я").id
    private val finance = FinanceService(db, clock).also { it.ensureDefaults(accountId) }
    private val rec = finance.recurring
    private val tz = "Europe/Moscow"
    private val telecom get() = "$accountId:c:online.telecom"

    @AfterTest
    fun close() = driver.close()

    private fun cash() = finance.accounts(accountId).first { it.type == FinAccountType.CASH }.id
    private fun d(s: String) = LocalDate.parse(s)
    private fun all() = finance.transactions(accountId, Long.MIN_VALUE, Long.MAX_VALUE)

    private fun internet(mode: RecurringMode = RecurringMode.ASK, start: String = "2026-09-15") = rec.save(accountId, RecurringDraft(
        name = "Интернет", kind = TxnKind.EXPENSE, amountMinor = 70_000, finAccountId = cash(), categoryId = telecom,
        rrule = "FREQ=MONTHLY", startDate = d(start), mode = mode,
    ))

    @Test
    fun askModeWaitsForConfirmationAndRecordsOnce() {
        val id = internet()
        val today = d("2026-10-20")
        assertEquals(listOf(d("2026-09-15"), d("2026-10-15")), rec.pending(accountId, today).map { it.date })

        // Подтвердили сентябрь с другой суммой, октябрь пропустили.
        val sep = rec.defaultTxn(rec.byId(id)!!, d("2026-09-15"), tz).copy(amountMinor = 72_500)
        rec.record(accountId, id, d("2026-09-15"), sep)
        rec.record(accountId, id, d("2026-09-15"), sep) // повторно — не задваивает
        rec.skip(accountId, id, d("2026-10-15"), tz)
        assertTrue(rec.pending(accountId, today).isEmpty())

        val t = all().single()
        assertEquals(72_500, t.amount.minor)
        assertEquals(telecom, t.categoryId)
        assertEquals("Интернет", t.description)
        assertEquals("$id|2026-09-15", t.id) // id от (платёж, дата): второе устройство не запишет снова
        assertEquals(-72_500, finance.accounts(accountId).first { it.id == cash() }.balance.minor)

        val states = rec.occurrences(accountId, d("2026-09-01"), d("2026-12-01"), today).associate { it.date to it.state }
        assertEquals(mapOf(
            d("2026-09-15") to OccurrenceState.RECORDED, d("2026-10-15") to OccurrenceState.SKIPPED, d("2026-11-15") to OccurrenceState.PLANNED,
        ), states)
    }

    @Test
    fun deletedRecordIsNotAskedAgain() {
        val id = internet()
        rec.record(accountId, id, d("2026-09-15"), rec.defaultTxn(rec.byId(id)!!, d("2026-09-15"), tz))
        finance.deleteTransaction("$id|2026-09-15")
        assertEquals(listOf(d("2026-10-15")), rec.pending(accountId, d("2026-10-20")).map { it.date })
    }

    @Test
    fun autoModeRecordsMissedDaysOnce() {
        internet(RecurringMode.AUTO, start = "2026-08-15")
        // Quazio не открывали с августа — при открытии записываются все наступившие дни.
        assertEquals(3, rec.recordDue(accountId, tz, d("2026-10-16")))
        assertEquals(0, rec.recordDue(accountId, tz, d("2026-10-16")))
        assertEquals(3, all().size)
        assertTrue(rec.pending(accountId, d("2026-10-16")).isEmpty()) // «сам» не спрашивает
    }

    @Test
    fun pauseHidesAndResumeDoesNotAskForThePause() {
        val id = internet()
        val r = rec.byId(id)!!
        val draft = RecurringDraft(id, r.name, r.kind, r.amountMinor, r.finAccountId, null, r.categoryId, r.rrule, r.startDate)
        rec.save(accountId, draft.copy(paused = true))
        assertTrue(rec.pending(accountId, d("2026-10-20")).isEmpty())
        assertTrue(rec.occurrences(accountId, d("2026-09-01"), d("2026-12-01"), d("2026-10-20")).isEmpty())
        // Сняли с паузы «сегодня» по часам устройства (21 сентября 2026) — сентябрьский платёж
        // пришёлся на паузу и не спрашивается, октябрьский — да.
        rec.save(accountId, draft.copy(paused = false))
        assertEquals(d("2026-09-21"), rec.byId(id)!!.activeFrom)
        assertEquals(listOf(d("2026-10-15")), rec.pending(accountId, d("2026-10-20")).map { it.date })
    }

    @Test
    fun lastDayOfMonthAndEndDate() {
        // «31-го» для платежей — последний день месяца (так пишет окно платежа).
        val id = rec.save(accountId, RecurringDraft(
            name = "Аренда", kind = TxnKind.EXPENSE, amountMinor = 1_000_000, finAccountId = cash(),
            rrule = "FREQ=MONTHLY;BYMONTHDAY=28,29,30,31;BYSETPOS=-1", startDate = d("2026-01-31"), endDate = d("2026-04-30"),
        ))
        val dates = rec.occurrences(accountId, d("2026-01-01"), d("2027-01-01"), d("2026-01-01")).map { it.date }
        assertEquals(listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30")), dates)
        assertEquals(null, rec.next(rec.byId(id)!!, d("2026-05-01")))
    }

    @Test
    fun shortMonthNextAndSkip() {
        // Выбор пользователя: 31-е в коротком месяце — 1-е следующего или пропуск.
        fun dates(policy: ShortMonth, rrule: String) = rec.save(accountId, RecurringDraft(
            name = "П$policy", kind = TxnKind.EXPENSE, amountMinor = 100, finAccountId = cash(),
            rrule = rrule, startDate = d("2026-01-31"), shortMonth = policy,
        )).let { id -> rec.occurrences(accountId, d("2026-01-01"), d("2026-05-15"), d("2026-01-01")).filter { it.recurring.id == id }.map { it.date } }
        assertEquals(listOf(d("2026-01-31"), d("2026-03-01"), d("2026-03-31"), d("2026-05-01")),
            dates(ShortMonth.NEXT, "FREQ=MONTHLY;BYMONTHDAY=28,29,30,31;BYSETPOS=-1"))
        assertEquals(listOf(d("2026-01-31"), d("2026-03-31")), dates(ShortMonth.SKIP, "FREQ=MONTHLY"))
    }

    @Test
    fun invalidDraftsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            rec.save(accountId, RecurringDraft(name = "X", kind = TxnKind.EXPENSE, amountMinor = 0, finAccountId = cash(), rrule = "FREQ=MONTHLY", startDate = d("2026-10-01")))
        }
        assertFailsWith<IllegalArgumentException> {
            rec.save(accountId, RecurringDraft(name = "X", kind = TxnKind.EXPENSE, amountMinor = 100, finAccountId = cash(), rrule = "FREQ=HOURLY", startDate = d("2026-10-01")))
        }
    }

    @Test
    fun categorySuggestionPrefersSubcategory() {
        // «Связь и интернет» → подкатегория «МТС»: название платежа выбирает её, а не родителя.
        val mts = finance.addCategory(accountId, telecom, "МТС", CategoryKind.EXPENSE)
        assertEquals(mts, finance.suggestCategory(accountId, "МТС", income = false, today = d("2026-10-08")))
        assertEquals(telecom, finance.suggestCategory(accountId, "Связь", income = false, today = d("2026-10-08")))
        // Доходной категории для расхода не подсказываем.
        assertEquals(null, finance.suggestCategory(accountId, "МТС", income = true, today = d("2026-10-08")))
        // Магазин с прошлой операцией — её категория.
        finance.saveTransaction(accountId, TransactionDraft(kind = TxnKind.EXPENSE, amountMinor = 100, finAccountId = cash(),
            categoryId = "$accountId:c:online.subscriptions", merchantName = "Кинотеатр Онлайн", occurredAt = wall, timeZone = tz))
        assertEquals("$accountId:c:online.subscriptions", finance.suggestCategory(accountId, "кинотеатр онлайн", income = false, today = d("2026-10-08")))
    }
}
