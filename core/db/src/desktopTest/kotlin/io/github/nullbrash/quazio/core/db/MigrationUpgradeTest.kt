package io.github.nullbrash.quazio.core.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Обновление установленной копии: база прежней версии (миграции 1–3, как на телефоне до
 * регулярных платежей) с данными → новая версия. Данные остаются, новые таблицы появляются.
 */
class MigrationUpgradeTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    @AfterTest
    fun close() = driver.close()

    private fun tables(): List<String> = driver.executeQuery(null, "SELECT name FROM sqlite_master WHERE type = 'table'", { c ->
        val names = mutableListOf<String>()
        while (c.next().value) names += c.getString(0)!!
        QueryResult.Value(names)
    }, 0).value

    @Test
    fun version4DatabaseUpgradesWithDataIntact() {
        QuazioDatabase.Schema.migrate(driver, 1, 4)
        assertTrue("recurring" !in tables())
        driver.execute(null, "INSERT INTO fin_account(id, account_id, name, type, hlc) VALUES ('a1', 'acc', 'Наличные', 'cash', 'h')", 0)
        driver.execute(null, "INSERT INTO txn(id, account_id, kind, amount_minor, fin_account_id, occurred_at, tz, hlc) VALUES ('t1', 'acc', 'expense', 100, 'a1', 0, 'UTC', 'h')", 0)

        QuazioDatabase.Schema.migrate(driver, 4, QuazioDatabase.Schema.version)

        assertTrue("recurring" in tables())
        val db = QuazioDatabase(driver)
        assertEquals(100, db.financeQueries.txnById("t1").executeAsOne().amount_minor)
        assertTrue(db.recurringQueries.recurringOf("acc").executeAsList().isEmpty())
    }
}
