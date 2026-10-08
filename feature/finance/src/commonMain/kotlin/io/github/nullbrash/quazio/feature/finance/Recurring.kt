package io.github.nullbrash.quazio.feature.finance

import io.github.nullbrash.quazio.core.accounts.DeviceClock
import io.github.nullbrash.quazio.core.db.ChangeLog
import io.github.nullbrash.quazio.core.db.QuazioDatabase
import io.github.nullbrash.quazio.core.model.Uuid7
import io.github.nullbrash.quazio.engine.recurrence.Frequency
import io.github.nullbrash.quazio.engine.recurrence.RRule
import io.github.nullbrash.quazio.engine.recurrence.Recurrence
import io.github.nullbrash.quazio.engine.recurrence.Until
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
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
    /** После паузы или смены срока: раньше этой даты повторения не спрашиваются. */
    val activeFrom: LocalDate?,
    /** Разброс: «с 20 по 25» — 5 дней; 0 — один день. */
    val windowDays: Int = 0,
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
    val windowDays: Int = 0,
)

enum class OccurrenceState {
    /** Операция записана. */
    RECORDED,
    /** Пропущено или записанное потом удалили — больше не спрашивается. */
    SKIPPED,
    /** Срок наступил, «спрашивать» — ждёт подтверждения. */
    PENDING,
    /** Ещё впереди. */
    PLANNED,
}

/** Повторение платежа: срок [date]…[windowEnd] (у платежа без разброса — один день). */
data class Occurrence(
    val recurring: Recurring,
    val date: LocalDate,
    val state: OccurrenceState,
    val windowEnd: LocalDate = date,
) {
    /** Ждёт, а срок с разбросом уже прошёл (у платежа на один день такой пометки нет — была бы всегда). */
    fun overdue(today: LocalDate) = state == OccurrenceState.PENDING && recurring.windowDays > 0 && today > windowEnd
}

/** Предложение подстроить срок по фактическим дням прихода: смещения от номинальной даты. */
data class WindowSuggestion(val fromOffset: Int, val toOffset: Int, val basedOn: Int)

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
        val termChanged = old != null && (old.rrule != draft.rrule || old.start_date != draft.startDate.toString() ||
            old.window_days != draft.windowDays.toLong())
        val activeFrom = when {
            old == null -> null
            // Снятие с паузы: пропущенное за паузу не спрашивается задним числом.
            old.paused != 0L && !draft.paused -> today().toString()
            // Новый срок — с сегодняшнего дня: прошлые месяцы не становятся заново «ждущими».
            termChanged && hasRecords(accountId, old.id) -> today().toString()
            else -> old.active_from
        }
        val fields = mapOf(
            "name" to name, "kind" to draft.kind.dbValue, "amount_minor" to draft.amountMinor.toString(),
            "fin_account_id" to draft.finAccountId, "to_fin_account_id" to toAccount, "category_id" to category,
            "rrule" to draft.rrule, "start_date" to draft.startDate.toString(), "end_date" to draft.endDate?.toString(),
            "mode" to draft.mode.dbValue, "remind_days" to draft.remindDays.toString(), "paused" to flag(draft.paused),
            "active_from" to activeFrom, "window_days" to draft.windowDays.toString(),
        )
        if (old == null) {
            val id = Uuid7.generate(clock.wallMillis())
            q.insertRecurring(id, accountId, name, draft.kind.dbValue, draft.amountMinor, draft.finAccountId, toAccount, category,
                draft.rrule, draft.startDate.toString(), draft.endDate?.toString(), draft.mode.dbValue, draft.remindDays.toLong(),
                if (draft.paused) 1 else 0, null, draft.windowDays.toLong(), hlc.toString())
            changeLog.record(accountId, T_RECURRING, id, hlc, fields + ("currency" to "RUB"))
            id
        } else {
            val before = mapOf(
                "name" to old.name, "kind" to old.kind, "amount_minor" to old.amount_minor.toString(),
                "fin_account_id" to old.fin_account_id, "to_fin_account_id" to old.to_fin_account_id, "category_id" to old.category_id,
                "rrule" to old.rrule, "start_date" to old.start_date, "end_date" to old.end_date, "mode" to old.mode,
                "remind_days" to old.remind_days.toString(), "paused" to old.paused.toString(), "active_from" to old.active_from,
                "window_days" to old.window_days.toString(),
            )
            val changed = fields.filter { (k, v) -> before[k] != v }
            if (changed.isNotEmpty()) {
                q.updateRecurring(name, draft.kind.dbValue, draft.amountMinor, draft.finAccountId, toAccount, category, draft.rrule,
                    draft.startDate.toString(), draft.endDate?.toString(), draft.mode.dbValue, draft.remindDays.toLong(),
                    if (draft.paused) 1 else 0, activeFrom, draft.windowDays.toLong(), hlc.toString(), old.id)
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

    /** Повторения, чей срок пересекает [from, toExclusive), по всем действующим платежам — для календаря и списков. */
    fun occurrences(accountId: String, from: LocalDate, toExclusive: LocalDate, today: LocalDate = today()): List<Occurrence> {
        val done = doneIds(accountId)
        return all(accountId).filter { !it.paused }.flatMap { r ->
            dates(r).takeWhile { it < toExclusive }.filter { end(r, it) >= from }.mapNotNull { d -> occurrence(r, d, done, today) }.toList()
        }.sortedWith(compareBy({ it.date }, { it.recurring.name }))
    }

    /** Повторения «спрашивать», чей срок наступил, ещё не записанные и не пропущенные. */
    fun pending(accountId: String, today: LocalDate = today()): List<Occurrence> {
        val done = doneIds(accountId)
        return all(accountId).filter { !it.paused && it.mode == RecurringMode.ASK }.flatMap { r ->
            dates(r).takeWhile { it <= today }.mapNotNull { d -> occurrence(r, d, done, today) }.filter { it.state == OccurrenceState.PENDING }.toList()
        }.sortedWith(compareBy({ it.date }, { it.recurring.name }))
    }

    /** Ближайшее повторение не раньше [today]; null — платёж закончился. */
    fun next(r: Recurring, today: LocalDate = today()): LocalDate? = dates(r).firstOrNull { it >= today }

    /** Ближайшие даты черновика, чей срок не раньше [from], — для окна платежа (как лягут, до сохранения). */
    fun preview(draft: RecurringDraft, count: Int, from: LocalDate = today()): List<LocalDate> =
        draft.toModel().let { r -> dates(r).filter { end(r, it) >= from }.take(count).toList() }

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

    /** Записать повторение; [txn] — поля из окна подтверждения (сумму, дату и прочее можно поправить). */
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

    /**
     * Поля операции повторения по умолчанию — как в платеже, в 12:00. Дата: у платежа с
     * разбросом — сегодня (день прихода заранее неизвестен), иначе — день платежа.
     */
    fun defaultTxn(r: Recurring, date: LocalDate, timeZone: String, today: LocalDate = today()): TransactionDraft =
        r.toTxn(if (r.windowDays > 0 && today >= date) today else date, timeZone)

    /**
     * Подстроить срок по истории (решение пользователя): после 3 записанных повторений — по
     * последним 6 фактическим дням прихода срок «от самого раннего до самого позднего».
     * null — данных мало, срок уже такой или предложение отложено до новых записей.
     */
    fun suggestWindow(accountId: String, r: Recurring): WindowSuggestion? {
        if (r.mode != RecurringMode.ASK) return null
        val offsets = recordedOffsets(accountId, r)
        if (offsets.size < MIN_RECORDS) return null
        val last = offsets.takeLast(LAST_RECORDS)
        val s = WindowSuggestion(last.min(), last.max(), last.size)
        if (s.fromOffset == 0 && s.toOffset == r.windowDays) return null
        if (dismissedAt(r.id) == offsets.size) return null
        return s
    }

    /** «Не сейчас»: предложение вернётся, когда появятся новые записи. */
    fun dismissSuggestion(accountId: String, r: Recurring) {
        db.appStateQueries.put(KEY_DISMISS + r.id, recordedOffsets(accountId, r).size.toString())
    }

    /** «Изменить срок»: первый день сдвигается на начало предложенного срока. */
    fun applySuggestion(accountId: String, r: Recurring, s: WindowSuggestion): String = save(accountId, RecurringDraft(
        id = r.id, name = r.name, kind = r.kind, amountMinor = r.amountMinor, finAccountId = r.finAccountId,
        toFinAccountId = r.toFinAccountId, categoryId = r.categoryId, rrule = r.rrule,
        startDate = r.startDate.plus(DatePeriod(days = s.fromOffset)), endDate = r.endDate, mode = r.mode,
        remindDays = r.remindDays, paused = r.paused, windowDays = s.toOffset - s.fromOffset,
    ))

    /** На сколько дней от номинальной даты приходили записанные повторения (по порядку дат). */
    private fun recordedOffsets(accountId: String, r: Recurring): List<Int> =
        q.recordedOf(accountId, likePrefix("${r.id}|")).executeAsList().mapNotNull { row ->
            val nominal = runCatching { LocalDate.parse(row.id.substringAfter('|')) }.getOrNull() ?: return@mapNotNull null
            val zone = runCatching { TimeZone.of(row.tz) }.getOrDefault(TimeZone.currentSystemDefault())
            nominal to nominal.daysUntil(Instant.fromEpochMilliseconds(row.occurred_at).toLocalDateTime(zone).date)
        }.sortedBy { it.first }.map { it.second }

    private fun dismissedAt(id: String): Int? = db.appStateQueries.get(KEY_DISMISS + id).executeAsOneOrNull()?.toIntOrNull()

    private fun hasRecords(accountId: String, recurringId: String) =
        q.recurringTxns(accountId).executeAsList().any { it.id.startsWith("$recurringId|") }

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
            !activeOn(r, d) -> return null // до снятия с паузы или смены срока — не спрашивается
            r.mode == RecurringMode.AUTO -> OccurrenceState.PLANNED // запишется при следующем recordDue
            else -> OccurrenceState.PENDING
        }
        return Occurrence(r, d, state, end(r, d))
    }

    private fun end(r: Recurring, d: LocalDate) = if (r.windowDays > 0) d.plus(DatePeriod(days = r.windowDays)) else d

    private fun activeOn(r: Recurring, d: LocalDate) = r.activeFrom == null || d >= r.activeFrom

    /** Операции платежей: id → удалена ли (удалённая = пропущенная). */
    private fun doneIds(accountId: String): Map<String, Boolean> =
        q.recurringTxns(accountId).executeAsList().associate { it.id to (it.deleted != 0L) }

    private fun dates(r: Recurring): Sequence<LocalDate> {
        val rule = RRule.parse(r.rrule) ?: return sequenceOf(r.startDate).filter { r.endDate == null || it <= r.endDate }
        val all = if (countsDays(rule)) dayCountDates(rule, r.startDate) else Recurrence.dates(rule, r.startDate)
        return r.endDate?.let { end -> all.takeWhile { it <= end } } ?: all
    }

    /** Простое «каждый N-й месяц / год» — число отсчитывается от 1-го, лишние дни уходят в следующий месяц. */
    private fun countsDays(rule: RRule) = (rule.freq == Frequency.MONTHLY || rule.freq == Frequency.YEARLY) &&
        rule.byDay.isEmpty() && rule.byMonthDay.isEmpty() && rule.byMonth.isEmpty() && rule.bySetPos.isEmpty()

    /**
     * Перенос 29–31-го «по счёту дней» (решение пользователя): в феврале 29 → 1, 30 → 2, 31 → 3
     * марта; в 30-дневном месяце 31 → 1-е. Так же 29 февраля у ежегодного → 1 марта.
     */
    private fun dayCountDates(rule: RRule, start: LocalDate): Sequence<LocalDate> {
        val stepMonths = rule.interval * (if (rule.freq == Frequency.YEARLY) 12 else 1)
        val base = LocalDate(start.year, start.month, 1)
        val untilDate = when (val u = rule.until) {
            null -> null
            is Until.Date -> u.date
            is Until.Floating -> u.dateTime.date
            is Until.Utc -> u.instant.toLocalDateTime(TimeZone.UTC).date
        }
        val s = generateSequence(0) { it + 1 }
            .map { k -> base.plus(DatePeriod(months = k * stepMonths)).plus(DatePeriod(days = start.day - 1)) }
            .takeWhile { untilDate == null || it <= untilDate }
        return rule.count?.let { s.take(it) } ?: s
    }

    private fun validate(accountId: String, d: RecurringDraft) {
        require(d.name.isNotBlank()) { "Пустое название" }
        require(d.amountMinor > 0) { "Сумма должна быть больше нуля" }
        require(d.kind != TxnKind.ADJUSTMENT) { "Корректировка не бывает регулярной" }
        require(RRule.parse(d.rrule) != null) { "Неподдерживаемое правило повтора" }
        require(d.endDate == null || d.endDate >= d.startDate) { "Конец раньше начала" }
        require(d.windowDays in 0..MAX_WINDOW) { "Разброс — от 0 до $MAX_WINDOW дней" }
        // День прихода при разбросе заранее неизвестен — записать самому нельзя (решение пользователя).
        require(d.windowDays == 0 || d.mode == RecurringMode.ASK) { "С разбросом срока платёж только подтверждается" }
        val from = requireNotNull(db.financeQueries.finAccountById(d.finAccountId).executeAsOneOrNull()) { "Нет счёта ${d.finAccountId}" }
        require(from.account_id == accountId && from.deleted == 0L) { "Счёт не принадлежит аккаунту" }
        if (d.kind == TxnKind.TRANSFER) {
            val to = requireNotNull(d.toFinAccountId) { "Не выбран счёт зачисления" }
            require(to != d.finAccountId) { "Перевод на тот же счёт" }
        }
    }

    private fun today(): LocalDate =
        Instant.fromEpochMilliseconds(clock.wallMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).date

    private fun RecurringDraft.toModel() = Recurring(
        id = id ?: "", name = name, kind = kind, amountMinor = amountMinor, finAccountId = finAccountId,
        toFinAccountId = toFinAccountId, categoryId = categoryId, rrule = rrule, startDate = startDate, endDate = endDate,
        mode = mode, remindDays = remindDays, paused = paused, activeFrom = null, windowDays = windowDays,
    )

    private fun io.github.nullbrash.quazio.core.db.Recurring.toModel() = Recurring(
        id = id, name = name, kind = TxnKind.fromDb(kind), amountMinor = amount_minor, finAccountId = fin_account_id,
        toFinAccountId = to_fin_account_id, categoryId = category_id, rrule = rrule, startDate = LocalDate.parse(start_date),
        endDate = end_date?.let(LocalDate::parse), mode = RecurringMode.fromDb(mode), remindDays = remind_days.toInt(),
        paused = paused != 0L, activeFrom = active_from?.let(LocalDate::parse), windowDays = window_days.toInt(),
    )

    companion object {
        fun occurrenceId(recurringId: String, date: LocalDate) = "$recurringId|$date"

        const val MAX_WINDOW = 14
        private const val MIN_RECORDS = 3
        private const val LAST_RECORDS = 6
        private const val T_RECURRING = "recurring"
        private const val T_TXN = "txn"
        private const val KEY_DISMISS = "finance.recurring_dismiss."
        private fun flag(b: Boolean) = if (b) "1" else "0"

        /** Шаблон LIKE «начинается с» с экранированием % и _. */
        private fun likePrefix(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }
}
