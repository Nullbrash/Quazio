package io.github.nullbrash.quazio.feature.finance

import io.github.nullbrash.quazio.core.accounts.DeviceClock
import io.github.nullbrash.quazio.core.db.ChangeLog
import io.github.nullbrash.quazio.core.db.QuazioDatabase
import io.github.nullbrash.quazio.core.model.CurrencyCode
import io.github.nullbrash.quazio.core.model.Hlc
import io.github.nullbrash.quazio.core.model.Money
import io.github.nullbrash.quazio.core.model.Uuid7
import io.github.nullbrash.quazio.engine.quickinput.AccountRole
import io.github.nullbrash.quazio.engine.quickinput.QuickAccount
import io.github.nullbrash.quazio.engine.quickinput.QuickCategory
import io.github.nullbrash.quazio.engine.quickinput.QuickKind
import io.github.nullbrash.quazio.engine.quickinput.QuickVocabulary
import io.github.nullbrash.quazio.engine.quickinput.WordRule

/**
 * Учёт финансов одного аккаунта (`accountId` — аккаунт Quazio, не банковский счёт).
 * Каждое изменение — в журнал синхронизации, в той же транзакции; в журнал идут
 * только изменённые поля. Методы блокирующие — не из главного потока.
 */
class FinanceService(private val db: QuazioDatabase, private val clock: DeviceClock) {

    private val q get() = db.financeQueries
    private val changeLog = ChangeLog(db)

    /** Регулярные платежи — та же база и те же часы. */
    val recurring = RecurringService(db, clock)

    // ===== Настройки этого устройства =====

    /** Открывать калькулятор сразу при добавлении операции (по умолчанию — да). */
    var openCalculatorOnNew: Boolean
        get() = db.appStateQueries.get(KEY_CALC_ON_NEW).executeAsOneOrNull() != "0"
        set(value) { db.appStateQueries.put(KEY_CALC_ON_NEW, if (value) "1" else "0") }

    // ===== Стартовый набор =====

    /** Категории и счёт «Наличные» для нового аккаунта; повторно ничего не делает. */
    fun ensureDefaults(accountId: String) = db.transaction {
        if (q.categoryCount(accountId).executeAsOne() > 0) return@transaction
        val hlc = clock.now()
        fun insert(c: DefaultCategory, parentId: String?, index: Int) {
            val id = defaultCategoryId(accountId, c.key)
            val sortKey = index.toString().padStart(3, '0')
            q.insertCategory(id, accountId, parentId, c.name, c.kind.dbValue, c.color, sortKey, hlc.toString())
            changeLog.record(accountId, T_CATEGORY, id, hlc, mapOf(
                "parent_id" to parentId, "name" to c.name, "kind" to c.kind.dbValue,
                "color" to c.color?.toString(), "sort_key" to sortKey,
            ))
            c.children.forEachIndexed { i, child -> insert(child, id, i) }
        }
        DEFAULT_CATEGORIES.forEachIndexed { i, c -> insert(c, null, i) }
        insertFinAccountRow(accountId, defaultCashAccountId(accountId), "Наличные", FinAccountType.CASH, 0, null, true, null, hlc)
    }

    // ===== Счета =====

    fun accounts(accountId: String): List<FinAccount> = q.finAccountsWithBalance(accountId).executeAsList().map { r ->
        val currency = CurrencyCode(r.currency)
        FinAccount(
            id = r.id,
            name = r.name,
            type = FinAccountType.fromDb(r.type),
            balance = Money(r.balance_minor ?: 0, currency),
            includeInTotal = r.include_in_total != 0L,
            creditLimit = r.credit_limit_minor?.let { Money(it, currency) },
            personTagId = r.person_tag_id,
            archived = r.archived != 0L,
        )
    }

    /** Сумма по счетам с галочкой «учитывать в итоге» (без архивных). */
    fun total(accounts: List<FinAccount>): Money =
        accounts.filter { it.includeInTotal && !it.archived }.fold(Money.rub(0)) { acc, a -> acc + a.balance }

    fun createAccount(
        accountId: String,
        name: String,
        type: FinAccountType,
        openingBalanceMinor: Long = 0,
        creditLimitMinor: Long? = null,
        includeInTotal: Boolean = type.defaultIncludeInTotal,
        personTagId: String? = null,
    ): String = db.transactionWithResult {
        val id = Uuid7.generate(clock.wallMillis())
        insertFinAccountRow(accountId, id, cleanName(name), type, openingBalanceMinor, creditLimitMinor, includeInTotal, personTagId, clock.now())
        id
    }

    fun updateAccount(
        id: String,
        name: String,
        type: FinAccountType,
        openingBalanceMinor: Long,
        creditLimitMinor: Long?,
        includeInTotal: Boolean,
        personTagId: String?,
        archived: Boolean,
    ) = db.transaction {
        val old = requireNotNull(q.finAccountById(id).executeAsOneOrNull()) { "Нет счёта $id" }
        val clean = cleanName(name)
        val new = mapOf(
            "name" to clean, "type" to type.dbValue, "opening_balance_minor" to openingBalanceMinor.toString(),
            "credit_limit_minor" to creditLimitMinor?.toString(), "include_in_total" to flag(includeInTotal),
            "person_tag_id" to personTagId, "archived" to flag(archived),
        )
        val before = mapOf(
            "name" to old.name, "type" to old.type, "opening_balance_minor" to old.opening_balance_minor.toString(),
            "credit_limit_minor" to old.credit_limit_minor?.toString(), "include_in_total" to old.include_in_total.toString(),
            "person_tag_id" to old.person_tag_id, "archived" to old.archived.toString(),
        )
        val changed = new.filter { (k, v) -> before[k] != v }
        if (changed.isEmpty()) return@transaction
        val hlc = clock.now()
        q.updateFinAccount(clean, type.dbValue, openingBalanceMinor, creditLimitMinor, if (includeInTotal) 1 else 0, personTagId,
            if (archived) 1 else 0, hlc.toString(), id)
        changeLog.record(old.account_id, T_FIN_ACCOUNT, id, hlc, changed)
    }

    /** Счёт с операциями удалить нельзя — только в архив: иначе пропадёт история. */
    fun deleteAccount(id: String) = db.transaction {
        val old = requireNotNull(q.finAccountById(id).executeAsOneOrNull()) { "Нет счёта $id" }
        require(!hasTransactions(id)) { "У счёта есть операции — его можно только убрать в архив" }
        markDeleted(old.account_id, T_FIN_ACCOUNT, id) { q.setFinAccountDeleted(it, id) }
    }

    /**
     * Довести баланс счёта до [targetMinor]: если отличается — операция-корректировка на
     * разницу (видна в истории, удаляется как обычная). Так пользователь вводит итоговую
     * цифру, а не считает разницу сам. Возвращает id корректировки или null, если менять нечего.
     */
    fun adjustBalanceTo(accountId: String, finAccountId: String, targetMinor: Long, description: String, atMillis: Long, timeZone: String): String? =
        db.transactionWithResult {
            val current = accounts(accountId).firstOrNull { it.id == finAccountId }?.balance?.minor
                ?: throw IllegalArgumentException("Нет счёта $finAccountId")
            val delta = targetMinor - current
            if (delta == 0L) return@transactionWithResult null
            saveTransaction(accountId, TransactionDraft(
                kind = TxnKind.ADJUSTMENT, amountMinor = delta, finAccountId = finAccountId,
                occurredAt = atMillis, timeZone = timeZone, description = description,
            ))
        }

    fun openingBalance(finAccountId: String): Long =
        requireNotNull(q.finAccountById(finAccountId).executeAsOneOrNull()) { "Нет счёта $finAccountId" }.opening_balance_minor

    fun hasTransactions(finAccountId: String): Boolean =
        q.anyTxnOfFinAccount(finAccountId).executeAsOneOrNull() != null

    private fun insertFinAccountRow(
        accountId: String, id: String, name: String, type: FinAccountType, opening: Long, creditLimit: Long?,
        includeInTotal: Boolean, personTagId: String?, hlc: Hlc,
    ) {
        val sortKey = nextAccountSortKey(accountId)
        q.insertFinAccount(id, accountId, name, type.dbValue, "RUB", opening, creditLimit, if (includeInTotal) 1 else 0, personTagId, sortKey, hlc.toString())
        changeLog.record(accountId, T_FIN_ACCOUNT, id, hlc, mapOf(
            "name" to name, "type" to type.dbValue, "currency" to "RUB", "opening_balance_minor" to opening.toString(),
            "credit_limit_minor" to creditLimit?.toString(), "include_in_total" to flag(includeInTotal),
            "person_tag_id" to personTagId, "sort_key" to sortKey,
        ))
    }

    /** Пока порядок не задавали — пусто (по имени); после ручной сортировки новый счёт встаёт в конец. */
    private fun nextAccountSortKey(accountId: String): String {
        val max = q.finAccountsWithBalance(accountId).executeAsList().mapNotNull { it.sort_key.toIntOrNull() }.maxOrNull() ?: return ""
        return sortKeyOf(max + 1)
    }

    private fun sortKeyOf(index: Int) = index.toString().padStart(4, '0')

    /** Ручной порядок счетов (как в списке); через журнал — чтобы порядок разошёлся на другие устройства. */
    fun reorderAccounts(accountId: String, orderedIds: List<String>) = db.transaction {
        val current = q.finAccountsWithBalance(accountId).executeAsList().associateBy { it.id }
        var hlc: Hlc? = null
        orderedIds.forEachIndexed { i, id ->
            val row = requireNotNull(current[id]) { "Нет счёта $id" }
            val key = sortKeyOf(i)
            if (row.sort_key == key) return@forEachIndexed
            val h = hlc ?: clock.now().also { hlc = it }
            q.setFinAccountSortKey(key, h.toString(), id)
            changeLog.record(accountId, T_FIN_ACCOUNT, id, h, mapOf("sort_key" to key))
        }
    }

    // ===== Категории =====

    fun categories(accountId: String, kind: CategoryKind? = null): List<CategoryNode> {
        val rows = q.categoriesOf(accountId).executeAsList()
            .map { CategoryRow(it.id, it.parent_id, it.name, CategoryKind.fromDb(it.kind), it.color, it.sort_key) }
            .filter { kind == null || it.kind == kind }
        return buildCategoryTree(rows)
    }

    fun addCategory(accountId: String, parentId: String?, name: String, kind: CategoryKind, color: Long? = null): String =
        db.transactionWithResult {
            if (parentId != null) {
                val parent = requireNotNull(q.categoryById(parentId).executeAsOneOrNull()) { "Нет категории $parentId" }
                require(parent.kind == kind.dbValue) { "Подкатегория должна быть того же вида, что и родитель" }
            }
            val id = Uuid7.generate(clock.wallMillis())
            val hlc = clock.now()
            val clean = cleanName(name)
            q.insertCategory(id, accountId, parentId, clean, kind.dbValue, color, "", hlc.toString())
            changeLog.record(accountId, T_CATEGORY, id, hlc, mapOf(
                "parent_id" to parentId, "name" to clean, "kind" to kind.dbValue, "color" to color?.toString(), "sort_key" to "",
            ))
            id
        }

    fun updateCategory(id: String, name: String, parentId: String?, color: Long?) = db.transaction {
        val old = requireNotNull(q.categoryById(id).executeAsOneOrNull()) { "Нет категории $id" }
        require(parentId != id) { "Категория не может быть своим родителем" }
        if (parentId != null) require(!isDescendant(parentId, of = id)) { "Нельзя переместить категорию внутрь её же подкатегории" }
        val clean = cleanName(name)
        val changed = buildMap {
            if (old.name != clean) put("name", clean)
            if (old.parent_id != parentId) put("parent_id", parentId)
            if (old.color != color) put("color", color?.toString())
        }
        if (changed.isEmpty()) return@transaction
        val hlc = clock.now()
        q.updateCategory(clean, parentId, color, old.sort_key, hlc.toString(), id)
        changeLog.record(old.account_id, T_CATEGORY, id, hlc, changed)
    }

    /** Удаляет категорию вместе с подкатегориями; операции сохраняют ссылку (видны как «без категории»). */
    fun deleteCategory(id: String) = db.transaction {
        val old = requireNotNull(q.categoryById(id).executeAsOneOrNull()) { "Нет категории $id" }
        val all = q.categoriesOf(old.account_id).executeAsList()
        val toDelete = mutableListOf(id)
        var i = 0
        while (i < toDelete.size) {
            val current = toDelete[i++]
            all.filter { it.parent_id == current }.forEach { toDelete += it.id }
        }
        toDelete.forEach { cid -> markDeleted(old.account_id, T_CATEGORY, cid) { q.setCategoryDeleted(it, cid) } }
    }

    private fun isDescendant(candidate: String, of: String): Boolean {
        var cur: String? = candidate
        var guard = 0
        while (cur != null && guard++ < 64) {
            if (cur == of) return true
            cur = q.categoryById(cur).executeAsOneOrNull()?.parent_id
        }
        return false
    }

    // ===== Метки и магазины =====

    fun tags(accountId: String): List<Tag> =
        q.tagsOf(accountId).executeAsList().map { Tag(it.id, it.name, TagKind.fromDb(it.kind)) }

    fun createTag(accountId: String, name: String, kind: TagKind): Tag = db.transactionWithResult {
        val clean = cleanName(name)
        tags(accountId).firstOrNull { it.kind == kind && it.name.equals(clean, ignoreCase = true) }?.let { return@transactionWithResult it }
        val id = Uuid7.generate(clock.wallMillis())
        val hlc = clock.now()
        q.insertTag(id, accountId, clean, kind.dbValue, hlc.toString())
        changeLog.record(accountId, T_TAG, id, hlc, mapOf("name" to clean, "kind" to kind.dbValue))
        Tag(id, clean, kind)
    }

    fun updateTag(id: String, name: String, kind: TagKind) = db.transaction {
        val old = requireNotNull(q.tagById(id).executeAsOneOrNull()) { "Нет метки $id" }
        val clean = cleanName(name)
        val changed = buildMap {
            if (old.name != clean) put("name", clean)
            if (old.kind != kind.dbValue) put("kind", kind.dbValue)
        }
        if (changed.isEmpty()) return@transaction
        val hlc = clock.now()
        q.updateTag(clean, kind.dbValue, hlc.toString(), id)
        changeLog.record(old.account_id, T_TAG, id, hlc, changed)
    }

    /** Удалить метку: с операций она исчезает, сами операции остаются. */
    fun deleteTag(id: String) = db.transaction {
        val old = requireNotNull(q.tagById(id).executeAsOneOrNull()) { "Нет метки $id" }
        markDeleted(old.account_id, T_TAG, id) { q.setTagDeleted(it, id) }
    }

    fun merchantSuggestions(accountId: String, prefix: String, limit: Long = 8): List<String> =
        q.merchantsMatching(accountId, likePrefix(normalize(prefix)), limit).executeAsList().map { it.name }

    fun descriptionSuggestions(accountId: String, prefix: String, limit: Long = 8): List<String> =
        q.recentDescriptions(accountId, likePrefix(prefix.trim().lowercase()), limit).executeAsList()

    /** Автозаполнение: по магазину, иначе по описанию — тип, счёт, категория и метки прошлой операции. */
    fun suggest(accountId: String, merchantName: String?, description: String?): Suggestion? {
        val byMerchant = merchantName?.let { normalize(it) }?.takeIf { it.isNotEmpty() }
            ?.let { q.merchantByNormalized(accountId, it).executeAsOneOrNull() }
            ?.let { q.lastTxnByMerchant(accountId, it.id).executeAsOneOrNull() }
        val txn = byMerchant
            ?: description?.trim()?.takeIf { it.isNotEmpty() }?.let { q.lastTxnByDescription(accountId, it).executeAsOneOrNull() }
            ?: return null
        return Suggestion(TxnKind.fromDb(txn.kind), txn.fin_account_id, txn.category_id, q.tagIdsOfTxn(txn.id).executeAsList().toSet())
    }

    private fun merchantIdFor(accountId: String, name: String?, hlc: Hlc): String? {
        val clean = name?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() } ?: return null
        val norm = normalize(clean)
        q.merchantByNormalized(accountId, norm).executeAsOneOrNull()?.let { return it.id }
        val id = Uuid7.generate(clock.wallMillis())
        q.insertMerchant(id, accountId, clean, norm, null, hlc.toString())
        changeLog.record(accountId, T_MERCHANT, id, hlc, mapOf("name" to clean, "normalized" to norm))
        return id
    }

    // ===== Операции =====

    /** Сохранить новую или изменённую операцию; возвращает её id. */
    fun saveTransaction(accountId: String, draft: TransactionDraft): String = db.transactionWithResult {
        validate(accountId, draft)
        val hlc = clock.now()
        val merchantId = merchantIdFor(accountId, draft.merchantName, hlc)
        val toAccount = if (draft.kind == TxnKind.TRANSFER) draft.toFinAccountId else null
        val category = if (draft.kind == TxnKind.EXPENSE || draft.kind == TxnKind.INCOME) draft.categoryId else null
        val fields = mapOf(
            "kind" to draft.kind.dbValue, "amount_minor" to draft.amountMinor.toString(), "fin_account_id" to draft.finAccountId,
            "to_fin_account_id" to toAccount, "category_id" to category, "merchant_id" to merchantId,
            "occurred_at" to draft.occurredAt.toString(), "tz" to draft.timeZone,
            "description" to draft.description.trim(), "note" to draft.note.trim(),
        )
        val id = draft.id
        if (id == null) {
            val newId = Uuid7.generate(clock.wallMillis())
            q.insertTxn(newId, accountId, draft.kind.dbValue, draft.amountMinor, "RUB", draft.finAccountId, toAccount, category, merchantId,
                draft.occurredAt, draft.timeZone, draft.description.trim(), draft.note.trim(), draft.source.dbValue, null, hlc.toString())
            changeLog.record(accountId, T_TXN, newId, hlc, fields + mapOf("currency" to "RUB", "source" to draft.source.dbValue))
            setTags(accountId, newId, draft.tagIds, hlc)
            newId
        } else {
            val old = requireNotNull(q.txnById(id).executeAsOneOrNull()) { "Нет операции $id" }
            val before = mapOf(
                "kind" to old.kind, "amount_minor" to old.amount_minor.toString(), "fin_account_id" to old.fin_account_id,
                "to_fin_account_id" to old.to_fin_account_id, "category_id" to old.category_id, "merchant_id" to old.merchant_id,
                "occurred_at" to old.occurred_at.toString(), "tz" to old.tz, "description" to old.description, "note" to old.note,
            )
            val changed = fields.filter { (k, v) -> before[k] != v }
            if (changed.isNotEmpty()) {
                q.updateTxn(draft.kind.dbValue, draft.amountMinor, draft.finAccountId, toAccount, category, merchantId,
                    draft.occurredAt, draft.timeZone, draft.description.trim(), draft.note.trim(), hlc.toString(), id)
                changeLog.record(accountId, T_TXN, id, hlc, changed)
            }
            setTags(accountId, id, draft.tagIds, hlc)
            id
        }
    }

    fun deleteTransaction(id: String) = db.transaction {
        val old = requireNotNull(q.txnById(id).executeAsOneOrNull()) { "Нет операции $id" }
        markDeleted(old.account_id, T_TXN, id) { q.setTxnDeleted(it, id) }
    }

    fun transaction(id: String): TransactionDraft? = q.txnById(id).executeAsOneOrNull()?.takeIf { it.deleted == 0L }?.let { t ->
        TransactionDraft(
            id = t.id, kind = TxnKind.fromDb(t.kind), amountMinor = t.amount_minor, finAccountId = t.fin_account_id,
            toFinAccountId = t.to_fin_account_id, categoryId = t.category_id,
            merchantName = t.merchant_id?.let { q.merchantById(it).executeAsOneOrNull()?.name },
            tagIds = q.tagIdsOfTxn(t.id).executeAsList().toSet(), occurredAt = t.occurred_at, timeZone = t.tz,
            description = t.description, note = t.note,
        )
    }

    /** Операции за период [fromMillis, toMillis), новые сверху. */
    fun transactions(accountId: String, fromMillis: Long, toMillis: Long): List<Transaction> {
        val rows = q.txnsInRange(accountId, fromMillis, toMillis).executeAsList()
        if (rows.isEmpty()) return emptyList()
        val tagsByTxn = rows.map { it.id }.chunked(500).flatMap { ids -> q.tagsOfTxns(ids).executeAsList() }
            .groupBy({ it.txn_id }, { Tag(it.id, it.name, TagKind.fromDb(it.kind)) })
        return rows.map { r ->
            Transaction(
                id = r.id, kind = TxnKind.fromDb(r.kind), amount = Money(r.amount_minor, CurrencyCode(r.currency)),
                finAccountId = r.fin_account_id, finAccountName = r.fin_account_name,
                toFinAccountId = r.to_fin_account_id, toFinAccountName = r.to_fin_account_name,
                categoryId = r.category_id, categoryName = r.category_name, merchantName = r.merchant_name,
                tags = tagsByTxn[r.id].orEmpty(), occurredAt = r.occurred_at, timeZone = r.tz,
                description = r.description, note = r.note,
            )
        }
    }

    /** Все операции аккаунта в CSV, от старых к новым. */
    fun exportCsv(accountId: String): String {
        val all = transactions(accountId, Long.MIN_VALUE, Long.MAX_VALUE).asReversed()
        val paths = categories(accountId).associate { it.id to it.path }
        return TransactionCsv.build(all, paths)
    }

    /**
     * Пометить удалёнными все финансовые данные аккаунта (для удаления аккаунта).
     * Вызывать внутри транзакции удаления аккаунта. Каждая запись — в журнал: другие
     * устройства узнают об удалении при синхронизации.
     */
    fun purgeAccountData(accountId: String) = db.transaction {
        q.txnsInRange(accountId, Long.MIN_VALUE, Long.MAX_VALUE).executeAsList().forEach { t ->
            markDeleted(accountId, T_TXN, t.id) { q.setTxnDeleted(it, t.id) }
        }
        q.finAccountsWithBalance(accountId).executeAsList().forEach { a ->
            markDeleted(accountId, T_FIN_ACCOUNT, a.id) { q.setFinAccountDeleted(it, a.id) }
        }
        q.categoriesOf(accountId).executeAsList().forEach { c ->
            markDeleted(accountId, T_CATEGORY, c.id) { q.setCategoryDeleted(it, c.id) }
        }
        q.tagsOf(accountId).executeAsList().forEach { tag ->
            markDeleted(accountId, T_TAG, tag.id) { q.setTagDeleted(it, tag.id) }
        }
        q.merchantsOf(accountId).executeAsList().forEach { m ->
            markDeleted(accountId, T_MERCHANT, m.id) { q.setMerchantDeleted(it, m.id) }
        }
        db.quickInputQueries.deleteQuickWordsOf(accountId)
    }

    // ===== Быстрый ввод =====

    /** Всё, что нужно разбору быстрого ввода: счета, категории, магазины, метки, память слов. */
    fun quickVocabulary(accountId: String): QuickVocabulary {
        val tagNames = tags(accountId).associate { it.id to it.name }
        val accounts = accounts(accountId).filter { !it.archived }.map { a ->
            val role = when (a.type) {
                FinAccountType.CARD, FinAccountType.CREDIT_CARD -> AccountRole.CARD
                FinAccountType.CASH -> AccountRole.CASH
                FinAccountType.SAVINGS -> AccountRole.SAVINGS
                FinAccountType.DEBT -> AccountRole.DEBT
                else -> AccountRole.OTHER
            }
            QuickAccount(a.id, a.name, role, a.personTagId?.let { tagNames[it] })
        }
        val nodes = categories(accountId)
        val parents = nodes.mapNotNullTo(HashSet()) { it.parentId }
        val keyPrefix = defaultCategoryId(accountId, "")
        val categories = nodes.map { c ->
            QuickCategory(c.id, c.name, c.kind == CategoryKind.INCOME, c.id.takeIf { it.startsWith(keyPrefix) }?.removePrefix(keyPrefix), c.id !in parents)
        }
        val merchants = db.quickInputQueries.merchantsWithLastCategory(accountId).executeAsList().associate { it.name to it.category_id }
        val rules = db.quickInputQueries.quickWordsOf(accountId).executeAsList().associate { r ->
            r.word to WordRule(r.kind?.let { k -> QuickKind.entries.firstOrNull { it.name == k } }, r.category_id, r.fin_account_id, r.second_fin_account_id)
        }
        return QuickVocabulary(accounts, categories, merchants, tagNames.values.toList(), rules)
    }

    /**
     * Категория по названию (регулярный платёж): последняя операция с таким магазином, иначе
     * то, что знает быстрый ввод (имя категории — подкатегория предпочитается, память слов,
     * известные марки). Только категория нужного вида; null — подсказать нечего.
     */
    fun suggestCategory(accountId: String, name: String, income: Boolean, today: kotlinx.datetime.LocalDate): String? {
        val clean = name.trim().takeIf { it.isNotEmpty() } ?: return null
        val kind = if (income) CategoryKind.INCOME.dbValue else CategoryKind.EXPENSE.dbValue
        fun ok(id: String?) = id != null && q.categoryById(id).executeAsOneOrNull()?.let { it.deleted == 0L && it.kind == kind } == true
        q.merchantByNormalized(accountId, normalize(clean)).executeAsOneOrNull()
            ?.let { q.lastTxnByMerchant(accountId, it.id).executeAsOneOrNull()?.category_id }
            ?.takeIf(::ok)?.let { return it }
        // Разбор как «<название> 1»: сумма нужна разбору, сама она не важна.
        val line = io.github.nullbrash.quazio.engine.quickinput.QuickParser.parse("$clean 1", quickVocabulary(accountId), today).firstOrNull()
        val draft = (line as? io.github.nullbrash.quazio.engine.quickinput.QuickLine.Operation)?.draft ?: return null
        return draft.categoryId?.takeIf(::ok)
    }

    /** Запомнить правку черновика: заданные поля [change] заменяют прежние, остальные остаются. */
    fun rememberQuickWord(accountId: String, word: String, change: WordRule) = db.transaction {
        val qi = db.quickInputQueries
        val old = qi.quickWord(accountId, word).executeAsOneOrNull()
        qi.putQuickWord(
            accountId, word,
            change.kind?.name ?: old?.kind,
            change.categoryId ?: old?.category_id,
            change.accountId ?: old?.fin_account_id,
            change.secondAccountId ?: old?.second_fin_account_id,
            clock.wallMillis(),
        )
    }

    /** Метки по названиям («#работа»): существующая любого вида, иначе новая тема. */
    fun tagIdsFor(accountId: String, names: List<String>): Set<String> {
        if (names.isEmpty()) return emptySet()
        val existing = tags(accountId)
        return names.mapTo(LinkedHashSet()) { name ->
            existing.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id ?: createTag(accountId, name, TagKind.TOPIC).id
        }
    }

    fun totals(accountId: String, fromMillis: Long, toMillis: Long): Totals {
        val r = q.totalsInRange(accountId, fromMillis, toMillis).executeAsOne()
        return Totals(Money.rub(r.income_minor), Money.rub(r.expense_minor))
    }

    private fun setTags(accountId: String, txnId: String, wanted: Set<String>, hlc: Hlc) {
        val current = q.tagIdsOfTxn(txnId).executeAsList().toSet()
        for (tagId in wanted - current) {
            val linkId = "$txnId|$tagId"
            q.upsertTxnTag(linkId, accountId, txnId, tagId, hlc.toString(), 0)
            changeLog.record(accountId, T_TXN_TAG, linkId, hlc, mapOf("txn_id" to txnId, "tag_id" to tagId, "deleted" to "0"))
        }
        for (tagId in current - wanted) {
            val linkId = "$txnId|$tagId"
            q.upsertTxnTag(linkId, accountId, txnId, tagId, hlc.toString(), 1)
            changeLog.record(accountId, T_TXN_TAG, linkId, hlc, mapOf("deleted" to "1"))
        }
    }

    private fun validate(accountId: String, d: TransactionDraft) {
        if (d.kind == TxnKind.ADJUSTMENT) require(d.amountMinor != 0L) { "Сумма корректировки не может быть нулевой" }
        else require(d.amountMinor > 0) { "Сумма должна быть больше нуля" }
        val from = requireNotNull(q.finAccountById(d.finAccountId).executeAsOneOrNull()) { "Нет счёта ${d.finAccountId}" }
        require(from.account_id == accountId && from.deleted == 0L) { "Счёт не принадлежит аккаунту" }
        if (d.kind == TxnKind.TRANSFER) {
            val toId = requireNotNull(d.toFinAccountId) { "Не выбран счёт зачисления" }
            require(toId != d.finAccountId) { "Перевод на тот же счёт" }
            val to = requireNotNull(q.finAccountById(toId).executeAsOneOrNull()) { "Нет счёта $toId" }
            require(to.account_id == accountId && to.deleted == 0L) { "Счёт не принадлежит аккаунту" }
        }
    }

    private inline fun markDeleted(accountId: String, table: String, id: String, update: (String) -> Unit) {
        val hlc = clock.now()
        update(hlc.toString())
        changeLog.record(accountId, table, id, hlc, mapOf("deleted" to "1"))
    }

    private fun cleanName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        require(clean.isNotEmpty()) { "Пустое название" }
        require(clean.length <= 100) { "Название длиннее 100 символов" }
        return clean
    }

    private companion object {
        const val T_FIN_ACCOUNT = "fin_account"
        const val T_CATEGORY = "category"
        const val T_MERCHANT = "merchant"
        const val T_TAG = "tag"
        const val T_TXN = "txn"
        const val T_TXN_TAG = "txn_tag"
        const val KEY_CALC_ON_NEW = "finance.calc_on_new"

        fun flag(b: Boolean) = if (b) "1" else "0"
        fun normalize(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

        /** Шаблон LIKE «начинается с» с экранированием % и _. */
        fun likePrefix(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }
}
