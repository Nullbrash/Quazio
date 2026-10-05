package io.github.nullbrash.quazio.core.db

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom

/**
 * Ключ базы на Windows: хранится в файле, зашифрованный DPAPI — расшифровать его
 * может только тот же пользователь Windows на этом компьютере.
 */
class DpapiKeyStore(private val file: Path) : DatabaseKeyStore {

    override fun loadOrCreateKey(): ByteArray {
        check(System.getProperty("os.name").startsWith("Windows")) { "DPAPI есть только в Windows" }
        if (Files.exists(file)) {
            val key = Crypt32Util.cryptUnprotectData(Files.readAllBytes(file))
            check(key.size == DATABASE_KEY_BYTES) { "Повреждён файл ключа: $file" }
            return key
        }
        val key = ByteArray(DATABASE_KEY_BYTES).also { SecureRandom().nextBytes(it) }
        Files.createDirectories(file.toAbsolutePath().parent)
        // Сначала во временный файл: обрыв посреди записи не должен оставить полуключ.
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.write(tmp, Crypt32Util.cryptProtectData(key))
        Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        return key
    }
}
