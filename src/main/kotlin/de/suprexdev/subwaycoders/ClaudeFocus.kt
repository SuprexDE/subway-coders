package de.suprexdev.subwaycoders

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Reaction to a Claude Code event: hide the four Subway Coders players and focus the terminal, so the
 * user snaps back from the brain-rot feed to whatever Claude just asked. Runs on the EDT (dispatched
 * by [ClaudeNotifyServer]); gated by the global [SubwayCodersSettings.claudeFocusEnabled] toggle.
 */
object ClaudeFocus {

    private val log = logger<ClaudeFocus>()

    /** Tool window ids of the four edge players, matching the registrations in plugin.xml. */
    private val SUBWAY_WINDOW_IDS = listOf(
        "Subway Coders (Left)",
        "Subway Coders (Right)",
        "Subway Coders (Top)",
        "Subway Coders (Bottom)",
    )

    /** The built-in terminal tool window; referenced by string to avoid a hard plugin dependency. */
    private const val TERMINAL_ID = "Terminal"

    fun onEvent(type: String, cwd: String?) {
        if (!SubwayCodersSettings.instance.claudeFocusEnabled) return
        val project = resolveProject(cwd) ?: run {
            log.info("Claude event '$type' ignored: no matching open project for cwd=$cwd")
            return
        }
        if (project.isDisposed) return
        val twm = ToolWindowManager.getInstance(project)
        SUBWAY_WINDOW_IDS.forEach { twm.getToolWindow(it)?.hide() }
        // Activate last so the terminal wins focus even though the bottom stripe may briefly reflow.
        twm.getToolWindow(TERMINAL_ID)?.activate(null)
    }

    /** Match the hook's cwd to an open project (exact base path, then ancestor); else the sole project. */
    private fun resolveProject(cwd: String?): Project? {
        val open = ProjectManager.getInstance().openProjects.filterNot { it.isDisposed }
        if (cwd.isNullOrBlank()) return open.singleOrNull()
        open.firstOrNull { it.basePath != null && FileUtil.pathsEqual(it.basePath, cwd) }?.let { return it }
        open.firstOrNull { it.basePath != null && FileUtil.isAncestor(it.basePath!!, cwd, false) }?.let { return it }
        return open.singleOrNull()
    }
}
