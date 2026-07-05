package de.suprexdev.subwaycoders

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Auto-installs the Claude Code hooks that drive [ClaudeNotifyServer]. Merges a `Notification` and a
 * `Stop` command hook into `~/.claude/settings.json` non-destructively (tree model, so unknown user
 * settings survive) and idempotently (skips if a `/claude-event` command is already present).
 */
object ClaudeHookInstaller {

    private val log = logger<ClaudeHookInstaller>()
    private val mapper = jacksonObjectMapper()
    private val installed = AtomicBoolean(false)

    private const val MARKER = "/claude-event"
    private val EVENTS = listOf("Notification" to "notification", "Stop" to "stop")

    fun installIfNeeded(port: Int) {
        if (!installed.compareAndSet(false, true)) return
        runCatching { install(port) }.onFailure { log.warn("Could not install Claude Code hooks", it) }
    }

    private fun install(port: Int) {
        val settings = Path.of(System.getProperty("user.home"), ".claude", "settings.json")
        val root = readRoot(settings) ?: return
        val hooks = root.get("hooks") as? ObjectNode
            ?: mapper.createObjectNode().also { root.set<JsonNode>("hooks", it) }

        var changed = false
        for ((event, type) in EVENTS) {
            val bucket = hooks.withArray(event)
            if (bucket.any { group -> commandsOf(group).any { it.contains(MARKER) } }) continue
            bucket.add(hookEntry(cmdFor(type, port)))
            changed = true
        }
        if (!changed) return

        runCatching {
            Files.createDirectories(settings.parent)
            Files.writeString(settings, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root))
            log.info("Installed Subway Coders Claude Code hooks into $settings")
        }.onFailure { log.warn("Could not write Claude Code settings at $settings", it) }
    }

    /** Missing file → empty root; unparseable → null so we never clobber the user's file. */
    private fun readRoot(settings: Path): ObjectNode? {
        if (!Files.exists(settings)) return mapper.createObjectNode()
        return runCatching { mapper.readTree(Files.readString(settings)) as? ObjectNode }
            .onFailure { log.warn("Claude Code settings.json is unreadable; leaving it untouched", it) }
            .getOrNull()
    }

    private fun commandsOf(group: JsonNode): List<String> =
        group.path("hooks").mapNotNull { it.path("command").takeIf { c -> c.isTextual }?.asText() }

    private fun hookEntry(command: String): ObjectNode =
        mapper.createObjectNode().apply {
            withArray("hooks").add(mapper.createObjectNode().put("type", "command").put("command", command))
        }

    // Single query param → no `&` to escape, so one string works in both `sh` and `cmd.exe`.
    // `--data-binary @-` forwards the hook's stdin JSON (with `cwd`) to the endpoint.
    private fun cmdFor(type: String, port: Int): String =
        "curl -sS -X POST --data-binary @- \"http://127.0.0.1:$port/claude-event?type=$type\""
}
