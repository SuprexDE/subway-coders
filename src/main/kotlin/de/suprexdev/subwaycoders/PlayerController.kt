package de.suprexdev.subwaycoders

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.network.CefCookie
import java.util.Date
import javax.swing.JComponent
import javax.swing.SwingConstants
import kotlin.random.Random

/**
 * Owns the embedded [JBCefBrowser] and everything about *what* it renders: clip selection, the three
 * playback modes (category clip, custom URL, Doomscroll shorts), YouTube theming and pause/resume.
 * The Swing shell ([SubwayCodersPanel]) drives it via [reload] / [setPlaybackPaused].
 */
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
            // Top-level navigation to the real site, so no EmbedServer/origin workaround is needed.
            b.loadURL(YOUTUBE_SHORTS_URL)
            return
        }
        val clip = pickClip()
        currentClip = clip
        if (clip == null) {
            b.loadHTML(PlayerHtml.blankPage())
            return
        }
        val id = extractVideoId(clip)
        // Start muted so autoplay isn't blocked; the player controls let the user unmute.
        if (id != null) b.loadURL(EmbedServer.instance.pageUrl(id, muted = true))
        else b.loadHTML(PlayerHtml.videoPage(clip))
    }

    /** Reloads the Doomscroll feed only when the IDE actually flipped light<->dark. */
    fun reloadIfThemeFlipped() {
        val bright = JBColor.isBright()
        if (bright == lastBright) return
        lastBright = bright
        if (config.doomscrollEnabled) reload()
    }

    fun setPlaybackPaused(pause: Boolean) {
        val b = browser ?: return
        b.cefBrowser.executeJavaScript(PlayerHtml.pauseScript(pause), b.cefBrowser.url ?: "", 0)
    }

    /** URL to open the current source in an external browser, or null if nothing is playable. */
    fun externalUrl(): String? =
        if (config.doomscrollEnabled) YOUTUBE_SHORTS_URL else currentClip?.let { watchOrDirect(it) }

    private fun pickClip(): String? {
        val custom = config.customUrl.trim()
        if (custom.isNotEmpty()) return custom
        val clips = VideoConfigService.instance.config.categories
            .firstOrNull { it.name == config.categoryName }?.clips.orEmpty()
        return if (clips.isEmpty()) null else clips[Random.nextInt(clips.size)]
    }

    private fun watchOrDirect(clip: String): String {
        val id = extractVideoId(clip)
        return if (id != null) buildWatchUrl(id) else clip
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
        /** The real YouTube Shorts feed; loaded as a top-level page (no EmbedServer/origin needed). */
        const val YOUTUBE_SHORTS_URL = "https://www.youtube.com/shorts"
    }
}
