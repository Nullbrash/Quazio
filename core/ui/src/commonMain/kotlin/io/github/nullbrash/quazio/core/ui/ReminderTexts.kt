package io.github.nullbrash.quazio.core.ui

import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.cal_tomorrow
import io.github.nullbrash.quazio.core.ui.res.rem_end_body
import io.github.nullbrash.quazio.core.ui.res.rem_channel_now
import io.github.nullbrash.quazio.core.ui.res.rem_channel_alarm
import io.github.nullbrash.quazio.core.ui.res.rem_channel_reminders
import io.github.nullbrash.quazio.core.ui.res.rem_dismiss
import io.github.nullbrash.quazio.core.ui.res.rem_snooze_action
import io.github.nullbrash.quazio.core.ui.res.rem_open
import io.github.nullbrash.quazio.core.ui.res.rem_record
import io.github.nullbrash.quazio.core.ui.res.rem_end_title
import io.github.nullbrash.quazio.core.ui.res.rem_now
import io.github.nullbrash.quazio.core.ui.res.rem_now_free
import io.github.nullbrash.quazio.core.ui.res.rem_now_next
import io.github.nullbrash.quazio.core.ui.res.rem_now_no_next
import io.github.nullbrash.quazio.core.ui.res.rem_pay_auto
import io.github.nullbrash.quazio.core.ui.res.rem_pay_before
import io.github.nullbrash.quazio.core.ui.res.rem_pay_body_ask
import io.github.nullbrash.quazio.core.ui.res.rem_pay_body_before
import io.github.nullbrash.quazio.core.ui.res.rem_pay_today
import io.github.nullbrash.quazio.core.ui.res.rem_pay_tomorrow
import io.github.nullbrash.quazio.core.ui.res.rem_sum_empty
import io.github.nullbrash.quazio.core.ui.res.rem_sum_events
import io.github.nullbrash.quazio.core.ui.res.rem_sum_payments
import io.github.nullbrash.quazio.core.ui.res.rem_sum_pending
import io.github.nullbrash.quazio.core.ui.res.rem_sum_title
import io.github.nullbrash.quazio.core.ui.screens.finance.shortDate
import io.github.nullbrash.quazio.feature.reminders.NowNext
import io.github.nullbrash.quazio.feature.reminders.Reminder
import io.github.nullbrash.quazio.feature.reminders.ReminderService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant

/**
 * Заголовок и текст уведомления — общие для телефона и ПК. Без сумм: уведомление видно,
 * не входя в Quazio, а суммы — под замком финансов (как в календаре — решение пользователя).
 */
suspend fun reminderText(r: Reminder, reminders: ReminderService): Pair<String, String> = when (r) {
    is Reminder.Payment -> {
        val title = when {
            r.daysBefore == 1 -> getString(Res.string.rem_pay_tomorrow, r.name)
            r.daysBefore > 1 -> getString(Res.string.rem_pay_before, r.daysBefore, r.name)
            !r.ask -> getString(Res.string.rem_pay_auto, r.name)
            else -> getString(Res.string.rem_pay_today, r.name)
        }
        val body = if (r.daysBefore > 0) getString(Res.string.rem_pay_body_before, shortDate(r.date))
        else if (r.ask) getString(Res.string.rem_pay_body_ask) else ""
        title to body
    }
    is Reminder.Summary -> {
        val day = reminders.summary(r.date)
        val z = TimeZone.currentSystemDefault()
        val parts = buildList {
            if (day.events.isNotEmpty()) add(getString(Res.string.rem_sum_events, day.events.take(5).joinToString(", ") { e ->
                if (e.allDay) e.title else hm(e.start, z) + " " + e.title
            }))
            if (day.payments.isNotEmpty()) add(getString(Res.string.rem_sum_payments, day.payments.joinToString(", ") { it.recurring.name }))
            if (day.pendingCount > 0) add(getString(Res.string.rem_sum_pending, day.pendingCount))
        }
        getString(Res.string.rem_sum_title) to (parts.joinToString("\n").ifEmpty { getString(Res.string.rem_sum_empty) })
    }
    is Reminder.EventEnd -> getString(Res.string.rem_end_title, r.minutes, r.title) to
        getString(Res.string.rem_end_body, hm(r.end, TimeZone.currentSystemDefault()))
}

/** Постоянное уведомление «что сейчас / что дальше». */
suspend fun nowNextText(nn: NowNext): Pair<String, String> {
    val z = TimeZone.currentSystemDefault()
    val title = nn.current?.let { getString(Res.string.rem_now, it.title, hm(it.end, z)) } ?: getString(Res.string.rem_now_free)
    val body = nn.next?.let { getString(Res.string.rem_now_next, it.title, hm(it.start, z)) } ?: getString(Res.string.rem_now_no_next)
    return title to body
}

private fun hm(millis: Long, z: TimeZone): String = Instant.fromEpochMilliseconds(millis).toLocalDateTime(z).let {
    "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
}

/** Подписи кнопок и каналов уведомлений — для кода платформы (тексты живут в ресурсах этого модуля). */
object ReminderLabels {
    suspend fun record() = getString(Res.string.rem_record)
    suspend fun open() = getString(Res.string.rem_open)
    suspend fun snooze() = getString(Res.string.rem_snooze_action)
    suspend fun dismiss() = getString(Res.string.rem_dismiss)
    suspend fun channelReminders() = getString(Res.string.rem_channel_reminders)
    suspend fun channelAlarm() = getString(Res.string.rem_channel_alarm)
    suspend fun channelNow() = getString(Res.string.rem_channel_now)
}

/** Подписи виджета-циферблата для кода платформы. */
object WidgetLabels {
    suspend fun tomorrow() = getString(Res.string.cal_tomorrow)
}
