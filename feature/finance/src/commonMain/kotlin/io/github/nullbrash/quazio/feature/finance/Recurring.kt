package io.github.nullbrash.quazio.feature.finance

import io.github.nullbrash.quazio.core.accounts.DeviceClock
import io.github.nullbrash.quazio.core.db.ChangeLog
import io.github.nullbrash.quazio.core.db.QuazioDatabase
import io.github.nullbrash.quazio.core.model.Uuid7
import io.github.nullbrash.quazio.engine.recurrence.RRule
import io.github.nullbrash.quazio.engine.recurrence.Recurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** «Спрашивать» в день платежа или «записывать самому» (решение пользователя: своё у каждого платежа). */
enum class RecurringMode(val dbValue: String) {
    ASK("ask"),
    AUTO("auto");

    companion object {
        fun fromDb(value: String): RecurringMode = entries.first { it.dbValue == value }
    }
}

data class Recurring(
    val id: String,
    val name: String,
    /** Расход, доход или перевод. */
    val kind: TxnKind,
    val amountMinor: Long,
    val finAccountId: String,
    val toFinAccountId: String?,
    val categoryId: String?,
    /** RFC 5545 без «RRULE:»; первое повторение — [startDate]. */
    val rrule: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val mode: RecurringMode,
    /** За сколько дней напомнить (напоминания — следующая фаза плана). */
    val remindDays: Int,
    val paused: Boolean,
    /** После паузы: раньше этой даты повторения не спрашиваются. */
    val activeFrom: LocalDate?,
)

/** Что сохранить; [id] = null — новый платёж. */
data class RecurringDraft(
    val id: String? = null,
    val name: String,
    val kind: TxnKind,
    val amountMinor: Long,
    val finAccountId: String,
    val toFinAccountId: String? = null,
    val categoryId: String? = null,
    val rrule: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val mode: RecurringMode = RecurringMode.ASK,
    val remindDays: Int = 1,
    val paused: Boolean = false,
)

enum class OccurrenceState {
    /** Операция записана. */
    RECORDED,
    /** Пропущено или записанное потом удалили — больше не спрашивается. */
    SKIPPED,
    /** День наступил, «спрашивать» — ждёт подтверждения. */
    PENDING,
    /** Ещё впереди. */
    PLANNED,
}

data class Occurrence(val recurring: Recurring, val date: LocalDate, val state: OccurrenceState)

/**
 * Регулярные платежи: правило в финансах, операция — в день платежа (сама или после
 * подтверждения). Id операции — «<платёж>|<дата>»: одно повторение не запишется дважды ни
 * на одном устройстве, ни на двух. Методы блокирующие — не из главного потока.
 */
class RecurringService internal constructor(private val db: QuazioDatabase, private val clock: DeviceClock) {

    private val q get() = db.recurringQueries
    private val changeLog = ChangeLog(db)

    fun all(accountId: String): List<Recurring> = q.recurringOf(accountId).executeAsList().map { it.toModel() }

    fun byId(id: String): Recurring? = q.recurringById(id).executeAsOneOrNull()?.takeIf { it.deleted == 0L }?.toModel()

    fun save(accountId: String, draft: RecurringDraft): String = db.transactionWithResult {
        validate(accountId, draft)
        val name = draft.name.trim().replace(Regex("\\s+"), " ")
        val category = if (draft.kind == TxnKind.TRANSFER) null else draft.categoryId
        val toAccount = if (draft.kind == TxnKind.TRANSFER) draft.toFinAccountId else null
        val hlc = clock.now()
        val old = draft.id?.let { q.recurringById(it).executeAsOneOrNull() }
        // Снятие с паузы: пропущенное за паузу не спрашивается задним числом.
        val activeFrom = when {
            old == null -> null
            old.paused != 0L && !draft.paused -> today().toString()
            else -> old.active_from
        }
        val fields = mapOf(
            "name" to name, "kind" to draft.kind.dbValue, "amount_minor" to draft.amountMinor.toString(),
            "fin_account_id" to draft.finAccountId, "to_fin_account_id" to toAccount, "category_id" to category,
            "rrule" to draft.rrule, "start_date" to draft.startDate.toString(), "end_date" to draft.endDate?.toString(),
            "mode" to draft.mode.dbValue, "remind_days" to draft.remindDays.toString(), "paused" to flag(draft.paused),
            "active_from" to activeFrom,
        )
        if (old == null) {
            val id = Uuid7.generate(clock.wallMillis())
            q.insertRecurring(id, accountId, name, draft.kind.dbValue, draft.amountMinor, draft.finAccountId, toAccount, category,
                draft.rrule, draft.startDate.toString(), draft.endDate?.toString(), draft.mode.dbValue, draft.remindDays.toLong(),
                if (draft.paused) 1 else 0, null, hlc.toString())
            changeLog.record(accountId, T_RECURRING, id, hlc, fields + ("currency" to "RUB"))
            id
        } else {
            val before = mapOf(
                "name" to old.name, "kind" to old.kind, "amount_minor" to old.amount_minor.toString(),
                "fin_account_id" to old.fin_account_id, "to_fin_account_id" to old.to_fin_account_id, "category_id" to old.category_id,
                "rrule" to old.rrule, "start_date" to old.start_date, "end_date" to old.end_date, "mode" to old.mode,
                "remind_days" to old.remind_days.toString(), "paused" to old.paused.toString(), "active_from" to old.active_from,
            )
            val changed = fields.filter { (k, v) -> before[k] != v }
            if (changed.isNotEmpty()) {
                q.updateRecurring(name, draft.kind.dbValue, draft.amountMinor, draft.finAccountId, toAccount, category, draft.rrule,
                    draft.startDate.toString(), draft.endDate?.toString(), draft.mode.dbValue, draft.remindDays.toLong(),
                    if (draft.paused) 1 else 0, activeFrom, hlc.toString(), old.id)
                changeLog.record(accountId, T_RECURRING, old.id, hlc, changed)
            }
            old.id
        }
    }

    /** Удалить платёж; уже записанные операции остаются в истории. */
    fun delete(id: String) = db.transaction {
        val old = requireNotNull(q.recurringById(id).executeAsOneOrNull()) { "Нет платежа $id" }
        val hlc = clock.now()
        q.setRecurringDeleted(hlc.toString(), id)
        changeLog.record(old.account_id, T_RECURRING, id, hlc, mapOf("deleted" to "1"))
    }

    /** Повторения в [from, toExclusive) по всем действующим платежам — для календаря и списков. */
    fun occurrences(accountId: String, from: LocalDate, toExclusive: LocalDate, today: LocalDate = today()): List<Occurrence> {
        val done = doneIds(accountId)
        return all(accountId).filter { !it.paused }.flatMap { r ->
            dates(r).dropWhile { it < from }.takeWhile { it < toExclusive }.mapNotNull { d -> occurrence(r, d, done, today) }.toList()
        }.sortedWith(compareBy({ it.date }, { it.recurring.name }))
    }

    /** Наступившие повторения «спрашивать», которые ещё не записаны и не пропущены. */
    fun pending(accountId: String, today: LocalDate = today()): List<Occurrence> {
        val done = doneIds(accountId)
        return all(accountId).filter { !it.paused && it.mode == RecurringMode.ASK }.flatMap { r ->
            dates(r).takeWhile { it <= today }.mapNotNull { d -> occurrence(r, d, done, today) }.filter { it.state == OccurrenceState.PENDING }.toList()
        }.sortedWith(compareBy({ it.date }, { it.recurring.name }))
    }

    /** Ближайшее повторение не раньше [today]; null — платёж закончился. */
    fun next(r: Recurring, today: LocalDate = today()): LocalDate? = dates(r).firstOrNull { it >= today }

    /** «Записывать сам»: операции за наступившие дни (и пропущенные, пока Quazio был закрыт). Сколько записано. */
    fun recordDue(accountId: String, timeZone: String, today: LocalDate = today()): Int = db.transactionWithResult {
        val done = doneIds(accountId)
        var count = 0
        all(accountId).filter { !it.paused && it.mode == RecurringMode.AUTO }.forEach { r ->
            dates(r).takeWhile { it <= today }.filter { activeOn(r, it) && occurrenceId(r.id, it) !in done }.forEach { d ->
                insertOccurrence(accountId, r, d, r.toTxn(d, timeZone), deleted = false)
                count++
            }
        }
        count
    }

    /** Записать повторение; [txn] — поля из окна подтверждения (сумму и прочее можно поправить). */
    fun record(accountId: String, recurringId: String, date: LocalDate, txn: TransactionDraft) = db.transaction {
        val r = requireNotNull(byId(recurringId)) { "Нет платежа $recurringId" }
        if (occurrenceId(r.id, date) in doneIds(accountId)) return@transaction
        insertOccurrence(accountId, r, date, txn, deleted = false)
    }

    /** Пропустить повторение: больше не спрашивается (на всех устройствах — после синхронизации). */
    fun skip(accountId: String, recurringId: String, date: LocalDate, timeZone: String) = db.transaction {
        val r = requireNotNull(byId(recurringId)) { "Нет платежа $recurringId" }
        if (occurrenceId(r.id, date) in doneIds(accountId)) return@transaction
        insertOccurrence(accountId, r, date, r.toTxn(date, timeZone), deleted = true)
    }

    /** Поля операции повторения по умолчанию — как в платеже, в 12:00 дня платежа. */
    fun defaultTxn(r: Recurring, date: LocalDate, timeZone: String): TransactionDraft = r.toTxn(date, timeZone)

    private fun Recurring.toTxn(date: LocalDate, timeZone: String) = TransactionDraft(
        kind = kind, amountMinor = amountMinor, finAccountId = finAccountId, toFinAccountId = toFinAccountId,
        categoryId = categoryId, occurredAt = LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.of(timeZone)).toEpochMilliseconds(),
        timeZone = timeZone, description = name,
    )

    /**
     * Строка операции с id повторения. Пропуск — та же строка, сразу помеченная удалённой:
     * удалённая запись и пропуск для платежа значат одно — «больше не спрашивать».
     */
    private fun insertOccurrence(accountId: String, r: Recurring, date: LocalDate, t: TransactionDraft, deleted: Boolean) {
        require(t.amountMinor > 0) { "Сумма должна быть больше нуля" }
        val id = occurrenceId(r.id, date)
        val hlc = clock.now()
        val toAccount = if (t.kind == TxnKind.TRANSFER) t.toFinAccountId else null
        val category = if (t.kind == TxnKind.TRANSFER) null else t.categoryId
        val description = t.description.trim().ifEmpty { r.name }
        q.insertTxnWithId(id, accountId, t.kind.dbValue, t.amountMinor, t.finAccountId, toAccount, category, null,
            t.occurredAt, t.timeZone, description, t.note.trim(), id, hlc.toString())
        changeLog.record(accountId, T_TXN, id, hlc, mapOf(
            "kind" to t.kind.dbValue, "amount_minor" to t.amountMinor.toString(), "currency" to "RUB",
            "fin_account_id" to t.finAccountId, "to_fin_account_id" to toAccount, "category_id" to category,
            "occurred_at" to t.occurredAt.toString(), "tz" to t.timeZone, "description" to description, "note" to t.note.trim(),
            "source" to "recurring", "dedup_key" to id,
        ))
        if (deleted) {
            val h = clock.now()
            db.financeQueries.setTxnDeleted(h.toString(), id)
            changeLog.record(accountId, T_TXN, id, h, mapOf("deleted" to "1"))
        }
    }

    private fun occurrence(r: Recurring, d: LocalDate, done: Map<String, Boolean>, today: LocalDate): Occurrence? {
        val deleted = done[occurrenceId(r.id, d)]
        val state = when {
            deleted != null -> if (deleted) OccurrenceState.SKIPPED else OccurrenceState.RECORDED
            d > today -> OccurrenceState.PLANNED
            !activeOn(r, d) -> return null // до снятия с паузы — не было и не спрашивается
            r.mode == RecurringMode.AUTO -> OccurrenceState.PLANNED // запишется при следующем recordDue
            else -> OccurrenceState.PENDING
        }
        return Occurrence(r, d, state)
    }

    private fun activeOn(r: Recurring, d: LocalDate) = r.activeFrom == null || d >= r.activeFrom

    /** Операции платежей: id → удалена ли (удалённая = пропущенная). */
    private fun doneIds(accountId: String): Map<String, Boolean> =
        q.recurringTxns(accountId).executeAsList().associate { it.id to (it.deleted != 0L) }

    private fun dates(r: Recurring): Sequence<LocalDate> {
        val rule = RRule.parse(r.rrule) ?: return sequenceOf(r.startDate).filter { r.endDate == null || it <= r.endDate }
        return Recurrence.dates(rule, r.startDate).let { s -> r.endDate?.let { end -> s.takeWhile { it <= end } } ?: s }
    }

    private fun validate(accountId: String, d: RecurringDraft) {
        require(d.name.isNotBlank()) { "Пустое название" }
        require(d.amountMinor > 0) { "Сумма должна быть больше нуля" }
        require(d.kind != TxnKind.ADJUSTMENT) { "Корректировка не бывает регулярной" }
        require(RRule.parse(d.rrule) != null) { "Неподдерживаемое правило повтора" }
        require(d.endDate == null || d.endDate >= d.startDate) { "Конец раньше начала" }
        val from = requireNotNull(db.financeQueries.finAccountById(d.finAccountId).executeAsOneOrNull()) { "Нет счёта ${d.finAccountId}" }
        require(from.account_id == accountId && from.deleted == 0L) { "Счёт не принадлежит аккаунту" }
        if (d.kind == TxnKind.TRANSFER) {
            val to = requireNotNull(d.toFinAccountId) { "Не выбран счёт зачисления" }
            require(to != d.finAccountId) { "Перевод на тот же счёт" }
        }
    }

    private fun today(): LocalDate =
        Instant.fromEpochMilliseconds(clock.wallMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).date

    private fun io.github.nullbrash.quazio.core.db.Recurring.toModel() = Recurring(
        id = id, name = name, kind = TxnKind.fromDb(kind), amountMinor = amount_minor, finAccountId = fin_account_id,
        toFinAccountId = to_fin_account_id, categoryId = category_id, rrule = rrule, startDate = LocalDate.parse(start_date),
        endDate = end_date?.let(LocalDate::parse), mode = RecurringMode.fromDb(mode), remindDays = remind_days.toInt(),
        paused = paused != 0L, activeFrom = active_from?.let(LocalDate::parse),
    )

    companion object {
        fun occurrenceId(recurringId: String, date: LocalDate) = "$recurringId|$date"

        private const val T_RECURRING = "recurring"
        private const val T_TXN = "txn"
        private fun flag(b: Boolean) = if (b) "1" else "0"
    }
}
