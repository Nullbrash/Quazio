package io.github.nullbrash.quazio.feature.finance

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.accounts.DeviceClock
import io.github.nullbrash.quazio.core.db.QuazioDatabase
import io.github.nullbrash.quazio.core.model.Money
import io.github.nullbrash.quazio.engine.quickinput.QuickKind
import io.github.nullbrash.quazio.engine.quickinput.QuickLine
import io.github.nullbrash.quazio.engine.quickinput.QuickParser
import io.github.nullbrash.quazio.engine.quickinput.WordRule
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinanceServiceTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { QuazioDatabase.Schema.create(it) }
    private val db = QuazioDatabase(driver)
    private var wall = 1_790_000_000_000L
    private val clock = DeviceClock(db) { wall++ }
    private val accountId = AccountService(db, clock).initialize("ПК", "windows", "Я").id
    private val finance = FinanceService(db, clock).also { it.ensureDefaults(accountId) }
    private val tz = "Europe/Moscow"

    @AfterTest
    fun close() = driver.close()

    private fun cash() = finance.accounts(accountId).first { it.type == FinAccountType.CASH }

    private fun expense(amount: Long, account: String, at: Long = wall, merchant: String? = null, tags: Set<String> = emptySet(), category: String? = null) =
        finance.saveTransaction(accountId, TransactionDraft(kind = TxnKind.EXPENSE, amountMinor = amount, finAccountId = account,
            merchantName = merchant, tagIds = tags, categoryId = category, occurredAt = at, timeZone = tz))

    @Test
    fun defaultsAreCreatedOnceWithStableIds() {
        val tree = finance.categories(accountId)
        assertTrue(tree.size > 40)
        val groceries = tree.first { it.name == "Продукты" }
        assertEquals("$accountId:c:shops.groceries", groceries.id)
        assertEquals("Магазины / Продукты", groceries.path)
        assertEquals(0xFF2E7D32, groceries.color) // цвет группы «Магазины»
        finance.ensureDefaults(accountId)
        assertEquals(tree.size, finance.categories(accountId).size)
        assertEquals(1, finance.accounts(accountId).size)
        assertEquals("Наличные", cash().name)
    }

    @Test
    fun balancesTransfersAndTotal() {
        val card = finance.createAccount(accountId, "Сбер-Карта", FinAccountType.CARD, openingBalanceMinor = 100_000)
        val bonus = finance.createAccount(accountId, "Спасибо", FinAccountType.BONUS, openingBalanceMinor = 5_000)
        expense(45_000, card)
        finance.saveTransaction(accountId, TransactionDraft(kind = TxnKind.TRANSFER, amountMinor = 20_000, finAccountId = card,
            toFinAccountId = cash().id, occurredAt = wall, timeZone = tz))
        finance.saveTransaction(accountId, TransactionDraft(kind = TxnKind.ADJUSTMENT, amountMinor = -1_000, finAccountId = card,
            occurredAt = wall, timeZone = tz))

        val accounts = finance.accounts(accountId).associateBy { it.id }
        assertEquals(Money.rub(100_000 - 45_000 - 20_000 - 1_000), accounts.getValue(card).balance)
        assertEquals(Money.rub(20_000), accounts.getValue(cash().id).balance)
        assertFalse(accounts.getValue(bonus).includeInTotal) // бонусы учитываются, но не в итоге
        assertEquals(Money.rub(34_000 + 20_000), finance.total(accounts.values.toList()))
    }

    @Test
    fun totalsCountOnlyIncomeAndExpense() {
        val card = finance.createAccount(accountId, "Карта", FinAccountType.CARD)
        expense(30_000, card, at = 1_000)
        finance.saveTransaction(accountId, TransactionDraft(kind = TxnKind.INCOME, amountMinor = 100_000, finAccountId = card, occurredAt = 2_000, timeZone = tz))
        finance.saveTransaction(accountId, TransactionDraft(kind = TxnKind.TRANSFER, amountMinor = 10_000, finAccountId = card,
            toFinAccountId = cash().id, occurredAt = 3_000, timeZone = tz))
        expense(99_999, card, at = 10_000) // вне периода
        val totals = finance.totals(accountId, 0, 5_000)
        assertEquals(Money.rub(100_000), totals.income)
        assertEquals(Money.rub(30_000), totals.expense)
        assertEquals(Money.rub(70_000), totals.net)
        assertEquals(3, finance.transactions(accountId, 0, 5_000).size)
    }

    @Test
    fun editLogsOnlyChangedFieldsAndTagsFollow() {
        val person = finance.createTag(accountId, "Катя", TagKind.PERSON)
        val topic = finance.createTag(accountId, "Отпуск", TagKind.TOPIC)
        assertEquals(person, finance.createTag(accountId, "катя", TagKind.PERSON)) // без дублей
        val id = expense(50_000, cash().id, merchant = "SPAR", tags = setOf(person.id))
        val before = db.syncOutboxQueries.count().executeAsOne()

        val draft = assertNotNull(finance.transaction(id))
        finance.saveTransaction(accountId, draft.copy(amountMinor = 55_000, tagIds = setOf(topic.id)))

        val log = db.syncOutboxQueries.forAccount(accountId).executeAsList().drop(before.toInt())
        assertEquals(listOf("txn" to "amount_minor", "txn_tag" to "txn_id", "txn_tag" to "tag_id", "txn_tag" to "deleted", "txn_tag" to "deleted"),
            log.map { it.table_name to it.field_ })
        val saved = finance.transactions(accountId, 0, Long.MAX_VALUE).single()
        assertEquals(Money.rub(55_000), saved.amount)
        assertEquals(listOf("Отпуск"), saved.tags.map { it.name })
        assertEquals("SPAR", saved.merchantName)
    }

    @Test
    fun merchantIsRememberedAndDrivesSuggestion() {
        val card = finance.createAccount(accountId, "Карта", FinAccountType.CARD)
        val coffee = finance.categories(accountId).first { it.name == "Кофе" }.id
        val tag = finance.createTag(accountId, "Абонемент", TagKind.TOPIC)
        expense(25_000, card, merchant = "  Coffee   Like ", tags = setOf(tag.id), category = coffee)
        expense(25_000, card, merchant = "coffee like", category = coffee)
        assertEquals(listOf("Coffee Like"), finance.merchantSuggestions(accountId, "cof"))

        val s = assertNotNull(finance.suggest(accountId, merchantName = "COFFEE LIKE", description = null))
        assertEquals(card, s.finAccountId)
        assertEquals(coffee, s.categoryId)
        assertNull(finance.suggest(accountId, merchantName = "Неизвестный", description = null))
    }

    @Test
    fun deletingCategoryRemovesSubtree() {
        val shops = finance.categories(accountId).first { it.name == "Магазины" }.id
        val spar = finance.addCategory(accountId, finance.categories(accountId).first { it.name == "Продукты" }.id, "SPAR", CategoryKind.EXPENSE)
        finance.deleteCategory(shops)
        val names = finance.categories(accountId).map { it.name }
        assertFalse("Магазины" in names || "Продукты" in names || "SPAR" in names)
        assertTrue("Транспорт" in names)
        assertFailsWith<IllegalArgumentException> { finance.updateCategory(spar, "SPAR", spar, null) }
    }

    @Test
    fun cannotMoveCategoryIntoItsOwnSubtree() {
        val tree = finance.categories(accountId)
        val shops = tree.first { it.name == "Магазины" }.id
        val groceries = tree.first { it.name == "Продукты" }.id
        assertFailsWith<IllegalArgumentException> { finance.updateCategory(shops, "Магазины", groceries, null) }
    }

    @Test
    fun accountWithTransactionsCanOnlyBeArchived() {
        val card = finance.createAccount(accountId, "Карта", FinAccountType.CARD)
        val empty = finance.createAccount(accountId, "Пустой", FinAccountType.CASH)
        expense(1_000, card)
        assertFailsWith<IllegalArgumentException> { finance.deleteAccount(card) }
        finance.deleteAccount(empty)
        assertFalse(finance.accounts(accountId).any { it.id == empty })
    }

    @Test
    fun balanceIsSetThroughAdjustmentOperations() {
        val debt = finance.createAccount(accountId, "Долг", FinAccountType.DEBT)
        // При создании: первая операция — «Начальный остаток».
        val first = finance.adjustBalanceTo(accountId, debt, -500_000, "Начальный остаток", 1_000, tz)
        assertNotNull(first)
        assertEquals(Money.rub(-500_000), finance.accounts(accountId).first { it.id == debt }.balance)
        // Позже: вводится итоговая цифра, создаётся корректировка на разницу.
        finance.adjustBalanceTo(accountId, debt, -200_000, "Корректировка баланса", 2_000, tz)
        assertEquals(Money.rub(-200_000), finance.accounts(accountId).first { it.id == debt }.balance)
        val ops = finance.transactions(accountId, 0, 10_000)
        assertEquals(listOf(300_000L, -500_000L), ops.map { it.amount.minor })
        assertTrue(ops.all { it.kind == TxnKind.ADJUSTMENT })
        // Та же цифра — ничего не создаётся.
        assertNull(finance.adjustBalanceTo(accountId, debt, -200_000, "Корректировка баланса", 3_000, tz))
        // Корректировки не входят в доходы и расходы.
        assertEquals(Money.rub(0), finance.totals(accountId, 0, 10_000).expense)
    }

    @Test
    fun tagsCanBeEditedAndDeleted() {
        val tag = finance.createTag(accountId, "Сбер", TagKind.PERSON)
        val id = expense(1_000, cash().id, tags = setOf(tag.id))
        finance.updateTag(tag.id, "Сбербанк", TagKind.ORGANIZATION)
        assertEquals(listOf(Tag(tag.id, "Сбербанк", TagKind.ORGANIZATION)), finance.tags(accountId))
        finance.deleteTag(tag.id)
        assertTrue(finance.tags(accountId).isEmpty())
        // Операция осталась, метка с неё исчезла.
        val op = finance.transactions(accountId, 0, Long.MAX_VALUE).single { it.id == id }
        assertTrue(op.tags.isEmpty())
    }

    @Test
    fun csvIsReadyForRussianExcel() {
        val coffee = finance.categories(accountId).first { it.name == "Кофе" }.id
        val person = finance.createTag(accountId, "Катя", TagKind.PERSON)
        // 05.10.2026 09:11 МСК
        finance.saveTransaction(accountId, TransactionDraft(kind = TxnKind.EXPENSE, amountMinor = 123_456, finAccountId = cash().id,
            categoryId = coffee, merchantName = "SPAR", tagIds = setOf(person.id), occurredAt = 1_791_180_660_000L, timeZone = tz,
            description = "кофе; с собой", note = "сказала \"спасибо\""))
        val csv = finance.exportCsv(accountId)
        assertTrue(csv.startsWith("\uFEFF"), "нет метки UTF-8")
        val lines = csv.removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(TransactionCsv.HEADER.joinToString(";"), lines[0])
        assertEquals(
            "05.10.2026;09:11;Расход;1234,56;RUB;Наличные;;Еда вне дома / Кофе;SPAR;Человек: Катя;\"кофе; с собой\";\"сказала \"\"спасибо\"\"\";Europe/Moscow;",
            lines[1].substringBeforeLast(';') + ";",
        )
    }

    @Test
    fun purgeRemovesEverythingOfTheAccount() {
        val card = finance.createAccount(accountId, "Карта", FinAccountType.CARD)
        finance.createTag(accountId, "Катя", TagKind.PERSON)
        expense(1_000, card, merchant = "SPAR")
        finance.purgeAccountData(accountId)
        assertTrue(finance.accounts(accountId).isEmpty())
        assertTrue(finance.categories(accountId).isEmpty())
        assertTrue(finance.tags(accountId).isEmpty())
        assertTrue(finance.transactions(accountId, Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
        assertTrue(finance.merchantSuggestions(accountId, "s").isEmpty())
    }

    @Test
    fun quickInputSeesCategoriesMerchantsAndMemory() {
        val card = finance.createAccount(accountId, "Карта", FinAccountType.CARD)
        val hosting = "$accountId:c:online.hosting"
        expense(30_000, card, merchant = "CraftHost", category = hosting)
        val v = finance.quickVocabulary(accountId)
        assertEquals(hosting, v.merchants["CraftHost"])
        assertEquals("online.hosting", v.categories.first { it.id == hosting }.key)
        assertFalse(v.categories.first { it.id == "$accountId:c:online" }.leaf)
        val draft = (QuickParser.parse("crafthost 300", v, LocalDate(2026, 10, 5)).single() as QuickLine.Operation).draft
        assertEquals("CraftHost", draft.merchant)
        assertEquals(hosting, draft.categoryId)

        // Правки сливаются по полям: второе запоминание не стирает первое.
        finance.rememberQuickWord(accountId, "забрал", WordRule(kind = QuickKind.EXPENSE))
        finance.rememberQuickWord(accountId, "забрал", WordRule(accountId = cash().id))
        assertEquals(WordRule(kind = QuickKind.EXPENSE, accountId = cash().id), finance.quickVocabulary(accountId).wordRules["забрал"])
        finance.purgeAccountData(accountId)
        assertTrue(finance.quickVocabulary(accountId).wordRules.isEmpty())
    }

    @Test
    fun quickInputTagsAndSource() {
        val ids = finance.tagIdsFor(accountId, listOf("работа", "Работа"))
        assertEquals(1, ids.size)
        assertEquals(ids, finance.tagIdsFor(accountId, listOf("РАБОТА")))
        val id = finance.saveTransaction(accountId, TransactionDraft(kind = TxnKind.EXPENSE, amountMinor = 6_400, finAccountId = cash().id,
            tagIds = ids, occurredAt = wall, timeZone = tz, source = TxnSource.QUICK_INPUT))
        assertEquals("quickinput", db.financeQueries.txnById(id).executeAsOne().source)
    }

    @Test
    fun invalidDraftsAreRejected() {
        assertFailsWith<IllegalArgumentException> { expense(0, cash().id) }
        assertFailsWith<IllegalArgumentException> {
            finance.saveTransaction(accountId, TransactionDraft(kind = TxnKind.TRANSFER, amountMinor = 100, finAccountId = cash().id,
                toFinAccountId = cash().id, occurredAt = wall, timeZone = tz))
        }
        assertFailsWith<IllegalArgumentException> { finance.createAccount(accountId, "   ", FinAccountType.CARD) }
    }
}
