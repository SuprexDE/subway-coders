package dev.suprex.subwaycoders.claude

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.suprex.subwaycoders.config.SubwayCodersSettings

class ClaudeNotifyStartup : ProjectActivity {

    override suspend fun execute(project: Project) {
        ClaudeNotifyServer.instance.ensureStarted()
        ClaudeHookInstaller.sync(SubwayCodersSettings.instance.claudeFocusEnabled)
    }
}