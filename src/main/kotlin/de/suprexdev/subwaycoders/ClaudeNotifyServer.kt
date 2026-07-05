package de.suprexdev.subwaycoders

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

/**
 * Loopback HTTP server that lets Claude Code hooks tell the IDE "Claude needs you". Claude runs in the
 * terminal where the IDE can't observe it, so [ClaudeHookInstaller] wires `Notification` and `Stop`
 * hooks that `curl` [PORT]`/claude-event?type=…`, forwarding the hook's stdin JSON (which carries
 * `cwd`) as the body. Each event hands off to [ClaudeFocus] to hide the players and focus the terminal.
 *
 * The port is fixed so the installed hook command stays a static, portable string. Only the first IDE
 * process to bind it owns Claude routing; later ones hit [BindException] and leave the feature inert.
 */
@Service(Service.Level.APP)
class ClaudeNotifyServer : Disposable {

    private val log = logger<ClaudeNotifyServer>()
    private val mapper = jacksonObjectMapper()

    @Volatile
    private var server: HttpServer? = null
    private var attempted = false

    /** @return the bound port, or null if the port is taken or the server failed to start. */
    @Synchronized
    fun ensureStarted(): Int? {
        server?.let { return PORT }
        if (attempted) return null
        attempted = true
        return try {
            HttpServer.create(InetSocketAddress("127.0.0.1", PORT), 0).apply {
                createContext("/claude-event", ::handle)
                start()
                server = this
            }
            log.info("Subway Coders Claude bridge listening on 127.0.0.1:$PORT")
            PORT
        } catch (e: BindException) {
            log.info("Subway Coders Claude bridge port $PORT busy; another IDE handles Claude events", e)
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
            // Respond before the UI work so the hook's curl returns immediately, off the server thread.
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
