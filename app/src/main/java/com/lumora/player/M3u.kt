package com.lumora.player

/**
 * A forgiving M3U / M3U8 playlist parser.
 *
 * Real playlists are messy: attributes with single quotes, commas inside channel names, EXTGRP
 * before or after EXTINF, comments between entries, duplicate URLs for different qualities. This
 * parser only trusts the text after the first comma that is *outside* quotes, so a channel called
 * `News, Weather` keeps its full name.
 */
object M3u {
    private val ATTR = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^,\s]+))""")
    private val MEDIA_EXT = Regex("""\.(mp4|mkv|avi|mov|m4v|webm|flv|wmv|mpg|mpeg|divx)(?:$|\?)""", RegexOption.IGNORE_CASE)
    private val SERIES_PATH = Regex("""/(series|serie|shows?)/""", RegexOption.IGNORE_CASE)
    private val MOVIE_PATH = Regex("""/(movie|movies|vod|films?)/""", RegexOption.IGNORE_CASE)
    private val EPISODE_NAME = Regex("""(?i)\bS\d{1,2}\s*[._ ]?E\d{1,3}\b""")
    private val EPISODE_ALT = Regex("""(?i)\b\d{1,2}x\d{1,3}\b""")
    private val HEADER_SUFFIX = Regex("""\|[A-Za-z-]+=""")

    fun parse(text: String, srcId: String): Triple<List<Item>, List<Item>, List<Item>> =
        parse(text.lineSequence(), srcId)

    /** Streaming overload: playlists can be huge, so the text never has to be in memory at once. */
    fun parse(lines: Sequence<String>, srcId: String): Triple<List<Item>, List<Item>, List<Item>> {
        val live = ArrayList<Item>()
        val vod = ArrayList<Item>()
        val series = ArrayList<Item>()
        var pending: Item? = null
        var pendingGroup = ""

        fun commit(url: String) {
            // A bare URL with no #EXTINF is still playable: use its last path segment as the name.
            val p = pending ?: Item(
                id = "", name = "", group = pendingGroup, logo = "", kind = "live",
                url = "", srcId = srcId
            )
            pending = null
            val cleanUrl = url.trim()
            // Some playlists append player headers as "url|User-Agent=...": ExoPlayer cannot use them.
            val streamUrl = if (HEADER_SUFFIX.containsMatchIn(cleanUrl)) cleanUrl.substringBefore('|') else cleanUrl
            if (streamUrl.isBlank() || !MediaUrlValidator.isStreamUrl(streamUrl)) return
            val kind = classify(streamUrl, p.name)
            val item = p.copy(
                id = streamUrl,
                name = p.name.trim().ifBlank {
                    streamUrl.substringAfterLast('/').substringBefore('?').ifBlank { "Unknown" }
                },
                url = streamUrl,
                kind = kind,
                ext = extensionOf(streamUrl),
                group = groupName(p.group.ifBlank { pendingGroup })
            )
            when (kind) {
                "live" -> live.add(item)
                "series" -> series.add(item)
                else -> vod.add(item)
            }
        }

        for (raw in lines) {
            val line = raw.trim().removePrefix("\uFEFF")
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter(':').trim()
                    pending?.let { p ->
                        if (p.group.isBlank() || p.group == "Uncategorized") pending = p.copy(group = pendingGroup)
                    }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val (attrs, title) = splitExtInf(line)
                    val map = HashMap<String, String>()
                    ATTR.findAll(attrs).forEach { m ->
                        val key = m.groupValues[1].lowercase()
                        val value = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
                        if (value.isNotBlank()) map[key] = value.trim()
                    }
                    val display = title.ifBlank { map["tvg-name"].orEmpty() }.ifBlank { map["tvg-id"].orEmpty() }
                    pending = Item(
                        id = "",
                        name = display,
                        group = map["group-title"].orEmpty(),
                        logo = map["tvg-logo"].orEmpty().ifBlank { map["logo"].orEmpty() },
                        kind = "live",
                        url = "",
                        srcId = srcId
                    )
                }
                line.startsWith("#") -> {}
                else -> commit(line)
            }
        }

        return Triple(dedupe(live), dedupe(vod), dedupe(series))
    }

    private fun dedupe(list: List<Item>): List<Item> {
        val seen = HashSet<String>()
        return list.filter { seen.add(it.name.trim().lowercase() + "|" + it.url) }
    }

    private fun classify(url: String, name: String): String {
        // Series episodes from a plain M3U are played as movies: there is no episode metadata to
        // build a season list from, so showing them as a normal title is the reliable behaviour.
        if (SERIES_PATH.containsMatchIn(url)) return "vod"
        if (MOVIE_PATH.containsMatchIn(url)) return "vod"
        if (EPISODE_NAME.containsMatchIn(name) || EPISODE_ALT.containsMatchIn(name)) return "vod"
        // A direct media file is a movie unless it is clearly a live stream with an extension.
        if (MEDIA_EXT.containsMatchIn(url) && !url.contains("/live/", true)) return "vod"
        return "live"
    }

    private fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "")
        return if (ext.length in 2..5) ext.lowercase() else ""
    }

    /** Splits `#EXTINF:-1 tvg-id="x",Channel, name` into the attributes part and the title. */
    private fun splitExtInf(line: String): Pair<String, String> {
        var body = line.substringAfter(':', "")
        body = body.removePrefix("-1").removePrefix("0").removePrefix("1")
        var quoted = false
        for (i in body.indices) {
            when (body[i]) {
                '"', '\'' -> quoted = !quoted
                ',' -> if (!quoted) {
                    return body.substring(0, i) to body.substring(i + 1).trim().trim('"', '\'').trim()
                }
            }
        }
        return body to ""
    }
}

/** Blank or "unknown" groups get a friendly, stable name so they still show as a category. */
fun groupName(raw: String): String {
    val g = raw.trim()
    return when {
        g.isBlank() -> "Uncategorized"
        g.equals("unknown", true) -> "Uncategorized"
        g.equals("undefined", true) -> "Uncategorized"
        else -> g
    }
}
