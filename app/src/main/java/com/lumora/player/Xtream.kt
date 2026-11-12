package com.lumora.player

import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

fun JSONObject.str(k: String): String {
    val v = opt(k)
    return if (v == null || v == JSONObject.NULL) "" else v.toString().trim()
}

private fun JSONObject.firstImage(k: String): String {
    val v = opt(k)
    return when (v) {
        is JSONArray -> if (v.length() > 0) v.optString(0) else ""
        null, JSONObject.NULL -> ""
        else -> v.toString()
    }.trim().let { if (it == "null") "" else it }
}

object Xtream {
    /**
     * Accept either a server root or a commonly pasted Xtream endpoint
     * (player_api.php, get.php, xmltv.php). Keep reverse-proxy path prefixes,
     * but discard endpoint query strings so we never append a second API path.
     */
    fun base(s: Source): String {
        var raw = s.url.trim()
        if (raw.isBlank()) return ""
        if (!raw.contains("://")) raw = "http://$raw"
        raw = raw.substringBefore('#').substringBefore('?').trimEnd('/')
        val endpoint = Regex("(?i)/(player_api\\.php|get\\.php|xmltv\\.php)$")
        raw = raw.replace(endpoint, "").trimEnd('/')
        return raw
    }

    private fun enc(v: String) = URLEncoder.encode(v.trim(), "UTF-8")
    private fun pathEnc(v: String) = enc(v).replace("+", "%20")

    fun api(s: Source, action: String? = null, extra: String? = null): String {
        var u = base(s) + "/player_api.php?username=" + enc(s.user) + "&password=" + enc(s.pass)
        if (action != null) u += "&action=" + enc(action)
        if (extra != null) u += "&" + extra
        return u
    }

    suspend fun login(s: Source) {
        if (s.url.isBlank()) throw IOException("Enter the server URL")
        if (s.user.isBlank() || s.pass.isBlank()) throw IOException("Enter username and password")
        val txt = try {
            Net.text(api(s), 30)
        } catch (e: IOException) {
            if (e.message == "Network error") throw IOException("Cannot reach the server. Check the URL and your internet connection.")
            throw e
        }
        val notXtream = "Not an Xtream server. Check the URL, for example http://host:port"
        val o = try {
            JSONObject(txt.trim())
        } catch (e: Exception) {
            throw IOException(notXtream)
        }
        val ui = o.optJSONObject("user_info") ?: throw IOException(notXtream)
        // Some panels send auth as a number, some as a boolean, some as "1"/"true".
        val auth = ui.str("auth").lowercase()
        if (auth != "1" && auth != "true") throw IOException("Wrong username or password")
        // Only reject statuses that clearly mean "not usable": Trial/Active/unknown values are fine.
        val st = ui.str("status")
        if (st.lowercase() in setOf("inactive", "disabled", "expired", "banned", "suspended", "0")) {
            throw IOException("Account is not active (status: $st)")
        }
        val exp = ui.optString("exp_date").toLongOrNull() ?: 0L
        if (exp > 0 && exp * 1000 < System.currentTimeMillis()) {
            val d = java.text.DateFormat.getDateInstance().format(java.util.Date(exp * 1000))
            throw IOException("Subscription expired on $d")
        }
    }

    private fun ratingOf(o: JSONObject): Double {
        val r = o.str("rating").toDoubleOrNull() ?: 0.0
        if (r > 0.0) return r
        return (o.str("rating_5based").toDoubleOrNull() ?: 0.0) * 2.0
    }

    /** Reads one JSON object from [r] as a flat map of scalar values (nested values are skipped). */
    private fun readFlat(r: JsonReader, keep: Set<String>, into: HashMap<String, String>) {
        into.clear()
        r.beginObject()
        while (r.hasNext()) {
            val k = r.nextName()
            if (k in keep) {
                when (r.peek()) {
                    JsonToken.NULL -> r.nextNull()
                    JsonToken.BEGIN_ARRAY -> { // e.g. cover as array: keep first string only
                        r.beginArray()
                        var first = ""
                        while (r.hasNext()) {
                            if (first.isEmpty() && r.peek() == JsonToken.STRING) first = r.nextString() else r.skipValue()
                        }
                        r.endArray()
                        into[k] = first.trim()
                    }
                    JsonToken.BEGIN_OBJECT -> r.skipValue()
                    else -> into[k] = r.nextString().trim()
                }
            } else r.skipValue()
        }
        r.endObject()
    }

    suspend fun catalog(s: Source, kind: String): List<Item> {
        val ca: String
        val da: String
        when (kind) {
            "live" -> { ca = "get_live_categories"; da = "get_live_streams" }
            "vod" -> { ca = "get_vod_categories"; da = "get_vod_streams" }
            else -> { ca = "get_series_categories"; da = "get_series" }
        }
        val cats = HashMap<String, String>()
        try {
            Net.stream(api(s, ca), 60) { rd ->
                JsonReader(rd).use { r ->
                    val m = HashMap<String, String>()
                    val keep = setOf("category_id", "category_name")
                    r.beginArray()
                    while (r.hasNext()) {
                        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                        readFlat(r, keep, m)
                        cats[m["category_id"].orEmpty()] = m["category_name"].orEmpty()
                    }
                    r.endArray()
                }
            }
        } catch (_: Exception) {
        }
        val keep = setOf(
            "category_id", "name", "stream_id", "stream_icon", "added", "container_extension",
            "rating", "rating_5based", "series_id", "cover", "last_modified"
        )
        return try {
            Net.stream(api(s, da), 300) { rd ->
                JsonReader(rd).use { r ->
                    val out = ArrayList<Item>()
                    val m = HashMap<String, String>()
                    try {
                        r.beginArray()
                    } catch (e: Exception) {
                        throw IOException("Invalid response from server")
                    }
                    while (r.hasNext()) {
                        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                        readFlat(r, keep, m)
                        val group = cats[m["category_id"].orEmpty()]?.ifBlank { null } ?: "Uncategorized"
                        val name = (m["name"] ?: "").ifBlank { "Unknown" }
                        val rating = run {
                            val a = m["rating"]?.toDoubleOrNull() ?: 0.0
                            if (a > 0.0) a else (m["rating_5based"]?.toDoubleOrNull() ?: 0.0) * 2.0
                        }
                        when (kind) {
                            "live" -> out.add(Item(m["stream_id"].orEmpty(), name, group, m["stream_icon"].orEmpty(), "live", srcId = s.id, added = m["added"]?.toLongOrNull() ?: 0L))
                            "vod" -> out.add(
                                Item(
                                    m["stream_id"].orEmpty(), name, group, m["stream_icon"].orEmpty(), "vod",
                                    ext = (m["container_extension"] ?: "").ifBlank { "mp4" }, srcId = s.id,
                                    added = m["added"]?.toLongOrNull() ?: 0L, rating = rating
                                )
                            )
                            else -> out.add(
                                Item(
                                    m["series_id"].orEmpty(), name, group, m["cover"].orEmpty(), "series", srcId = s.id,
                                    added = m["last_modified"]?.toLongOrNull() ?: 0L, rating = rating
                                )
                            )
                        }
                    }
                    r.endArray()
                    out
                }
            }
        } catch (e: OutOfMemoryError) {
            throw IOException("Playlist too large for this device's memory")
        } catch (e: java.io.EOFException) {
            throw IOException("Invalid response from server")
        } catch (e: java.io.IOException) {
            if (e.javaClass.name.contains("MalformedJson")) throw IOException("Invalid response from server")
            throw e
        } catch (e: IllegalStateException) {
            throw IOException("Invalid response from server")
        }
    }

    private fun metaOf(info: JSONObject?, fallbackPoster: String): Meta {
        if (info == null) return Meta(poster = fallbackPoster)
        val year = info.str("releasedate").ifBlank { info.str("releaseDate") }.ifBlank { info.str("year") }.take(4)
        return Meta(
            plot = info.str("plot").ifBlank { info.str("description") },
            genre = info.str("genre"),
            year = year,
            rating = ratingOf(info),
            duration = info.str("duration").ifBlank { info.str("episode_run_time").let { if (it.isNotBlank()) "$it min" else "" } },
            cast = info.str("cast").ifBlank { info.str("actors") },
            director = info.str("director"),
            backdrop = info.firstImage("backdrop_path"),
            poster = info.str("movie_image").ifBlank { info.str("cover") }.ifBlank { fallbackPoster }
        )
    }

    suspend fun vodMeta(s: Source, item: Item): Meta {
        val txt = Net.text(api(s, "get_vod_info", "vod_id=" + item.id), 30)
        return withContext(Dispatchers.Default) {
            metaOf(JSONObject(txt).optJSONObject("info"), item.logo)
        }
    }

    suspend fun series(s: Source, serie: Item): Pair<Meta, List<Item>> {
        val txt = Net.text(api(s, "get_series_info", "series_id=" + serie.id), 60)
        return withContext(Dispatchers.Default) {
            val d = JSONObject(txt)
            val meta = metaOf(d.optJSONObject("info"), serie.logo)
            val eps = ArrayList<Item>()
            val e = d.opt("episodes")
            fun addSeason(sn: Int, arr: JSONArray) {
                for (j in 0 until arr.length()) {
                    val o = arr.optJSONObject(j) ?: continue
                    val n = o.str("episode_num").toIntOrNull() ?: (j + 1)
                    val info = o.optJSONObject("info")
                    val title = o.str("title").ifBlank { "Episode $n" }
                    eps.add(
                        Item(
                            o.str("id"), title, "Season $sn",
                            info?.str("movie_image").orEmpty().ifBlank { serie.logo }, "ep",
                            ext = o.str("container_extension").ifBlank { "mp4" },
                            srcId = s.id, season = sn, num = n,
                            rating = info?.let { ratingOf(it) } ?: 0.0,
                            plot = info?.str("plot").orEmpty(),
                            info = info?.str("duration").orEmpty(),
                            parent = serie.id
                        )
                    )
                }
            }
            if (e is JSONObject) {
                val keys = e.keys().asSequence().toList()
                keys.forEachIndexed { idx, k -> e.optJSONArray(k)?.let { addSeason(k.toIntOrNull() ?: (idx + 1), it) } }
            } else if (e is JSONArray) {
                for (i in 0 until e.length()) e.optJSONArray(i)?.let { addSeason(i + 1, it) }
            }
            Pair(meta, eps.sortedWith(compareBy({ it.season }, { it.num })))
        }
    }

    /** Build a live URL for a specific container format. */
    fun resolveLive(s: Source, it: Item, format: String = s.format): String {
        if (it.url.isNotBlank()) return it.url
        // Auto starts with TS (widely supported by Xtream panels); the player
        // switches to HLS once if TS cannot be prepared.
        val ext = if (format.equals("ts", true) || format.equals("auto", true)) "ts" else "m3u8"
        return "${base(s)}/live/${pathEnc(s.user)}/${pathEnc(s.pass)}/${pathEnc(it.id)}.$ext"
    }

    fun alternateLive(s: Source, it: Item): String {
        val current = resolveLive(s, it)
        val alternateFormat = if (s.format.equals("ts", true) || s.format.equals("auto", true)) "m3u8" else "ts"
        val alternate = resolveLive(s, it, alternateFormat)
        return if (current == alternate) "" else alternate
    }

    fun resolve(s: Source, it: Item): String {
        if (it.url.isNotEmpty()) return it.url
        val b = base(s)
        val u = pathEnc(s.user)
        val p = pathEnc(s.pass)
        return when (it.kind) {
            "live" -> resolveLive(s, it)
            "ep" -> "$b/series/$u/$p/${pathEnc(it.id)}.${it.ext.ifBlank { "mp4" }}"
            else -> "$b/movie/$u/$p/${pathEnc(it.id)}.${it.ext.ifBlank { "mp4" }}"
        }
    }
}
