package com.lumora.player

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Source(
    val id: String,
    val name: String,
    val type: String,
    val url: String,
    val user: String = "",
    val pass: String = "",
    val format: String = "auto",
    val mac: String = "",
    val proxy: String = ""
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("type", type)
        .put("url", url).put("user", user).put("pass", pass).put("format", format).put("mac", mac).put("proxy", proxy)

    companion object {
        fun from(o: JSONObject) = Source(
            o.getString("id"), o.optString("name"), o.optString("type", "xtream"), o.optString("url"),
            o.optString("user"), o.optString("pass"), o.optString("format", "auto"),
            o.optString("mac"), o.optString("proxy")
        )
    }
}

data class Item(
    val id: String,
    val name: String,
    val group: String,
    val logo: String,
    val kind: String,
    val ext: String = "",
    val url: String = "",
    val srcId: String = "",
    val season: Int = 0,
    val num: Int = 0,
    val added: Long = 0L,
    val rating: Double = 0.0,
    val plot: String = "",
    val info: String = "",
    val parent: String = "",
    val cmd: String = ""
) {
    val key: String get() = "$srcId|$kind|$id"

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("group", group)
        .put("logo", logo).put("kind", kind).put("ext", ext).put("url", url).put("srcId", srcId)
        .put("season", season).put("num", num).put("added", added).put("rating", rating)
        .put("plot", plot).put("info", info).put("parent", parent).put("cmd", cmd)

    companion object {
        fun from(o: JSONObject) = Item(
            o.optString("id"), o.optString("name"), o.optString("group"), o.optString("logo"),
            o.optString("kind", "live"), o.optString("ext"), o.optString("url"), o.optString("srcId"),
            o.optInt("season"), o.optInt("num"), o.optLong("added"), o.optDouble("rating", 0.0),
            o.optString("plot"), o.optString("info"), o.optString("parent"), o.optString("cmd")
        )
    }
}

data class Progress(val item: Item, val pos: Long, val dur: Long, val ts: Long)

data class Meta(
    val plot: String = "", val genre: String = "", val year: String = "", val rating: Double = 0.0,
    val duration: String = "", val cast: String = "", val director: String = "", val backdrop: String = "",
    val poster: String = ""
)

data class GroupRef(val kind: String, val name: String)

data class Viewed(val item: Item, val count: Int)

data class Download(val item: Item, val id: Long, val path: String, val total: Long = 0L, val done: Boolean = false)

object Store {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        if (::sp.isInitialized) return
        sp = c.getSharedPreferences("lumora", Context.MODE_PRIVATE)
    }

    fun sources(): MutableList<Source> {
        val out = mutableListOf<Source>()
        try {
            val a = JSONArray(sp.getString("sources", "[]"))
            for (i in 0 until a.length()) out.add(Source.from(a.getJSONObject(i)))
        } catch (_: Exception) {
        }
        return out
    }

    fun saveSources(l: List<Source>) {
        val a = JSONArray()
        l.forEach { a.put(it.toJson()) }
        sp.edit().putString("sources", a.toString()).apply()
    }

    fun favs(): MutableList<Item> {
        val out = mutableListOf<Item>()
        try {
            val a = JSONArray(sp.getString("favs", "[]"))
            for (i in 0 until a.length()) out.add(Item.from(a.getJSONObject(i)))
        } catch (_: Exception) {
        }
        return out
    }

    fun saveFavs(l: List<Item>) {
        val a = JSONArray()
        l.forEach { a.put(it.toJson()) }
        sp.edit().putString("favs", a.toString()).apply()
    }

    private fun arr(k: String): JSONArray = try { JSONArray(sp.getString(k, "[]")) } catch (_: Exception) { JSONArray() }

    private fun sourceKey(base: String, srcId: String) = "${base}_source_${srcId}"

    fun favsFor(srcId: String): MutableList<Item> {
        if (srcId.isBlank()) return mutableListOf()
        val key = sourceKey("favs", srcId)
        if (sp.contains(key)) {
            val out = mutableListOf<Item>()
            val a = arr(key)
            for (i in 0 until a.length()) try {
                out.add(Item.from(a.getJSONObject(i)))
            } catch (_: Exception) {}
            return out
        }
        return favs().filter { it.srcId == srcId && srcId.isNotBlank() }.toMutableList()
    }

    fun saveFavsFor(srcId: String, l: List<Item>) {
        val a = JSONArray()
        l.forEach { a.put(it.toJson()) }
        sp.edit().putString(sourceKey("favs", srcId), a.toString()).apply()
    }

    fun progressFor(srcId: String): MutableList<Progress> {
        if (srcId.isBlank()) return mutableListOf()
        val key = sourceKey("progress", srcId)
        if (sp.contains(key)) {
            val out = mutableListOf<Progress>()
            val a = arr(key)
            for (i in 0 until a.length()) try {
                val o = a.getJSONObject(i)
                val item = Item.from(o.getJSONObject("item"))
                out.add(Progress(item, o.optLong("pos"), o.optLong("dur"), o.optLong("ts")))
            } catch (_: Exception) {}
            return out
        }
        return progress().filter { it.item.srcId == srcId && srcId.isNotBlank() }.toMutableList()
    }

    fun saveProgressFor(srcId: String, l: List<Progress>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("item", it.item.toJson())
                .put("pos", it.pos).put("dur", it.dur).put("ts", it.ts))
        }
        sp.edit().putString(sourceKey("progress", srcId), a.toString()).apply()
    }

    fun viewsFor(srcId: String): MutableList<Viewed> {
        if (srcId.isBlank()) return mutableListOf()
        val key = sourceKey("views", srcId)
        if (sp.contains(key)) {
            val out = mutableListOf<Viewed>()
            val a = arr(key)
            for (i in 0 until a.length()) try {
                val o = a.getJSONObject(i)
                out.add(Viewed(Item.from(o.getJSONObject("item")), o.optInt("count")))
            } catch (_: Exception) {}
            return out
        }
        return views().filter { it.item.srcId == srcId && srcId.isNotBlank() }.toMutableList()
    }

    fun saveViewsFor(srcId: String, l: List<Viewed>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("item", it.item.toJson()).put("count", it.count))
        }
        sp.edit().putString(sourceKey("views", srcId), a.toString()).apply()
    }

    /**
     * Copies the old global lists into the per-source keys once. Safe to call on every start:
     * after the keys exist it returns immediately. Removes hidden writes from the getters.
     */
    fun migrate(srcId: String) {
        if (srcId.isBlank() || !::sp.isInitialized) return
        if (!sp.contains(sourceKey("favs", srcId))) saveFavsFor(srcId, favs().filter { it.srcId == srcId })
        if (!sp.contains(sourceKey("progress", srcId))) saveProgressFor(srcId, progress().filter { it.item.srcId == srcId })
        if (!sp.contains(sourceKey("views", srcId))) saveViewsFor(srcId, views().filter { it.item.srcId == srcId })
    }

    /** True once [init] has run; used to fail gracefully instead of crashing. */
    fun ready(): Boolean = ::sp.isInitialized

    /**
     * Removes every per-source list (favorites, progress, views) for a deleted source, and also
     * strips its entries from the legacy global lists so they cannot be migrated back later.
     */
    fun removeSourceData(srcId: String) {
        sp.edit()
            .remove(sourceKey("favs", srcId))
            .remove(sourceKey("progress", srcId))
            .remove(sourceKey("views", srcId))
            .apply()
        saveFavs(favs().filterNot { it.srcId == srcId })
        saveProgress(progress().filterNot { it.item.srcId == srcId })
        saveViews(views().filterNot { it.item.srcId == srcId })
    }

    fun progress(): MutableList<Progress> {
        val out = mutableListOf<Progress>()
        val a = arr("progress")
        for (i in 0 until a.length()) {
            try {
                val o = a.getJSONObject(i)
                out.add(Progress(Item.from(o.getJSONObject("item")), o.optLong("pos"), o.optLong("dur"), o.optLong("ts")))
            } catch (_: Exception) {
            }
        }
        return out
    }

    fun saveProgress(l: List<Progress>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("item", it.item.toJson()).put("pos", it.pos).put("dur", it.dur).put("ts", it.ts)) }
        sp.edit().putString("progress", a.toString()).apply()
    }

    fun views(): MutableList<Viewed> {
        val out = mutableListOf<Viewed>()
        val a = arr("views")
        for (i in 0 until a.length()) {
            try {
                val o = a.getJSONObject(i)
                out.add(Viewed(Item.from(o.getJSONObject("item")), o.optInt("count")))
            } catch (_: Exception) {
            }
        }
        return out
    }

    fun saveViews(l: List<Viewed>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("item", it.item.toJson()).put("count", it.count)) }
        sp.edit().putString("views", a.toString()).apply()
    }

    fun downloads(): MutableList<Download> {
        val out = mutableListOf<Download>()
        val a = arr("downloads")
        for (i in 0 until a.length()) {
            try {
                val o = a.getJSONObject(i)
                out.add(Download(Item.from(o.getJSONObject("item")), o.optLong("id"), o.optString("path"), o.optLong("total"), o.optBoolean("done")))
            } catch (_: Exception) {
            }
        }
        return out
    }

    fun saveDownloads(l: List<Download>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("item", it.item.toJson()).put("id", it.id).put("path", it.path).put("total", it.total).put("done", it.done)) }
        sp.edit().putString("downloads", a.toString()).apply()
    }

    fun getBool(k: String, d: Boolean): Boolean =
        if (::sp.isInitialized) sp.getBoolean(k, d) else d

    fun putBool(k: String, v: Boolean) {
        if (::sp.isInitialized) sp.edit().putBoolean(k, v).apply()
    }

    fun getString(k: String, d: String = ""): String =
        if (::sp.isInitialized) sp.getString(k, d) ?: d else d

    fun putString(k: String, v: String) {
        if (::sp.isInitialized) sp.edit().putString(k, v).apply()
    }

    /** Synchronous write, so the value is on disk when this returns (used for pairing data). */
    fun putStringNow(k: String, v: String) {
        if (::sp.isInitialized) sp.edit().putString(k, v).commit()
    }
}
