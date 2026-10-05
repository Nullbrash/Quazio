package io.github.nullbrash.quazio.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Разрешение на календари устройства (Android); запрашивается только при включении календаря. */
interface CalendarAccess {
    fun granted(): Boolean

    /** Системный запрос; true — разрешили. */
    suspend fun request(): Boolean
}

/** Платформа даёт его через [QuazioApp]; null — разрешение не нужно или календарей нет. */
val LocalCalendarAccess = staticCompositionLocalOf<CalendarAccess?> { null }
