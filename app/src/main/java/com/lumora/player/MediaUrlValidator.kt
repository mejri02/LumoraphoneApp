package com.lumora.player

/** Validation shared by playlist parsing, source checks, and downloads. */
object MediaUrlValidator {
    private val HTTP_SCHEMES = setOf("http", "https", "rtsp")

    /** Extra transports that are played by the LibVLC fallback player (see AppVM.startPlayback). */
    private val VLC_SCHEMES = setOf("rtmp", "rtmps", "rtmpe", "rtmpt", "udp", "rtp", "mms", "mmsh", "mmsu")

    /**
     * Lenient syntax check. java.net.URI rejects characters that are very common in real playlists
     * (non-ASCII file names, '|', '[', ']', '{', '}'), which silently dropped valid channels, so the
     * check looks only at scheme, host and port.
     */
    private fun hasValidAuthority(value: String, schemes: Set<String>): Boolean {
        val v = value.trim()
        if (v.isEmpty() || v.any { it.isWhitespace() }) return false
        val sep = v.indexOf("://")
        if (sep <= 0) return false
        if (v.substring(0, sep).lowercase() !in schemes) return false
        val rest = v.substring(sep + 3)
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.isEmpty() || authority.contains('@')) return false
        val host: String
        val port: String
        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            if (end < 0) return false
            host = authority.substring(1, end)
            port = authority.substring(end + 1).removePrefix(":")
        } else {
            host = authority.substringBefore(':')
            port = if (authority.contains(':')) authority.substringAfter(':') else ""
        }
        if (host.isBlank()) return false
        if (port.isNotEmpty()) {
            val p = port.toIntOrNull() ?: return false
            if (p !in 1..65535) return false
        }
        return true
    }

    /** Remote media/source URLs supported by the HTTP client and ExoPlayer setup. */
    fun isHttpUrl(value: String): Boolean = hasValidAuthority(value, HTTP_SCHEMES)

    /** Any stream URL the app can play: HTTP(S)/RTSP through ExoPlayer, the rest through LibVLC. */
    fun isStreamUrl(value: String): Boolean = hasValidAuthority(value, HTTP_SCHEMES + VLC_SCHEMES)

    fun normalizedHttpUrl(value: String): String? = value.trim().takeIf(::isHttpUrl)
}
