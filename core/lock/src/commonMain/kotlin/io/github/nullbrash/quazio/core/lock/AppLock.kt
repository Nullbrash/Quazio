package io.github.nullbrash.quazio.core.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Через сколько времени в фоне снова спрашивать вход. */
enum class LockTimeout(val id: String, val millis: Long) {
    IMMEDIATE("0", 0),
    MIN_1("1", 60_000),
    MIN_5("5", 5 * 60_000),
    MIN_15("15", 15 * 60_000),
    /** Только системная блокировка (Windows) или перезапуск приложения. */
    NEVER("never", Long.MAX_VALUE);

    companion object {
        val DEFAULT = MIN_5
        fun fromId(id: String?): LockTimeout = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

data class LockState(
    val unlocked: Boolean = false,
    val backgroundSince: Long? = null,
    val timeout: LockTimeout = LockTimeout.DEFAULT,
) {
    val locked: Boolean get() = !unlocked
}

/**
 * Замок интерфейса — только он. Данные зашифрованы отдельным ключом, который
 * не зависит от входа: фоновые части работают, пока замок закрыт.
 * Потокобезопасен (атомарные `update`): блокировку Windows сообщает другой поток.
 */
class AppLock(private val now: () -> Long, timeout: LockTimeout = LockTimeout.DEFAULT) {

    private val _state = MutableStateFlow(LockState(timeout = timeout))
    val state: StateFlow<LockState> = _state.asStateFlow()

    val isLocked: Boolean get() = _state.value.locked

    fun setTimeout(timeout: LockTimeout) = _state.update { it.copy(timeout = timeout) }

    fun unlock() = _state.update { it.copy(unlocked = true, backgroundSince = null) }

    fun lockNow() = _state.update { it.copy(unlocked = false, backgroundSince = null) }

    fun onBackground() {
        val t = now()
        _state.update { if (it.unlocked && it.backgroundSince == null) it.copy(backgroundSince = t) else it }
    }

    fun onForeground() {
        val t = now()
        _state.update {
            val since = it.backgroundSince
            when {
                since == null -> it
                t - since >= it.timeout.millis -> it.copy(unlocked = false, backgroundSince = null)
                else -> it.copy(backgroundSince = null)
            }
        }
    }
}
