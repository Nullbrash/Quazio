package io.github.nullbrash.quazio.core.ui

import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import io.github.nullbrash.quazio.feature.calendar.PaymentMark
import io.github.nullbrash.quazio.feature.finance.OccurrenceState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Регулярные платежи слоем в календаре: в календари телефона не копируются (решение
 * пользователя). Как события на весь день — полночь UTC. Без суммы: календарь открыт без входа.
 * Блокирующий вызов — не из главного потока.
 */
internal fun paymentEvents(services: AppServices, from: LocalDate, toExclusive: LocalDate): List<CalendarEvent> {
    val accountId = services.accounts.current().id
    return services.finance.recurring.occurrences(accountId, from, toExclusive)
        .filter { it.state != OccurrenceState.SKIPPED }
        .map { o ->
            val start = LocalDateTime(o.date, LocalTime(0, 0)).toInstant(TimeZone.UTC).toEpochMilliseconds()
            CalendarEvent(
                eventId = "payment:${o.recurring.id}|${o.date}", calendarId = PAYMENTS_CALENDAR, title = o.recurring.name,
                start = start, end = start + 86_400_000L, allDay = true, timeZone = "UTC", color = PAYMENT_COLOR,
                location = null, recurring = true, instanceStart = start,
                payment = PaymentMark(o.recurring.id, waiting = o.state == OccurrenceState.PENDING),
            )
        }
}

internal const val PAYMENTS_CALENDAR = "payments"
internal const val PAYMENT_COLOR = 0xFFFFA000
