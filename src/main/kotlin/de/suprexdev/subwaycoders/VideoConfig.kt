package de.suprexdev.subwaycoders

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path

data class ClipCategory(
    val name: String = "",
    val clips: List<String> = emptyList(),
)

data class VideoConfig(
    val categories: List<ClipCategory> = emptyList(),
)

/**
 * Loads the category -> clips mapping. The bundled `default-categories.json` is the fallback; a
 * user copy at `<config>/subway-coders/categories.json` (created on first run) takes precedence and
 * is what users edit to define their own categories and clips.
 */
@Service(Service.Level.APP)
class VideoConfigService {

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val log = logger<VideoConfigService>()

    val configFile: Path = Path.of(PathManager.getConfigPath(), "subway-coders", "categories.json")

    @Volatile
    var config: VideoConfig = parse(readConfigJson())
        private set

    fun reload() {
        config = parse(readConfigJson())
    }

    fun ensureUserFile() {
        if (Files.exists(configFile)) return
        runCatching {
            Files.createDirectories(configFile.parent)
            bundledJson()?.let { Files.writeString(configFile, it) }
        }.onFailure { log.warn("Could not write default Subway Coders config", it) }
    }

    private fun readConfigJson(): String? =
        runCatching { if (Files.exists(configFile)) Files.readString(configFile) else null }.getOrNull()
            ?: bundledJson()

    private fun parse(json: String?): VideoConfig =
        json?.let { runCatching { mapper.readValue<VideoConfig>(it) }.getOrNull() }
            ?.takeIf { it.categories.isNotEmpty() }
            ?: VideoConfig()

    private fun bundledJson(): String? =
        javaClass.getResourceAsStream("/config/default-categories.json")?.use { it.readBytes().decodeToString() }

    companion object {
        val instance: VideoConfigService get() = service()
    }
}
