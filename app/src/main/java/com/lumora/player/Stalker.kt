package com.lumora.player

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Stalker/MAG portal client. The Cloudflare worker is required because Android
 * browsers/network stacks cannot reliably send the portal's Cookie header through
 * some reverse proxies. It receives the target URL (u), Cookie (c), and token (a).
 */
object Stalker {
    const val DEFAULT_PROXY = "https://lumora-proxy.soso1236u.workers.dev/"
    private val tokens = HashMap<String, String>()

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    private fun endpoint(s: Source): String {
        val u = s.url.trim().trimEnd('/')
        return if (u.endsWith(".php", true)) u else "$u/portal.php"
    }
    private fun proxy(s: Source) = s.proxy.trim().ifBlank { DEFAULT_PROXY }.let { if (it.endsWith("/")) it else "$it/" }

    private suspend fun call(s: Source, params: Map<String, String>, token: String = ""): JSONObject {
        val query = params.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        val target = endpoint(s) + "?" + query + "&JsHttpRequest=1-xml"
        val cookie = "mac=${s.mac}; stb_lang=en; timezone=Europe/Paris"
        val proxied = proxy(s) + "?u=" + enc(target) + "&c=" + enc(cookie) + "&a=" + enc(token)
        val raw = Net.text(proxied, 60)
        val root = try { JSONObject(raw) } catch (_: Exception) { throw IOException("Stalker proxy returned an invalid response") }
        return root.optJSONObject("js") ?: throw IOException(root.optString("error").ifBlank { "Stalker portal returned no data" })
    }

    suspend fun login(s: Source) {
        if (s.url.isBlank()) throw IOException("Enter the Stalker portal URL")
        val mac = s.mac.trim().uppercase().replace('-', ':').let { value ->
            if (Regex("^[0-9A-F]{12}$").matches(value)) value.chunked(2).joinToString(":") else value
        }
        if (!Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$").matches(mac)) {
            throw IOException("MAC must look like 00:1A:79:XX:XX:XX")
        }
        val normalized = if (mac == s.mac) s else s.copy(mac = mac)
        tokens[s.id]?.let { return }
        val handshake = call(normalized, mapOf("type" to "stb", "action" to "handshake", "token" to ""))
        val token = handshake.optString("token")
        if (token.isBlank()) throw IOException("Stalker handshake failed. Check portal URL and MAC address.")
        call(normalized, mapOf("type" to "stb", "action" to "get_profile", "hd" to "1", "stb_lang" to "en", "timezone" to "Europe/Paris"), token)
        tokens[s.id] = token
    }

    private suspend fun <T> session(s: Source, block: suspend (String) -> T): T {
        login(s)
        val token = tokens[s.id] ?: throw IOException("Stalker session expired")
        return try { block(token) } catch (e: Exception) {
            tokens.remove(s.id)
            throw e
        }
    }

    private fun list(v: Any?): List<JSONObject> {
        val a = when (v) {
            is JSONArray -> v
            is JSONObject -> JSONArray().also { out -> v.keys().forEach { out.put(v.opt(it)) } }
            else -> JSONArray()
        }
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    private fun logo(s: Source, value: String): String {
        if (value.isBlank()) return ""
        if (value.startsWith("http", true)) return value
        val origin = Regex("^(https?://[^/]+)", RegexOption.IGNORE_CASE).find(s.url)?.groupValues?.get(1).orEmpty()
        return "$origin/stalker_portal/misc/logos/320/$value"
    }

    suspend fun catalog(s: Source, kind: String): List<Item> = session(s) { token ->
        if (kind == "live") {
            val genres = runCatching { list(call(s, mapOf("type" to "itv", "action" to "get_genres"), token)) }.getOrDefault(emptyList())
            val names = genres.associate { it.optString("id") to it.optString("title") }
            val channelResponse = call(s, mapOf("type" to "itv", "action" to "get_all_channels"), token)
            val channels = list(channelResponse.opt("data"))
            channels.map { c ->
                Item(c.optString("id"), c.optString("name").ifBlank { "Unknown" }, names[c.optString("tv_genre_id")] ?: "Uncategorized",
                    logo(s, c.optString("logo")), "live", srcId = s.id, cmd = c.optString("cmd"))
            }
        } else {
            val type = if (kind == "vod") "vod" else "series"
            val cats = runCatching { list(call(s, mapOf("type" to type, "action" to "get_categories"), token)) }.getOrDefault(emptyList())
            val names = cats.associate { it.optString("id") to it.optString("title") }
            val out = ArrayList<Item>()
            var page = 1
            var pages = 1
            while (page <= pages && page <= 400) {
                val r = call(s, mapOf("type" to type, "action" to "get_ordered_list", "genre" to "*", "p" to page.toString(), "sortby" to "added"), token)
                val rows = list(r.opt("data"))
                if (page == 1) {
                    val total = r.optInt("total_items", 0)
                    pages = if (rows.isNotEmpty() && total > 0) ((total + rows.size - 1) / rows.size).coerceAtMost(400) else 1
                }
                rows.forEach { c ->
                    val id = c.optString("id")
                    val title = c.optString("name").ifBlank { "Unknown" }
                    out.add(Item(id, title, names[c.optString("category_id")] ?: "Uncategorized",
                        logo(s, c.optString("screenshot_uri").ifBlank { c.optString("logo") }),
                        if (kind == "vod") "vod" else "series", srcId = s.id,
                        rating = c.optString("rating_imdb").toDoubleOrNull() ?: 0.0,
                        plot = c.optString("description"), cmd = c.optString("cmd")))
                }
                page++
            }
            out
        }
    }

    suspend fun series(s: Source, serie: Item): Pair<Meta, List<Item>> = session(s) { token ->
        val r = call(s, mapOf("type" to "series", "action" to "get_ordered_list", "movie_id" to serie.id,
            "season_id" to "0", "episode_id" to "0", "p" to "1"), token)
        val eps = list(r.opt("data")).mapIndexed { i, e ->
            val season = e.optString("season_num").toIntOrNull() ?: e.optString("season").toIntOrNull() ?: 1
            val num = e.optString("episode_num").toIntOrNull() ?: i + 1
            Item(e.optString("id"), e.optString("name").ifBlank { "Episode ${i + 1}" }, "Season $season",
                logo(s, e.optString("screenshot_uri")).ifBlank { serie.logo }, "ep",
                srcId = s.id, season = season, num = num, plot = e.optString("description"),
                parent = serie.id, cmd = e.optString("cmd").ifBlank { serie.cmd })
        }.sortedWith(compareBy({ it.season }, { it.num }))
        Pair(Meta(plot = serie.plot, poster = serie.logo), eps)
    }

    suspend fun resolve(s: Source, item: Item): String = session(s) { token ->
        val type = if (item.kind == "live") "itv" else "vod"
        val r = call(s, mapOf("type" to type, "action" to "create_link", "cmd" to item.cmd,
            "series" to if (item.kind == "ep") item.num.toString() else "",
            "forced_storage" to "undefined", "disable_ad" to "0", "download" to "0"), token)
        val cmd = r.optString("cmd").replace(Regex("^(ffmpeg|ffrt\\d*|auto)\\s+", RegexOption.IGNORE_CASE), "").trim()
        if (cmd.isBlank()) throw IOException("Stalker portal did not return a stream link")
        cmd
    }
}
