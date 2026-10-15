package dev.suprex.subwaycoders.claude

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path

object ClaudeHookInstaller {

    private val log = logger<ClaudeHookInstaller>()
    private val mapper = jacksonObjectMapper()

    private const val MARKER = "/claude-event"
    private val EVENTS = listOf("Notification" to "notification", "Stop" to "stop")

    fun sync(enabled: Boolean) = if (enabled) install() else uninstall()

    private fun install() = edit { root ->
        val hooks = root.get("hooks") as? ObjectNode
            ?: mapper.createObjectNode().also { root.set<JsonNode>("hooks", it) }
        var changed = false
        for ((event, type) in EVENTS) {
            val bucket = hooks.withArray(event)
            if (bucket.any { group -> commandsOf(group).any { it.contains(MARKER) } }) continue
            bucket.add(hookEntry(cmdFor(type)))
            changed = true
        }
        changed
    }

    private fun uninstall() = edit { root ->
        val hooks = root.get("hooks") as? ObjectNode ?: return@edit false
        var changed = false
        for ((event, _) in EVENTS) {
            val bucket = hooks.get(event) as? ArrayNode ?: continue
            val kept = bucket.filterNot { group -> commandsOf(group).any { it.contains(MARKER) } }
            if (kept.size == bucket.size()) continue
            changed = true
            if (kept.isEmpty()) hooks.remove(event) else bucket.apply { removeAll(); kept.forEach(::add) }
        }
        if (hooks.isEmpty) root.remove("hooks")
        changed
    }

    @Synchronized
    private fun edit(mutate: (ObjectNode) -> Boolean) {
        val settings = Path.of(System.getProperty("user.home"), ".claude", "settings.json")
        runCatching {
            val root = readRoot(settings) ?: return
            if (!mutate(root)) return
            Files.createDirectories(settings.parent)
            Files.writeString(settings, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root))
            log.info("Updated Subway Coders Claude Code hooks in $settings")
        }.onFailure { log.warn("Could not update Claude Code hooks at $settings", it) }
    }

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

    private fun cmdFor(type: String): String =
        "curl -s -m 2 -X POST --data-binary @- \"http://127.0.0.1:${ClaudeNotifyServer.PORT}/claude-event?type=$type\""
}