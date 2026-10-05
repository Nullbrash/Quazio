package io.github.nullbrash.quazio.core.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Ключ базы на Android: 32 случайных байта, зашифрованные ключом из Android
 * Keystore (он не покидает защищённое хранилище). Без подтверждения пользователя:
 * фоновые части (уведомления, будильник) открывают базу, пока интерфейс закрыт.
 * Файл — в `noBackupFilesDir`: в системные резервные копии не попадает.
 */
class AndroidKeystoreKeyStore(context: Context) : DatabaseKeyStore {

    private val file = File(context.noBackupFilesDir, "db.key")

    override fun loadOrCreateKey(): ByteArray {
        val wrapKey = wrappingKey()
        if (file.exists()) {
            val blob = file.readBytes()
            val iv = blob.copyOfRange(0, IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(128, iv))
            val key = cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
            check(key.size == DATABASE_KEY_BYTES) { "Повреждён файл ключа" }
            return key
        }
        val key = ByteArray(DATABASE_KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey)
        val blob = cipher.iv + cipher.doFinal(key)
        // Сначала во временный файл: обрыв посреди записи не должен оставить полуключ.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(blob)
        check(tmp.renameTo(file)) { "Не удалось сохранить ключ базы" }
        return key
    }

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "quazio.db.wrap"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
