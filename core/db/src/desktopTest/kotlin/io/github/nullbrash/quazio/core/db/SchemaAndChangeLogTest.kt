package io.github.nullbrash.quazio.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.nullbrash.quazio.core.model.Hlc
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SchemaAndChangeLogTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { QuazioDatabase.Schema.create(it) }
    private val db = QuazioDatabase(driver)
    private val changeLog = ChangeLog(db)
    private val node = "0199a8f2-1c00-7000-8000-00000000000a"

    @AfterTest
    fun close() = driver.close()

    @Test
    fun freshSchemaHasCoreTables() {
        val tables = driver.executeQuery(null, "SELECT name FROM sqlite_master WHERE type = 'table'", { c ->
            val names = mutableListOf<String>()
            while (c.next().value) names += c.getString(0)!!
            app.cash.sqldelight.db.QueryResult.Value(names)
        }, 0).value
        for (t in listOf("app_state", "device", "account", "account_access", "account_writer", "sync_outbox", "field_clock", "sync_peer")) {
            assertTrue(t in tables, "нет таблицы $t")
        }
    }

    @Test
    fun changeIsLoggedTogetherWithTheRow() {
        val hlc = Hlc(1_790_000_000_000L, 0, node)
        db.transaction {
            db.accountQueries.insert("acc-1", "Я", 1_790_000_000_000L, hlc.toString())
            changeLog.record("acc-1", "account", "acc-1", hlc, mapOf("name" to "Я", "created_at" to "1790000000000"))
        }
        val rows = db.syncOutboxQueries.forAccount("acc-1").executeAsList()
        assertEquals(listOf("name", "created_at"), rows.map { it.field_ })
        assertEquals(hlc.toString(), db.syncOutboxQueries.fieldClock("account", "acc-1", "name").executeAsOne())
    }

    @Test
    fun failedTransactionLeavesNeitherRowNorLog() {
        val hlc = Hlc(1_790_000_000_000L, 0, node)
        assertFailsWith<IllegalStateException> {
            db.transaction {
                db.accountQueries.insert("acc-2", "Ошибка", 1L, hlc.toString())
                changeLog.record("acc-2", "account", "acc-2", hlc, mapOf("name" to "Ошибка"))
                error("сбой посреди изменения")
            }
        }
        assertEquals(null, db.accountQueries.byId("acc-2").executeAsOneOrNull())
        assertEquals(0L, db.syncOutboxQueries.count().executeAsOne())
    }

    @Test
    fun laterChangeUpdatesFieldClock() {
        val first = Hlc(1_000L, 0, node)
        val second = Hlc(1_000L, 1, node)
        db.transaction { changeLog.record("acc-1", "account", "acc-1", first, mapOf("name" to "А")) }
        db.transaction { changeLog.record("acc-1", "account", "acc-1", second, mapOf("name" to "Б")) }
        assertEquals(second.toString(), db.syncOutboxQueries.fieldClock("account", "acc-1", "name").executeAsOne())
        assertEquals(2L, db.syncOutboxQueries.count().executeAsOne())
    }
}
