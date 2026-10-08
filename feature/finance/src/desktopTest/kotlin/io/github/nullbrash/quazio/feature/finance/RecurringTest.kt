package io.github.nullbrash.quazio.feature.finance

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.accounts.DeviceClock
import io.github.nullbrash.quazio.core.db.QuazioDatabase
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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

    private fun datesOf(start: String, rrule: String = "FREQ=MONTHLY", from: String = "2026-01-01", to: String = "2027-01-01") =
        rec.save(accountId, RecurringDraft(name = "П$start$rrule", kind = TxnKind.EXPENSE, amountMinor = 100, finAccountId = cash(),
            rrule = rrule, startDate = d(start))).let { id ->
            rec.occurrences(accountId, d(from), d(to), d(from)).filter { it.recurring.id == id }.map { it.date.toString() }
        }

    @Test
    fun shortMonthsCountDaysForward() {
        // Решение пользователя: не хватает дней — вперёд на столько, сколько не хватило.
        // Февраль (27, 28, 1): 29 → 1, 30 → 2, 31 → 3 марта; 30-дневный месяц: 31 → 1-е.
        assertEquals(listOf("2026-01-31", "2026-03-03", "2026-03-31", "2026-05-01", "2026-05-31"), datesOf("2026-01-31", to = "2026-06-01"))
        assertEquals(listOf("2026-01-30", "2026-03-02", "2026-03-30", "2026-04-30"), datesOf("2026-01-30", to = "2026-05-01"))
        assertEquals(listOf("2026-01-29", "2026-03-01", "2026-03-29"), datesOf("2026-01-29", to = "2026-04-01"))
        // Високосный год: 29 февраля есть.
        assertEquals(listOf("2028-01-31", "2028-03-02"), datesOf("2028-01-31", from = "2028-01-01", to = "2028-03-10"))
        // Ежегодный 29 февраля — 1 марта в невисокосные годы.
        assertEquals(listOf("2028-02-29", "2029-03-01", "2030-03-01", "2031-03-01", "2032-02-29"),
            datesOf("2028-02-29", "FREQ=YEARLY", from = "2028-01-01", to = "2033-01-01"))
        // Раз в 3 месяца с 31-го: май, август — 31-е; ноябрь (30 дней) — 1 декабря.
        assertEquals(listOf("2026-05-31", "2026-08-31", "2026-12-01"), datesOf("2026-05-31", "FREQ=MONTHLY;INTERVAL=3", from = "2026-05-01"))
    }

    @Test
    fun endDateAndPreview() {
        val id = rec.save(accountId, RecurringDraft(
            name = "Аренда", kind = TxnKind.EXPENSE, amountMinor = 1_000_000, finAccountId = cash(),
            rrule = "FREQ=MONTHLY", startDate = d("2026-01-15"), endDate = d("2026-04-30"),
        ))
        assertEquals(listOf(d("2026-01-15"), d("2026-02-15"), d("2026-03-15"), d("2026-04-15")),
            rec.occurrences(accountId, d("2026-01-01"), d("2027-01-01"), d("2026-01-01")).map { it.date })
        assertEquals(null, rec.next(rec.byId(id)!!, d("2026-05-01")))
        // Окно платежа показывает даты до сохранения.
        val draft = RecurringDraft(name = "Х", kind = TxnKind.EXPENSE, amountMinor = 1, finAccountId = cash(), rrule = "FREQ=MONTHLY", startDate = d("2026-01-31"))
        assertEquals(listOf(d("2026-01-31"), d("2026-03-03"), d("2026-03-31")), rec.preview(draft, 3, from = d("2026-01-01")))
        // У идущего платежа — с сегодняшнего дня, не с первого платежа.
        assertEquals(listOf(d("2026-05-31"), d("2026-07-01")), rec.preview(draft, 2, from = d("2026-05-10")))
    }

    @Test
    fun windowWaitsFromFirstDayAndGoesOverdue() {
        // «Деньги приходят в 20-х»: срок 20–25.
        val id = rec.save(accountId, RecurringDraft(name = "С аренды", kind = TxnKind.INCOME, amountMinor = 2_000_000, finAccountId = cash(),
            rrule = "FREQ=MONTHLY", startDate = d("2026-10-20"), windowDays = 5))
        assertTrue(rec.pending(accountId, d("2026-10-19")).isEmpty())
        val o = rec.pending(accountId, d("2026-10-22")).single()
        assertEquals(d("2026-10-25"), o.windowEnd)
        assertEquals(false, o.overdue(d("2026-10-22")))
        assertEquals(true, rec.pending(accountId, d("2026-10-27")).single().overdue(d("2026-10-27"))) // «срок прошёл»
        // У платежа на один день — без пометки, даже когда день прошёл.
        val single = rec.save(accountId, RecurringDraft(name = "Связь", kind = TxnKind.EXPENSE, amountMinor = 100, finAccountId = cash(),
            rrule = "FREQ=MONTHLY", startDate = d("2026-10-10")))
        val late = rec.pending(accountId, d("2026-10-27")).single { it.recurring.id == single }
        assertEquals(false, late.overdue(d("2026-10-27")))
        // В календаре — на все дни срока.
        assertEquals(1, rec.occurrences(accountId, d("2026-10-24"), d("2026-10-25"), d("2026-10-22")).size)
        // «Записать» — сегодняшней датой.
        val t = rec.defaultTxn(rec.byId(id)!!, d("2026-10-20"), tz, today = d("2026-10-23"))
        assertEquals(d("2026-10-23"), kotlin.time.Instant.fromEpochMilliseconds(t.occurredAt).toLocalDateTime(kotlinx.datetime.TimeZone.of(tz)).date)
        // Срок через конец месяца: 29–31 в феврале → 1–3 марта.
        val feb = rec.save(accountId, RecurringDraft(name = "Конец месяца", kind = TxnKind.INCOME, amountMinor = 100, finAccountId = cash(),
            rrule = "FREQ=MONTHLY", startDate = d("2027-01-29"), windowDays = 2))
        val march = rec.occurrences(accountId, d("2027-02-01"), d("2027-03-05"), d("2027-01-01")).single { it.recurring.id == feb }
        assertEquals(d("2027-03-01") to d("2027-03-03"), march.date to march.windowEnd)
    }

    @Test
    fun windowIsSuggestedFromActualDays() {
        val id = rec.save(accountId, RecurringDraft(name = "С аренды", kind = TxnKind.INCOME, amountMinor = 2_000_000, finAccountId = cash(),
            rrule = "FREQ=MONTHLY", startDate = d("2026-05-20"), windowDays = 5))
        fun arrived(month: Int, day: Int) {
            val r = rec.byId(id)!!
            val nominal = LocalDate(2026, month, 20)
            val at = kotlinx.datetime.LocalDateTime(LocalDate(2026, month, day), kotlinx.datetime.LocalTime(12, 0))
                .toInstant(kotlinx.datetime.TimeZone.of(tz)).toEpochMilliseconds()
            rec.record(accountId, id, nominal, rec.defaultTxn(r, nominal, tz).copy(occurredAt = at))
        }
        arrived(5, 21); arrived(6, 23)
        assertEquals(null, rec.suggestWindow(accountId, rec.byId(id)!!)) // мало данных
        arrived(7, 24); arrived(8, 23)
        assertEquals(WindowSuggestion(1, 4, 4), rec.suggestWindow(accountId, rec.byId(id)!!)) // «обычно 21–24»

        rec.dismissSuggestion(accountId, rec.byId(id)!!)
        assertEquals(null, rec.suggestWindow(accountId, rec.byId(id)!!)) // «Не сейчас» — до новых записей
        arrived(9, 27) // пришёл позже срока — предложит расширить
        val wider = rec.suggestWindow(accountId, rec.byId(id)!!)!!
        assertEquals(WindowSuggestion(1, 7, 5), wider)

        rec.applySuggestion(accountId, rec.byId(id)!!, wider)
        val r = rec.byId(id)!!
        assertEquals(d("2026-05-21") to 6, r.startDate to r.windowDays)
        assertTrue(r.activeFrom != null) // новый срок — с сегодняшнего дня, прошлое не «ждёт» заново
    }

    @Test
    fun invalidDraftsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            rec.save(accountId, RecurringDraft(name = "X", kind = TxnKind.EXPENSE, amountMinor = 0, finAccountId = cash(), rrule = "FREQ=MONTHLY", startDate = d("2026-10-01")))
        }
        assertFailsWith<IllegalArgumentException> {
            rec.save(accountId, RecurringDraft(name = "X", kind = TxnKind.EXPENSE, amountMinor = 100, finAccountId = cash(), rrule = "FREQ=HOURLY", startDate = d("2026-10-01")))
        }
        // С разбросом — только «спрашивать»: день прихода заранее неизвестен.
        assertFailsWith<IllegalArgumentException> {
            rec.save(accountId, RecurringDraft(name = "X", kind = TxnKind.EXPENSE, amountMinor = 100, finAccountId = cash(), rrule = "FREQ=MONTHLY",
                startDate = d("2026-10-01"), windowDays = 3, mode = RecurringMode.AUTO))
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
