package io.github.nullbrash.quazio.core.lock

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.nullbrash.quazio.core.db.QuazioDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordVaultTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { QuazioDatabase.Schema.create(it) }
    private val db = QuazioDatabase(driver)
    private var now = 1_000_000L
    private val vault = PasswordVault(db) { now }

    @AfterTest
    fun close() = driver.close()

    @Test
    fun setAndVerify() {
        assertFalse(vault.hasPassword())
        vault.setPassword("1234")
        assertTrue(vault.hasPassword())
        assertEquals(CheckResult.Ok, vault.verify("1234"))
        assertIs<CheckResult.Wrong>(vault.verify("4321"))
    }

    @Test
    fun passwordIsNotStoredInClear() {
        vault.setPassword("секретный пароль")
        val stored = db.appStateQueries.get("lock.password").executeAsOne()
        assertFalse("секретный" in stored)
        assertTrue(stored.startsWith("pbkdf2-sha256$"))
    }

    @Test
    fun recoveryCodeResetsPasswordAndIsReplaced() {
        val code = vault.setPassword("старый")
        assertTrue(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}").matches(code), code)
        // Регистр и разделители при вводе не важны.
        val (result, newCode) = vault.recover(code.lowercase().replace("-", " "), "новый")
        assertEquals(CheckResult.Ok, result)
        assertEquals(CheckResult.Ok, vault.verify("новый"))
        assertIs<CheckResult.Wrong>(vault.verify("старый"))
        assertNotEquals(code, newCode)
        // Старый код больше не действует.
        assertIs<CheckResult.Wrong>(vault.recover(code, "ещё раз").first)
    }

    @Test
    fun wrongRecoveryCodeChangesNothing() {
        vault.setPassword("пароль")
        val (result, code) = vault.recover("AAAA-AAAA-AAAA", "другой")
        assertIs<CheckResult.Wrong>(result)
        assertNull(code)
        assertEquals(CheckResult.Ok, vault.verify("пароль"))
    }

    @Test
    fun repeatedFailuresBlockAndBlockSurvivesRestart() {
        vault.setPassword("пароль")
        repeat(PasswordVault.FREE_ATTEMPTS - 1) { assertEquals(CheckResult.Wrong(0), vault.verify("нет")) }
        assertEquals(CheckResult.Wrong(30_000), vault.verify("нет"))
        // Даже верный пароль не проверяется, пока идёт блокировка — и после «перезапуска».
        val afterRestart = PasswordVault(db) { now }
        assertIs<CheckResult.Blocked>(afterRestart.verify("пароль"))
        now += 30_000
        assertEquals(CheckResult.Ok, afterRestart.verify("пароль"))
    }

    @Test
    fun blockGrowsAndIsCapped() {
        assertEquals(0L, PasswordVault.blockDuration(4))
        assertEquals(30_000L, PasswordVault.blockDuration(5))
        assertEquals(60_000L, PasswordVault.blockDuration(6))
        assertEquals(15 * 60_000L, PasswordVault.blockDuration(100))
    }

    @Test
    fun removePasswordAndTimeoutSetting() {
        vault.setPassword("пароль")
        vault.removePassword()
        assertFalse(vault.hasPassword())
        assertEquals(LockTimeout.DEFAULT, vault.timeout)
        vault.timeout = LockTimeout.IMMEDIATE
        assertEquals(LockTimeout.IMMEDIATE, PasswordVault(db) { now }.timeout)
    }

    @Test
    fun tooShortPasswordIsRejected() {
        assertFailsWith<IllegalArgumentException> { vault.setPassword("123") }
    }
}
