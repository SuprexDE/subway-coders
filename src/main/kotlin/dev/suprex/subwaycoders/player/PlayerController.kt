package dev.suprex.subwaycoders.player

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import dev.suprex.subwaycoders.config.SubwayCodersSettings
import dev.suprex.subwaycoders.config.VideoConfigService
import dev.suprex.subwaycoders.youtube.buildWatchUrl
import dev.suprex.subwaycoders.youtube.extractVideoId
import org.cef.network.CefCookie
import java.util.Date
import javax.swing.JComponent
import javax.swing.SwingConstants
import kotlin.random.Random

class PlayerController(
    private val config: SubwayCodersSettings.WindowConfig,
) : Disposable {

    private val browser: JBCefBrowser? = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private var currentClip: String? = null
    private var lastBright = JBColor.isBright()

    val supported: Boolean get() = browser != null

    val component: JComponent = browser?.component ?: unsupportedLabel()

    fun reload() {
        val b = browser ?: return
        if (config.doomscrollEnabled) {
            currentClip = null
            applyYouTubeTheme()
            b.loadURL(YOUTUBE_SHORTS_URL)
            return
        }
        val clip = pickClip()
        currentClip = clip
        if (clip == null) {
            b.loadHTML(PlayerHtml.emptyPage())
            return
        }
        val id = extractVideoId(clip)
        if (id != null) b.loadURL(EmbedServer.instance.pageUrl(id, muted = true))
        else b.loadHTML(PlayerHtml.videoPage(clip))
    }

    fun reloadIfThemeFlipped() {
        val bright = JBColor.isBright()
        if (bright == lastBright) return
        lastBright = bright
        if (config.doomscrollEnabled) reload()
    }

    fun setPlaybackPaused(pause: Boolean) = runScript(PlayerHtml.pauseScript(pause))

    fun fade(visible: Boolean, durationMs: Int) = runScript(PlayerHtml.fadeScript(visible, durationMs))

    fun showBanner(text: String) = runScript(PlayerHtml.bannerScript(text))

    private fun runScript(script: String) {
        val b = browser ?: return
        b.cefBrowser.executeJavaScript(script, b.cefBrowser.url ?: "", 0)
    }

    fun externalUrl(): String? =
        if (config.doomscrollEnabled) YOUTUBE_SHORTS_URL else currentClip?.let { watchOrDirect(it) }

    fun reloadIfClipRemoved() {
        if (config.doomscrollEnabled || config.customUrl.isNotBlank()) return
        val clips = categoryClips()
        val clip = currentClip
        if (if (clip == null) clips.isNotEmpty() else clip !in clips) reload()
    }

    private fun categoryClips(): List<String> =
        VideoConfigService.instance.config.categories
            .firstOrNull { it.name == config.categoryName }?.clips.orEmpty()

    private fun pickClip(): String? {
        val custom = config.customUrl.trim()
        if (custom.isNotEmpty()) return custom
        val clips = categoryClips()
        val candidates = clips.filter { it != currentClip }.ifEmpty { clips }
        return if (candidates.isEmpty()) null else candidates[Random.nextInt(candidates.size)]
    }

    private fun watchOrDirect(clip: String): String {
        val id = extractVideoId(clip)
        return if (id != null) buildWatchUrl(id) else clip
    }

    private fun applyYouTubeTheme() {
        val manager = browser?.jbCefCookieManager?.cefCookieManager ?: return
        val pref = if (JBColor.isBright()) "f6=0" else "f6=400"
        val now = Date()
        val expires = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
        val cookie = CefCookie("PREF", pref, ".youtube.com", "/", false, false, now, now, true, expires)
        runCatching { manager.setCookie("https://www.youtube.com", cookie) }
    }

    private fun unsupportedLabel(): JComponent =
        JBLabel(
            "<html><center>The embedded browser (JCEF) is not available in this runtime,<br>" +
                "so Subway Coders can't render the player.</center></html>",
        ).apply {
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
        }

    override fun dispose() {
        browser?.let { Disposer.dispose(it) }
    }

    private companion object {
        const val YOUTUBE_SHORTS_URL = "https://www.youtube.com/shorts"
    }
}