package io.github.nullbrash.quazio.core.db

import io.github.nullbrash.quazio.core.model.Hlc

/**
 * Журнал изменений для синхронизации: каждое изменённое поле синхронизируемой
 * записи — строка в `sync_outbox` и новая метка в `field_clock`.
 *
 * Вызывать **внутри той же транзакции**, что и само изменение: иначе при сбое
 * запись поменяется, а другое устройство об этом не узнает (или наоборот).
 */
class ChangeLog(private val db: QuazioDatabase) {

    fun record(accountId: String, table: String, rowId: String, hlc: Hlc, fields: Map<String, String?>) {
        require(fields.isNotEmpty()) { "Нет изменённых полей" }
        val stamp = hlc.toString()
        for ((field, value) in fields) {
            db.syncOutboxQueries.append(accountId, table, rowId, field, value, stamp)
            db.syncOutboxQueries.setFieldClock(table, rowId, field, stamp)
        }
    }
}
