package io.github.nullbrash.quazio.core.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.mc.SQLiteMCSqlCipherConfig
import java.nio.file.Path
import java.util.Properties

/** Открытие зашифрованной базы на ПК. */
object DesktopDatabase {

    /**
     * Формат SQLCipher 4 с сырым ключом — тот же, что на Android, чтобы файлы
     * (резервные копии) были переносимы между платформами.
     */
    fun driver(file: Path, key: ByteArray): SqlDriver {
        require(key.size == DATABASE_KEY_BYTES) { "Ключ должен быть $DATABASE_KEY_BYTES байта" }
        val props: Properties = SQLiteMCSqlCipherConfig.getV4Defaults().withRawUnsaltedKey(key).build().toProperties()
        props.setProperty("foreign_keys", "true")
        return JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}", props, QuazioDatabase.Schema)
    }

    fun open(file: Path, keyStore: DatabaseKeyStore): QuazioDatabase =
        QuazioDatabase(driver(file, keyStore.loadOrCreateKey()))
}
