package de.suprexdev.subwaycoders

/** The HTML pages and control script rendered inside the embedded [JBCefBrowser] player. */
internal object PlayerHtml {

    /** A raw direct-video URL played as a fullscreen, looping, muted element. */
    fun videoPage(src: String): String =
        """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
            video{position:fixed;inset:0;width:100%;height:100%;object-fit:cover}</style></head>
            <body><video src="$src" autoplay loop muted controls playsinline></video></body></html>
        """.trimIndent()

    /** Blank black page shown when a category has no clips. */
    fun blankPage(): String = "<html><body style='margin:0;background:#000'></body></html>"

    /**
     * Wrapper page hosting the YouTube clip in a real cross-origin `<iframe>` (see [EmbedServer]).
     * [origin] must match the serving http(s) origin or the player refuses to play (error 152).
     */
    fun embedPage(videoId: String, muted: Boolean, origin: String): String =
        """
        <!DOCTYPE html><html><head><meta charset="utf-8">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
        iframe{position:fixed;inset:0;width:100%;height:100%;border:0}</style></head>
        <body><iframe src="${buildEmbedUrl(videoId, muted, origin)}"
        referrerpolicy="strict-origin-when-cross-origin"
        allow="autoplay; fullscreen; encrypted-media" allowfullscreen></iframe></body></html>
        """.trimIndent()

    /**
     * Pauses or resumes playback across all modes: `<video>` pages and the Doomscroll feed expose
     * `<video>` elements directly; the YouTube embed is a cross-origin `<iframe>` driven via the
     * IFrame Player API `postMessage` (needs `enablejsapi=1` on the embed URL).
     */
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
