package com.lumora.player

import java.net.URI

/** Validation shared by playlist parsing, source checks, and downloads. */
object MediaUrlValidator {
    /** Remote media/source URLs supported by the HTTP client and ExoPlayer setup. */
    fun isHttpUrl(value: String): Boolean {
        if (value.isBlank() || value.any { it.isWhitespace() }) return false
        return try {
            val uri = URI(value.trim())
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                (uri.port == -1 || uri.port in 1..65535)
        } catch (_: Exception) {
            false
        }
    }

    fun normalizedHttpUrl(value: String): String? = value.trim().takeIf(::isHttpUrl)
}
