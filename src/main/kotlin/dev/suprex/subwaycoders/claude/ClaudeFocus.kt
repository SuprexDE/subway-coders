package dev.suprex.subwaycoders.claude

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import dev.suprex.subwaycoders.config.SubwayCodersSettings
import dev.suprex.subwaycoders.ui.SubwayCodersPanel
import dev.suprex.subwaycoders.ui.SubwayCodersToolWindowFactory

object ClaudeFocus {

    private val log = logger<ClaudeFocus>()

    private const val TERMINAL_ID = "Terminal"

    private val HIDDEN_BY_CLAUDE = Key.create<MutableSet<String>>("SubwayCoders.hiddenByClaude")

    fun onEvent(type: String, cwd: String?) {
        if (!SubwayCodersSettings.instance.claudeFocusEnabled) return
        val project = resolveProject(cwd) ?: run {
            log.info("Claude event '$type' ignored: no matching open project for cwd=$cwd")
            return
        }
        if (project.isDisposed) return
        when (type) {
            "notification" -> collapse(project, "Claude needs you")
            "stop" -> collapse(project, "Claude is done")
            "prompt" -> expand(project)
        }
    }

    fun forget(project: Project, windowId: String) {
        project.getUserData(HIDDEN_BY_CLAUDE)?.remove(windowId)
    }

    private fun collapse(project: Project, banner: String) {
        val twm = ToolWindowManager.getInstance(project)
        val hidden = hiddenSet(project)
        for (id in SubwayCodersToolWindowFactory.WINDOW_IDS) {
            val window = twm.getToolWindow(id)?.takeIf { it.isVisible } ?: continue
            hidden += id
            panelOf(window)?.hideAnimated(banner) ?: window.hide()
        }
        twm.getToolWindow(TERMINAL_ID)?.activate(null)
    }

    private fun expand(project: Project) {
        val hidden = project.getUserData(HIDDEN_BY_CLAUDE) ?: return
        val ids = hidden.toList()
        hidden.clear()
        if (!SubwayCodersSettings.instance.claudeReopenEnabled) return
        val twm = ToolWindowManager.getInstance(project)
        val terminal = twm.getToolWindow(TERMINAL_ID)
        for (id in ids) {
            val window = twm.getToolWindow(id) ?: continue
            if (!window.isVisible && wouldDisplace(window, terminal)) continue
            panelOf(window)?.reveal() ?: window.show(null)
        }
    }

    private fun wouldDisplace(window: ToolWindow, terminal: ToolWindow?): Boolean {
        if (terminal == null || !terminal.isVisible) return false
        if (!window.isDockedInIde() || !terminal.isDockedInIde()) return false
        return window.anchor == terminal.anchor && window.isSplitMode == terminal.isSplitMode
    }

    private fun ToolWindow.isDockedInIde() = type == ToolWindowType.DOCKED || type == ToolWindowType.SLIDING

    private fun hiddenSet(project: Project): MutableSet<String> =
        project.getUserData(HIDDEN_BY_CLAUDE) ?: LinkedHashSet<String>().also { project.putUserData(HIDDEN_BY_CLAUDE, it) }

    private fun panelOf(window: ToolWindow): SubwayCodersPanel? =
        window.contentManagerIfCreated?.contents?.firstNotNullOfOrNull { it.getUserData(SubwayCodersPanel.KEY) }

    private fun resolveProject(cwd: String?): Project? {
        val open = ProjectManager.getInstance().openProjects.filterNot { it.isDisposed }
        if (cwd.isNullOrBlank()) return open.singleOrNull()
        open.firstOrNull { it.basePath != null && FileUtil.pathsEqual(it.basePath, cwd) }?.let { return it }
        open.firstOrNull { it.basePath != null && FileUtil.isAncestor(it.basePath!!, cwd, false) }?.let { return it }
        return open.singleOrNull()
    }
}
