package io.github.nullbrash.quazio.feature.finance

import io.github.nullbrash.quazio.core.model.Money

/** Тип счёта: поведение и значение «учитывать в итоге» по умолчанию. */
enum class FinAccountType(val dbValue: String, val defaultIncludeInTotal: Boolean = true) {
    CARD("card"),
    SAVINGS("savings"),
    CASH("cash"),
    /** Около-банковский счёт (Ozon Банк, ЮMoney): настоящие деньги, ведёт себя как карта. */
    EWALLET("ewallet"),
    /** Рубли внутри сайта (хостинг, игровой магазин): только потратить там; пополнение — перевод. */
    SITE_BALANCE("site_balance"),
    /** Баллы и бонусы: учитываются, но в итог денег не входят. */
    BONUS("bonus", defaultIncludeInTotal = false),
    CREDIT_CARD("credit_card"),
    /** Долг с человеком: плюс — мне должны, минус — я должен. */
    DEBT("debt");

    companion object {
        fun fromDb(value: String): FinAccountType = entries.first { it.dbValue == value }
    }
}

enum class TxnKind(val dbValue: String) {
    EXPENSE("expense"),
    INCOME("income"),
    TRANSFER("transfer"),
    /** Корректировка баланса («выравнивание»): не доход и не расход, знак суммы свой. */
    ADJUSTMENT("adjustment");

    companion object {
        fun fromDb(value: String): TxnKind = entries.first { it.dbValue == value }
    }
}

enum class CategoryKind(val dbValue: String) {
    EXPENSE("expense"),
    INCOME("income");

    companion object {
        fun fromDb(value: String): CategoryKind = entries.first { it.dbValue == value }
    }
}

/** Сквозные метки поверх категорий (решение пользователя: тема / человек / организация). */
enum class TagKind(val dbValue: String) {
    TOPIC("topic"),
    PERSON("person"),
    ORGANIZATION("organization");

    companion object {
        fun fromDb(value: String): TagKind = entries.first { it.dbValue == value }
    }
}

data class FinAccount(
    val id: String,
    val name: String,
    val type: FinAccountType,
    val balance: Money,
    val includeInTotal: Boolean,
    val creditLimit: Money?,
    val personTagId: String?,
    val archived: Boolean,
)

/** Узел дерева категорий; [color] уже с учётом наследования от группы. */
data class CategoryNode(
    val id: String,
    val parentId: String?,
    val name: String,
    val kind: CategoryKind,
    val color: Long?,
    val depth: Int,
    /** «Магазины / Продукты» — для поиска и выгрузки. */
    val path: String,
)

data class Tag(val id: String, val name: String, val kind: TagKind)

data class Transaction(
    val id: String,
    val kind: TxnKind,
    val amount: Money,
    val finAccountId: String,
    val finAccountName: String?,
    val toFinAccountId: String?,
    val toFinAccountName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val merchantName: String?,
    val tags: List<Tag>,
    val occurredAt: Long,
    val timeZone: String,
    val description: String,
    val note: String,
)

/** Что сохранить. [id] = null — новая операция. Сумма — в копейках, больше нуля (у корректировки — не ноль). */
data class TransactionDraft(
    val id: String? = null,
    val kind: TxnKind,
    val amountMinor: Long,
    val finAccountId: String,
    val toFinAccountId: String? = null,
    val categoryId: String? = null,
    val merchantName: String? = null,
    val tagIds: Set<String> = emptySet(),
    val occurredAt: Long,
    val timeZone: String,
    val description: String = "",
    val note: String = "",
    /** Откуда операция; пишется только при создании. */
    val source: TxnSource = TxnSource.MANUAL,
)

enum class TxnSource(val dbValue: String) { MANUAL("manual"), QUICK_INPUT("quickinput") }

data class Totals(val income: Money, val expense: Money) {
    val net: Money get() = income - expense
}

/** Подсказка для автозаполнения — из последней похожей операции. */
data class Suggestion(
    val kind: TxnKind,
    val finAccountId: String,
    val categoryId: String?,
    val tagIds: Set<String>,
)
