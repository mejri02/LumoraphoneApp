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
    val format: String = "m3u8"
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("type", type)
        .put("url", url).put("user", user).put("pass", pass).put("format", format)

    companion object {
        fun from(o: JSONObject) = Source(
            o.getString("id"), o.optString("name"), o.optString("type", "xtream"), o.optString("url"),
            o.optString("user"), o.optString("pass"), o.optString("format", "m3u8")
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
    val num: Int = 0
) {
    val key: String get() = "$srcId|$kind|$id"

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("group", group)
        .put("logo", logo).put("kind", kind).put("ext", ext).put("url", url).put("srcId", srcId)
        .put("season", season).put("num", num)

    companion object {
        fun from(o: JSONObject) = Item(
            o.optString("id"), o.optString("name"), o.optString("group"), o.optString("logo"),
            o.optString("kind", "live"), o.optString("ext"), o.optString("url"), o.optString("srcId"),
            o.optInt("season"), o.optInt("num")
        )
    }
}

object Store {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
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

    fun getString(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun putString(k: String, v: String) {
        sp.edit().putString(k, v).apply()
    }
}
