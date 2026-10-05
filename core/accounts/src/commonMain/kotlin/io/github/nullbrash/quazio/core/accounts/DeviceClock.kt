package io.github.nullbrash.quazio.core.accounts

import io.github.nullbrash.quazio.core.db.QuazioDatabase
import io.github.nullbrash.quazio.core.model.Hlc
import io.github.nullbrash.quazio.core.model.HlcClock
import io.github.nullbrash.quazio.core.model.Uuid7

/**
 * Устройство и его часы HLC — **один объект на процесс**, общий для всех служб:
 * две копии часов одного устройства могли бы выдать одинаковые метки.
 */
class DeviceClock(db: QuazioDatabase, val wallMillis: () -> Long) {

    val deviceId: String = db.appStateQueries.get(KEY_DEVICE_ID).executeAsOneOrNull()
        ?: Uuid7.generate(wallMillis()).also { db.appStateQueries.put(KEY_DEVICE_ID, it) }

    private val clock = HlcClock(deviceId, wallMillis)

    init {
        // Продолжить с последней метки: если системные часы отстали после перезапуска,
        // новые изменения всё равно должны оказаться «позже» уже записанных.
        db.syncOutboxQueries.latestHlc().executeAsOneOrNull()?.hlc?.let { clock.receive(Hlc.parse(it)) }
    }

    fun now(): Hlc = clock.now()

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
