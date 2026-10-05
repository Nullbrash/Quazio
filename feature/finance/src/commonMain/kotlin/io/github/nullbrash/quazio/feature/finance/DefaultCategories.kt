package io.github.nullbrash.quazio.feature.finance

/**
 * Стартовое дерево категорий (утверждено пользователем). Структура — по образцу
 * его Money Manager: группа задаёт цвет, внутри — подкатегории. Ключ — для id:
 * `<аккаунт>:c:<ключ>` одинаков на всех устройствах, поэтому набор, созданный
 * на телефоне и на ПК, при синхронизации сливается, а не задваивается.
 */
internal data class DefaultCategory(
    val key: String,
    val name: String,
    val kind: CategoryKind,
    val color: Long? = null,
    val children: List<DefaultCategory> = emptyList(),
)

private fun expense(key: String, name: String, color: Long? = null, vararg children: Pair<String, String>) =
    DefaultCategory(key, name, CategoryKind.EXPENSE, color, children.map { (k, n) -> DefaultCategory("$key.$k", n, CategoryKind.EXPENSE) })

private fun income(key: String, name: String) = DefaultCategory(key, name, CategoryKind.INCOME, INCOME_COLOR)

private const val INCOME_COLOR = 0xFF0288D1

internal val DEFAULT_CATEGORIES: List<DefaultCategory> = listOf(
    expense(
        "shops", "Магазины", 0xFF2E7D32,
        "groceries" to "Продукты", "pharmacy" to "Аптеки", "clothes" to "Одежда и обувь",
        "electronics" to "Электроника", "home" to "Дом и быт",
    ),
    expense(
        "online", "Интернет-покупки", 0xFFC62828,
        "marketplaces" to "Маркетплейсы", "games" to "Игры и цифровые товары", "subscriptions" to "Подписки",
        "telecom" to "Связь и интернет", "hosting" to "Хостинг и онлайн-сервисы",
    ),
    expense(
        "food_out", "Еда вне дома", 0xFF00897B,
        "restaurants" to "Кафе и рестораны", "fastfood" to "Фастфуд", "coffee" to "Кофе", "delivery" to "Доставка еды",
    ),
    expense(
        "transport", "Транспорт", 0xFF546E7A,
        "public" to "Метро и автобус", "taxi" to "Такси", "car" to "Автомобиль", "trips" to "Поездки и путешествия",
    ),
    expense(
        "housing", "Жильё", 0xFFEF6C00,
        "rent" to "Аренда и ипотека", "electricity" to "Электричество", "water" to "Вода",
        "utilities" to "Квартплата (ЕПД)", "internet" to "Интернет дома",
    ),
    expense(
        "services", "Услуги", 0xFF1565C0,
        "medicine" to "Медицина", "beauty" to "Красота и уход", "repair" to "Ремонт", "education" to "Образование",
    ),
    expense("gifts", "Подарки и донаты", 0xFFAD1457, "gifts" to "Подарки", "donations" to "Донаты"),
    expense("pets", "Питомцы", 0xFF6A1B9A, "food" to "Корм", "toys" to "Игрушки", "supplies" to "Инвентарь", "vet" to "Ветеринар"),
    expense("taxes", "Налоги и штрафы", 0xFF616161),
    expense("other", "Прочее", 0xFF757575),
    income("salary", "Зарплата"),
    income("side", "Подработка"),
    income("gifts_in", "Подарки"),
    income("cashback", "Кешбэк и проценты"),
    income("debt_return", "Возврат долгов"),
    income("other_in", "Прочие доходы"),
)

internal fun defaultCategoryId(accountId: String, key: String) = "$accountId:c:$key"

internal fun defaultCashAccountId(accountId: String) = "$accountId:a:cash"
