package com.lumora.player

import android.app.Application
import android.app.DownloadManager
import android.net.Uri
import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

data class PlayReq(val url: String, val title: String, val kind: String, val item: Item?, val src: Source?)

data class DlInfo(val status: String, val percent: Int, val done: Long, val total: Long, val ready: Boolean, val failed: Boolean)

val ADULT = Regex("xxx|adult|\\+18|18\\+|للكبار", RegexOption.IGNORE_CASE)

val ITEM_SORTS = listOf("latest" to "Latest", "oldest" to "Oldest", "rating" to "By rating", "name" to "By name")

fun fmtBytes(b: Long): String = when {
    b <= 0L -> "—"
    b >= 1073741824L -> "%.2f GB".format(b / 1073741824.0)
    b >= 1048576L -> "%.1f MB".format(b / 1048576.0)
    else -> "%d KB".format(b / 1024)
}

fun AppVM.locked(group: String): Boolean = pin.isNotEmpty() && !unlocked && ADULT.containsMatchIn(group)

class AppVM(app: Application) : AndroidViewModel(app) {
    val sources = mutableStateListOf<Source>()
    val favs = mutableStateListOf<Item>()
    val progress = mutableStateListOf<Progress>()
    val viewed = mutableStateListOf<Viewed>()
    val downloads = mutableStateListOf<Download>()
    val catalog = mutableStateMapOf<String, List<Item>>()
    val loading = mutableStateMapOf<String, Boolean>()
    val dlInfo = mutableStateMapOf<String, DlInfo>()

    val accents: List<Int> = listOf(0xFFE5173F.toInt(), 0xFF8B5CF6.toInt(), 0xFF3B82F6.toInt(), 0xFF10B981.toInt())

    var activeSrc by mutableStateOf("")
    var message by mutableStateOf("")
    var playing by mutableStateOf<PlayReq?>(null)
    var seriesOpen by mutableStateOf<Item?>(null)
    var actionItem by mutableStateOf<Item?>(null)
    var episodes by mutableStateOf<List<Item>>(emptyList())
    var episodesLoading by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var sort by mutableStateOf(Store.getString("sort", "count"))
    var itemSort by mutableStateOf(Store.getString("itemSort", "latest"))
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

    fun toast(m: String) {
        message = m
    }

    fun accentInt(): Int = accents[accentIdx.coerceIn(0, accents.size - 1)]
    fun changeAccent(i: Int) {
        accentIdx = i
        Store.putString("accent", i.toString())
    }

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
                if (s.type == "xtream") {
                    Xtream.login(s)
                } else {
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

    // ---------- sorting of posters ----------
    fun changeItemSort(v: String) {
        itemSort = v
        Store.putString("itemSort", v)
    }

    fun sortItems(list: List<Item>): List<Item> {
        if (list.isNotEmpty() && list.all { it.kind == "live" }) return list.sortedBy { it.name.lowercase() }
        return when (itemSort) {
            "oldest" -> list.sortedWith(compareBy<Item> { it.added }.thenBy { it.id.toLongOrNull() ?: 0L })
            "rating" -> list.sortedWith(compareByDescending<Item> { it.rating }.thenBy { it.name.lowercase() })
            "name" -> list.sortedBy { it.name.lowercase() }
            else -> list.sortedWith(compareByDescending<Item> { it.added }.thenByDescending { it.id.toLongOrNull() ?: 0L })
        }
    }

    // ---------- favorites ----------
    fun isFav(it: Item) = favs.any { f -> f.key == it.key }

    fun toggleFav(it: Item) {
        val i = favs.indexOfFirst { f -> f.key == it.key }
        if (i >= 0) favs.removeAt(i) else favs.add(0, it)
        Store.saveFavs(favs)
    }

    // ---------- playback ----------
    fun open(it: Item) {
        val s = srcOf(it.srcId) ?: src() ?: return
        if (it.kind == "series") {
            seriesOpen = it
            episodes = emptyList()
            episodesLoading = true
            viewModelScope.launch {
                try {
                    episodes = Xtream.episodes(s, it)
                } catch (e: Exception) {
                    message = e.message ?: "Network error"
                }
                episodesLoading = false
            }
            return
        }
        bumpView(it)
        playOrCast(PlayReq(Xtream.resolve(s, it), it.name, it.kind, it, s))
    }

    fun playHere(it: Item) {
        val s = srcOf(it.srcId) ?: src() ?: return
        bumpView(it)
        playing = PlayReq(Xtream.resolve(s, it), it.name, it.kind, it, s)
    }

    /** Sends to the TV when "Play on TV" is on and a TV is linked; otherwise plays on this phone. */
    fun playOrCast(req: PlayReq) {
        if (!castOn || !TvLink.paired()) {
            playing = req
            return
        }
        message = "Connecting to TV…"
        viewModelScope.launch {
            val ok = TvLink.sendWhenReachable("cast", castData(req, 0))
            if (ok) {
                message = "Sent to TV"
            } else {
                playing = req
                message = "TV is offline, playing on this phone"
            }
        }
    }

    fun cast(p: PlayReq, startSec: Long, closePlayer: Boolean) {
        if (!TvLink.paired()) {
            message = "Link a TV first"
            return
        }
        message = "Connecting to TV…"
        viewModelScope.launch {
            val ok = TvLink.sendWhenReachable("cast", castData(p, startSec))
            message = if (ok) "Sent to TV" else "Could not reach the TV (same Wi-Fi?)"
            if (ok && closePlayer) playing = null
        }
    }

    private fun castData(p: PlayReq, startSec: Long): JSONObject {
        val s = p.src
        val srcJ = JSONObject().put("type", s?.type ?: "").put("url", s?.url ?: "").put("user", s?.user ?: "")
            .put("pass", s?.pass ?: "").put("mac", "").put("format", s?.format ?: "")
        val it = p.item
        val itemJ = JSONObject().put("id", it?.id ?: "").put("kind", it?.kind ?: "").put("name", p.title)
            .put("group", it?.group ?: "").put("ext", it?.ext ?: "")
        val data = JSONObject().put("url", p.url).put("title", p.title).put("kind", p.kind)
            .put("logo", it?.logo ?: "").put("ref", JSONObject().put("src", srcJ).put("item", itemJ))
        if (startSec > 0) data.put("start", startSec)
        return data
    }

    fun sendSourceToTv(s: Source) {
        val m = JSONObject().put("type", s.type).put("url", s.url).put("user", s.user).put("pass", s.pass)
            .put("name", s.name).put("format", s.format)
        viewModelScope.launch {
            val ok = TvLink.sendWhenReachable("source", JSONObject().put("source", m))
            message = if (ok) "Source sent to TV" else "Could not reach the TV (same Wi-Fi?)"
        }
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

    fun clearProgress() {
        progress.clear()
        Store.saveProgress(progress)
    }

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
        val old = downloads.firstOrNull { d -> d.item.key == item.key }
        if (old != null) {
            if (dlInfo[item.key]?.failed == true) {
                try {
                    dm.remove(old.downloadId)
                } catch (_: Exception) {
                }
                downloads.remove(old)
            } else {
                message = "Already downloaded or downloading"
                return
            }
        }
        val app = getApplication<Application>()
        val ext = item.ext.ifBlank { "mp4" }
        val base = item.name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(60).ifBlank { "video" }
        val fileName = base + "_" + item.kind + item.id + "_" + (item.srcId.hashCode() and 0x7fffffff) + "." + ext
        val req = DownloadManager.Request(Uri.parse(Xtream.resolve(s, item)))
            .setTitle(item.name)
            .setDescription("Lumora")
            .addRequestHeader("User-Agent", Net.UA)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_MOVIES, fileName)
        val id = dm.enqueue(req)
        downloads.add(0, Download(item, id))
        dlInfo[item.key] = DlInfo("Starting…", 0, 0L, 0L, false, false)
        Store.saveDownloads(downloads)
        message = "Download started"
    }

    /** Reads progress for every download in one system query. Called every second by the poller below. */
    fun refreshDownloads() {
        if (downloads.isEmpty()) {
            if (dlInfo.isNotEmpty()) dlInfo.clear()
            return
        }
        val byId = HashMap<Long, String>()
        downloads.forEach { byId[it.downloadId] = it.item.key }
        val found = HashSet<Long>()
        try {
            val q = DownloadManager.Query().setFilterById(*byId.keys.toLongArray())
            dm.query(q)?.use { cur ->
                while (cur.moveToNext()) {
                    val id = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    val key = byId[id] ?: continue
                    val st = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                    found.add(id)
                    val next = when (st) {
                        DownloadManager.STATUS_SUCCESSFUL -> DlInfo("Ready offline", 100, done, total, true, false)
                        DownloadManager.STATUS_FAILED -> DlInfo("Failed · tap download to retry", pct, done, total, false, true)
                        DownloadManager.STATUS_PAUSED -> DlInfo("Waiting for network…", pct, done, total, false, false)
                        DownloadManager.STATUS_PENDING -> DlInfo("Starting…", 0, 0L, total, false, false)
                        else -> DlInfo(if (total > 0) "Downloading $pct%" else "Downloading…", pct, done, total, false, false)
                    }
                    if (dlInfo[key] != next) dlInfo[key] = next
                }
            }
        } catch (_: Exception) {
        }
        downloads.forEach { d ->
            if (d.downloadId !in found) {
                val next = DlInfo("Removed from system", 0, 0L, 0L, false, true)
                if (dlInfo[d.item.key] != next) dlInfo[d.item.key] = next
            }
        }
    }

    fun playOffline(d: Download) {
        val uri = dm.getUriForDownloadedFile(d.downloadId)
        if (uri == null) {
            message = "Not downloaded yet"
            return
        }
        playing = PlayReq(uri.toString(), d.item.name, d.item.kind, d.item, srcOf(d.item.srcId))
    }

    fun deleteDownload(d: Download) {
        try {
            dm.remove(d.downloadId)
        } catch (_: Exception) {
        }
        downloads.removeAll { x -> x.item.key == d.item.key }
        dlInfo.remove(d.item.key)
        Store.saveDownloads(downloads)
    }

    // ---------- settings ----------
    fun changeSort(v: String) {
        sort = v
        Store.putString("sort", v)
    }

    fun changePin(v: String) {
        pin = v
        unlocked = false
        Store.putString("pin", v)
    }

    fun setResume(v: Boolean) {
        resumeOn = v
        Store.putBool("resume", v)
    }

    fun changeCastOn(v: Boolean) {
        castOn = v
        Store.putBool("castOn", v)
        message = if (v) "Playing on TV when it is connected" else "Cast off: playing on this phone"
    }

    fun clearFavs() {
        favs.clear()
        Store.saveFavs(favs)
    }

    // ---------- background poller (downloads progress) ----------
    init {
        viewModelScope.launch {
            while (true) {
                refreshDownloads()
                delay(1000L)
            }
        }
    }
}
