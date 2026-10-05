package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.acc_type_bonus
import io.github.nullbrash.quazio.core.ui.res.acc_type_card
import io.github.nullbrash.quazio.core.ui.res.acc_type_cash
import io.github.nullbrash.quazio.core.ui.res.acc_type_credit_card
import io.github.nullbrash.quazio.core.ui.res.acc_type_debt
import io.github.nullbrash.quazio.core.ui.res.acc_type_ewallet
import io.github.nullbrash.quazio.core.ui.res.acc_type_savings
import io.github.nullbrash.quazio.core.ui.res.acc_type_site_balance
import io.github.nullbrash.quazio.core.ui.res.tag_organization
import io.github.nullbrash.quazio.core.ui.res.tag_person
import io.github.nullbrash.quazio.core.ui.res.tag_topic
import io.github.nullbrash.quazio.core.ui.res.txn_adjustment
import io.github.nullbrash.quazio.core.ui.res.txn_expense
import io.github.nullbrash.quazio.core.ui.res.txn_income
import io.github.nullbrash.quazio.core.ui.res.txn_transfer
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.TagKind
import io.github.nullbrash.quazio.feature.finance.TxnKind
import io.github.nullbrash.quazio.feature.finance.YearMonth
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal val FinAccountType.label: StringResource
    get() = when (this) {
        FinAccountType.CARD -> Res.string.acc_type_card
        FinAccountType.SAVINGS -> Res.string.acc_type_savings
        FinAccountType.CASH -> Res.string.acc_type_cash
        FinAccountType.EWALLET -> Res.string.acc_type_ewallet
        FinAccountType.SITE_BALANCE -> Res.string.acc_type_site_balance
        FinAccountType.BONUS -> Res.string.acc_type_bonus
        FinAccountType.CREDIT_CARD -> Res.string.acc_type_credit_card
        FinAccountType.DEBT -> Res.string.acc_type_debt
    }

internal val TxnKind.label: StringResource
    get() = when (this) {
        TxnKind.EXPENSE -> Res.string.txn_expense
        TxnKind.INCOME -> Res.string.txn_income
        TxnKind.TRANSFER -> Res.string.txn_transfer
        TxnKind.ADJUSTMENT -> Res.string.txn_adjustment
    }

internal val TagKind.label: StringResource
    get() = when (this) {
        TagKind.TOPIC -> Res.string.tag_topic
        TagKind.PERSON -> Res.string.tag_person
        TagKind.ORGANIZATION -> Res.string.tag_organization
    }

private val MONTHS = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
private val MONTHS_GENITIVE = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
private val WEEKDAYS = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")

internal fun monthTitle(m: YearMonth) = "${MONTHS[m.month - 1]} ${m.year}"

@OptIn(ExperimentalTime::class)
internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

internal fun currentZone(): TimeZone = TimeZone.currentSystemDefault()

@OptIn(ExperimentalTime::class)
internal fun localDateTime(millis: Long, zone: String): LocalDateTime =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(runCatching { TimeZone.of(zone) }.getOrElse { currentZone() })

@OptIn(ExperimentalTime::class)
internal fun toMillis(dt: LocalDateTime, zone: TimeZone): Long = dt.toInstant(zone).toEpochMilliseconds()

internal fun currentMonth(): YearMonth = localDateTime(nowMillis(), currentZone().id).let { YearMonth(it.year, it.month.ordinal + 1) }

/** «5 октября, воскресенье». */
internal fun dayTitle(dt: LocalDateTime) = "${dt.day} ${MONTHS_GENITIVE[dt.month.ordinal]}, ${WEEKDAYS[dt.dayOfWeek.ordinal]}"

internal fun dateText(dt: LocalDateTime) =
    "${dt.day.toString().padStart(2, '0')}.${(dt.month.ordinal + 1).toString().padStart(2, '0')}.${dt.year}"

internal fun timeText(dt: LocalDateTime) = "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"

internal fun colorOf(argb: Long?): Color = argb?.let { Color(it.toInt()) } ?: Color(0xFF9E9E9E)

@Composable
internal fun ColorDot(argb: Long?, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(colorOf(argb), CircleShape))
}

/**
 * Однострочное поле, которое подтверждается Enter и «Готово» (правило для всех диалогов).
 * Клавиатура — обычная (кириллица), в отличие от поля пароля.
 */
@Composable
internal fun FinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    isError: Boolean = false,
    onSubmit: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = modifier.onPreviewKeyEvent { e ->
            val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
            if (singleLine && enter && e.type == KeyEventType.KeyDown) { onSubmit(); true } else false
        },
    )
}
