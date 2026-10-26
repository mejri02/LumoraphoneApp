package com.lumora.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

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
        val txt = Net.text(api(s), 30)
        val o = try {
            JSONObject(txt.trim())
        } catch (e: Exception) {
            throw IOException("Invalid response from server")
        }
        val ui = o.optJSONObject("user_info") ?: throw IOException("Invalid response from server")
        if (ui.optString("auth") != "1") throw IOException("Invalid username or password")
        val st = ui.optString("status")
        if (st.isNotEmpty() && st != "Active") throw IOException("Account status: $st")
        val exp = ui.optString("exp_date").toLongOrNull() ?: 0L
        if (exp > 0 && exp * 1000 < System.currentTimeMillis()) throw IOException("Account expired")
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
                cats[c.optString("category_id")] = c.optString("category_name")
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
                val group = cats[o.optString("category_id")] ?: "Uncategorized"
                val name = o.optString("name").ifBlank { "Unknown" }
                when (kind) {
                    "live" -> out.add(Item(o.optString("stream_id"), name, group, o.optString("stream_icon"), "live", srcId = s.id))
                    "vod" -> out.add(
                        Item(
                            o.optString("stream_id"), name, group, o.optString("stream_icon"), "vod",
                            ext = o.optString("container_extension").ifBlank { "mp4" }, srcId = s.id
                        )
                    )
                    else -> out.add(Item(o.optString("series_id"), name, group, o.optString("cover"), "series", srcId = s.id))
                }
            }
            out
        }
    }

    suspend fun episodes(s: Source, serie: Item): List<Item> {
        val txt = Net.text(api(s, "get_series_info", "series_id=" + serie.id), 60)
        return withContext(Dispatchers.Default) {
            val d = JSONObject(txt)
            val eps = ArrayList<Item>()
            val e = d.opt("episodes")
            fun addSeason(sn: Int, arr: JSONArray) {
                for (j in 0 until arr.length()) {
                    val o = arr.optJSONObject(j) ?: continue
                    val n = o.optString("episode_num").toIntOrNull() ?: (j + 1)
                    val info = o.optJSONObject("info")
                    eps.add(
                        Item(
                            o.optString("id"), o.optString("title").ifBlank { "Episode $n" }, "Season $sn",
                            info?.optString("movie_image") ?: serie.logo, "ep",
                            ext = o.optString("container_extension").ifBlank { "mp4" },
                            srcId = s.id, season = sn, num = n
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
            eps.sortedWith(compareBy({ it.season }, { it.num }))
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
