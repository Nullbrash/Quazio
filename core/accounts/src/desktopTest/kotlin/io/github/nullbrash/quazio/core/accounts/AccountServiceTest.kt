package io.github.nullbrash.quazio.core.accounts

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.nullbrash.quazio.core.db.QuazioDatabase
import io.github.nullbrash.quazio.core.model.Hlc
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AccountServiceTest {

    private val dir = Files.createTempDirectory("quazio-acc")
    private val url = "jdbc:sqlite:${dir.resolve("a.db").toAbsolutePath()}"
    private var wall = 1_790_000_000_000L

    private fun open(): Pair<JdbcSqliteDriver, AccountService> {
        val driver = JdbcSqliteDriver(url, java.util.Properties(), QuazioDatabase.Schema)
        val db = QuazioDatabase(driver)
        return driver to AccountService(db, DeviceClock(db) { wall })
    }

    @AfterTest
    fun cleanup() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun firstLaunchCreatesDeviceAndAccountOnce() {
        val (driver, service) = open()
        driver.use {
            val first = service.initialize("ПК", "windows", "Я")
            assertEquals("Я", first.name)
            assertEquals(AccessLevel.FULL, first.access)
            assertEquals(first, service.initialize("ПК", "windows", "Другое имя"))
            assertEquals(listOf(first), service.accounts())
            assertEquals(first, service.current())
        }
    }

    @Test
    fun createSwitchAndRename() {
        val (driver, service) = open()
        driver.use {
            val me = service.initialize("ПК", "windows", "Я")
            wall += 1_000 // в одну миллисекунду порядок UUIDv7 случаен
            val mom = service.create("  Мама  ")
            assertEquals("Мама", mom.name)
            assertEquals(me, service.current())
            service.switchTo(mom.id)
            assertEquals(mom.id, service.current().id)
            service.rename(mom.id, "Мама (лекарства)")
            assertEquals("Мама (лекарства)", service.current().name)
            assertEquals(listOf(me.id, mom.id), service.accounts().map { it.id })
        }
    }

    @Test
    fun changesGoToTheLogUnderTheirAccount() {
        val (driver, service) = open()
        driver.use {
            val me = service.initialize("ПК", "windows", "Я")
            service.rename(me.id, "Новое")
            val db = QuazioDatabase(driver)
            val fields = db.syncOutboxQueries.forAccount(me.id).executeAsList().map { it.field_ to it.value_ }
            assertEquals(listOf("name" to "Я", "created_at" to wall.toString(), "name" to "Новое"), fields)
        }
    }

    @Test
    fun deviceIdSurvivesRestartAndClockKeepsGoingForward() {
        val (d1, s1) = open()
        val deviceId: String
        val lastBefore: Hlc
        d1.use {
            val me = s1.initialize("ПК", "windows", "Я")
            s1.rename(me.id, "А")
            deviceId = s1.deviceId
            lastBefore = Hlc.parse(QuazioDatabase(d1).syncOutboxQueries.latestHlc().executeAsOne().hlc!!)
        }
        wall -= 60_000 // после перезапуска системные часы отстали на минуту
        val (d2, s2) = open()
        d2.use {
            assertEquals(deviceId, s2.deviceId)
            s2.rename(s2.current().id, "Б")
            val after = Hlc.parse(QuazioDatabase(d2).syncOutboxQueries.latestHlc().executeAsOne().hlc!!)
            assertTrue(after > lastBefore, "$after не позже $lastBefore")
        }
    }

    @Test
    fun invalidInputIsRejected() {
        val (driver, service) = open()
        driver.use {
            service.initialize("ПК", "windows", "Я")
            assertFailsWith<IllegalArgumentException> { service.create("   ") }
            assertFailsWith<IllegalArgumentException> { service.create("x".repeat(51)) }
            assertFailsWith<IllegalArgumentException> { service.switchTo("нет-такого") }
        }
    }
}
