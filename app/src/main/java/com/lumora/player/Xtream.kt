package com.lumora.player

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
    fun base(s: Source): String {
        var u = s.url.trim()
        if (!u.contains("://")) u = "http://$u"
        return u.trimEnd('/')
    }

    private fun enc(v: String) = URLEncoder.encode(v.trim(), "UTF-8")

    fun api(s: Source, action: String? = null, extra: String? = null): String {
        var u = base(s) + "/player_api.php?username=" + enc(s.user) + "&password=" + enc(s.pass)
        if (action != null) u += "&action=$action"
        if (extra != null) u += "&$extra"
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
        if (ui.optString("auth") != "1") throw IOException("Wrong username or password")
        val st = ui.optString("status")
        if (st.isNotEmpty() && st != "Active") throw IOException("Account is not active (status: $st)")
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
            val a = JSONArray(Net.text(api(s, ca), 60))
            for (i in 0 until a.length()) {
                val c = a.getJSONObject(i)
                cats[c.str("category_id")] = c.str("category_name")
            }
        } catch (_: Exception) {
        }
        val raw = Net.text(api(s, da), 180)
        return withContext(Dispatchers.Default) {
            val arr = try {
                JSONArray(raw.trim())
            } catch (e: Exception) {
                throw IOException("Invalid response from server")
            }
            val out = ArrayList<Item>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val group = cats[o.str("category_id")]?.ifBlank { null } ?: "Uncategorized"
                val name = o.str("name").ifBlank { "Unknown" }
                when (kind) {
                    "live" -> out.add(Item(o.str("stream_id"), name, group, o.str("stream_icon"), "live", srcId = s.id, added = o.str("added").toLongOrNull() ?: 0L))
                    "vod" -> out.add(
                        Item(
                            o.str("stream_id"), name, group, o.str("stream_icon"), "vod",
                            ext = o.str("container_extension").ifBlank { "mp4" }, srcId = s.id,
                            added = o.str("added").toLongOrNull() ?: 0L, rating = ratingOf(o)
                        )
                    )
                    else -> out.add(
                        Item(
                            o.str("series_id"), name, group, o.str("cover"), "series", srcId = s.id,
                            added = o.str("last_modified").toLongOrNull() ?: 0L, rating = ratingOf(o)
                        )
                    )
                }
            }
            out
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

    fun resolve(s: Source, it: Item): String {
        if (it.url.isNotEmpty()) return it.url
        val b = base(s)
        val u = enc(s.user)
        val p = enc(s.pass)
        return when (it.kind) {
            "live" -> "$b/live/$u/$p/${it.id}." + (if (s.format == "ts") "ts" else "m3u8")
            "ep" -> "$b/series/$u/$p/${it.id}.${it.ext.ifBlank { "mp4" }}"
            else -> "$b/movie/$u/$p/${it.id}.${it.ext.ifBlank { "mp4" }}"
        }
    }
}
