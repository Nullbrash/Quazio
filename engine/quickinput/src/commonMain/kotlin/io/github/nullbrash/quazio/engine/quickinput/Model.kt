package io.github.nullbrash.quazio.engine.quickinput

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Вид черновика. Долг — отдельно от перевода: направление «деньги ушли от меня» (OUT)
 * или «пришли ко мне» (IN), как знак у операции «Долг» в окне операции.
 */
enum class QuickKind { EXPENSE, INCOME, TRANSFER, DEBT_OUT, DEBT_IN }

/** Роль счёта для разбора: «наличными», «с карты», «забрал» (с накоплений), долги. */
enum class AccountRole { CARD, CASH, SAVINGS, DEBT, OTHER }

data class QuickAccount(
    val id: String,
    val name: String,
    val role: AccountRole,
    /** Долг: с кем (имя человека или организации). */
    val counterparty: String? = null,
)

data class QuickCategory(
    val id: String,
    val name: String,
    val income: Boolean,
    /** Ключ стартового набора («food_out.coffee») — по нему находит словарь; у своих категорий null. */
    val key: String? = null,
    val leaf: Boolean = true,
)

/** Память слова: что пользователь выбрал, поправив черновик. null — не запоминалось. */
data class WordRule(
    val kind: QuickKind? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val secondAccountId: String? = null,
)

/** Всё, что разбор знает об аккаунте. Собирает вызывающий (финансы, позже — клавиатура). */
class QuickVocabulary(
    val accounts: List<QuickAccount> = emptyList(),
    val categories: List<QuickCategory> = emptyList(),
    /** Известные магазины → категория последней операции с ними (null — без категории). */
    val merchants: Map<String, String?> = emptyMap(),
    val tags: List<String> = emptyList(),
    /** Ключ — слово в нижнем регистре, «ё» → «е». */
    val wordRules: Map<String, WordRule> = emptyMap(),
    /** Счёт, если в тексте не назван. По умолчанию — первый обычный (не долг, не накопления). */
    val defaultAccountId: String? = accounts.firstOrNull { it.role != AccountRole.DEBT && it.role != AccountRole.SAVINGS }?.id
        ?: accounts.firstOrNull { it.role != AccountRole.DEBT }?.id,
)

data class QuickDraft(
    val kind: QuickKind,
    /** Модуль суммы в копейках, > 0. */
    val amountMinor: Long,
    val date: LocalDate,
    val time: LocalTime? = null,
    /** Свой счёт: списания и зачисления; у перевода — «откуда». null — не определён. */
    val accountId: String? = null,
    /** Перевод — «куда»; долг — счёт-долг. */
    val secondAccountId: String? = null,
    val categoryId: String? = null,
    val merchant: String? = null,
    val description: String = "",
    /** Метки из «#…» — названиями: какие из них новые, решает вызывающий. */
    val tags: List<String> = emptyList(),
    /** Слово, под которым запоминается правка черновика («забрал», «такси»). */
    val keyWord: String? = null,
)

/** Строка (или несколько строк) разобранного текста. */
sealed interface QuickLine {
    val text: String

    /** [signedMinor] — с каким знаком строка входит в сверку «= …». */
    data class Operation(override val text: String, val draft: QuickDraft, val signedMinor: Long) : QuickLine

    /** «= - 4 500р.»: [expectedMinor] — записано, [actualMinor] — сумма строк выше. */
    data class Total(override val text: String, val expectedMinor: Long, val actualMinor: Long) : QuickLine {
        val matches: Boolean get() = expectedMinor == actualMinor
    }

    /** «Остаток: 43 000р.» — сверка с балансом счёта ([accountId] — о каком счёте речь, если понятно). */
    data class Balance(override val text: String, val amountMinor: Long, val accountId: String?) : QuickLine

    /** Не операция: «не брал», заголовок, дата, непонятное. [signedMinor] — участие в сверке. */
    data class Skipped(
        override val text: String,
        val signedMinor: Long? = null,
        val reason: SkipReason = SkipReason.UNKNOWN,
    ) : QuickLine
}

/** Почему строка не стала операцией — показывается пользователю. */
enum class SkipReason {
    /** «За 5-ое по 500р не брал» — деньги не двигались. */
    NEGATED,
    /** «Забрал:» — относится к строкам ниже. */
    HEADER,
    /** «29 августа 2022 17 09 17» — дата для строк ниже. */
    DATE,
    UNKNOWN,
}
