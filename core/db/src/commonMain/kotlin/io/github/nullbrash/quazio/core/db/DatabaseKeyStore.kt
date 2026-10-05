package io.github.nullbrash.quazio.core.db

/**
 * Ключ шифрования базы: 32 случайных байта, создаются один раз и хранятся
 * защищёнными средствами ОС (Android Keystore, Windows DPAPI).
 * Ключ не зависит от пароля пользователя: фоновые части должны открывать базу,
 * пока интерфейс заблокирован.
 */
interface DatabaseKeyStore {
    fun loadOrCreateKey(): ByteArray
}

internal const val DATABASE_KEY_BYTES = 32

/** Ключ в формате «сырого ключа» SQLCipher (`x'…'`) — без медленного PBKDF2 при каждом открытии. */
internal fun sqlCipherRawKey(key: ByteArray): String {
    require(key.size == DATABASE_KEY_BYTES) { "Ключ должен быть $DATABASE_KEY_BYTES байта" }
    return "x'" + key.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') } + "'"
}
