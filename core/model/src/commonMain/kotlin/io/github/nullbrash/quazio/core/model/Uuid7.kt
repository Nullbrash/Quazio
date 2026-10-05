package io.github.nullbrash.quazio.core.model

import kotlin.random.Random

/**
 * Идентификаторы записей — UUIDv7 (RFC 9562): первые 48 бит — время в мс, поэтому
 * id растут со временем (вставка в индекс в конец), остальное — 74 случайных бита.
 * Генерируются на устройстве: синхронизации не нужен общий счётчик.
 */
object Uuid7 {

    fun generate(nowMillis: Long, random: Random = Random.Default): String {
        require(nowMillis in 0..0xFFFF_FFFF_FFFFL) { "Время вне 48 бит: $nowMillis" }
        val bytes = random.nextBytes(16)
        for (i in 0 until 6) bytes[i] = (nowMillis ushr (8 * (5 - i))).toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte() // версия 7
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte() // вариант RFC
        return format(bytes)
    }

    /** Время создания из id — для отладки и проверок. */
    fun timestampOf(id: String): Long {
        require(isValid(id)) { "Не UUIDv7: \"$id\"" }
        return id.substring(0, 8).toLong(16) shl 16 or id.substring(9, 13).toLong(16)
    }

    fun isValid(id: String): Boolean = pattern.matches(id)

    private val pattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    private fun format(b: ByteArray): String {
        val hex = "0123456789abcdef"
        val sb = StringBuilder(36)
        for (i in 0 until 16) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-')
            val v = b[i].toInt() and 0xFF
            sb.append(hex[v ushr 4]).append(hex[v and 0x0F])
        }
        return sb.toString()
    }
}
