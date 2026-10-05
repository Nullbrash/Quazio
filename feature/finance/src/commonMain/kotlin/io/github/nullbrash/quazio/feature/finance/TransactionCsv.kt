package io.github.nullbrash.quazio.feature.finance

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Выгрузка операций в CSV для Excel с русскими настройками: разделитель «;»,
 * метка UTF-8 в начале (иначе кириллица — «кракозябры»), запятая в суммах,
 * дата «дд.мм.гггг», строки — CRLF. Столбец ID — чтобы будущий импорт узнал
 * свои же операции и не задвоил их. Выгрузка всегда бесплатна (решение пользователя).
 */
object TransactionCsv {

    val HEADER = listOf(
        "Дата", "Время", "Тип", "Сумма", "Валюта", "Счёт", "На счёт", "Категория",
        "Магазин", "Метки", "Описание", "Заметка", "Часовой пояс", "ID",
    )

    @OptIn(ExperimentalTime::class)
    fun build(transactions: List<Transaction>, categoryPaths: Map<String, String>): String {
        val sb = StringBuilder("﻿")
        sb.appendRow(HEADER)
        for (t in transactions) {
            val zone = runCatching { TimeZone.of(t.timeZone) }.getOrElse { TimeZone.UTC }
            val dt = Instant.fromEpochMilliseconds(t.occurredAt).toLocalDateTime(zone)
            sb.appendRow(listOf(
                "${dt.day.pad()}.${(dt.month.ordinal + 1).pad()}.${dt.year}",
                "${dt.hour.pad()}:${dt.minute.pad()}",
                kindName(t.kind),
                formatAmountForEdit(t.amount.minor),
                t.amount.currency.code,
                t.finAccountName.orEmpty(),
                t.toFinAccountName.orEmpty(),
                t.categoryId?.let { categoryPaths[it] ?: t.categoryName }.orEmpty(),
                t.merchantName.orEmpty(),
                t.tags.joinToString(" | ") { "${tagKindName(it.kind)}: ${it.name}" },
                t.description,
                t.note,
                t.timeZone,
                t.id,
            ))
        }
        return sb.toString()
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.joinTo(this, ";") { escape(it) }
        append("\r\n")
    }

    /** Кавычки — только если нужны: «;», кавычка, перевод строки; кавычка внутри — удваивается. */
    private fun escape(cell: String): String =
        if (cell.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

    private fun Int.pad() = toString().padStart(2, '0')

    private fun kindName(k: TxnKind) = when (k) {
        TxnKind.INCOME -> "Доход"
        TxnKind.EXPENSE -> "Расход"
        TxnKind.TRANSFER -> "Перевод"
        TxnKind.ADJUSTMENT -> "Корректировка"
    }

    private fun tagKindName(k: TagKind) = when (k) {
        TagKind.TOPIC -> "Тема"
        TagKind.PERSON -> "Человек"
        TagKind.ORGANIZATION -> "Организация"
    }
}
