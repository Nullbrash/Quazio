package io.github.nullbrash.quazio.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.security.SecureRandom
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class EncryptionTest {

    private val dir = Files.createTempDirectory("quazio-enc")
    private val file = dir.resolve("test.db")
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @AfterTest
    fun cleanup() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun fileIsUnreadableWithoutTheKey() {
        DesktopDatabase.driver(file, key).use { driver ->
            QuazioDatabase(driver).appStateQueries.put("probe", "секрет")
        }

        // Обычный SQLite-файл начинается с «SQLite format 3»; зашифрованный — нет.
        val header = Files.readAllBytes(file).copyOfRange(0, 15).decodeToString()
        assertFalse(header == "SQLite format 3", "заголовок не зашифрован")

        assertFails("открылся без ключа") {
            JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}").use { plain ->
                QuazioDatabase(plain).appStateQueries.get("probe").executeAsOneOrNull()
            }
        }

        val wrongKey = key.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertFails("открылся чужим ключом") {
            DesktopDatabase.driver(file, wrongKey).use { QuazioDatabase(it).appStateQueries.get("probe").executeAsOneOrNull() }
        }

        DesktopDatabase.driver(file, key).use { driver ->
            assertEquals("секрет", QuazioDatabase(driver).appStateQueries.get("probe").executeAsOne())
        }
    }

    @Test
    fun rejectsKeyOfWrongSize() {
        assertFails { DesktopDatabase.driver(file, ByteArray(16)) }
    }
}
