package io.github.nullbrash.quazio.core.ui

import io.github.nullbrash.quazio.feature.calendar.CalendarPrefs
import io.github.nullbrash.quazio.feature.calendar.ical.LinkedCalendars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Когда ПК загружает календари по ссылкам (решение пользователя): при открытии календаря, затем
 * раз в 15 минут, пока Quazio открыт, и по кнопке «Обновить». Календарь не открывали — сети нет.
 */
class CalendarRefresher(
    private val links: LinkedCalendars,
    private val prefs: CalendarPrefs,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)
    private val _revision = MutableStateFlow(0)

    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Растёт после загрузки, изменившей данные или ошибки, — экран перечитывает события. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** Загрузить, если с прошлой попытки прошло не меньше [minAgeMs]; 0 — сразу (кнопка). */
    suspend fun refresh(minAgeMs: Long = 0) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val last = links.lastAttempt
            if (last != null && now() - last < minAgeMs) return@withLock
            if (!prefs.enabled || links.links().isEmpty()) return@withLock
            _running.value = true
            try {
                if (links.refresh()) _revision.value++
            } finally {
                _running.value = false
            }
        }
    }

    /** Открытие календаря; частые переключения вкладок не дёргают сеть каждый раз. */
    suspend fun onCalendarOpened() = refresh(minAgeMs = ON_OPEN_MIN_AGE_MS)

    /** Пока жив процесс (окно Quazio открыто): раз в 15 минут, если календарь уже открывали. */
    suspend fun runWhileOpen() {
        while (true) {
            delay(CHECK_EVERY_MS)
            if (links.lastAttempt != null) refresh(minAgeMs = PERIOD_MS)
        }
    }

    /** Ссылки поменяли в настройках — экран перечитывает список календарей. */
    fun changed() {
        _revision.value++
    }

    companion object {
        const val PERIOD_MS = 15 * 60_000L
        private const val ON_OPEN_MIN_AGE_MS = 60_000L
        private const val CHECK_EVERY_MS = 60_000L
    }
}

/** Оболочка даёт его на ПК (календари по ссылкам); null — источник сам знает изменения (Android). */
val LocalCalendarRefresher = androidx.compose.runtime.staticCompositionLocalOf<CalendarRefresher?> { null }
