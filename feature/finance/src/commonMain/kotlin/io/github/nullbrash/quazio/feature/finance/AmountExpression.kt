package io.github.nullbrash.quazio.feature.finance

/**
 * Калькулятор суммы (по образцу Wallet пользователя): «350+120×2», «1 200,50−99».
 * Операции + − × ÷ с обычным приоритетом, без скобок. Числа — с запятой или точкой.
 * Счёт — в сотых долях на Long, без Double: деньги не должны терять копейки;
 * при × и ÷ результат округляется до копейки (половина — вверх).
 * Возвращает копейки или null, если выражение неполное или неверное.
 */
object AmountExpression {

    const val OPERATORS = "+−×÷"

    fun evaluate(expression: String): Long? {
        val tokens = tokenize(expression) ?: return null
        if (tokens.isEmpty() || tokens.size % 2 == 0) return null
        return try {
            // Сначала × и ÷, затем + и −.
            val terms = ArrayList<Long>()
            val signs = ArrayList<Char>()
            var current = tokens[0] as Long
            var i = 1
            while (i < tokens.size) {
                val op = tokens[i] as Char
                val n = tokens[i + 1] as Long
                when (op) {
                    '×' -> current = mul(current, n)
                    '÷' -> current = div(current, n) ?: return null
                    else -> { terms += current; signs += op; current = n }
                }
                i += 2
            }
            terms += current
            var result = terms[0]
            for (k in signs.indices) result = if (signs[k] == '+') add(result, terms[k + 1]) else add(result, neg(terms[k + 1]))
            result
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Есть ли в строке операции (тогда показываем предварительный результат). */
    fun hasOperators(expression: String): Boolean = expression.drop(1).any { normalizeOp(it) in OPERATORS }

    /** Приведение вводимых знаков к «калькуляторным»: * → ×, / и : → ÷, - и – → −. */
    fun normalizeOp(c: Char): Char = when (c) {
        '*', 'x', 'х' -> '×'
        '/', ':' -> '÷'
        '-', '–' -> '−'
        else -> c
    }

    private fun tokenize(s: String): List<Any>? {
        val out = ArrayList<Any>()
        val number = StringBuilder()
        fun flush(): Boolean {
            if (number.isEmpty()) return false
            val minor = parseAmountMinor(number.toString()) ?: return false
            out += minor
            number.clear()
            return true
        }
        for ((index, raw) in s.withIndex()) {
            val c = normalizeOp(raw)
            when {
                c.isDigit() || c == ',' || c == '.' -> number.append(c)
                c == ' ' || c == ' ' -> Unit
                c in OPERATORS -> {
                    // Минус в самом начале — знак числа, а не операция.
                    if (c == '−' && index == 0 && number.isEmpty() && out.isEmpty()) { number.append('-'); continue }
                    if (!flush()) return null
                    out += c
                }
                else -> return null
            }
        }
        if (!flush()) return null
        return out
    }

    private fun add(a: Long, b: Long): Long {
        val r = a + b
        if ((a xor r) and (b xor r) < 0) throw ArithmeticException()
        return r
    }

    private fun neg(a: Long): Long = if (a == Long.MIN_VALUE) throw ArithmeticException() else -a

    private fun mul(a: Long, b: Long): Long {
        if (a != 0L && (if (a > 0) a else -a) > Long.MAX_VALUE / maxOf(1, if (b > 0) b else -b)) throw ArithmeticException()
        return roundDiv(a * b, 100)
    }

    private fun div(a: Long, b: Long): Long? {
        if (b == 0L) return null
        if ((if (a > 0) a else -a) > Long.MAX_VALUE / 100) throw ArithmeticException()
        return roundDiv(a * 100, b)
    }

    /** Деление с округлением половины от нуля. */
    private fun roundDiv(n: Long, d: Long): Long {
        val q = n / d
        val r = n % d
        return if (2 * kotlin.math.abs(r) >= kotlin.math.abs(d)) q + (if ((n < 0) xor (d < 0)) -1 else 1) else q
    }
}
