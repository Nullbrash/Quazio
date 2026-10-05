package io.github.nullbrash.quazio.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Текст, присланный в Quazio извне — «В учёт Quazio» в меню выделенного текста или
 * «Поделиться» на Android. Финансы разбирают его быстрым вводом и показывают черновики.
 */
class IncomingText {
    private val pending = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = pending

    fun offer(value: String?) {
        if (!value.isNullOrBlank()) pending.value = value
    }

    fun consume() {
        pending.value = null
    }
}

/** Платформа даёт его через [QuazioApp]; null — принимать текст извне нечем (ПК). */
val LocalIncomingText = staticCompositionLocalOf<IncomingText?> { null }
