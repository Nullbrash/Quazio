package io.github.nullbrash.quazio.feature.reminders

import io.github.nullbrash.quazio.core.db.QuazioDatabase
import kotlin.random.Random

/** Уведомление или будильник на весь экран со звуком. */
enum class ReminderKind(val dbValue: String) {
    NOTIFICATION("notification"),
    ALARM("alarm");

    companion object {
        fun fromDb(value: String): ReminderKind = entries.firstOrNull { it.dbValue == value } ?: NOTIFICATION
    }
}

/**
 * Профиль напоминания — как профили будильников AMdroid (решение пользователя: все настройки
 * уведомлений и будильников — от этой идеи). Сейчас — малый набор; случайные промежутки,
 * испытания для отключения и прочее добавятся сюда же (этап 7 «Будильник»).
 */
data class ReminderProfile(
    val id: String,
    val name: String,
    val kind: ReminderKind,
    /** Мелодия телефона; null — по умолчанию (у будильника — будильник телефона). */
    val soundUri: String? = null,
    /** За сколько секунд громкость дорастает до полной (у будильника); 0 — сразу. */
    val rampSeconds: Int = 0,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    val builtin: Boolean = false,
)

/** Профили этого устройства (звук — адрес мелодии телефона, поэтому без синхронизации). */
class ProfileStore(private val db: QuazioDatabase) {

    private val q get() = db.reminderQueries

    fun all(): List<ReminderProfile> {
        ensureBuiltins()
        return q.profiles().executeAsList().map { it.toModel() }
    }

    fun byId(id: String): ReminderProfile = q.profileById(id).executeAsOneOrNull()?.toModel()
        ?: all().first { it.id == NOTIFICATION }

    fun save(p: ReminderProfile): ReminderProfile {
        val id = p.id.ifBlank { "p" + Random.nextLong().toULong().toString(16) }
        val builtin = id == NOTIFICATION || id == ALARM
        val name = p.name.trim().ifBlank { "—" }
        q.upsertProfile(id, name, p.kind.dbValue, p.soundUri, p.rampSeconds.coerceIn(0, 120).toLong(), if (p.vibrate) 1 else 0,
            p.snoozeMinutes.coerceIn(1, 60).toLong(), if (builtin) 1 else 0, 0)
        return byId(id)
    }

    /** Готовые профили не удаляются; напоминания с удалённым профилем переходят на «Уведомление». */
    fun delete(id: String) = q.deleteProfile(id)

    private fun ensureBuiltins() {
        if (q.profileById(NOTIFICATION).executeAsOneOrNull() == null) {
            q.upsertProfile(NOTIFICATION, "Уведомление", ReminderKind.NOTIFICATION.dbValue, null, 0, 1, 10, 1, 0)
        }
        if (q.profileById(ALARM).executeAsOneOrNull() == null) {
            q.upsertProfile(ALARM, "Будильник", ReminderKind.ALARM.dbValue, null, 30, 1, 10, 1, 1)
        }
    }

    private fun io.github.nullbrash.quazio.core.db.Reminder_profile.toModel() = ReminderProfile(
        id = id, name = name, kind = ReminderKind.fromDb(kind), soundUri = sound_uri, rampSeconds = ramp_seconds.toInt(),
        vibrate = vibrate != 0L, snoozeMinutes = snooze_minutes.toInt(), builtin = builtin != 0L,
    )

    companion object {
        const val NOTIFICATION = "notification"
        const val ALARM = "alarm"
    }
}
