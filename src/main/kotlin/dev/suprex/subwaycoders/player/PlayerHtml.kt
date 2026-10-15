package dev.suprex.subwaycoders.player

import com.intellij.openapi.util.text.StringUtil
import dev.suprex.subwaycoders.youtube.buildEmbedUrl

internal object PlayerHtml {

    fun videoPage(src: String): String =
        """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
            video{position:fixed;inset:0;width:100%;height:100%;object-fit:cover}</style></head>
            <body><video src="${escapeAttr(src)}" autoplay loop muted controls playsinline></video></body></html>
        """.trimIndent()

    fun emptyPage(): String =
        """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <style>html,body{margin:0;height:100%;background:#000;color:#999;
            font:13px system-ui,sans-serif;display:flex;align-items:center;justify-content:center;text-align:center}</style></head>
            <body><div>No clips in this category.<br>Add some via the gear menu &gt; Edit Config.</div></body></html>
        """.trimIndent()

    private fun escapeAttr(s: String): String =
        s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    fun embedPage(videoId: String, muted: Boolean, origin: String): String =
        """
        <!DOCTYPE html><html><head><meta charset="utf-8">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
        iframe{position:fixed;inset:0;width:100%;height:100%;border:0}</style></head>
        <body><iframe src="${buildEmbedUrl(videoId, muted, origin)}"
        referrerpolicy="strict-origin-when-cross-origin"
        allow="autoplay; fullscreen; encrypted-media" allowfullscreen></iframe></body></html>
        """.trimIndent()

    private const val REMOVE_BANNER = "var old = document.getElementById('sc-banner'); if (old) old.remove();"

    fun fadeScript(visible: Boolean, durationMs: Int): String {
        val opacity = if (visible) "1" else "0"
        val clear = if (visible) REMOVE_BANNER else ""
        return """
            (function(){ $clear var s = document.documentElement.style;
                s.transition = 'opacity ${durationMs}ms ease'; s.opacity = '$opacity'; })();
        """.trimIndent()
    }

    fun bannerScript(text: String): String =
        """
            (function(){ $REMOVE_BANNER
                var b = document.createElement('div'); b.id = 'sc-banner';
                b.textContent = "${StringUtil.escapeStringCharacters(text)}";
                b.style.cssText = 'position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);z-index:2147483647;' +
                    'padding:12px 22px;border-radius:999px;background:rgba(0,0,0,.78);color:#fff;' +
                    'font:600 16px system-ui,sans-serif;box-shadow:0 4px 24px rgba(0,0,0,.5);pointer-events:none';
                document.documentElement.appendChild(b); })();
        """.trimIndent()

    fun pauseScript(pause: Boolean): String {
        val method = if (pause) "pause" else "play"
        val command = if (pause) "pauseVideo" else "playVideo"
        return """
            document.querySelectorAll('video').forEach(function(v){ try{ v.$method(); }catch(e){} });
            document.querySelectorAll('iframe').forEach(function(fr){
                try{ fr.contentWindow.postMessage(
                    JSON.stringify({event:'command',func:'$command',args:''}), '*'); }catch(e){}
            });
        """.trimIndent()
    }
}