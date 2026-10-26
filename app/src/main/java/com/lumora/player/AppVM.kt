package com.lumora.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.json.JSONObject

data class PlayReq(val url: String, val title: String, val kind: String, val item: Item?, val src: Source?)

val ADULT = Regex("xxx|adult|\\+18|18\\+|للكبار", RegexOption.IGNORE_CASE)

fun AppVM.locked(group: String): Boolean = pin.isNotEmpty() && !unlocked && ADULT.containsMatchIn(group)

class AppVM : ViewModel() {
    val sources = mutableStateListOf<Source>()
    val favs = mutableStateListOf<Item>()
    val progress = mutableStateListOf<Progress>()
    val viewed = mutableStateListOf<Viewed>()
    val catalog = mutableStateMapOf<String, List<Item>>()
    val loading = mutableStateMapOf<String, Boolean>()

    var activeSrc by mutableStateOf("")
    var message by mutableStateOf("")
    var playing by mutableStateOf<PlayReq?>(null)
    var seriesOpen by mutableStateOf<Item?>(null)
    var episodes by mutableStateOf<List<Item>>(emptyList())
    var episodesLoading by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var sort by mutableStateOf(Store.getString("sort", "count"))
    var resumeOn by mutableStateOf(Store.getBool("resume", true))
    var pin by mutableStateOf(Store.getString("pin", ""))
    var unlocked by mutableStateOf(false)

    init {
        sources.addAll(Store.sources())
        favs.addAll(Store.favs())
        progress.addAll(Store.progress())
        viewed.addAll(Store.views())
        activeSrc = Store.getString("activeSrc").ifEmpty { sources.firstOrNull()?.id ?: "" }
    }

    fun src(): Source? = sources.firstOrNull { it.id == activeSrc } ?: sources.firstOrNull()
    fun srcOf(id: String): Source? = sources.firstOrNull { it.id == id }

    fun toast(m: String) { message = m }

    fun selectSource(id: String) {
        activeSrc = id
        Store.putString("activeSrc", id)
        catalog.clear()
    }

    fun saveSource(s: Source) {
        val i = sources.indexOfFirst { it.id == s.id }
        if (i >= 0) sources[i] = s else sources.add(s)
        Store.saveSources(sources)
        selectSource(s.id)
    }

    fun deleteSource(id: String) {
        sources.removeAll { it.id == id }
        Store.saveSources(sources)
        if (activeSrc == id) selectSource(sources.firstOrNull()?.id ?: "")
    }

    fun test(s: Source, done: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                if (s.type == "xtream") Xtream.login(s) else {
                    val t = Net.text(s.url.trim(), 60)
                    if (!t.contains("#EXTM3U") && !t.contains("#EXTINF")) throw java.io.IOException("Invalid response from server")
                }
                done(null)
            } catch (e: Exception) {
                done(e.message ?: "Network error")
            }
        }
    }

    fun load(kind: String, force: Boolean = false) {
        val s = src() ?: return
        if (!force && catalog.containsKey(kind)) return
        if (loading[kind] == true) return
        loading[kind] = true
        viewModelScope.launch {
            try {
                if (s.type == "xtream") {
                    catalog[kind] = Xtream.catalog(s, kind)
                } else {
                    val t = Net.text(s.url.trim(), 180)
                    val r = M3u.parse(t, s.id)
                    catalog["live"] = r.first
                    catalog["vod"] = r.second
                    catalog["series"] = emptyList()
                }
            } catch (e: Exception) {
                message = e.message ?: "Network error"
                catalog[kind] = emptyList()
            }
            loading[kind] = false
        }
    }

    fun isFav(it: Item) = favs.any { f -> f.key == it.key }
    fun toggleFav(it: Item) {
        val i = favs.indexOfFirst { f -> f.key == it.key }
        if (i >= 0) favs.removeAt(i) else favs.add(0, it)
        Store.saveFavs(favs)
    }

    fun open(it: Item) {
        val s = srcOf(it.srcId) ?: src() ?: return
        if (it.kind == "series") {
            seriesOpen = it
            episodes = emptyList()
            episodesLoading = true
            viewModelScope.launch {
                try { episodes = Xtream.episodes(s, it) } catch (e: Exception) { message = e.message ?: "Network error" }
                episodesLoading = false
            }
            return
        }
        bumpView(it)
        val url = Xtream.resolve(s, it)
        if (TvLink.connected()) cast(PlayReq(url, it.name, it.kind, it, s), 0, true)
        else {
            if (TvLink.paired()) message = "TV is offline, playing on this phone"
            playing = PlayReq(url, it.name, it.kind, it, s)
        }
    }

    fun playHere(it: Item) {
        val s = srcOf(it.srcId) ?: src() ?: return
        bumpView(it)
        playing = PlayReq(Xtream.resolve(s, it), it.name, it.kind, it, s)
    }

    // ---------- continue watching ----------
    fun saveProgress(item: Item, pos: Long, dur: Long) {
        if (item.kind == "live" || pos < 30_000L) return
        val finished = dur > 0 && pos > dur * 0.95
        progress.removeAll { p -> p.item.key == item.key }
        if (!finished) progress.add(0, Progress(item, pos, dur, System.currentTimeMillis()))
        Store.saveProgress(progress)
    }

    fun progressOf(item: Item): Long? = progress.firstOrNull { p -> p.item.key == item.key }?.pos?.takeIf { it > 0 }

    fun continueWatching(): List<Progress> = progress.take(20)

    fun clearProgress() { progress.clear(); Store.saveProgress(progress) }

    // ---------- most viewed ----------
    fun bumpView(item: Item) {
        if (item.kind != "live") return
        val i = viewed.indexOfFirst { v -> v.item.key == item.key }
        if (i >= 0) viewed[i] = viewed[i].copy(count = viewed[i].count + 1) else viewed.add(Viewed(item, 1))
        Store.saveViews(viewed)
    }

    fun mostViewed(): List<Viewed> = viewed.sortedByDescending { v -> v.count }.take(12)

    // ---------- settings ----------
    fun setSort(v: String) { sort = v; Store.putString("sort", v) }
    fun setResume(v: Boolean) { resumeOn = v; Store.putBool("resume", v) }
    fun setPin(v: String) { pin = v; unlocked = false; Store.putString("pin", v) }
    fun clearFavs() { favs.clear(); Store.saveFavs(favs) }

    fun cast(p: PlayReq, startSec: Long, closePlayer: Boolean) {
        if (!TvLink.paired()) { message = "Link a TV first"; return }
        val s = p.src
        val srcJ = JSONObject().put("type", s?.type ?: "").put("url", s?.url ?: "").put("user", s?.user ?: "")
            .put("pass", s?.pass ?: "").put("mac", "").put("format", s?.format ?: "")
        val it = p.item
        val itemJ = JSONObject().put("id", it?.id ?: "").put("kind", it?.kind ?: "").put("name", p.title)
            .put("group", it?.group ?: "").put("ext", it?.ext ?: "")
        val data = JSONObject().put("url", p.url).put("title", p.title).put("kind", p.kind)
            .put("logo", it?.logo ?: "").put("ref", JSONObject().put("src", srcJ).put("item", itemJ))
        if (startSec > 0) data.put("start", startSec)
        message = "Connecting…"
        TvLink.send("cast", data) { ok ->
            message = if (ok) "Sent to TV" else "Could not reach the relay"
            if (ok && closePlayer) playing = null
        }
    }

    fun sendSourceToTv(s: Source) {
        val m = JSONObject().put("type", s.type).put("url", s.url).put("user", s.user).put("pass", s.pass)
            .put("name", s.name).put("format", s.format)
        TvLink.send("source", JSONObject().put("source", m)) { ok ->
            message = if (ok) "Source sent to TV" else "Could not reach the relay"
        }
    }
}
