package dev.suprex.subwaycoders.player

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.suprex.subwaycoders.youtube.extractVideoId
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Service(Service.Level.APP)
class EmbedServer : Disposable {

    private val log = logger<EmbedServer>()

    @Volatile
    private var server: HttpServer? = null

    @Synchronized
    private fun ensureStarted(): HttpServer =
        server ?: HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/embed", ::handleEmbed)
            start()
            server = this
        }

    fun pageUrl(videoId: String, muted: Boolean): String {
        val port = ensureStarted().address.port
        return "http://127.0.0.1:$port/embed?v=$videoId&mute=${if (muted) 1 else 0}"
    }

    private fun handleEmbed(exchange: HttpExchange) {
        try {
            val params = parseQuery(exchange.requestURI.rawQuery)
            val id = extractVideoId(params["v"])
            if (id == null) {
                exchange.sendResponseHeaders(400, -1)
                return
            }
            val origin = "http://127.0.0.1:${exchange.localAddress.port}"
            val body = PlayerHtml.embedPage(id, params["mute"] != "0", origin).toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        } catch (t: Throwable) {
            log.warn("Failed to serve Subway Coders embed page", t)
        } finally {
            exchange.close()
        }
    }

    private fun parseQuery(raw: String?): Map<String, String> =
        raw?.split("&")?.mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i < 0) null else decode(pair.substring(0, i)) to decode(pair.substring(i + 1))
        }?.toMap().orEmpty()

    private fun decode(s: String): String = URLDecoder.decode(s, StandardCharsets.UTF_8)

    @Synchronized
    override fun dispose() {
        server?.stop(0)
        server = null
    }

    companion object {
        val instance: EmbedServer get() = service()
    }
}