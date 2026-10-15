package dev.suprex.subwaycoders.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import dev.suprex.subwaycoders.config.SubwayCodersSettings
import dev.suprex.subwaycoders.config.VideoConfigListener
import dev.suprex.subwaycoders.config.VideoConfigService
import dev.suprex.subwaycoders.player.PlayerController
import dev.suprex.subwaycoders.youtube.extractVideoId
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class SubwayCodersPanel(
    private val project: Project,
    private val windowId: String,
    defaultCategory: String,
) : JPanel(BorderLayout()), Disposable {

    private val config = SubwayCodersSettings.instance.configFor(windowId, defaultCategory)
    private val controller = PlayerController(config)
    private var lastVisible = true

    private var actionToolbar: ActionToolbar? = null

    init {
        Disposer.register(this, controller)
        if (controller.supported) {
            ensureValidCategory()
            val bar = buildToolbar().component
            bar.isVisible = !config.controlsHidden
            add(bar, BorderLayout.NORTH)
            add(controller.component, BorderLayout.CENTER)
            val appBus = ApplicationManager.getApplication().messageBus.connect(this)
            appBus.subscribe(LafManagerListener.TOPIC, LafManagerListener { controller.reloadIfThemeFlipped() })
            appBus.subscribe(VideoConfigListener.TOPIC, VideoConfigListener { onConfigChanged() })
            project.messageBus.connect(this)
                .subscribe(
                    ToolWindowManagerListener.TOPIC,
                    object : ToolWindowManagerListener {
                        override fun stateChanged(manager: ToolWindowManager) {
                            val visible = manager.getToolWindow(windowId)?.isVisible == true
                            if (visible == lastVisible) return
                            lastVisible = visible
                            controller.setPlaybackPaused(!visible)
                        }
                    },
                )
            controller.reload()
        } else {
            add(controller.component, BorderLayout.CENTER)
        }
    }

    private fun categories() = VideoConfigService.instance.config.categories

    private fun categoryNames() = categories().map { it.name }

    private fun buildToolbar(): ActionToolbar {
        val group = DefaultActionGroup().apply {
            add(CategoryAction())
            add(ShuffleAction())
            add(OpenInBrowserAction())
        }
        return ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true).also {
            it.targetComponent = this
            actionToolbar = it
        }
    }

    fun openConfig() = openConfigFile()

    fun areControlsHidden(): Boolean = config.controlsHidden

    fun setControlsHidden(hidden: Boolean) {
        config.controlsHidden = hidden
        actionToolbar?.component?.let {
            it.isVisible = !hidden
            it.revalidate()
            it.repaint()
        }
    }

    private fun selectCategory(name: String) {
        config.categoryName = name
        config.customUrl = ""
        config.doomscrollEnabled = false
        controller.reload()
        actionToolbar?.updateActionsAsync()
    }

    private fun shuffle() {
        VideoConfigService.instance.reload()
        ensureValidCategory()
        controller.reload()
        actionToolbar?.updateActionsAsync()
    }

    private fun onConfigChanged() {
        ensureValidCategory()
        controller.reloadIfClipRemoved()
        actionToolbar?.updateActionsAsync()
    }

    private fun promptForUrl() {
        val input = Messages.showInputDialog(
            project,
            "Paste a YouTube or direct video URL:",
            "Play Video URL",
            null,
            config.customUrl,
            UrlValidator,
        ) ?: return
        config.customUrl = input.trim()
        config.doomscrollEnabled = false
        controller.reload()
        actionToolbar?.updateActionsAsync()
    }

    private fun clearCustomUrl() {
        config.customUrl = ""
        config.doomscrollEnabled = false
        controller.reload()
        actionToolbar?.updateActionsAsync()
    }

    private fun hasCustomUrl() = config.customUrl.isNotBlank()

    private fun ensureValidCategory() {
        val names = categoryNames()
        if (config.categoryName !in names) {
            config.categoryName = names.firstOrNull().orEmpty()
        }
    }

    private fun toggleDoomscroll(enabled: Boolean) {
        config.doomscrollEnabled = enabled
        if (enabled) config.customUrl = ""
        controller.reload()
        actionToolbar?.updateActionsAsync()
    }

    private fun openConfigFile() {
        val service = VideoConfigService.instance
        service.ensureUserFile()
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(service.configFile) ?: return
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    override fun dispose() = Unit

    private inner class CategoryAction : ComboBoxAction() {
        override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup =
            DefaultActionGroup().apply {
                for (name in categoryNames()) {
                    add(object : ToggleAction(name) {
                        override fun isSelected(e: AnActionEvent) =
                            !config.doomscrollEnabled && !hasCustomUrl() && config.categoryName == name

                        override fun setSelected(e: AnActionEvent, state: Boolean) = selectCategory(name)

                        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                    })
                }
                addSeparator()
                add(object : ToggleAction("Doomscroll (YouTube Shorts)", null, AllIcons.Actions.MoveDown) {
                    override fun isSelected(e: AnActionEvent) = config.doomscrollEnabled
                    override fun setSelected(e: AnActionEvent, state: Boolean) = toggleDoomscroll(state)
                    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                })
                addSeparator()
                add(object : AnAction("Paste video URL…", null, AllIcons.Actions.MenuPaste) {
                    override fun actionPerformed(e: AnActionEvent) = promptForUrl()
                })
                if (hasCustomUrl()) {
                    add(object : AnAction("Clear custom URL", null, AllIcons.Actions.Cancel) {
                        override fun actionPerformed(e: AnActionEvent) = clearCustomUrl()
                    })
                }
            }

        override fun update(e: AnActionEvent) {
            e.presentation.text = when {
                config.doomscrollEnabled -> "Doomscroll"
                hasCustomUrl() -> "Custom URL"
                else -> config.categoryName.ifEmpty { "Category" }
            }
            e.presentation.description = config.customUrl.ifBlank { null }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class ShuffleAction : AnAction(
        "Shuffle",
        "Play another clip from this category and re-read the config",
        AllIcons.Actions.Refresh,
    ) {
        override fun actionPerformed(e: AnActionEvent) = shuffle()

        override fun update(e: AnActionEvent) {
            e.presentation.isVisible = !config.doomscrollEnabled
            e.presentation.isEnabled = !hasCustomUrl()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class OpenInBrowserAction : AnAction(
        "Open in Browser",
        "Open the current clip in your browser",
        AllIcons.General.Web,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            controller.externalUrl()?.let { BrowserUtil.browse(it) }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = controller.externalUrl() != null
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private object UrlValidator : InputValidator {
        override fun checkInput(input: String?): Boolean {
            val s = input?.trim().orEmpty()
            return s.isEmpty() || extractVideoId(s) != null || s.startsWith("https://") || s.startsWith("http://")
        }

        override fun canClose(input: String?): Boolean = checkInput(input)
    }

    private companion object {
        const val TOOLBAR_PLACE = "SubwayCodersToolbar"
    }
}
