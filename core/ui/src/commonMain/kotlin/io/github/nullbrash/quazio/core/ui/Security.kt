package io.github.nullbrash.quazio.core.ui

/**
 * Вход через блокировку экрана устройства (отпечаток, PIN). Даёт платформа:
 * на Android — BiometricPrompt, на ПК пока нет (Windows Hello — позже).
 */
interface DeviceAuthenticator {
    /** Есть ли на устройстве блокировка экрана. Проверять каждый раз: её могут снять. */
    fun isAvailable(): Boolean

    /** Показать системное окно входа; true — подтверждено. Вызывать из главного потока. */
    suspend fun authenticate(): Boolean
}

/** Как закрыт чувствительный раздел. */
enum class GateMode {
    /** Не закрыт: на ПК пароль необязателен и не задан. */
    OPEN,
    /** Вход через блокировку экрана устройства. */
    DEVICE,
    /** Вход по паролю Quazio. */
    PASSWORD,
    /** Телефон без блокировки экрана и без пароля — раздел закрыт, пока пароль не задан. */
    SETUP_REQUIRED,
}

fun gateMode(deviceAuthAvailable: Boolean, hasPassword: Boolean, passwordRequired: Boolean): GateMode = when {
    deviceAuthAvailable -> GateMode.DEVICE
    hasPassword -> GateMode.PASSWORD
    passwordRequired -> GateMode.SETUP_REQUIRED
    else -> GateMode.OPEN
}

/**
 * Разделы, закрытые входом: финансы (позже — здоровье, статистика).
 * Календарь, задачи, будильник — открыты (решение пользователя).
 */
val Destination.isSensitive: Boolean
    get() = when (this) {
        Destination.FINANCE -> true
        // Календарь открыт и без входа (решение пользователя 2026-10-05: закрыты только финансы, здоровье, статистика).
        Destination.CALENDAR -> false
        Destination.SETTINGS -> false
    }
