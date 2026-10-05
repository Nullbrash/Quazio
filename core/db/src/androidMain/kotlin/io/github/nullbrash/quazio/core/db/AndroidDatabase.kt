package io.github.nullbrash.quazio.core.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Открытие зашифрованной базы на Android (SQLCipher). Только из основного процесса. */
object AndroidDatabase {

    const val FILE_NAME = "quazio.db"

    fun driver(context: Context, key: ByteArray): SqlDriver {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(sqlCipherRawKey(key).encodeToByteArray())
        return AndroidSqliteDriver(
            schema = QuazioDatabase.Schema,
            context = context.applicationContext,
            name = FILE_NAME,
            factory = factory,
            callback = object : AndroidSqliteDriver.Callback(QuazioDatabase.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        )
    }

    fun open(context: Context, keyStore: DatabaseKeyStore): QuazioDatabase =
        QuazioDatabase(driver(context, keyStore.loadOrCreateKey()))
}
