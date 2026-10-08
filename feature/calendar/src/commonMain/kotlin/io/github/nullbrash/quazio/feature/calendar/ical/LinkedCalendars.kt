package io.github.nullbrash.quazio.feature.calendar.ical

import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import io.github.nullbrash.quazio.feature.calendar.CalendarInfo
import io.github.nullbrash.quazio.feature.calendar.CalendarKind
import io.github.nullbrash.quazio.feature.calendar.CalendarSource
import io.github.nullbrash.quazio.feature.calendar.EventDraft
import io.github.nullbrash.quazio.feature.calendar.KeyValueStore
import kotlinx.datetime.TimeZone
import kotlin.concurrent.Volatile
import kotlin.random.Random

/** Календарь по ссылке iCal. [url] — секрет (по нему читается весь календарь): не показывать и не писать в журналы. */
data class IcalLink(val id: String, val url: String, val name: String, val color: Long)

enum class FetchProblem { OFFLINE, NOT_FOUND, HTTP, NOT_CALENDAR, TOO_LARGE }

/** Ошибка загрузки — без адреса в тексте: адрес секретный. */
class FetchException(val problem: FetchProblem, val httpCode: Int? = null) : Exception("$problem${httpCode?.let { " $it" } ?: ""}")

/** Загрузка по ссылке; сетевой код — в отдельном модуле ПК, сюда он приходит этим интерфейсом. */
fun interface IcalFetcher {
    /** Тело ответа; ошибки — [FetchException]. */
    fun fetch(url: String): String
}

data class LinkStatus(val fetchedAt: Long?, val problem: FetchProblem?, val httpCode: Int?)

/**
 * Календари по секретным ссылкам iCal (на ПК — Google, решение пользователя): только чтение.
 * Ссылки и последняя загрузка хранятся в зашифрованной базе (`app_state`) — без сети видна
 * последняя загрузка. Когда загружать — решает вызывающий (оболочка: при открытии календаря,
 * раз в 15 минут и по кнопке «Обновить»). Вызывать не из главного потока.
 *
 * Id календарей — `ical:<id ссылки>`: позже рядом встанут календари с телефона (синхронизация,
 * этап 3) со своим префиксом.
 */
class LinkedCalendars(
    private val store: KeyValueStore,
    private val fetcher: IcalFetcher,
    private val clock: () -> Long,
    private val deviceZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : CalendarSource {

    // Разобранные выгрузки по id ссылки (null — сохранённое не разбирается); заменяется целиком.
    @Volatile
    private var cache: Map<String, IcalExpander?>? = null

    /** Когда пробовали загрузить в последний раз (успешно или нет); null — в этом запуске ещё нет. */
    @Volatile
    var lastAttempt: Long? = null
        private set

    fun links(): List<IcalLink> = store.get(KEY_LINKS).orEmpty().lines().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size < 4) null else IcalLink(f[0], f[1], f[2], f[3].toLongOrNull() ?: PALETTE[0])
    }

    fun status(linkId: String): LinkStatus {
        val p = store.get(KEY_PROBLEM + linkId).orEmpty()
        return LinkStatus(
            fetchedAt = store.get(KEY_FETCHED + linkId)?.toLongOrNull(),
            problem = FetchProblem.entries.firstOrNull { it.name == p.substringBefore(':') },
            httpCode = p.substringAfter(':', "").toIntOrNull(),
        )
    }

    /**
     * Добавить ссылку: сначала загрузка — неработающая ссылка не сохраняется ([FetchException]).
     * Имя пустое — из самого календаря, иначе [fallbackName].
     */
    fun add(input: String, name: String, fallbackName: String): IcalLink {
        val url = normalizeUrl(input) ?: throw IllegalArgumentException("not https")
        val body = fetcher.fetch(url)
        val parsed = IcalParser.parse(body) ?: throw FetchException(FetchProblem.NOT_CALENDAR)
        val existing = links()
        val color = PALETTE.firstOrNull { c -> existing.none { it.color == c } } ?: PALETTE[existing.size % PALETTE.size]
        val link = IcalLink(
            id = Random.nextLong().toULong().toString(16),
            url = url,
            name = name.clean().ifBlank { parsed.name?.clean().orEmpty() }.ifBlank { fallbackName },
            color = color,
        )
        saveLinks(existing + link)
        saveBody(link.id, body)
        return link
    }

    fun rename(linkId: String, name: String) {
        if (name.isBlank()) return
        saveLinks(links().map { if (it.id == linkId) it.copy(name = name.clean()) else it })
    }

    fun remove(linkId: String) {
        saveLinks(links().filter { it.id != linkId })
        listOf(KEY_BODY, KEY_FETCHED, KEY_PROBLEM).forEach { store.remove(it + linkId) }
        cache = cache?.minus(linkId)
    }

    /** Загрузить все ссылки; ошибка одной не мешает остальным, её прошлая загрузка остаётся. true — что-то изменилось. */
    fun refresh(): Boolean {
        lastAttempt = clock()
        var changed = false
        for (link in links()) {
            try {
                val body = fetcher.fetch(link.url)
                if (IcalParser.parse(body) == null) throw FetchException(FetchProblem.NOT_CALENDAR)
                if (body != store.get(KEY_BODY + link.id)) {
                    saveBody(link.id, body)
                    changed = true
                } else {
                    store.put(KEY_FETCHED + link.id, clock().toString())
                }
                if (store.get(KEY_PROBLEM + link.id).orEmpty().isNotEmpty()) {
                    store.remove(KEY_PROBLEM + link.id)
                    changed = true
                }
            } catch (e: FetchException) {
                store.put(KEY_PROBLEM + link.id, e.problem.name + (e.httpCode?.let { ":$it" } ?: ""))
                changed = true
            }
        }
        return changed
    }

    override fun calendars(): List<CalendarInfo> {
        return links().map { l ->
            CalendarInfo(
                id = PREFIX + l.id, name = l.name, accountName = "Google",
                kind = CalendarKind.GOOGLE, color = l.color, visibleInSystem = true, writable = false, primary = false,
            )
        }
    }

    override fun events(from: Long, to: Long, calendarIds: Set<String>): List<CalendarEvent> {
        val parsed = parsed()
        return links().filter { PREFIX + it.id in calendarIds }.flatMap { l -> parsed[l.id]?.events(from, to).orEmpty() }
    }

    override fun event(eventId: String): EventDraft? {
        val linkId = eventId.removePrefix(PREFIX).substringBefore('/')
        return parsed()[linkId]?.draft(eventId.substringAfter('/'))
    }

    override fun reminders(eventId: String): List<Int> = emptyList()

    // Ссылка iCal — только чтение: календари помечены как недоступные для записи, правки сюда не доходят.
    override fun create(draft: EventDraft): String = throw UnsupportedOperationException()
    override fun update(eventId: String, draft: EventDraft) = throw UnsupportedOperationException()
    override fun updateInstance(eventId: String, instanceStart: Long, draft: EventDraft) = throw UnsupportedOperationException()
    override fun delete(eventId: String) = throw UnsupportedOperationException()
    override fun deleteInstance(eventId: String, instanceStart: Long) = throw UnsupportedOperationException()

    private fun parsed(): Map<String, IcalExpander?> {
        cache?.let { return it }
        val built = links().associate { it.id to parse(it, store.get(KEY_BODY + it.id).orEmpty()) }
        cache = built
        return built
    }

    private fun parse(link: IcalLink, body: String): IcalExpander? =
        IcalParser.parse(body)?.let { IcalExpander(it, PREFIX + link.id, link.color, deviceZone()) }

    private fun saveBody(linkId: String, body: String) {
        store.put(KEY_BODY + linkId, body)
        store.put(KEY_FETCHED + linkId, clock().toString())
        store.remove(KEY_PROBLEM + linkId)
        val link = links().firstOrNull { it.id == linkId } ?: return
        cache = (cache ?: parsed()) + (linkId to parse(link, body))
    }

    private fun saveLinks(list: List<IcalLink>) {
        store.put(KEY_LINKS, list.joinToString("\n") { "${it.id}\t${it.url}\t${it.name}\t${it.color}" })
        val ids = list.mapTo(HashSet()) { it.id }
        cache = cache?.filterKeys { it in ids }
    }

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    companion object {
        const val PREFIX = "ical:"

        /** Только https (в webcal:// Google отдаёт тот же адрес): секретная ссылка не должна идти открытым текстом. */
        fun normalizeUrl(input: String): String? {
            val t = input.trim()
            val url = if (t.startsWith("webcal://", ignoreCase = true)) "https://" + t.substring("webcal://".length) else t
            if (!url.startsWith("https://", ignoreCase = true) || url.length <= "https://".length) return null
            if (url.any { it.isWhitespace() }) return null
            return url
        }

        // Цвета календарей Google: новый календарь получает ещё не занятый.
        private val PALETTE = listOf(0xFF039BE5, 0xFF33B679, 0xFFE67C73, 0xFFF6BF26, 0xFF8E24AA, 0xFFF4511E, 0xFF3F51B5, 0xFF0B8043)

        private const val KEY_LINKS = "calendar.links"
        private const val KEY_BODY = "calendar.link.body."
        private const val KEY_FETCHED = "calendar.link.fetched."
        private const val KEY_PROBLEM = "calendar.link.problem."
    }
}
