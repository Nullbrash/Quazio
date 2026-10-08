package io.github.nullbrash.quazio.core.ui

import io.github.nullbrash.quazio.core.db.QuazioDatabase
import io.github.nullbrash.quazio.feature.calendar.KeyValueStore

/** Настройки устройства в таблице `app_state` (не синхронизируются). */
fun QuazioDatabase.appStateStore(): KeyValueStore = object : KeyValueStore {
    override fun get(key: String): String? = appStateQueries.get(key).executeAsOneOrNull()
    override fun put(key: String, value: String) {
        appStateQueries.put(key, value)
    }
    override fun remove(key: String) {
        appStateQueries.delete(key)
    }
}
