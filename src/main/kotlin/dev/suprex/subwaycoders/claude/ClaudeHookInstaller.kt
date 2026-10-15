package dev.suprex.subwaycoders.claude

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path

object ClaudeHookInstaller {

    private val log = logger<ClaudeHookInstaller>()
    private val mapper = ObjectMapper()

    private const val MARKER = "/claude-event"
    private val EVENTS = mapOf(
        "Notification" to "notification",
        "Stop" to "stop",
        "UserPromptSubmit" to "prompt",
    )

    fun sync(enabled: Boolean) = if (enabled) install() else uninstall()

    private fun install() = edit { root ->
        val hooks = root.get("hooks") as? ObjectNode
            ?: mapper.createObjectNode().also { root.set<JsonNode>("hooks", it) }
        var changed = hooks.fieldNames().asSequence().toList()
            .filter { it !in EVENTS }
            .map { strip(hooks, it) }
            .any { it }
        for ((event, type) in EVENTS) {
            val entry = hookEntry(cmdFor(type))
            if (hooks.withArray(event).filter(::isOurs) == listOf(entry)) continue
            strip(hooks, event)
            hooks.withArray(event).add(entry)
            changed = true
        }
        changed
    }

    private fun uninstall() = edit { root ->
        val hooks = root.get("hooks") as? ObjectNode ?: return@edit false
        val changed = hooks.fieldNames().asSequence().toList().map { strip(hooks, it) }.any { it }
        if (hooks.isEmpty) root.remove("hooks")
        changed
    }

    private fun strip(hooks: ObjectNode, event: String): Boolean {
        val bucket = hooks.get(event) as? ArrayNode ?: return false
        var changed = false
        val groups = bucket.toList()
        bucket.removeAll()
        for (group in groups) {
            val commands = group.path("hooks") as? ArrayNode
            val kept = commands?.filterNot { it.path("command").asText().contains(MARKER) }
            if (commands != null && kept != null && kept.size != commands.size()) {
                changed = true
                if (kept.isEmpty()) continue
                commands.removeAll()
                kept.forEach(commands::add)
            }
            bucket.add(group)
        }
        if (bucket.isEmpty) hooks.remove(event)
        return changed
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

    private fun isOurs(group: JsonNode): Boolean =
        group.path("hooks").any { it.path("command").asText().contains(MARKER) }

    private fun hookEntry(command: String): ObjectNode =
        mapper.createObjectNode().apply {
            withArray("hooks").add(
                mapper.createObjectNode().put("type", "command").put("command", command).put("async", true),
            )
        }

    private fun cmdFor(type: String): String =
        "curl -s -m 2 -X POST --data-binary @- \"http://127.0.0.1:${ClaudeNotifyServer.PORT}/claude-event?type=$type\""
}