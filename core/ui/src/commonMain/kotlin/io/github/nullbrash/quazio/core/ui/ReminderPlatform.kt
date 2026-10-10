package io.github.nullbrash.quazio.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Что умеет платформа для напоминаний. Android — точные будильники системы, уведомления,
 * будильник на весь экран; ПК — уведомления Windows, пока окно открыто.
 */
interface ReminderPlatform {
    /** Будильник на весь экран и постоянное уведомление — только на телефоне. */
    val phone: Boolean

    /** Пересчитать, когда проснуться: поменялись платежи, настройки или профили. */
    fun reschedule()

    /** Разрешение на уведомления (Android 13+); спрашивается при включении напоминаний. */
    suspend fun ensureNotifications(): Boolean = true

    /** Можно ли показывать будильник на весь экран (Android 14 спрашивает отдельно). */
    fun canFullScreen(): Boolean = true

    fun openFullScreenSettings() {}

    /** Выбор мелодии телефона; null — отменили, "" — «по умолчанию». */
    suspend fun pickSound(current: String?, alarm: Boolean): String? = null

    fun soundName(uri: String?, alarm: Boolean): String? = null

    /** Система не даёт работать в фоне (у Samsung — «спящие» приложения): будильники откладываются. */
    fun backgroundRestricted(): Boolean = false

    fun openBackgroundSettings() {}
}

val LocalReminderPlatform = staticCompositionLocalOf<ReminderPlatform?> { null }

/** Перерисовать виджеты на рабочем столе (поменялись их настройки); null — виджетов нет (ПК). */
val LocalWidgetUpdater = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Картинка виджета с цветами [DialStyle] и стороной в пикселях — для превью в настройках;
 * рисует платформа тем же кодом, что виджет. Блокирующая — из фона. null — виджетов нет (ПК).
 */
val LocalWidgetPreview = staticCompositionLocalOf<((io.github.nullbrash.quazio.feature.calendar.DialStyle, Int) -> androidx.compose.ui.graphics.ImageBitmap?)?> { null }

/** Куда открыть Quazio по нажатию на уведомление: «Записать» платёж, день календаря. */
sealed interface OpenRequest {
    data class RecordPayment(val recurringId: String, val date: kotlinx.datetime.LocalDate) : OpenRequest
    data class CalendarDay(val date: kotlinx.datetime.LocalDate) : OpenRequest
    /** «+» на виджете — окно нового события. */
    data object NewEvent : OpenRequest
}

class OpenRequests {
    private val pending = MutableStateFlow<OpenRequest?>(null)
    val request: StateFlow<OpenRequest?> = pending

    fun offer(value: OpenRequest?) {
        if (value != null) pending.value = value
    }

    fun consume() {
        pending.value = null
    }
}

val LocalOpenRequests = staticCompositionLocalOf<OpenRequests?> { null }
