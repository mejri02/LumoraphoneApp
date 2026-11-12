package com.lumora.player

object M3u {
    private val attr = Regex("([\\w-]+)=(?:\"([^\"]*)\"|([^,\\s]+))")
    private val file = Regex("\\.(mp4|mkv|avi|mov|wmv|flv|webm|m4v|mpg|mpeg|3gp)(\\?|$)", RegexOption.IGNORE_CASE)

    private val headerSuffix = Regex("\\|[A-Za-z-]+=")

    fun parse(text: String, srcId: String) = parse(text.lineSequence(), srcId)

    fun parse(lines: Sequence<String>, srcId: String): Triple<List<Item>, List<Item>, List<Item>> {
        val live = ArrayList<Item>()
        val vod = ArrayList<Item>()
        var name = ""
        var group = "Uncategorized"
        var extGrp = ""
        var logo = ""
        var have = false
        for (raw in lines) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTGRP:")) {
                // #EXTGRP can appear before or after #EXTINF, so keep it for both cases.
                extGrp = line.substringAfter(':').trim()
                if (have) group = extGrp.ifBlank { group }
                continue
            }
            if (line.startsWith("#EXTINF")) {
                val m = HashMap<String, String>()
                attr.findAll(line).forEach {
                    val k = it.groupValues[1].lowercase()
                    m[k] = it.groupValues[2].ifEmpty { it.groupValues[3] }
                }
                val lastQ = line.lastIndexOf('"') + 1
                val comma = line.indexOf(',', lastQ)
                name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                if (name.isEmpty()) name = m["tvg-name"] ?: "Unknown"
                group = m["group-title"].takeUnless { it.isNullOrBlank() } ?: extGrp.ifBlank { "Uncategorized" }
                logo = m["tvg-logo"] ?: ""
                have = true
            } else if (!line.startsWith("#")) {
                // Some playlists append player headers as "url|User-Agent=...": ExoPlayer cannot use them.
                val url = if (headerSuffix.containsMatchIn(line)) line.substringBefore('|') else line
                if (MediaUrlValidator.isStreamUrl(url)) {
                    val isVod = url.contains("/movie/") || url.contains("/series/") || file.containsMatchIn(url)
                    val it = Item(
                        url, if (have) name else url, if (have) group else "Uncategorized",
                        if (have) logo else "", if (isVod) "vod" else "live", url = url, srcId = srcId
                    )
                    if (isVod) vod.add(it) else live.add(it)
                }
                have = false
            }
        }
        // Playlists often repeat the same URL in several groups; keep the first occurrence only.
        return Triple(live.distinctBy { it.url }, vod.distinctBy { it.url }, emptyList())
    }
}
