package io.github.nullbrash.quazio.net.http

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream

/** Почему не удалось: без адреса и текста ответа — адрес бывает секретным (ссылки iCal). */
class HttpFailure(val kind: Kind, val code: Int? = null) : IOException("$kind${code?.let { " $it" } ?: ""}") {
    enum class Kind { OFFLINE, STATUS, TOO_LARGE }
}

/**
 * Загрузка текста по https встроенным в Java клиентом — без сторонних библиотек. Только GET,
 * только https, ограничение размера (выгрузка календаря за годы — единицы мегабайт; куча ПК-версии
 * ограничена 256 МБ).
 */
class HttpsText(
    private val userAgent: String,
    private val maxBytes: Int = 32 * 1024 * 1024,
    private val timeout: Duration = Duration.ofSeconds(30),
) {
    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            // Только https → https: с защищённого адреса на открытый не уходим.
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    fun get(url: String): String {
        val uri = URI(url)
        require(uri.scheme.equals("https", ignoreCase = true)) { "only https" }
        return fetch(uri)
    }

    /** Без проверки https — только для проверок на локальном сервере. */
    internal fun fetch(uri: URI): String {
        val request = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("User-Agent", userAgent)
            .header("Accept-Encoding", "gzip")
            .GET()
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: IOException) {
            throw HttpFailure(HttpFailure.Kind.OFFLINE)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw HttpFailure(HttpFailure.Kind.OFFLINE)
        }
        response.body().use { raw ->
            if (response.statusCode() !in 200..299) throw HttpFailure(HttpFailure.Kind.STATUS, response.statusCode())
            val gzip = response.headers().firstValue("Content-Encoding").orElse("").equals("gzip", ignoreCase = true)
            val bytes = try {
                readLimited(if (gzip) GZIPInputStream(raw) else raw)
            } catch (e: HttpFailure) {
                throw e
            } catch (e: IOException) {
                throw HttpFailure(HttpFailure.Kind.OFFLINE)
            }
            val charset = response.headers().firstValue("Content-Type").orElse("")
                .substringAfter("charset=", "").substringBefore(';').trim().ifEmpty { "UTF-8" }
            return String(bytes, runCatching { charset(charset) }.getOrDefault(Charsets.UTF_8))
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > maxBytes) throw HttpFailure(HttpFailure.Kind.TOO_LARGE)
        }
        return out.toByteArray()
    }
}
