package de.suprexdev.subwaycoders

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * On project open, starts the [ClaudeNotifyServer] and installs the Claude Code hooks. Both calls are
 * idempotent, so this runs harmlessly for every opened project. Runs off the EDT (background
 * coroutine) — socket bind + file IO only, no tool-window access here.
 */
class ClaudeNotifyStartup : ProjectActivity {

    override suspend fun execute(project: Project) {
        val port = ClaudeNotifyServer.instance.ensureStarted() ?: return
        ClaudeHookInstaller.installIfNeeded(port)
    }
}
