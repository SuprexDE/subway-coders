package de.suprexdev.subwaycoders

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.network.CefCookie
import java.awt.BorderLayout
import java.util.Date
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.random.Random

class SubwayCodersPanel(
    private val project: Project,
    private val windowId: String,
    defaultCategory: String,
) : JPanel(BorderLayout()), Disposable {

    private val config = SubwayCodersSettings.instance.configFor(windowId, defaultCategory)
    private var browser: JBCefBrowser? = null
    private var currentClip: String? = null
    private var lastBright = JBColor.isBright()
    private var lastVisible = true

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
            // Re-theme the feed only when the IDE actually flips between light and dark (the LAF
            // listener also fires for unrelated theme tweaks, which must not reset the feed).
            ApplicationManager.getApplication().messageBus.connect(this)
                .subscribe(
                    LafManagerListener.TOPIC,
                    LafManagerListener {
                        val bright = JBColor.isBright()
                        if (bright != lastBright) {
                            lastBright = bright
                            if (config.doomscrollEnabled) reload()
                        }
                    },
                )
            // stateChanged fires for every tool window, so match ours by id; isVisible is false both
            // when it's collapsed and when another tab takes over its anchor.
            project.messageBus.connect(this)
                .subscribe(
                    ToolWindowManagerListener.TOPIC,
                    object : ToolWindowManagerListener {
                        override fun stateChanged(manager: ToolWindowManager) {
                            val visible = manager.getToolWindow(windowId)?.isVisible == true
                            if (visible == lastVisible) return
                            lastVisible = visible
                            setPlaybackPaused(!visible)
                        }
                    },
                )
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
        config.doomscrollEnabled = false
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
        config.doomscrollEnabled = false
        reload()
        actionToolbar?.updateActionsImmediately()
    }

    private fun clearCustomUrl() {
        config.customUrl = ""
        config.doomscrollEnabled = false
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

    private fun toggleDoomscroll(enabled: Boolean) {
        config.doomscrollEnabled = enabled
        // Doomscroll is its own source: drop any custom URL so the tri-state stays mutually exclusive.
        if (enabled) config.customUrl = ""
        reload()
        actionToolbar?.updateActionsImmediately()
    }

    /**
     * Mirrors the IDE's light/dark theme onto YouTube by writing its `PREF` cookie before the feed
     * loads. `f6` is a bit field whose `0x400` bit is the dark theme; clearing it (`f6=0`) yields the
     * light theme. Set on the embedded browser's own cookie store (a guest profile, so overwriting
     * the whole `PREF` value is fine).
     */
    private fun applyYouTubeTheme() {
        val manager = browser?.jbCefCookieManager?.cefCookieManager ?: return
        val pref = if (JBColor.isBright()) "f6=0" else "f6=400"
        val now = Date()
        val expires = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
        val cookie = CefCookie("PREF", pref, ".youtube.com", "/", false, false, now, now, true, expires)
        runCatching { manager.setCookie("https://www.youtube.com", cookie) }
    }

    private fun reload() {
        val b = browser ?: return
        if (config.doomscrollEnabled) {
            currentClip = null
            applyYouTubeTheme()
            // Top-level navigation to the real site, so no EmbedServer/origin workaround is needed.
            b.loadURL(YOUTUBE_SHORTS_URL)
            return
        }
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

    /**
     * `<video>` pages and the Doomscroll feed expose `<video>` elements directly; the YouTube embed
     * is a cross-origin `<iframe>` driven via the IFrame Player API `postMessage` (needs
     * `enablejsapi=1` on the embed URL).
     */
    private fun setPlaybackPaused(pause: Boolean) {
        val b = browser ?: return
        val method = if (pause) "pause" else "play"
        val command = if (pause) "pauseVideo" else "playVideo"
        val js =
            """
            document.querySelectorAll('video').forEach(function(v){ try{ v.$method(); }catch(e){} });
            document.querySelectorAll('iframe').forEach(function(fr){
                try{ fr.contentWindow.postMessage(
                    JSON.stringify({event:'command',func:'$command',args:''}), '*'); }catch(e){}
            });
            """.trimIndent()
        b.cefBrowser.executeJavaScript(js, b.cefBrowser.url ?: "", 0)
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
                            !config.doomscrollEnabled && !hasCustomUrl() && config.categoryName == name

                        override fun setSelected(e: AnActionEvent, state: Boolean) = selectCategory(name)

                        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                    })
                }
                addSeparator()
                add(object : ToggleAction("Doomscroll (YouTube Shorts)", null, AllIcons.Actions.Download) {
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
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class OpenInBrowserAction : AnAction(
        "Open in Browser",
        "Open the current clip in your browser",
        AllIcons.General.Web,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            if (config.doomscrollEnabled) BrowserUtil.browse(YOUTUBE_SHORTS_URL)
            else currentClip?.let { BrowserUtil.browse(watchOrDirect(it)) }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = config.doomscrollEnabled || currentClip != null
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private companion object {
        const val TOOLBAR_PLACE = "SubwayCodersToolbar"

        /** The real YouTube Shorts feed; loaded as a top-level page (no EmbedServer/origin needed). */
        const val YOUTUBE_SHORTS_URL = "https://www.youtube.com/shorts"
    }
}
