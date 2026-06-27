package de.suprexdev.subwaycoders

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.random.Random

class SubwayCodersPanel(
    private val project: Project,
    windowId: String,
    defaultCategory: String,
) : JPanel(BorderLayout()), Disposable {

    private val config = SubwayCodersSettings.instance.configFor(windowId, defaultCategory)
    private var browser: JBCefBrowser? = null
    private var currentClip: String? = null

    private var actionToolbar: ActionToolbar? = null

    init {
        if (JBCefApp.isSupported()) {
            val created = JBCefBrowser()
            browser = created
            ensureValidCategory()
            val bar = buildToolbar().component
            bar.isVisible = !config.controlsHidden
            add(bar, BorderLayout.NORTH)
            add(created.component, BorderLayout.CENTER)
            reload()
        } else {
            add(unsupportedLabel(), BorderLayout.CENTER)
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
        reload()
        actionToolbar?.updateActionsImmediately()
    }

    private fun shuffle() {
        VideoConfigService.instance.reload()
        ensureValidCategory()
        reload()
        actionToolbar?.updateActionsImmediately()
    }

    private fun promptForUrl() {
        val input = Messages.showInputDialog(
            project,
            "Paste a YouTube or direct video URL:",
            "Play Video URL",
            null,
            config.customUrl,
            null,
        ) ?: return
        config.customUrl = input.trim()
        reload()
        actionToolbar?.updateActionsImmediately()
    }

    private fun clearCustomUrl() {
        config.customUrl = ""
        reload()
        actionToolbar?.updateActionsImmediately()
    }

    private fun hasCustomUrl() = config.customUrl.isNotBlank()

    private fun ensureValidCategory() {
        val names = categoryNames()
        if (config.categoryName !in names) {
            config.categoryName = names.firstOrNull().orEmpty()
        }
    }

    private fun unsupportedLabel(): JComponent =
        JBLabel(
            "<html><center>The embedded browser (JCEF) is not available in this runtime,<br>" +
                "so Subway Coders can't render the player.</center></html>",
        ).apply {
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
        }

    private fun pickClip(): String? {
        val custom = config.customUrl.trim()
        if (custom.isNotEmpty()) return custom
        val clips = categories().firstOrNull { it.name == config.categoryName }?.clips.orEmpty()
        return if (clips.isEmpty()) null else clips[Random.nextInt(clips.size)]
    }

    private fun watchOrDirect(clip: String): String {
        val id = extractVideoId(clip)
        return if (id != null) buildWatchUrl(id) else clip
    }

    private fun reload() {
        val b = browser ?: return
        val clip = pickClip()
        currentClip = clip
        if (clip == null) {
            b.loadHTML("<html><body style='margin:0;background:#000'></body></html>")
            return
        }
        val id = extractVideoId(clip)
        // Start muted so autoplay isn't blocked; the player controls let the user unmute.
        if (id != null) b.loadURL(EmbedServer.instance.pageUrl(id, muted = true))
        else b.loadHTML(videoPageHtml(clip))
    }

    private fun videoPageHtml(src: String): String =
        """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
            video{position:fixed;inset:0;width:100%;height:100%;object-fit:cover}</style></head>
            <body><video src="$src" autoplay loop muted controls playsinline></video></body></html>
        """.trimIndent()

    private fun openConfigFile() {
        val service = VideoConfigService.instance
        service.ensureUserFile()
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(service.configFile) ?: return
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    override fun dispose() {
        browser?.let { Disposer.dispose(it) }
        browser = null
    }

    /** Source picker: checkable categories plus the custom-URL entry, in one control. */
    private inner class CategoryAction : ComboBoxAction() {
        override fun createPopupActionGroup(button: JComponent): DefaultActionGroup =
            DefaultActionGroup().apply {
                for (name in categoryNames()) {
                    add(object : ToggleAction(name) {
                        override fun isSelected(e: AnActionEvent) =
                            !hasCustomUrl() && config.categoryName == name

                        override fun setSelected(e: AnActionEvent, state: Boolean) = selectCategory(name)

                        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                    })
                }
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
            e.presentation.text = if (hasCustomUrl()) "Custom URL" else config.categoryName.ifEmpty { "Category" }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class ShuffleAction : AnAction(
        "Shuffle",
        "Play another clip from this category and re-read the config",
        AllIcons.Actions.Refresh,
    ) {
        override fun actionPerformed(e: AnActionEvent) = shuffle()
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class OpenInBrowserAction : AnAction(
        "Open in Browser",
        "Open the current clip in your browser",
        AllIcons.General.Web,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            currentClip?.let { BrowserUtil.browse(watchOrDirect(it)) }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = currentClip != null
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private companion object {
        const val TOOLBAR_PLACE = "SubwayCodersToolbar"
    }
}
