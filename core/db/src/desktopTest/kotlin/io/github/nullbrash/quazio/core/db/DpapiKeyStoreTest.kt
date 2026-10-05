package io.github.nullbrash.quazio.core.db

import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DpapiKeyStoreTest {

    private val dir = Files.createTempDirectory("quazio-key")
    private val file = dir.resolve("db.key")

    @BeforeTest
    fun onlyOnWindows() {
        // На сервере автосборки (Linux) DPAPI нет — тест помечается пропущенным, а не «прошедшим».
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
    }

    @AfterTest
    fun cleanup() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun keyIsCreatedOnceAndStoredProtected() {
        val key = DpapiKeyStore(file).loadOrCreateKey()
        assertEquals(32, key.size)
        val stored = Files.readAllBytes(file)
        assertFalse(stored.contentEquals(key), "ключ лежит открытым")
        assertContentEquals(key, DpapiKeyStore(file).loadOrCreateKey())
    }
}
