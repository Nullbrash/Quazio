package io.github.nullbrash.quazio.engine.quickinput

import kotlinx.datetime.LocalDate

/**
 * Быстрый ввод: фраза («такси 450», «зарплата +85к») или пост из нескольких строк →
 * черновики операций, сверки «= …» и «Остаток: …», пропущенные строки.
 * Ничего не сохраняет и не обращается к базе: всё нужное приходит в [QuickVocabulary].
 */
object QuickParser {

    fun parse(text: String, vocab: QuickVocabulary, today: LocalDate): List<QuickLine> =
        Block(vocab, today, singleLine = text.lines().count { it.isNotBlank() } == 1).run(text)
}

/** Разбор поста сверху вниз: заголовок списка, повтор суммы следующей строкой, сверки. */
private class Block(private val vocab: QuickVocabulary, private val today: LocalDate, private val singleLine: Boolean) {

    private val interpreter = Interpreter(vocab, today)
    private val out = ArrayList<QuickLine>()
    /** «Забрал:» — действие для строк-сумм под ним, до пустой строки. */
    private var header: Interpretation? = null
    /** Под заголовком уже были строки: следующая пустая строка его закрывает. */
    private var headerUsed = false
    /** Строка «29 августа 2022 …» — дата для строк без своей даты. */
    private var contextDate: LocalDate? = null
    /** Сумма строк со знаком для сверки «= …». */
    private var running = 0L
    /** Строка, которую может повторить следующая строка-сумма («Забрал 1 500р.» → «- 1 500р.»). */
    private var restatable: Int? = null
    /** Счёт последней операции — о нём «Остаток: …». */
    private var focusAccount: String? = null

    fun run(text: String): List<QuickLine> {
        for (raw in text.lines()) {
            if (raw.isBlank()) {
                // «Забрал:», пустая строка, затем список — заголовок ещё действует.
                if (headerUsed) header = null
                restatable = null
                continue
            }
            line(LineLexer.lex(raw, today))
        }
        return out
    }

    private fun line(lx: Lexed) {
        when {
            lx.type == LineType.TOTAL -> {
                val expected = lx.amount?.let { it * (lx.sign ?: 1) }
                if (expected == null) {
                    skip(lx.text, null)
                } else {
                    out += QuickLine.Total(lx.text, expected, running)
                    running = expected
                }
                header = null
                restatable = null
            }
            lx.type == LineType.BALANCE -> {
                val amount = lx.amount
                if (amount == null) skip(lx.text, null) else {
                    val named = interpreter.interpret(lx).takeIf { it.accountNamed }?.accountId
                    out += QuickLine.Balance(lx.text, amount, named ?: focusAccount)
                }
                restatable = null
            }
            lx.dateOnly -> {
                contextDate = lx.date
                skip(lx.text, null)
                restatable = null
            }
            lx.tokens.isEmpty() && lx.amount != null -> amountOnly(lx, lx.amount)
            lx.amount == null -> wordsOnly(lx)
            else -> full(lx, lx.amount)
        }
    }

    /** Строка из одной суммы (и, может быть, даты). */
    private fun amountOnly(lx: Lexed, amount: Long) {
        val target = restatable
        val h = header
        when {
            target != null && !lx.datePrefix && canRestate(out[target], lx, amount) -> restate(target, lx, amount)
            h != null -> {
                val date = lx.date ?: h.date ?: contextDate ?: today
                operation(lx.text, h, amount, lx.sign, date, lx)
                headerUsed = true
                restatable = null
            }
            singleLine -> full(lx, amount)
            else -> skip(lx.text, lx.sign?.let { it * amount }).also { restatable = null }
        }
    }

    private fun wordsOnly(lx: Lexed) {
        val it = interpreter.interpret(lx)
        skip(lx.text, null)
        if (it.hasAction && !it.negated) {
            header = it
            headerUsed = false
            restatable = null
        } else {
            restatable = out.lastIndex
        }
    }

    private fun full(lx: Lexed, amount: Long) {
        val it = interpreter.interpret(lx)
        if (it.negated) {
            // «За 5-ое по 500р не брал» — не операция, но в сверке участвует (недобранное — в плюс).
            val signed = amount * (lx.sign ?: 1)
            skip(lx.text, signed)
            running += signed
            restatable = out.lastIndex
            return
        }
        operation(lx.text, it, amount, lx.sign, lx.date ?: it.date ?: contextDate ?: today, lx)
        restatable = out.lastIndex
    }

    private fun operation(text: String, it: Interpretation, amount: Long, sign: Int?, date: LocalDate, lx: Lexed) {
        val draft = QuickDraft(
            kind = it.kind, amountMinor = amount, date = date, time = lx.time,
            accountId = it.accountId, secondAccountId = it.secondAccountId, categoryId = it.categoryId,
            merchant = it.merchant, description = it.description, tags = it.tags, keyWord = it.keyWord,
        )
        val signed = sign?.let { s -> s * amount } ?: defaultSigned(it.kind, amount)
        out += QuickLine.Operation(text, draft, signed)
        running += signed
        focusAccount = it.accountId ?: focusAccount
    }

    /** Следующая строка повторила сумму предыдущей: уточняет знак и сумму, отдельной операцией не становится. */
    private fun restate(index: Int, lx: Lexed, amount: Long) {
        val text = out[index].text + "\n" + lx.text
        when (val prev = out[index]) {
            is QuickLine.Operation -> {
                val signed = lx.sign?.let { it * amount } ?: defaultSigned(prev.draft.kind, amount)
                running += signed - prev.signedMinor
                out[index] = QuickLine.Operation(text, prev.draft.copy(amountMinor = amount), signed)
            }
            is QuickLine.Skipped -> {
                val signed = amount * (lx.sign ?: 1)
                running += signed - (prev.signedMinor ?: 0)
                out[index] = QuickLine.Skipped(text, signed)
            }
            else -> skip(lx.text, null)
        }
        restatable = null
    }

    /** Повтор — та же сумма или явный знак («+ 2 000р.» под «не брал по 500р»); иначе это другая строка. */
    private fun canRestate(prev: QuickLine, lx: Lexed, amount: Long): Boolean {
        if (lx.sign != null) return true
        return when (prev) {
            is QuickLine.Operation -> prev.draft.amountMinor == amount
            is QuickLine.Skipped -> prev.signedMinor?.let { kotlin.math.abs(it) } == amount
            else -> false
        }
    }

    private fun skip(text: String, signed: Long?) {
        out += QuickLine.Skipped(text, signed)
    }

    private fun defaultSigned(kind: QuickKind, amount: Long) = when (kind) {
        QuickKind.INCOME, QuickKind.DEBT_IN -> amount
        QuickKind.EXPENSE, QuickKind.TRANSFER, QuickKind.DEBT_OUT -> -amount
    }
}
