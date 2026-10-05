package io.github.nullbrash.quazio.core.lock

/** Криптография платформы: PBKDF2-HMAC-SHA256 и криптостойкий генератор. */
expect object LockCrypto {
    fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, bytes: Int): ByteArray
    fun randomBytes(count: Int): ByteArray
}
