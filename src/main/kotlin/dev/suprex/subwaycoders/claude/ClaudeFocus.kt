package dev.suprex.subwaycoders.claude

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.ToolWindowManager
import dev.suprex.subwaycoders.config.SubwayCodersSettings

object ClaudeFocus {

    private val log = logger<ClaudeFocus>()

    private val SUBWAY_WINDOW_IDS = listOf(
        "Subway Coders (Left)",
        "Subway Coders (Right)",
        "Subway Coders (Top)",
        "Subway Coders (Bottom)",
    )

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
        twm.getToolWindow(TERMINAL_ID)?.activate(null)
    }

    private fun resolveProject(cwd: String?): Project? {
        val open = ProjectManager.getInstance().openProjects.filterNot { it.isDisposed }
        if (cwd.isNullOrBlank()) return open.singleOrNull()
        open.firstOrNull { it.basePath != null && FileUtil.pathsEqual(it.basePath, cwd) }?.let { return it }
        open.firstOrNull { it.basePath != null && FileUtil.isAncestor(it.basePath!!, cwd, false) }?.let { return it }
        return open.singleOrNull()
    }
}