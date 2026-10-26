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
import kotlinx.coroutines.Job

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
    var detail by mutableStateOf<Item?>(null)
    var meta by mutableStateOf<Meta?>(null)
    var openGroup by mutableStateOf<GroupRef?>(null)
    var playList by mutableStateOf<List<Item>>(emptyList())
    var full by mutableStateOf(false)
    var pip by mutableStateOf(false)
    var actionItem by mutableStateOf<Item?>(null)
    var episodes by mutableStateOf<List<Item>>(emptyList())
    var episodesLoading by mutableStateOf(false)
    val updatedAt = mutableStateMapOf<String, Long>()
    var refreshHours by mutableStateOf(Store.getString("refreshH", "6").toIntOrNull() ?: 6)
    private var detailJob: Job? = null
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

    private fun loadKey(s: Source, kind: String) = if (s.type == "xtream") kind else "all"

    fun isLoading(kind: String): Boolean {
        val s = src() ?: return false
        return loading[loadKey(s, kind)] == true
    }

    private fun stale(s: Source, kind: String): Boolean {
        val t = CatalogCache.time(s.id, kind)
        if (t == 0L) return true
        if (refreshHours <= 0) return false
        return System.currentTimeMillis() - t > refreshHours * 3_600_000L
    }

    /** Opens the lists from the phone storage first (instant), then refreshes from the server only when they are old. */
    fun load(kind: String, force: Boolean = false) {
        val s = src() ?: return
        val key = loadKey(s, kind)
        if (loading[key] == true) return
        val have = catalog.containsKey(kind)
        if (have && !force && !stale(s, kind)) return
        loading[key] = true
        viewModelScope.launch {
            try {
                if (!have && !force) {
                    val kinds = if (s.type == "xtream") listOf(kind) else listOf("live", "vod", "series")
                    for (k in kinds) {
                        val c = CatalogCache.read(s.id, k)
                        if (c != null && src()?.id == s.id) {
                            catalog[k] = c
                            updatedAt[k] = CatalogCache.time(s.id, k)
                        }
                    }
                }
                if (force || !catalog.containsKey(kind) || stale(s, kind)) {
                    if (s.type == "xtream") {
                        val list = Xtream.catalog(s, kind)
                        CatalogCache.write(s.id, kind, list)
                        if (src()?.id == s.id) {
                            catalog[kind] = list
                            updatedAt[kind] = System.currentTimeMillis()
                        }
                    } else {
                        val t = Net.text(s.url.trim(), 180)
                        val r = M3u.parse(t, s.id)
                        CatalogCache.write(s.id, "live", r.first)
                        CatalogCache.write(s.id, "vod", r.second)
                        CatalogCache.write(s.id, "series", emptyList())
                        if (src()?.id == s.id) {
                            catalog["live"] = r.first
                            catalog["vod"] = r.second
                            catalog["series"] = emptyList()
                            val now = System.currentTimeMillis()
                            updatedAt["live"] = now; updatedAt["vod"] = now; updatedAt["series"] = now
                        }
                    }
                }
            } catch (e: Exception) {
                if (!catalog.containsKey(kind)) catalog[kind] = emptyList()
                if (force || (catalog[kind]?.isEmpty() != false)) message = e.message ?: "Network error"
            }
            loading[key] = false
        }
    }

    fun refreshAll() {
        listOf("live", "vod", "series").forEach { load(it, force = true) }
        message = "Updating lists…"
    }

    fun changeRefresh(h: Int) {
        refreshHours = h
        Store.putString("refreshH", h.toString())
    }

    fun clearCache() {
        CatalogCache.clear()
        catalog.clear()
        updatedAt.clear()
        message = "Saved lists cleared"
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
    /** Opens the detail page. Movies / episodes show Play; channels start immediately; series show seasons + episodes. */
    fun open(it: Item, auto: Boolean = false) {
        val s = srcOf(it.srcId) ?: src() ?: return
        detailJob?.cancel()
        playing = null
        full = false
        meta = null
        episodes = emptyList()
        episodesLoading = false
        playList = emptyList()
        when (it.kind) {
            "series" -> {
                detail = it
                loadSeries(s, it, null, auto)
            }
            "ep" -> {
                if (it.parent.isNotEmpty()) {
                    val stub = Item(it.parent, it.name.substringBefore(" · "), it.group, it.logo, "series", srcId = it.srcId)
                    detail = stub
                    loadSeries(s, stub, it, true)
                } else {
                    detail = it
                    play(it)
                }
            }
            "live" -> {
                detail = it
                val all = catalog["live"] ?: emptyList()
                playList = sortItems(all.filter { x -> x.group == it.group })
                play(it)
            }
            else -> {
                detail = it
                detailJob = viewModelScope.launch {
                    try {
                        if (s.type == "xtream" && it.url.isEmpty()) meta = Xtream.vodMeta(s, it)
                    } catch (_: Exception) {
                    }
                }
                if (auto) play(it)
            }
        }
    }

    private fun loadSeries(s: Source, serie: Item, autoEp: Item?, autoFirst: Boolean) {
        episodesLoading = true
        detailJob = viewModelScope.launch {
            try {
                val r = Xtream.series(s, serie)
                meta = r.first
                episodes = r.second
                playList = r.second
                if (autoEp != null) {
                    r.second.firstOrNull { e -> e.id == autoEp.id }?.let { e -> playEpisode(serie, e) }
                } else if (autoFirst) {
                    nextEpisodeFor(serie)?.let { e -> playEpisode(serie, e) }
                }
            } catch (e: Exception) {
                message = e.message ?: "Network error"
            }
            episodesLoading = false
        }
    }

    /** The episode to continue (last watched of this series) or the first one. */
    fun nextEpisodeFor(serie: Item): Item? {
        val last = progress.firstOrNull { p -> p.item.parent == serie.id && p.item.srcId == serie.srcId }
        val e = last?.let { l -> episodes.firstOrNull { x -> x.id == l.item.id } }
        return e ?: episodes.firstOrNull()
    }

    fun playEpisode(serie: Item, e: Item) {
        val named = e.copy(
            name = serie.name + " · S" + e.season + "E" + e.num,
            logo = e.logo.ifBlank { serie.logo }, parent = serie.id
        )
        play(named)
    }

    fun play(it: Item) {
        val s = srcOf(it.srcId) ?: src() ?: return
        bumpView(it)
        playOrCast(PlayReq(Xtream.resolve(s, it), it.name, it.kind, it, s))
    }

    fun closeDetail() {
        playing = null
        detail = null
        full = false
        meta = null
        detailJob?.cancel()
    }

    fun canStep(delta: Int): Boolean {
        val cur = playing?.item ?: return false
        val i = playList.indexOfFirst { x -> x.id == cur.id }
        return i >= 0 && (i + delta) in playList.indices
    }

    fun step(delta: Int) {
        val cur = playing?.item ?: return
        val i = playList.indexOfFirst { x -> x.id == cur.id }
        val n = playList.getOrNull(i + delta) ?: return
        zapTo(n)
    }

    fun zapTo(n: Item) {
        val d = detail ?: return
        if (n.kind == "ep") playEpisode(d, n) else {
            detail = n
            meta = null
            play(n)
        }
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
            if (ok && closePlayer) { playing = null; full = false }
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

    fun castItem(it: Item) {
        if (!TvLink.paired()) {
            message = "Link a TV first"
            return
        }
        val s = srcOf(it.srcId) ?: src() ?: return
        val target = if (it.kind == "series") {
            nextEpisodeFor(it)?.let { e -> e.copy(name = it.name + " · S" + e.season + "E" + e.num, logo = e.logo.ifBlank { it.logo }, parent = it.id) }
        } else it
        if (target == null) {
            message = "Episodes are still loading"
            return
        }
        cast(PlayReq(Xtream.resolve(s, target), target.name, target.kind, target, s), (progressOf(target) ?: 0L) / 1000, true)
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
        if (item.kind == "live" || pos < 8_000L) return
        val finished = dur > 0 && pos > dur * 0.95
        progress.removeAll { p -> p.item.key == item.key }
        if (!finished) progress.add(0, Progress(item, pos, dur, System.currentTimeMillis()))
        Store.saveProgress(progress)
    }

    fun progressOf(item: Item): Long? = progress.firstOrNull { p -> p.item.key == item.key }?.pos?.takeIf { it > 0 }

    fun continueWatching(): List<Progress> = progress.take(20)

    fun removeProgress(item: Item) {
        progress.removeAll { p -> p.item.key == item.key }
        Store.saveProgress(progress)
    }

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
        showSettings = false
        detailJob?.cancel()
        detail = d.item
        meta = null
        episodes = emptyList()
        playList = emptyList()
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
