package io.github.nullbrash.quazio.engine.quickinput

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Фразы из обсуждения фазы 5 (черновик ассистента, одобренный пользователем) и посты
 * в стиле ручного учёта пользователя — с изменёнными суммами и датами.
 */
class QuickParserTest {

    // Понедельник.
    private val today = LocalDate(2026, 10, 5)

    private fun cat(key: String, name: String, income: Boolean = false, leaf: Boolean = true) =
        QuickCategory("c:$key", name, income, key, leaf)

    private val categories = listOf(
        cat("shops", "Магазины", leaf = false), cat("shops.groceries", "Продукты"), cat("shops.pharmacy", "Аптеки"),
        cat("shops.electronics", "Электроника"),
        cat("online", "Интернет-покупки", leaf = false), cat("online.marketplaces", "Маркетплейсы"),
        cat("online.hosting", "Хостинг и онлайн-сервисы"), cat("online.telecom", "Связь и интернет"),
        cat("food_out", "Еда вне дома", leaf = false), cat("food_out.restaurants", "Кафе и рестораны"), cat("food_out.coffee", "Кофе"),
        cat("transport", "Транспорт", leaf = false), cat("transport.public", "Метро и автобус"), cat("transport.taxi", "Такси"),
        cat("housing.internet", "Интернет дома"),
        cat("gifts", "Подарки и донаты", leaf = false), cat("gifts.gifts", "Подарки"),
        cat("salary", "Зарплата", income = true), cat("gifts_in", "Подарки", income = true),
        cat("cashback", "Кешбэк и проценты", income = true),
    )
    private val card = QuickAccount("card", "Карта Т-Банк", AccountRole.CARD)
    private val cash = QuickAccount("cash", "Наличные", AccountRole.CASH)
    private val savings = QuickAccount("savings", "Копилка", AccountRole.SAVINGS)
    private val katya = QuickAccount("debt-katya", "Долг Кати", AccountRole.DEBT, counterparty = "Катя")
    private val vocab = QuickVocabulary(accounts = listOf(card, cash, savings, katya), categories = categories)

    private fun one(text: String, v: QuickVocabulary = vocab): QuickDraft {
        val lines = QuickParser.parse(text, v, today)
        assertEquals(1, lines.size, "строк: $lines")
        return assertIs<QuickLine.Operation>(lines.single()).draft
    }

    private fun rub(r: Long) = r * 100

    @Test
    fun simpleExpenses() {
        one("такси 450").let {
            assertEquals(QuickKind.EXPENSE, it.kind)
            assertEquals(rub(450), it.amountMinor)
            assertEquals("c:transport.taxi", it.categoryId)
            assertEquals("card", it.accountId)
            assertEquals(today, it.date)
            assertEquals("", it.description)
            assertEquals("такси", it.keyWord)
        }
        assertEquals("c:transport.taxi", one("450 такси").categoryId)
        one("кофе 180 вчера").let {
            assertEquals("c:food_out.coffee", it.categoryId)
            assertEquals(LocalDate(2026, 10, 4), it.date)
        }
        one("аптека 890 позавчера").let {
            assertEquals("c:shops.pharmacy", it.categoryId)
            assertEquals(LocalDate(2026, 10, 3), it.date)
        }
        one("метро 64 #работа").let {
            assertEquals("c:transport.public", it.categoryId)
            assertEquals(listOf("работа"), it.tags)
            assertEquals("", it.description)
        }
    }

    @Test
    fun amountsInDifferentForms() {
        assertEquals(123456, one("пятёрочка 1234,56").amountMinor)
        assertEquals(rub(2500), one("продукты 2,5к в пятницу").amountMinor)
        assertEquals(rub(85_000), one("зарплата +85к").amountMinor)
        assertEquals(rub(1500), one("Забрал 1 500р.").amountMinor)
        assertEquals(rub(12_000), one("ремонт 12 тыс").amountMinor)
        assertEquals(rub(990), one("подписка 990 ₽").amountMinor)
        assertEquals(rub(2500), one("пицца 5 × 500").amountMinor)
    }

    @Test
    fun datesInDifferentForms() {
        assertEquals(LocalDate(2026, 10, 2), one("продукты 2,5к в пятницу").date)
        one("12.09 подарок маме 3000").let {
            assertEquals(LocalDate(2026, 9, 12), it.date)
            assertEquals(rub(3000), it.amountMinor)
            assertEquals("c:gifts.gifts", it.categoryId)
            assertEquals("подарок маме", it.description)
        }
        assertEquals(LocalDate(2026, 9, 14), one("обед 400 14 сентября").date)
        // Дата без года из будущего — это прошлый год.
        assertEquals(LocalDate(2025, 12, 28), one("28.12 ёлка 2000").date)
        one("кофе 200 в 14:30").let {
            assertEquals(LocalTime(14, 30), it.time)
            assertEquals(rub(200), it.amountMinor)
        }
        assertEquals(today, one("сегодня обед 350").date)
    }

    @Test
    fun merchantsAndCategories() {
        one("пятёрочка 1234,56").let {
            assertEquals("Пятёрочка", it.merchant)
            assertEquals("c:shops.groceries", it.categoryId)
            assertEquals("", it.description)
        }
        one("ozon 1990 наушники").let {
            assertEquals("Ozon", it.merchant)
            assertEquals("c:online.marketplaces", it.categoryId)
            assertEquals("наушники", it.description)
        }
        one("хостинг 300 crafthost").let {
            assertEquals("Crafthost", it.merchant)
            assertEquals("c:online.hosting", it.categoryId)
            assertEquals("", it.description)
        }
        one("обед 350 наличными").let {
            assertEquals("c:food_out.restaurants", it.categoryId)
            assertEquals("cash", it.accountId)
            assertEquals("обед", it.description)
        }
        // Знакомый магазин из прошлых операций — с его категорией и в его написании.
        val known = QuickVocabulary(accounts = vocab.accounts, categories = categories, merchants = mapOf("CraftHost" to "c:online.hosting"))
        one("crafthost 300", known).let {
            assertEquals("CraftHost", it.merchant)
            assertEquals("c:online.hosting", it.categoryId)
        }
    }

    @Test
    fun income() {
        one("зарплата +85к").let {
            assertEquals(QuickKind.INCOME, it.kind)
            assertEquals("c:salary", it.categoryId)
            assertEquals("card", it.accountId)
        }
        one("+1500 кешбэк").let {
            assertEquals(QuickKind.INCOME, it.kind)
            assertEquals("c:cashback", it.categoryId)
        }
        // «Подарки» есть и в расходах, и в доходах: знак решает.
        assertEquals("c:gifts_in", one("+3000 подарок от бабушки").categoryId)
        assertEquals(QuickKind.INCOME, one("получил 5000").kind)
    }

    @Test
    fun transfers() {
        one("перевод 5000 с карты на наличные").let {
            assertEquals(QuickKind.TRANSFER, it.kind)
            assertEquals("card", it.accountId)
            assertEquals("cash", it.secondAccountId)
            assertEquals("", it.description)
        }
        // «Забрал» — с накоплений на основной счёт.
        one("Забрал 1 500р.").let {
            assertEquals(QuickKind.TRANSFER, it.kind)
            assertEquals("savings", it.accountId)
            assertEquals("card", it.secondAccountId)
            assertEquals("забрал", it.keyWord)
        }
        assertEquals("cash", one("забрал 1000 на наличные").secondAccountId)
        // Накоплений нет — «откуда» остаётся пустым, выберет пользователь.
        val noSavings = QuickVocabulary(accounts = listOf(card, cash), categories = categories)
        one("забрал 1000", noSavings).let {
            assertEquals(QuickKind.TRANSFER, it.kind)
            assertNull(it.accountId)
            assertEquals("card", it.secondAccountId)
        }
    }

    @Test
    fun debts() {
        one("дал в долг кате 5000").let {
            assertEquals(QuickKind.DEBT_OUT, it.kind)
            assertEquals("debt-katya", it.secondAccountId)
            assertEquals("card", it.accountId)
            assertEquals("", it.description)
        }
        one("катя вернула 2000").let {
            assertEquals(QuickKind.DEBT_IN, it.kind)
            assertEquals("debt-katya", it.secondAccountId)
        }
        one("вернул кате 1000 наличными").let {
            assertEquals(QuickKind.DEBT_OUT, it.kind)
            assertEquals("debt-katya", it.secondAccountId)
            assertEquals("cash", it.accountId)
        }
        assertEquals(QuickKind.DEBT_IN, one("занял у кати 3000").kind)
        // Долга с таким человеком нет — имя остаётся в описании, долг выберут в окне операции.
        one("дал в долг пете 700").let {
            assertEquals(QuickKind.DEBT_OUT, it.kind)
            assertNull(it.secondAccountId)
            assertEquals("пете", it.description)
        }
    }

    @Test
    fun correctedWordIsRemembered() {
        // Пользователь поправил черновик «забрал» на расход с копилки — дальше так же.
        val learned = QuickVocabulary(
            accounts = vocab.accounts, categories = categories,
            wordRules = mapOf("забрал" to WordRule(kind = QuickKind.EXPENSE, accountId = "savings")),
        )
        one("забрал 700", learned).let {
            assertEquals(QuickKind.EXPENSE, it.kind)
            assertEquals("savings", it.accountId)
            assertNull(it.secondAccountId)
        }
        // Явно написанное в тексте важнее памяти.
        val taxi = QuickVocabulary(
            accounts = vocab.accounts, categories = categories,
            wordRules = mapOf("такси" to WordRule(accountId = "card", categoryId = "c:transport.public")),
        )
        one("такси 300 наличными", taxi).let {
            assertEquals("cash", it.accountId)
            assertEquals("c:transport.public", it.categoryId)
        }
    }

    @Test
    fun postWithRestatedAmountAndNorm() {
        val lines = QuickParser.parse(
            """
            Забрал 2 000р.
            - 2 000р

            За 7-ое по 500р не брал.
            + 500р.

            = - 1 500р.

            Остаток: 61 000р.
            """.trimIndent(),
            vocab, today,
        )
        assertEquals(4, lines.size, "$lines")
        val op = assertIs<QuickLine.Operation>(lines[0])
        assertEquals(QuickKind.TRANSFER, op.draft.kind)
        assertEquals(rub(2000), op.draft.amountMinor)
        assertEquals("savings", op.draft.accountId)
        assertEquals(-rub(2000), op.signedMinor)
        assertEquals("Забрал 2 000р.\n- 2 000р", op.text)
        val norm = assertIs<QuickLine.Skipped>(lines[1])
        assertEquals(rub(500), norm.signedMinor)
        assertEquals(SkipReason.NEGATED, norm.reason)
        val total = assertIs<QuickLine.Total>(lines[2])
        assertEquals(-rub(1500), total.expectedMinor)
        assertTrue(total.matches, "$total")
        val balance = assertIs<QuickLine.Balance>(lines[3])
        assertEquals(rub(61_000), balance.amountMinor)
        assertEquals("savings", balance.accountId)
    }

    @Test
    fun postWithMultiplicationLine() {
        val lines = QuickParser.parse(
            """
            Забрал 1 200.
            - 1 200р.

            За 10-13-ое по 500р не брал.
            4 × 500 = + 2 000р.

            = + 800р.
            """.trimIndent(),
            vocab, today,
        )
        assertEquals(3, lines.size, "$lines")
        assertEquals(rub(1200), assertIs<QuickLine.Operation>(lines[0]).draft.amountMinor)
        assertEquals(rub(2000), assertIs<QuickLine.Skipped>(lines[1]).signedMinor)
        assertTrue(assertIs<QuickLine.Total>(lines[2]).matches)
    }

    @Test
    fun postWithDatedListUnderHeader() {
        val lines = QuickParser.parse(
            """
            Забрал:

            14.07: - 2 000р.
            18.07: - 500р.
            21.07: - 700р.

            = - 3 200р.

            За 13.07-21.07 по 500р не брал.
            + 4 500р.

            = + 1 300р.

            Остаток: 52 000р.
            """.trimIndent(),
            vocab, today,
        )
        // Как в посте пользователя: пустая строка после «Забрал:» заголовок не закрывает.
        val drafts = lines.filterIsInstance<QuickLine.Operation>().map { it.draft }
        assertEquals(listOf(rub(2000), rub(500), rub(700)), drafts.map { it.amountMinor })
        assertEquals(listOf(LocalDate(2026, 7, 14), LocalDate(2026, 7, 18), LocalDate(2026, 7, 21)), drafts.map { it.date })
        assertTrue(drafts.all { it.kind == QuickKind.TRANSFER && it.accountId == "savings" })
        val totals = lines.filterIsInstance<QuickLine.Total>()
        assertEquals(2, totals.size)
        assertTrue(totals.all { it.matches }, "$totals")
        assertEquals("savings", lines.filterIsInstance<QuickLine.Balance>().single().accountId)
    }

    @Test
    fun totalThatDoesNotMatchIsReported() {
        val lines = QuickParser.parse("кофе 200\nтакси 300\n= - 600р.", vocab, today)
        val total = assertIs<QuickLine.Total>(lines.last())
        assertEquals(-rub(500), total.actualMinor)
        assertEquals(false, total.matches)
    }

    @Test
    fun monthlySummaryIsNotTurnedIntoOperations() {
        val lines = QuickParser.parse(
            """
            30 сентября 2026 18 05 41
            1) +64 300 ₽
            🍥Общий приход

            2) +64 300  ₽
            3) 64 300 ₽
            """.trimIndent(),
            vocab, today,
        )
        assertTrue(lines.none { it is QuickLine.Operation }, "$lines")
        assertEquals(SkipReason.DATE, assertIs<QuickLine.Skipped>(lines[0]).reason)
    }

    @Test
    fun templateWithZeroAmountsIsSkipped() {
        val lines = QuickParser.parse("Образец:\n\n1) +- 000 ₽\n(Эта сумма берётся из того, что было снято)\n3) 000 ₽", vocab, today)
        assertTrue(lines.all { it is QuickLine.Skipped }, "$lines")
    }

    @Test
    fun bareAmountIsAnExpenseOnlyAsASinglePhrase() {
        assertEquals(QuickKind.EXPENSE, one("450").kind)
        assertEquals(QuickKind.INCOME, one("+450").kind)
        assertTrue(QuickParser.parse("кофе 200\n450", vocab, today).let { it[1] is QuickLine.Skipped || it.size == 1 })
    }
}
