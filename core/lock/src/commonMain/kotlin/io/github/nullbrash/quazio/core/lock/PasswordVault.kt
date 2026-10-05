package io.github.nullbrash.quazio.core.lock

import io.github.nullbrash.quazio.core.db.QuazioDatabase

sealed interface CheckResult {
    data object Ok : CheckResult
    /** Неверно; [blockedForMillis] > 0 — следующая попытка только через это время. */
    data class Wrong(val blockedForMillis: Long) : CheckResult
    /** Попытки временно заблокированы после серии ошибок. */
    data class Blocked(val waitMillis: Long) : CheckResult
}

/**
 * Пароль Quazio и код восстановления. Хранятся только «отпечатки» PBKDF2 с солью —
 * в базе устройства (она сама зашифрована), в синхронизацию не идут.
 * Пароль закрывает интерфейс и данные не шифрует. Методы медленные (PBKDF2) —
 * не из главного потока.
 */
class PasswordVault(private val db: QuazioDatabase, private val now: () -> Long) {

    fun hasPassword(): Boolean = get(KEY_PASSWORD) != null

    var timeout: LockTimeout
        get() = LockTimeout.fromId(get(KEY_TIMEOUT))
        set(value) = put(KEY_TIMEOUT, value.id)

    /** Задать (сменить) пароль. Возвращает новый код восстановления — показать один раз. */
    fun setPassword(password: String): String {
        validate(password)
        val code = RecoveryCode.generate()
        db.transaction {
            put(KEY_PASSWORD, hash(password))
            put(KEY_RECOVERY, hash(RecoveryCode.normalize(code)))
            resetFailures()
        }
        return code
    }

    fun verify(password: String): CheckResult = check { matches(get(KEY_PASSWORD), password) }

    /** Вход по коду восстановления с заданием нового пароля; при успехе — новый код. */
    fun recover(code: String, newPassword: String): Pair<CheckResult, String?> {
        validate(newPassword)
        val result = check { matches(get(KEY_RECOVERY), RecoveryCode.normalize(code)) }
        return if (result == CheckResult.Ok) result to setPassword(newPassword) else result to null
    }

    /** Убрать пароль (на ПК он необязателен). Вызывать только после успешного [verify]. */
    fun removePassword() = db.transaction {
        delete(KEY_PASSWORD)
        delete(KEY_RECOVERY)
        resetFailures()
    }

    private fun check(test: () -> Boolean): CheckResult {
        val t = now()
        val blockedUntil = get(KEY_BLOCKED_UNTIL)?.toLong() ?: 0
        if (t < blockedUntil) return CheckResult.Blocked(blockedUntil - t)
        if (test()) {
            resetFailures()
            return CheckResult.Ok
        }
        val failures = (get(KEY_FAILURES)?.toInt() ?: 0) + 1
        put(KEY_FAILURES, failures.toString())
        val block = blockDuration(failures)
        if (block > 0) put(KEY_BLOCKED_UNTIL, (t + block).toString())
        return CheckResult.Wrong(block)
    }

    private fun resetFailures() {
        delete(KEY_FAILURES)
        delete(KEY_BLOCKED_UNTIL)
    }

    private fun hash(secret: String): String {
        val salt = LockCrypto.randomBytes(SALT_BYTES)
        val h = LockCrypto.pbkdf2(secret.toCharArray(), salt, ITERATIONS, HASH_BYTES)
        return "pbkdf2-sha256$$ITERATIONS$${salt.toHex()}$${h.toHex()}"
    }

    private fun matches(stored: String?, secret: String): Boolean {
        val parts = stored?.split('$') ?: return false
        if (parts.size != 4 || parts[0] != "pbkdf2-sha256") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val expected = parts[3].hexToBytes()
        val actual = LockCrypto.pbkdf2(secret.toCharArray(), parts[2].hexToBytes(), iterations, expected.size)
        return constantTimeEquals(expected, actual)
    }

    private fun validate(password: String) {
        require(password.length in MIN_LENGTH..MAX_LENGTH) { "Пароль: от $MIN_LENGTH до $MAX_LENGTH символов" }
    }

    private fun get(key: String): String? = db.appStateQueries.get(key).executeAsOneOrNull()
    private fun put(key: String, value: String) {
        db.appStateQueries.put(key, value)
    }

    private fun delete(key: String) {
        db.appStateQueries.delete(key)
    }

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 128
        const val FREE_ATTEMPTS = 5

        /** После [FREE_ATTEMPTS] ошибок: 30 с, затем вдвое дольше каждый раз, не больше 15 мин. */
        fun blockDuration(failures: Int): Long {
            if (failures < FREE_ATTEMPTS) return 0
            val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(5)
            return (30_000L shl doublings).coerceAtMost(15 * 60_000L)
        }

        private const val KEY_PASSWORD = "lock.password"
        private const val KEY_RECOVERY = "lock.recovery"
        private const val KEY_FAILURES = "lock.failures"
        private const val KEY_BLOCKED_UNTIL = "lock.blocked_until"
        private const val KEY_TIMEOUT = "lock.timeout"
        private const val SALT_BYTES = 16
        private const val HASH_BYTES = 32
        // Ориентир — сотни миллисекунд на телефоне; подбор отдельного пароля дорогой.
        private const val ITERATIONS = 120_000
    }
}

/** Код восстановления: 12 знаков без похожих (0/O, 1/I/L), ~59 бит случайности. */
object RecoveryCode {
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    fun generate(): String {
        val bytes = LockCrypto.randomBytes(12)
        val chars = bytes.map { ALPHABET[(it.toInt() and 0xFF) % ALPHABET.length] }
        return chars.chunked(4).joinToString("-") { it.joinToString("") }
    }

    /** Регистр, пробелы и дефисы при вводе не важны. */
    fun normalize(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }
}

private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/** Сравнение без раннего выхода — время не подсказывает, сколько байт совпало. */
private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
    return diff == 0
}
