package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.calc_title
import io.github.nullbrash.quazio.feature.finance.AmountExpression
import io.github.nullbrash.quazio.feature.finance.formatAmountForEdit
import io.github.nullbrash.quazio.feature.finance.formatMoney
import io.github.nullbrash.quazio.core.model.Money
import org.jetbrains.compose.resources.stringResource

/**
 * Калькулятор суммы — раскладка как в Money Manager пользователя: `C ÷ × ⌫`, цифры, `− + =`,
 * `, 0 000 ✓`. На ПК — ещё и с клавиатуры: цифры, + - * /, запятая/точка, Backspace,
 * Enter — готово, Esc — отмена. Отрицательная сумма допустима только если [allowNegative].
 * [allowSign] — можно начать с «+» или «−»: окно операции выбирает по знаку доход/расход;
 * в [onDone] приходит и явный знак (null — знака не было).
 */
@Composable
internal fun CalculatorDialog(
    initial: String,
    allowNegative: Boolean,
    onDone: (minor: Long, explicitSign: Int?) -> Unit,
    onDismiss: () -> Unit,
    allowSign: Boolean = false,
) {
    var expr by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun press(key: String) {
        expr = when (key) {
            "C" -> ""
            "⌫" -> expr.dropLast(1)
            "=" -> AmountExpression.evaluate(expr)?.let(::formatAmountForEdit) ?: expr
            "000" -> if (expr.isEmpty() || expr.last() in AmountExpression.OPERATORS) expr else expr + "000"
            in AmountExpression.OPERATORS.map { it.toString() } -> {
                val op = key[0]
                when {
                    expr.isEmpty() -> if ((op == '−' && (allowNegative || allowSign)) || (op == '+' && allowSign)) op.toString() else expr
                    expr.last() in AmountExpression.OPERATORS || expr.last() == ',' -> expr.dropLast(1) + op
                    else -> expr + op
                }
            }
            "," -> {
                val lastNumber = expr.takeLastWhile { it !in AmountExpression.OPERATORS }
                if (',' in lastNumber) expr else expr + (if (lastNumber.isEmpty()) "0," else ",")
            }
            else -> if (expr.length < 40) expr + key else expr
        }
    }

    val result = AmountExpression.evaluate(expr)
    val sign = AmountExpression.explicitSign(expr)
    val done: () -> Unit = {
        if (result != null && (allowNegative || allowSign || result > 0) && (result != 0L || allowNegative)) onDone(result, sign)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp, modifier = Modifier.widthIn(max = 380.dp)) {
            Column(
                Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Enter, Key.NumPadEnter -> { done(); true }
                        Key.Escape -> { onDismiss(); true }
                        Key.Backspace -> { press("⌫"); true }
                        Key.Delete -> { press("C"); true }
                        else -> {
                            val ch = AmountExpression.normalizeOp(e.utf16CodePoint.toChar())
                            when {
                                ch.isDigit() -> { press(ch.toString()); true }
                                ch == ',' || ch == '.' -> { press(","); true }
                                ch in AmountExpression.OPERATORS -> { press(ch.toString()); true }
                                ch == '=' -> { press("="); true }
                                else -> false
                            }
                        }
                    }
                }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(Res.string.calc_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    expr.ifEmpty { "0" },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (AmountExpression.hasOperators(expr) && result != null) "= " + formatMoney(Money.rub(result)) else " ",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
                val rows = listOf(
                    listOf("C", "÷", "×", "⌫"),
                    listOf("7", "8", "9", "−"),
                    listOf("4", "5", "6", "+"),
                    listOf("1", "2", "3", "="),
                    listOf(",", "0", "000", "✓"),
                )
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            Box(Modifier.weight(1f).aspectRatio(1.6f)) {
                                FilledTonalButton(onClick = { if (k == "✓") done() else press(k) }, modifier = Modifier.fillMaxWidth().aspectRatio(1.6f)) {
                                    if (k == "✓") Icon(Icons.Filled.Check, contentDescription = null) else Text(k, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(Res.string.action_cancel)) }
            }
        }
    }
}
