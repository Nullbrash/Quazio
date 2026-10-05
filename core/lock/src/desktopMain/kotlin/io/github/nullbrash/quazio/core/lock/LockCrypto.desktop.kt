package io.github.nullbrash.quazio.core.lock

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

actual object LockCrypto {
    private val random = SecureRandom()

    actual fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, bytes: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, bytes * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    actual fun randomBytes(count: Int): ByteArray = ByteArray(count).also { random.nextBytes(it) }
}
