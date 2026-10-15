package dev.suprex.subwaycoders.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
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
import dev.suprex.subwaycoders.claude.ClaudeHookInstaller
import dev.suprex.subwaycoders.claude.ClaudeNotifyServer
import dev.suprex.subwaycoders.config.SubwayCodersSettings
import dev.suprex.subwaycoders.config.VideoConfigService

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
        content.putUserData(SubwayCodersPanel.KEY, panel)
        toolWindow.contentManager.addContent(content)

        val toggleControls = object : ToggleAction("Show Controls") {
            override fun isSelected(e: AnActionEvent): Boolean = !panel.areControlsHidden()
            override fun setSelected(e: AnActionEvent, state: Boolean) = panel.setControlsHidden(!state)
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        val toggleClaudeFocus = object : ToggleAction("Focus Terminal on Claude Events") {
            override fun isSelected(e: AnActionEvent): Boolean = SubwayCodersSettings.instance.claudeFocusEnabled
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                SubwayCodersSettings.instance.claudeFocusEnabled = state
                ApplicationManager.getApplication().executeOnPooledThread {
                    ClaudeHookInstaller.sync(state)
                    if (state && ClaudeNotifyServer.instance.ensureStarted() == null) notifyPortBusy(project)
                }
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        val toggleClaudeReopen = object : ToggleAction("Reopen After Replying to Claude") {
            override fun isSelected(e: AnActionEvent): Boolean = SubwayCodersSettings.instance.claudeReopenEnabled
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                SubwayCodersSettings.instance.claudeReopenEnabled = state
            }
            override fun update(e: AnActionEvent) {
                super.update(e)
                e.presentation.isEnabled = SubwayCodersSettings.instance.claudeFocusEnabled
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        val editConfig = object : AnAction("Edit Config…", "Edit categories and clips", AllIcons.General.Settings) {
            override fun actionPerformed(e: AnActionEvent) = panel.openConfig()
        }
        val gearActions = DefaultActionGroup().apply {
            add(toggleControls)
            add(toggleClaudeFocus)
            add(toggleClaudeReopen)
            addSeparator()
            add(editConfig)
        }
        toolWindow.setAdditionalGearActions(gearActions)
    }

    private fun notifyPortBusy(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                "Claude Code events not available",
                "Port ${ClaudeNotifyServer.PORT} is already in use, probably by another IDE window. " +
                    "That window will handle Claude Code events instead.",
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    companion object {
        private const val NOTIFICATION_GROUP = "Subway Coders"

        private val DEFAULT_CATEGORY = mapOf(
            "Subway Coders (Left)" to "Minecraft Parkour",
            "Subway Coders (Right)" to "Subway Surfers",
            "Subway Coders (Top)" to "Temple Run",
            "Subway Coders (Bottom)" to "Minecraft Story",
        )

        val WINDOW_IDS: Set<String> = DEFAULT_CATEGORY.keys
    }
}
