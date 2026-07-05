package de.suprexdev.subwaycoders

/**
 * Builds the YouTube embed `<iframe>` src. [origin] must be the real http(s) origin of the page
 * hosting the iframe (see [EmbedServer]); YouTube validates it against the request's Referer/Origin
 * and refuses playback (error 152) for a missing or mismatched origin.
 */
fun buildEmbedUrl(videoId: String, muted: Boolean, origin: String): String {
    val mute = if (muted) 1 else 0
    return "https://www.youtube-nocookie.com/embed/$videoId" +
        "?autoplay=1&mute=$mute&loop=1&playlist=$videoId" +
        "&controls=1&modestbranding=1&rel=0&playsinline=1&fs=1" +
        "&enablejsapi=1&origin=$origin"
}

fun buildWatchUrl(videoId: String): String =
    "https://www.youtube.com/watch?v=$videoId"

fun extractVideoId(raw: String?): String? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    Regex("(?:v=|/embed/|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})")
        .find(s)?.let { return it.groupValues[1] }
    return if (Regex("^[A-Za-z0-9_-]{11}$").matches(s)) s else null
}
