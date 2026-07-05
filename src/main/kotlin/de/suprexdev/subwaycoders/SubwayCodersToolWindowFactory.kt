package de.suprexdev.subwaycoders

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class SubwayCodersToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = VideoConfigService.instance
        service.ensureUserFile()

        val categories = service.config.categories.map { it.name }
        val preferred = DEFAULT_CATEGORY[toolWindow.id]
        val defaultCategory = preferred?.takeIf { it in categories } ?: categories.firstOrNull().orEmpty()

        val panel = SubwayCodersPanel(project, toolWindow.id, defaultCategory)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)

        val toggleControls = object : ToggleAction("Show Controls") {
            override fun isSelected(e: AnActionEvent): Boolean = !panel.areControlsHidden()
            override fun setSelected(e: AnActionEvent, state: Boolean) = panel.setControlsHidden(!state)
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        // Global (shared by all four windows' gear menus): snap back to the terminal on Claude events.
        val toggleClaudeFocus = object : ToggleAction("Focus Terminal on Claude Events") {
            override fun isSelected(e: AnActionEvent): Boolean = SubwayCodersSettings.instance.claudeFocusEnabled
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                SubwayCodersSettings.instance.claudeFocusEnabled = state
                // Install/remove the Claude Code hooks off the EDT (settings.json IO).
                ApplicationManager.getApplication().executeOnPooledThread { ClaudeHookInstaller.sync(state) }
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        val editConfig = object : AnAction("Edit Config…", "Edit categories and clips", AllIcons.General.Settings) {
            override fun actionPerformed(e: AnActionEvent) = panel.openConfig()
        }
        val gearActions = DefaultActionGroup().apply {
            add(toggleControls)
            add(toggleClaudeFocus)
            addSeparator()
            add(editConfig)
        }
        toolWindow.setAdditionalGearActions(gearActions)
    }

    private companion object {
        val DEFAULT_CATEGORY = mapOf(
            "Subway Coders (Left)" to "Minecraft Parkour",
            "Subway Coders (Right)" to "Subway Surfers",
            "Subway Coders (Top)" to "Temple Run",
            "Subway Coders (Bottom)" to "Minecraft Story",
        )
    }
}
