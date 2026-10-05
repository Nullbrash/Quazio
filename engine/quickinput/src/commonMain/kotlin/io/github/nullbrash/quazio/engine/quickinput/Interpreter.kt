package io.github.nullbrash.quazio.engine.quickinput

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Смысл слов строки: вид, счета, категория, магазин, описание. Сумма и дата — уже из [Lexed]. */
internal class Interpretation(
    val kind: QuickKind,
    /** Вид задан словом-действием (забрал, перевод, долг) — такая строка может быть заголовком списка. */
    val hasAction: Boolean,
    val negated: Boolean,
    val accountId: String?,
    val secondAccountId: String?,
    val categoryId: String?,
    val merchant: String?,
    val description: String,
    val tags: List<String>,
    val keyWord: String?,
    val date: LocalDate?,
    /** Счёт назван в тексте («на карте», «наличными»), а не взят по умолчанию. */
    val accountNamed: Boolean,
    /** «не брал» — слово памяти для строк с отрицанием. */
    val negatedKey: String?,
)

internal class Interpreter(private val vocab: QuickVocabulary, private val today: LocalDate) {

    private val ownAccounts = vocab.accounts.filter { it.role != AccountRole.DEBT }
    private val debtAccounts = vocab.accounts.filter { it.role == AccountRole.DEBT }

    fun interpret(lexed: Lexed): Interpretation {
        val t = lexed.tokens
        val n = t.map { it.norm }
        fun has(word: String) = word in n

        val negIdx = n.indices.firstOrNull { n[it] == "не" && n.getOrNull(it + 1) in Lexicon.negatedVerbs }
        val negated = negIdx != null

        // Дата словами.
        var date = lexed.date
        t.forEachIndexed { i, tk ->
            val d = when (tk.norm) {
                "сегодня" -> today
                "вчера" -> today.minus(DatePeriod(days = 1))
                "позавчера" -> today.minus(DatePeriod(days = 2))
                else -> Lexicon.weekdays.indexOfFirst { tk.norm in it }.takeIf { it >= 0 }?.let { recentWeekday(it) }
            }
            if (d != null) {
                date = date ?: d
                tk.used = true
                if (i > 0 && t[i - 1].norm in setOf("в", "во")) t[i - 1].used = true
            }
        }

        val tags = t.filter { it.text.startsWith("#") && it.text.length > 1 }.onEach { it.used = true }.map { it.text.drop(1) }

        // Счета: «с карты», «на наличные», «наличными», «со сбера».
        var fromAcc: String? = null
        var toAcc: String? = null
        var plainAcc: String? = null
        t.forEachIndexed { i, tk ->
            if (tk.used) return@forEachIndexed
            val next = t.getOrNull(i + 1)
            when {
                tk.norm in setOf("с", "со") && next != null -> accountFor(next.norm)?.let { fromAcc = it; tk.used = true; next.used = true }
                tk.norm == "на" && next != null -> accountFor(next.norm)?.let { toAcc = it; tk.used = true; next.used = true }
                tk.norm == "по" && next != null && isCardWord(next.norm) -> accountFor(next.norm)?.let { plainAcc = it; tk.used = true; next.used = true }
                isInstrumentalAccount(tk.norm) -> accountFor(tk.norm)?.let { plainAcc = it; tk.used = true }
            }
        }

        // Слово памяти — первое значимое слово, до разбора действий.
        val keyWord = t.firstOrNull { !it.used && it.norm !in Lexicon.particles && it.norm.any(Char::isLetter) }?.norm
        val rule = keyWord?.let { vocab.wordRules[it] }

        var kind: QuickKind? = null
        var hasAction = false
        var account: String? = null
        var second: String? = null

        // Долги.
        val debtWord = n.any { it in setOf("долг", "долга", "взаймы") }
        val lendVerb = n.indexOfFirst { it.startsWith("одолжил") || it.startsWith("занял") }
        val gaveVerb = n.indexOfFirst { it in setOf("дал", "дала", "дали") }
        val tookVerb = n.indexOfFirst { it in setOf("взял", "взяла") }
        val returnVerb = n.indexOfFirst { it.startsWith("вернул") || it == "вернули" || (debtWord && it.startsWith("отдал")) }
        val fromPerson = has("у")
        when {
            !negated && gaveVerb >= 0 && debtWord -> kind = QuickKind.DEBT_OUT
            !negated && lendVerb >= 0 -> kind = if (fromPerson || n[lendVerb].startsWith("занял")) QuickKind.DEBT_IN else QuickKind.DEBT_OUT
            !negated && tookVerb >= 0 && debtWord -> kind = QuickKind.DEBT_IN
            !negated && returnVerb >= 0 -> {
                val nameBefore = (0 until returnVerb).any { isNameLike(t[it]) }
                kind = if (has("мне") || nameBefore) QuickKind.DEBT_IN else QuickKind.DEBT_OUT
            }
        }
        if (kind != null) {
            hasAction = true
            listOf(gaveVerb, lendVerb, tookVerb, returnVerb).filter { it >= 0 }.forEach { t[it].used = true }
            t.filter { it.norm in Lexicon.particles }.forEach { it.used = true }
            val person = t.filter { isNameLike(it) }
            val debt = person.firstNotNullOfOrNull { p -> debtAccounts.firstOrNull { matchesDebt(it, p.norm) }?.also { p.used = true } }
            second = debt?.id
            account = plainAcc ?: fromAcc ?: toAcc
        }

        // Переводы и «забрал».
        if (kind == null && !negated) {
            val transferIdx = n.indexOfFirst { it in Lexicon.transferWords }
            val withdrawIdx = n.indexOfFirst { it in Lexicon.withdrawWords }
            when {
                transferIdx >= 0 || (fromAcc != null && toAcc != null) -> {
                    if (transferIdx >= 0) t[transferIdx].used = true
                    kind = QuickKind.TRANSFER
                    hasAction = true
                    account = fromAcc ?: plainAcc
                    second = toAcc
                }
                withdrawIdx >= 0 -> {
                    // Забрал себе у кого-то: «у кати» — у кого; не сказано — единственный долг.
                    t[withdrawIdx].used = true
                    kind = QuickKind.DEBT_IN
                    hasAction = true
                    t.filter { it.norm in Lexicon.particles }.forEach { it.used = true }
                    val person = t.filter { isNameLike(it) }
                    val debt = if (person.isEmpty()) debtAccounts.singleOrNull()
                    else person.firstNotNullOfOrNull { p -> debtAccounts.firstOrNull { matchesDebt(it, p.norm) }?.also { p.used = true } }
                    second = debt?.id
                    account = toAcc ?: plainAcc
                }
            }
        }

        // Память слова: правка пользователя важнее словарей, но явно написанное в тексте — ещё важнее.
        if (rule?.kind != null) {
            val fromText = kind
            kind = rule.kind
            hasAction = hasAction || rule.kind != QuickKind.EXPENSE && rule.kind != QuickKind.INCOME
            if (fromText != null && fromText != rule.kind) {
                // Например, «забрал» запомнили как расход: «откуда» становится своим счётом.
                if (rule.kind == QuickKind.EXPENSE || rule.kind == QuickKind.INCOME) second = null
            }
        }
        if (rule != null) {
            if (fromAcc == null && plainAcc == null && rule.accountId != null) account = rule.accountId
            if (toAcc == null && rule.secondAccountId != null) second = rule.secondAccountId
        }

        // Магазин и категория — у расходов и доходов.
        var merchant: String? = null
        var categoryId: String? = null
        var categoryToken: Token? = null
        var categoryByName = false
        val signHint = when (lexed.sign) { 1 -> true; -1 -> false; else -> null }
        val incomeHint = signHint ?: if (n.any { it in Lexicon.incomeWords }) true else null
        if (kind == null || kind == QuickKind.EXPENSE || kind == QuickKind.INCOME) {
            val wantIncome = when (kind) { QuickKind.INCOME -> true; QuickKind.EXPENSE -> false; else -> incomeHint }
            findMerchant(t)?.let { (name, category) ->
                merchant = name
                if (category != null && fits(category, wantIncome)) categoryId = category
            }
            if (rule?.categoryId != null && fits(rule.categoryId, wantIncome)) categoryId = rule.categoryId
            if (categoryId == null) {
                findByName(t, wantIncome)?.let { (category, token) ->
                    categoryId = category.id; categoryToken = token; categoryByName = true
                }
            }
            if (categoryId == null) {
                findByKeyword(t, wantIncome)?.let { categoryId = it.id }
            }
            if (kind == null) {
                val category = vocab.categories.firstOrNull { it.id == categoryId }
                kind = when {
                    signHint == true -> QuickKind.INCOME
                    signHint == false -> QuickKind.EXPENSE
                    category != null -> if (category.income) QuickKind.INCOME else QuickKind.EXPENSE
                    incomeHint == true -> QuickKind.INCOME
                    else -> QuickKind.EXPENSE
                }
            }
            if (categoryId != null && !fits(categoryId, kind == QuickKind.INCOME)) categoryId = null
            if (kind == QuickKind.INCOME) t.filter { it.norm in Lexicon.incomeWords }.forEach { it.used = true }
            account = account ?: plainAcc ?: fromAcc ?: toAcc
        }

        if (kind == QuickKind.TRANSFER && second == account) second = null
        if (kind == QuickKind.TRANSFER && account == null) account = vocab.defaultAccountId?.takeIf { it != second }
        if (account == null && kind != QuickKind.TRANSFER) account = vocab.defaultAccountId
        if (kind == QuickKind.TRANSFER || kind == QuickKind.DEBT_IN || kind == QuickKind.DEBT_OUT) {
            t.filter { it.norm in Lexicon.particles }.forEach { it.used = true }
        }

        // Предлог на краю описания («кофе в» после «в 14:30») — остаток разобранного, не текст.
        val left = t.filter { !it.used }.dropWhile { it.norm in Lexicon.particles }.dropLastWhile { it.norm in Lexicon.particles }
        val description = when {
            left.isEmpty() -> ""
            categoryByName && left.singleOrNull() === categoryToken -> ""
            else -> left.joinToString(" ") { it.text }
        }
        return Interpretation(
            kind = kind, hasAction = hasAction, negated = negated,
            accountId = account, secondAccountId = second, categoryId = categoryId,
            merchant = merchant, description = description, tags = tags, keyWord = keyWord, date = date,
            accountNamed = fromAcc != null || toAcc != null || plainAcc != null,
            negatedKey = negIdx?.let { "не " + n[it + 1] },
        )
    }

    private fun recentWeekday(target: Int): LocalDate {
        val back = (today.dayOfWeek.ordinal - target + 7) % 7
        return today.minus(DatePeriod(days = back))
    }

    // ===== Счета =====

    private fun isCardWord(w: String) = Regex("^карт(а|у|ой|ы|е|очк\\p{L}*)$").matches(w)
    private fun isCashWord(w: String) = w.startsWith("налич") || w in setOf("нал", "налом", "наликом", "кэш", "кеш", "кэшем", "кешем", "cash")
    private fun isSavingsWord(w: String) = Lexicon.savingsWords.any { w.startsWith(it) }

    /** Слово само по себе называет счёт, которым платили: «наличными», «картой». */
    private fun isInstrumentalAccount(w: String) =
        // Голое название («ozon») — скорее магазин, чем счёт «Ozon Банк»: счёт — только с «с», «на», «по».
        w in setOf("наличными", "налом", "наликом", "кэшем", "кешем", "картой", "нал", "наличкой")

    private fun accountFor(w: String): String? {
        if (w.length < 3 && w != "нал") return null
        ownAccounts.filter { a -> nameWords(a.name).any { nameMatch(w, it) } }.singleOrNull()?.let { return it.id }
        val role = when {
            isCashWord(w) -> AccountRole.CASH
            isCardWord(w) -> AccountRole.CARD
            isSavingsWord(w) -> AccountRole.SAVINGS
            else -> return null
        }
        return ownAccounts.singleOrNull { it.role == role }?.id
    }

    private fun matchesDebt(a: QuickAccount, w: String): Boolean =
        listOfNotNull(a.counterparty, a.name).any { name -> nameWords(name).any { nameMatch(w, it) } }

    private fun isNameLike(tk: Token) =
        !tk.used && tk.norm.length >= 2 && tk.norm.all(Char::isLetter) && tk.norm !in Lexicon.particles &&
            !tk.norm.startsWith("вернул") && tk.norm !in setOf("дал", "дала", "дали", "взял", "взяла", "долг", "вернули")

    // ===== Магазины и категории =====

    private fun findMerchant(t: List<Token>): Pair<String, String?>? {
        val free = t.filter { !it.used && it.norm !in Lexicon.particles }
        // Сначала свои магазины (память): из двух слов, потом из одного.
        for (size in listOf(2, 1)) {
            for (i in 0..free.size - size) {
                val words = free.subList(i, i + size)
                val joined = words.joinToString(" ") { it.norm }
                val known = vocab.merchants.entries.firstOrNull { (name, _) ->
                    val nn = normalize(name)
                    nn == joined || (size == 1 && nn.length >= 5 && sameWord(nn, joined))
                }
                if (known != null) {
                    words.forEach { it.used = true }
                    return known.key to known.value
                }
            }
        }
        for (tk in free) {
            val brand = Lexicon.brands.firstOrNull { (aliases, _, _) -> aliases.any { it == tk.norm || (it.length >= 6 && sameWord(it, tk.norm)) } }
            if (brand != null) {
                tk.used = true
                return brand.second to vocab.categories.firstOrNull { it.key == brand.third }?.id
            }
        }
        // Незнакомое слово латиницей — скорее всего название магазина или сервиса.
        val latin = free.firstOrNull { tk -> tk.norm.length >= 3 && tk.norm.all { it in 'a'..'z' || it.isDigit() } && tk.norm.any { it in 'a'..'z' } }
        if (latin != null && Lexicon.keywords.none { (stem, _) -> latin.norm.startsWith(stem) }) {
            latin.used = true
            return latin.text.replaceFirstChar { it.uppercase() } to null
        }
        return null
    }

    private fun fits(categoryId: String, wantIncome: Boolean?): Boolean {
        val c = vocab.categories.firstOrNull { it.id == categoryId } ?: return false
        return wantIncome == null || c.income == wantIncome
    }

    private fun findByName(t: List<Token>, wantIncome: Boolean?): Pair<QuickCategory, Token>? {
        var best: Triple<Int, QuickCategory, Token>? = null
        for (tk in t) {
            if (tk.used || tk.norm.length < 3 || tk.norm in Lexicon.particles) continue
            for (c in vocab.categories) {
                if (wantIncome != null && c.income != wantIncome) continue
                val words = nameWords(c.name)
                if (words.isEmpty()) continue
                var score = when {
                    sameWord(tk.norm, words[0]) -> 3
                    words.drop(1).any { sameWord(tk.norm, it) } -> 1
                    else -> continue
                }
                if (c.leaf) score += 1
                if (tk.norm == words[0]) score += 1
                // При равенстве — расход: трат больше, чем доходов.
                score = score * 2 + if (!c.income) 1 else 0
                if (best == null || score > best.first) best = Triple(score, c, tk)
            }
        }
        return best?.let { it.second to it.third }
    }

    private fun findByKeyword(t: List<Token>, wantIncome: Boolean?): QuickCategory? {
        for (tk in t) {
            if (tk.used || tk.norm in Lexicon.particles) continue
            val key = Lexicon.keywords.firstOrNull { (stem, _) -> tk.norm.startsWith(stem) }?.second ?: continue
            val c = vocab.categories.firstOrNull { it.key == key } ?: continue
            if (wantIncome == null || c.income == wantIncome) return c
        }
        return null
    }
}

internal fun nameWords(name: String): List<String> = normalize(name).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }

/** Одно слово в разных формах: «аптека» / «аптеки», «подарок» / «подарки», «пятницу» / «пятница». */
internal fun sameWord(a: String, b: String): Boolean {
    if (a == b) return true
    val min = minOf(a.length, b.length)
    return min >= 4 && commonPrefix(a, b) >= maxOf(4, min - 2)
}

/** Имена склоняются сильнее: «катя» / «кате» / «катей», «сбербанк» / «сбербанку». */
internal fun nameMatch(a: String, b: String): Boolean {
    if (a == b) return true
    val min = minOf(a.length, b.length)
    return min >= 3 && commonPrefix(a, b) >= maxOf(3, min - 2)
}

private fun commonPrefix(a: String, b: String): Int {
    var i = 0
    while (i < a.length && i < b.length && a[i] == b[i]) i++
    return i
}
