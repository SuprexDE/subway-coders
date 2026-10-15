package dev.suprex.subwaycoders.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.Topic
import java.nio.file.Files
import java.nio.file.Path

data class ClipCategory(
    val name: String = "",
    val clips: List<String> = emptyList(),
)

data class VideoConfig(
    val categories: List<ClipCategory> = emptyList(),
)

fun interface VideoConfigListener {
    fun configChanged()

    companion object {
        @Topic.AppLevel
        val TOPIC = Topic(VideoConfigListener::class.java)
    }
}

@Service(Service.Level.APP)
class VideoConfigService : Disposable {

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val log = logger<VideoConfigService>()

    val configFile: Path = Path.of(PathManager.getConfigPath(), "subway-coders", "categories.json")
    private val configPath = configFile.toString()

    @Volatile
    var config: VideoConfig = parse(readConfigJson())
        private set

    init {
        val app = ApplicationManager.getApplication()
        app.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.none { FileUtil.pathsEqual(it.path, configPath) }) return
                    app.executeOnPooledThread {
                        reload()
                        app.invokeLater { app.messageBus.syncPublisher(VideoConfigListener.TOPIC).configChanged() }
                    }
                }
            },
        )
    }

    override fun dispose() = Unit

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