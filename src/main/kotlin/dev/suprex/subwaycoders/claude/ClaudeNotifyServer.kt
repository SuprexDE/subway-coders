package dev.suprex.subwaycoders.claude

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.BindException
import java.net.InetSocketAddress

@Service(Service.Level.APP)
class ClaudeNotifyServer : Disposable {

    private val log = logger<ClaudeNotifyServer>()
    private val mapper = ObjectMapper()

    @Volatile
    private var server: HttpServer? = null

    @Synchronized
    fun ensureStarted(): Int? {
        server?.let { return PORT }
        return try {
            HttpServer.create(InetSocketAddress("127.0.0.1", PORT), 0).apply {
                createContext("/claude-event", ::handle)
                start()
                server = this
            }
            log.info("Subway Coders Claude bridge listening on 127.0.0.1:$PORT")
            PORT
        } catch (e: BindException) {
            log.info("Subway Coders Claude bridge port $PORT busy; another IDE handles Claude events")
            null
        } catch (t: Throwable) {
            log.warn("Failed to start Subway Coders Claude bridge", t)
            null
        }
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val type = exchange.requestURI.query?.substringAfter("type=", "")?.substringBefore("&").orEmpty()
            val cwd = runCatching {
                mapper.readTree(exchange.requestBody).path("cwd").takeIf { it.isTextual }?.asText()
            }.getOrNull()
            exchange.sendResponseHeaders(204, -1)
            ApplicationManager.getApplication().invokeLater({ ClaudeFocus.onEvent(type, cwd) }, ModalityState.any())
        } catch (t: Throwable) {
            log.warn("Failed to handle Claude event", t)
        } finally {
            exchange.close()
        }
    }

    @Synchronized
    override fun dispose() {
        server?.stop(0)
        server = null
    }

    companion object {
        const val PORT = 63451

        val instance: ClaudeNotifyServer get() = service()
    }
}