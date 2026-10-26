package com.lumora.player

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.json.JSONObject

data class PlayReq(val url: String, val title: String, val kind: String, val item: Item?, val src: Source?)

val ADULT = Regex("xxx|adult|\\+18|18\\+|للكبار", RegexOption.IGNORE_CASE)

fun AppVM.locked(group: String): Boolean = pin.isNotEmpty() && !unlocked && ADULT.containsMatchIn(group)

class AppVM(app: Application) : AndroidViewModel(app) {
    val sources = mutableStateListOf<Source>()
    val favs = mutableStateListOf<Item>()
    val progress = mutableStateListOf<Progress>()
    val viewed = mutableStateListOf<Viewed>()
    val downloads = mutableStateListOf<Download>()
    val catalog = mutableStateMapOf<String, List<Item>>()
    val loading = mutableStateMapOf<String, Boolean>()

    val accents: List<Int> = listOf(0xFFE5173F.toInt(), 0xFF8B5CF6.toInt(), 0xFF3B82F6.toInt(), 0xFF10B981.toInt())

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
    var accentIdx by mutableStateOf(Store.getString("accent", "0").toIntOrNull() ?: 0)
    var castOn by mutableStateOf(Store.getBool("castOn", true))

    init {
        sources.addAll(Store.sources())
        favs.addAll(Store.favs())
        progress.addAll(Store.progress())
        viewed.addAll(Store.views())
        downloads.addAll(Store.downloads())
        activeSrc = Store.getString("activeSrc").ifEmpty { sources.firstOrNull()?.id ?: "" }
    }

    fun src(): Source? = sources.firstOrNull { it.id == activeSrc } ?: sources.firstOrNull()
    fun srcOf(id: String): Source? = sources.firstOrNull { it.id == id }

    fun toast(m: String) { message = m }

    fun accentInt(): Int = accents[accentIdx.coerceIn(0, accents.size - 1)]
    fun changeAccent(i: Int) { accentIdx = i; Store.putString("accent", i.toString()) }

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
                    if (!t.contains("#EXTM3U") && !t.contains("#EXTINF")) throw java.io.IOException("Not a valid M3U playlist")
                }
                done(null)
            } catch (e: Exception) {
                done(e.message ?: "Unknown error")
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
        if (castOn && TvLink.connected()) cast(PlayReq(url, it.name, it.kind, it, s), 0, true)
        else {
            if (castOn && TvLink.paired()) message = "TV is offline, playing on this phone"
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

    // ---------- offline downloads ----------
    private val dm: DownloadManager
        get() = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun download(item: Item) {
        val s = srcOf(item.srcId) ?: src() ?: return
        if (downloads.any { d -> d.item.key == item.key }) { message = "Already downloaded"; return }
        val app = getApplication<Application>()
        val ext = item.ext.ifBlank { "mp4" }
        val base = item.name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(60).ifBlank { "video" }
        val req = DownloadManager.Request(Uri.parse(Xtream.resolve(s, item)))
            .setTitle(item.name)
            .setDescription("Lumora")
            .addRequestHeader("User-Agent", Net.UA)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_MOVIES, base + "_" + item.id + "." + ext)
        val id = dm.enqueue(req)
        downloads.add(0, Download(item, id))
        Store.saveDownloads(downloads)
        message = "Download started"
    }

    fun downloadState(d: Download): String {
        dm.query(DownloadManager.Query().setFilterById(d.downloadId)).use { cur ->
            if (cur.moveToFirst()) {
                val st = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                return when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Ready offline"
                    DownloadManager.STATUS_FAILED -> "Failed"
                    else -> "Downloading…"
                }
            }
        }
        return "Removed"
    }

    fun playOffline(d: Download) {
        val uri = dm.getUriForDownloadedFile(d.downloadId)
        if (uri == null) { message = "Not downloaded yet"; return }
        playing = PlayReq(uri.toString(), d.item.name, d.item.kind, d.item, srcOf(d.item.srcId))
    }

    fun deleteDownload(d: Download) {
        dm.remove(d.downloadId)
        downloads.removeAll { x -> x.item.key == d.item.key }
        Store.saveDownloads(downloads)
    }

    // ---------- settings ----------
    fun changeSort(v: String) { sort = v; Store.putString("sort", v) }
    fun changePin(v: String) { pin = v; unlocked = false; Store.putString("pin", v) }
    fun setResume(v: Boolean) { resumeOn = v; Store.putBool("resume", v) }
    fun setCastOn(v: Boolean) {
        castOn = v; Store.putBool("castOn", v)
        message = if (v) "Playing on TV when it is connected" else "Cast off: playing on this phone"
    }
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
