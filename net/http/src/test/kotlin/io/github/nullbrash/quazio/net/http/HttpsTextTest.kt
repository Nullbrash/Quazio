package io.github.nullbrash.quazio.net.http

import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Локальный сервер вместо настоящей сети: сжатие, кодировка, коды ответа, предел размера. */
class HttpsTextTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/plain") { ex ->
            val body = "BEGIN:VCALENDAR\nSUMMARY:Встреча\n".toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "text/calendar; charset=UTF-8")
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        createContext("/gzip") { ex ->
            val packed = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write("сжато".toByteArray()) } }.toByteArray()
            ex.responseHeaders.add("Content-Encoding", "gzip")
            ex.sendResponseHeaders(200, packed.size.toLong()); ex.responseBody.use { it.write(packed) }
        }
        createContext("/gone") { ex -> ex.sendResponseHeaders(404, -1); ex.close() }
        createContext("/big") { ex ->
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.use { out -> runCatching { repeat(64) { out.write(ByteArray(1024)) } } }
        }
        start()
    }
    private fun uri(path: String) = URI("http://127.0.0.1:${server.address.port}$path")
    private val http = HttpsText(userAgent = "Quazio/test", maxBytes = 16 * 1024)

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun readsPlainAndGzip() {
        assertEquals("BEGIN:VCALENDAR\nSUMMARY:Встреча\n", http.fetch(uri("/plain")))
        assertEquals("сжато", http.fetch(uri("/gzip")))
    }

    @Test
    fun failuresAreTypedWithoutAddress() {
        assertEquals(404, assertFailsWith<HttpFailure> { http.fetch(uri("/gone")) }.code)
        assertEquals(HttpFailure.Kind.TOO_LARGE, assertFailsWith<HttpFailure> { http.fetch(uri("/big")) }.kind)
        val port = server.address.port
        server.stop(0)
        val offline = assertFailsWith<HttpFailure> { http.fetch(URI("http://127.0.0.1:$port/plain")) }
        assertEquals(HttpFailure.Kind.OFFLINE, offline.kind)
        assertEquals("OFFLINE", offline.message) // в тексте ошибки нет адреса — он бывает секретным
    }

    @Test
    fun onlyHttps() {
        assertFailsWith<IllegalArgumentException> { http.get("http://calendar.google.com/x.ics") }
    }
}
