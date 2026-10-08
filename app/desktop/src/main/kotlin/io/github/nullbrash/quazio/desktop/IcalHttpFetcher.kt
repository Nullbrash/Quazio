package io.github.nullbrash.quazio.desktop

import io.github.nullbrash.quazio.feature.calendar.ical.FetchException
import io.github.nullbrash.quazio.feature.calendar.ical.FetchProblem
import io.github.nullbrash.quazio.feature.calendar.ical.IcalFetcher
import io.github.nullbrash.quazio.net.http.HttpFailure
import io.github.nullbrash.quazio.net.http.HttpsText

/** Загрузка ссылок iCal через сетевой модуль ПК; ошибки — словами календаря, без адреса. */
internal class IcalHttpFetcher(version: String) : IcalFetcher {
    private val http = HttpsText(userAgent = "Quazio/$version")

    override fun fetch(url: String): String = try {
        http.get(url)
    } catch (e: HttpFailure) {
        throw when (e.kind) {
            HttpFailure.Kind.OFFLINE -> FetchException(FetchProblem.OFFLINE)
            HttpFailure.Kind.TOO_LARGE -> FetchException(FetchProblem.TOO_LARGE)
            // Сброшенная в Google секретная ссылка отвечает 404; 401/403 — тоже «ссылка не работает».
            HttpFailure.Kind.STATUS -> when (e.code) {
                401, 403, 404, 410 -> FetchException(FetchProblem.NOT_FOUND, e.code)
                else -> FetchException(FetchProblem.HTTP, e.code)
            }
        }
    } catch (e: IllegalArgumentException) {
        throw FetchException(FetchProblem.NOT_FOUND)
    }
}
