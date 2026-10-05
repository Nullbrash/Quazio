package io.github.nullbrash.quazio.engine.quickinput

/**
 * Словари разбора. Ключи категорий — из стартового набора финансов (`DefaultCategories`):
 * переименованная категория находится по ключу, удалённая — просто не находится.
 * Слова — основами: подходит любое слово, которое с основы начинается.
 */
internal object Lexicon {

    /** Основа слова → ключ категории. Названия самих категорий сюда не входят — они ищутся отдельно. */
    val keywords: List<Pair<String, String>> = listOf(
        "food_out.restaurants" to "обед ужин завтрак ресторан кафе столов бар пицц суши",
        "food_out.fastfood" to "бургер шаурм шаверм макдак фастфуд хот-дог",
        "food_out.coffee" to "кофе капучин латте раф",
        "food_out.delivery" to "доставк",
        "shops.groceries" to "продукт супермаркет",
        "shops.pharmacy" to "аптек лекарств таблет",
        "shops.clothes" to "одежд обув куртк кроссовк джинс",
        "shops.electronics" to "электроник наушник зарядк",
        "shops.home" to "хозтовар посуд",
        "online.subscriptions" to "подписк",
        "online.games" to "игр стим",
        "online.telecom" to "связь мобильн сотов",
        "online.hosting" to "хостинг сервер домен vps",
        "transport.public" to "метро автобус трамва троллейбус электричк проезд маршрутк",
        "transport.taxi" to "такси",
        "transport.car" to "бензин заправк парковк мойк автомойк шиномонтаж",
        "transport.trips" to "билет поезд самолет отел гостиниц",
        "housing.rent" to "аренд ипотек",
        "housing.electricity" to "электричеств свет",
        "housing.water" to "вода",
        "housing.utilities" to "квартплат коммуналк жкх епд",
        "services.medicine" to "врач стоматолог анализ клиник больниц",
        "services.beauty" to "стрижк парикмахер маникюр барбер",
        "services.repair" to "ремонт",
        "services.education" to "курс обучени учеб книг",
        "gifts.gifts" to "подар",
        "gifts.donations" to "донат",
        "pets.food" to "корм",
        "pets.vet" to "ветеринар ветклиник",
        "taxes" to "налог штраф",
        "salary" to "зарплат зп аванс преми",
        "side" to "подработк фриланс",
        "cashback" to "кешбэк кэшбэк кешбек кэшбек процент",
        "debt_return" to "возврат",
    ).flatMap { (key, stems) -> stems.split(' ').map { it to key } }

    /** Известные магазины: написания → (название, ключ категории). */
    val brands: List<Triple<List<String>, String, String>> = listOf(
        Triple(listOf("пятерочка", "пятерка"), "Пятёрочка", "shops.groceries"),
        Triple(listOf("магнит"), "Магнит", "shops.groceries"),
        Triple(listOf("перекресток"), "Перекрёсток", "shops.groceries"),
        Triple(listOf("ашан"), "Ашан", "shops.groceries"),
        Triple(listOf("лента"), "Лента", "shops.groceries"),
        Triple(listOf("вкусвилл"), "ВкусВилл", "shops.groceries"),
        Triple(listOf("дикси"), "Дикси", "shops.groceries"),
        Triple(listOf("spar", "спар"), "SPAR", "shops.groceries"),
        Triple(listOf("ozon", "озон"), "Ozon", "online.marketplaces"),
        Triple(listOf("wildberries", "вайлдберриз", "вб", "wb"), "Wildberries", "online.marketplaces"),
        Triple(listOf("aliexpress", "алиэкспресс"), "AliExpress", "online.marketplaces"),
        Triple(listOf("steam"), "Steam", "online.games"),
        Triple(listOf("kfc"), "KFC", "food_out.fastfood"),
    )

    val cashWords = listOf("налич", "нал", "налом", "наликом", "кэш", "кеш", "cash")
    val cardWords = listOf("карт")
    val savingsWords = listOf("накоплен", "копилк", "вклад", "сбережен", "заначк")

    /** «Забрал», «снял» — по умолчанию долг «мне вернули»: деньги забрал себе у кого-то (решение пользователя). */
    val withdrawWords = setOf("забрал", "забрала", "забрали", "снял", "сняла", "сняли", "взял", "взяла")
    val transferWords = setOf("перевод", "перевел", "перевела", "перекинул", "перекинула", "переложил", "переложила")
    val incomeWords = setOf("получил", "получила", "пришло", "пришли", "пришла", "поступило", "поступление", "доход", "приход")
    val negatedVerbs = setOf("брал", "брала", "взял", "взяла", "тратил", "тратила", "снимал", "снимала", "забирал", "забирала")

    val weekdays: List<List<String>> = listOf(
        listOf("понедельник", "пн"), listOf("вторник", "вт"), listOf("среда", "среду", "ср"), listOf("четверг", "чт"),
        listOf("пятница", "пятницу", "пт"), listOf("суббота", "субботу", "сб"), listOf("воскресенье", "вс"),
    )

    val months = listOf(
        "январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр",
    )

    /** Служебные слова: не описание и не имя. */
    val particles = setOf("в", "во", "на", "с", "со", "у", "по", "за", "из", "и", "от", "к", "мне", "долг", "долга", "взаймы")
}
