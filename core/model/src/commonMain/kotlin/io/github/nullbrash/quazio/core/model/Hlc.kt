package io.github.nullbrash.quazio.core.model

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Метка гибридных логических часов (HLC): время в мс + счётчик + устройство.
 * Сравнение меток задаёт порядок изменений между устройствами даже при сбитых
 * часах; устройство разрешает равенство, чтобы порядок был полным.
 */
data class Hlc(val millis: Long, val counter: Int, val node: String) : Comparable<Hlc> {

    init {
        require(millis in 0..MAX_MILLIS) { "Время вне диапазона: $millis" }
        require(counter in 0..MAX_COUNTER) { "Счётчик вне диапазона: $counter" }
        require(node.isNotEmpty()) { "Пустой узел" }
    }

    override fun compareTo(other: Hlc): Int = compareValuesBy(this, other, Hlc::millis, Hlc::counter, Hlc::node)

    /** Строка сравнивается так же, как сами метки: поля фиксированной ширины. */
    override fun toString(): String =
        millis.toString().padStart(15, '0') + SEPARATOR + counter.toString().padStart(5, '0') + SEPARATOR + node

    companion object {
        const val MAX_COUNTER = 0xFFFF
        /** 47 бит: часы упаковывают время со счётчиком в один Long (`millis shl 16`). */
        const val MAX_MILLIS = 0x7FFF_FFFF_FFFFL
        private const val SEPARATOR = '-'

        /** Разбор по позициям: в узле (id устройства) тоже есть дефисы. */
        fun parse(text: String): Hlc {
            require(text.length > 22 && text[15] == SEPARATOR && text[21] == SEPARATOR) { "Неверная метка HLC: \"$text\"" }
            val millis = text.substring(0, 15).toLongOrNull()
            val counter = text.substring(16, 21).toIntOrNull()
            require(millis != null && counter != null) { "Неверная метка HLC: \"$text\"" }
            return Hlc(millis, counter, text.substring(22))
        }
    }
}

/**
 * Часы HLC одного устройства. Потокобезопасны без блокировок: время и счётчик
 * упакованы в один Long (`millis shl 16 or counter`) и меняются через CAS.
 */
@OptIn(ExperimentalAtomicApi::class)
class HlcClock(private val node: String, private val wallMillis: () -> Long) {

    private val state = AtomicLong(0L)

    /** Метка для нового локального изменения. */
    fun now(): Hlc = advance { l, c ->
        val pt = wallMillis()
        if (pt > l) pt to 0 else l to c + 1
    }

    /** Учесть метку, пришедшую с другого устройства (при синхронизации). */
    fun receive(remote: Hlc): Hlc = advance { l, c ->
        val pt = wallMillis()
        val m = maxOf(l, remote.millis, pt)
        val counter = when {
            m == l && m == remote.millis -> maxOf(c, remote.counter) + 1
            m == l -> c + 1
            m == remote.millis -> remote.counter + 1
            else -> 0
        }
        m to counter
    }

    private inline fun advance(step: (Long, Int) -> Pair<Long, Int>): Hlc {
        while (true) {
            val old = state.load()
            var (l, c) = step(old ushr 16, (old and 0xFFFF).toInt())
            // Переполнение счётчика: сдвигаем время на 1 мс — порядок сохраняется.
            if (c > Hlc.MAX_COUNTER) { l += 1; c = 0 }
            if (state.compareAndSet(old, (l shl 16) or c.toLong())) return Hlc(l, c, node)
        }
    }
}
