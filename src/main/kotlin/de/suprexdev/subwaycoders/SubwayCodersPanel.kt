package de.suprexdev.subwaycoders

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
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
    private var updatingCombo = false

    private val categoryCombo = ComboBox<String>()
    private val urlField = JBTextField()
    private var toolbar: JComponent? = null

    init {
        if (JBCefApp.isSupported()) {
            val created = JBCefBrowser()
            browser = created
            val bar = buildToolbar()
            toolbar = bar
            bar.isVisible = !config.controlsHidden
            add(bar, BorderLayout.NORTH)
            add(created.component, BorderLayout.CENTER)
            reload()
        } else {
            add(unsupportedLabel(), BorderLayout.CENTER)
        }
    }

    private fun categories() = VideoConfigService.instance.config.categories

    private fun buildToolbar(): JComponent {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4)))

        populateCategories()
        categoryCombo.addActionListener {
            if (updatingCombo) return@addActionListener
            (categoryCombo.selectedItem as? String)?.let { config.categoryName = it }
            config.customUrl = ""
            urlField.text = ""
            reload()
        }

        urlField.columns = 14
        urlField.text = config.customUrl
        urlField.emptyText.text = "or paste a video URL…"
        urlField.toolTipText = "Paste any YouTube or direct video URL, then press Enter"
        urlField.addActionListener {
            config.customUrl = urlField.text.trim()
            reload()
        }

        val shuffleButton = JButton("Shuffle").apply {
            toolTipText = "Play another clip from this category and re-read the config"
            addActionListener {
                VideoConfigService.instance.reload()
                populateCategories()
                reload()
            }
        }
        val openButton = JButton(AllIcons.General.Web).apply {
            toolTipText = "Open the current clip in your browser"
            addActionListener { currentClip?.let { BrowserUtil.browse(watchOrDirect(it)) } }
        }

        bar.add(categoryCombo)
        bar.add(urlField)
        bar.add(shuffleButton)
        bar.add(openButton)
        return bar
    }

    fun openConfig() = openConfigFile()

    fun areControlsHidden(): Boolean = config.controlsHidden

    fun setControlsHidden(hidden: Boolean) {
        config.controlsHidden = hidden
        toolbar?.let {
            it.isVisible = !hidden
            it.revalidate()
            it.repaint()
        }
    }

    private fun populateCategories() {
        updatingCombo = true
        val names = categories().map { it.name }
        categoryCombo.model = DefaultComboBoxModel(names.toTypedArray())
        val selected = config.categoryName.takeIf { it in names } ?: names.firstOrNull().orEmpty()
        categoryCombo.selectedItem = selected
        config.categoryName = selected
        updatingCombo = false
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
}
