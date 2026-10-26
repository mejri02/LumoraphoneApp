package com.lumora.player

object M3u {
    private val attr = Regex("([\\w-]+)=\"([^\"]*)\"")
    private val file = Regex("\\.(mp4|mkv|avi|mov|wmv|flv|webm|m4v|mpg|mpeg|3gp)(\\?|$)", RegexOption.IGNORE_CASE)

    fun parse(text: String, srcId: String): Triple<List<Item>, List<Item>, List<Item>> {
        val live = ArrayList<Item>()
        val vod = ArrayList<Item>()
        var name = ""
        var group = "Uncategorized"
        var logo = ""
        var have = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) {
                val m = HashMap<String, String>()
                attr.findAll(line).forEach { m[it.groupValues[1].lowercase()] = it.groupValues[2] }
                val lastQ = line.lastIndexOf('"') + 1
                val comma = line.indexOf(',', lastQ)
                name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                if (name.isEmpty()) name = m["tvg-name"] ?: "Unknown"
                group = m["group-title"] ?: "Uncategorized"
                logo = m["tvg-logo"] ?: ""
                have = true
            } else if (!line.startsWith("#")) {
                if (Regex("^[a-zA-Z]+://\\S+$").matches(line)) {
                    val isVod = line.contains("/movie/") || line.contains("/series/") || file.containsMatchIn(line)
                    val it = Item(
                        line, if (have) name else line, if (have) group else "Uncategorized",
                        if (have) logo else "", if (isVod) "vod" else "live", url = line, srcId = srcId
                    )
                    if (isVod) vod.add(it) else live.add(it)
                }
                have = false
            }
        }
        return Triple(live, vod, emptyList())
    }
}
