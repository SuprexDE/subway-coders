package de.suprexdev.subwaycoders

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * On project open, starts the [ClaudeNotifyServer] and syncs the Claude Code hooks to the current
 * toggle state. Both calls are idempotent, so this runs harmlessly for every opened project. Runs off
 * the EDT (background coroutine) — socket bind + file IO only, no tool-window access here.
 */
class ClaudeNotifyStartup : ProjectActivity {

    override suspend fun execute(project: Project) {
        ClaudeNotifyServer.instance.ensureStarted()
        ClaudeHookInstaller.sync(SubwayCodersSettings.instance.claudeFocusEnabled)
    }
}
