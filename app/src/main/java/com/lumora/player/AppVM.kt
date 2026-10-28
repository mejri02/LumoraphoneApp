@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lumora.player

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

data class PlayReq(val url: String, val title: String, val kind: String, val item: Item?, val src: Source?)

data class DlInfo(val status: String, val percent: Int, val done: Long, val total: Long, val ready: Boolean, val failed: Boolean, val paused: Boolean = false)

val ADULT = Regex("xxx|adult|\\+18|18\\+|للكبار", RegexOption.IGNORE_CASE)

val ITEM_SORTS = listOf("latest" to "Latest", "oldest" to "Oldest", "rating" to "By rating", "name" to "By name")

fun fmtBytes(b: Long): String = when {
    b <= 0L -> "—"
    b >= 1073741824L -> "%.2f GB".format(b / 1073741824.0)
    b >= 1048576L -> "%.1f MB".format(b / 1048576.0)
    else -> "%d KB".format(b / 1024)
}

fun AppVM.locked(group: String): Boolean = pin.isNotEmpty() && !unlocked && ADULT.containsMatchIn(group)

private fun friendlyPlaybackError(error: PlaybackException): String {
    val detail = (error.cause?.message ?: error.message ?: "").lowercase()
    return when {
        "timeout" in detail || "timed out" in detail -> "The stream took too long to respond. Check your internet and retry."
        "404" in detail || "not found" in detail -> "This channel or stream is unavailable (not found). Try another channel."
        "401" in detail || "403" in detail || "unauthorized" in detail || "forbidden" in detail -> "Access was denied by the provider. Check your subscription or playlist details."
        "cleartext" in detail -> "The provider connection is blocked by Android security settings."
        "resolve host" in detail || "unknownhost" in detail || "network" in detail || "connect" in detail -> "Cannot reach the stream server. Check your internet connection."
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "The stream format is invalid or unsupported. Try another source."
        else -> "Playback failed. The stream may be offline or unsupported. Check your connection and retry."
    }
}

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
    val accountExpiry = mutableStateMapOf<String, String>()
    val accountStatus = mutableStateMapOf<String, String>()
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
    // Full UI theme; index 0 preserves the original Lumora appearance.
    var themeIdx by mutableStateOf(Store.getString("uiTheme", "0").toIntOrNull()?.coerceIn(0, 2) ?: 0)
    var castOn by mutableStateOf(Store.getBool("castOn", true))

    // ---------- player state: lives here so playback keeps going when minimized or in picture-in-picture ----------
    var exo: ExoPlayer? = null
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var playerError by mutableStateOf("")
    var minimized by mutableStateOf(false)
    var showDownloads by mutableStateOf(false)
    private var playerUrl = ""
    private var playerItem: Item? = null
    private var playerKind = ""
    private var playerRetries = 0
    private var resumeAt = 0L
    private var resumed = false

    init {
        // Online subtitle API integration removed; subtitles are loaded from local files.
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
        accentIdx = i.coerceIn(0, accents.size - 1)
        Store.putString("accent", accentIdx.toString())
    }

    fun changeTheme(i: Int) {
        themeIdx = i.coerceIn(0, 2)
        Store.putString("uiTheme", themeIdx.toString())
    }

    private fun sourceCacheKey(s: Source): String {
        // Include credentials in cache identity so an edited Xtream account never sees old lists.
        val raw = "${s.type}|${s.url.trim().trimEnd('/')}|${s.user.trim()}|${s.pass}|${s.format}"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }
        return "${s.id}_$digest"
    }

    fun selectSource(id: String) {
        activeSrc = id
        Store.putString("activeSrc", id)
        catalog.clear()
        loading.clear()
        updatedAt.clear()
        detail = null
        meta = null
        episodes = emptyList()
        episodesLoading = false
        openGroup = null
        playList = emptyList()
    }

    fun saveSource(s: Source) {
        val i = sources.indexOfFirst { it.id == s.id }
        if (i >= 0) sources[i] = s else sources.add(s)
        Store.saveSources(sources)
        catalog.clear()
        loading.clear()
        updatedAt.clear()
        selectSource(s.id)
        message = "Account saved. Loading the new account…"
    }

    fun deleteSource(id: String) {
        sources.removeAll { it.id == id }
        Store.saveSources(sources)
        if (activeSrc == id) selectSource(sources.firstOrNull()?.id ?: "")
    }

    fun refreshAccountInfo(s: Source, force: Boolean = false) {
        if (s.type != "xtream") return
        val key = sourceCacheKey(s)
        if (!force && accountExpiry.containsKey(key)) return
        accountExpiry[key] = "Loading subscription…"
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val raw = Net.text(Xtream.api(s), 30)
                    org.json.JSONObject(raw).optJSONObject("user_info")
                        ?: throw java.io.IOException("Account details unavailable")
                }
                val exp = info.optString("exp_date").toLongOrNull() ?: 0L
                val status = info.optString("status").ifBlank { "Unknown" }
                accountStatus[key] = status
                accountExpiry[key] = if (exp > 0L) {
                    val date = java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT
                    ).format(java.util.Date(exp * 1000L))
                    val remaining = exp * 1000L - System.currentTimeMillis()
                    if (remaining <= 0L) "Expired · $date"
                    else {
                        val days = remaining / 86_400_000L
                        "Expires: $date · ${days} day(s) left"
                    }
                } else "Expiry not provided by server"
            } catch (e: Exception) {
                accountExpiry[key] = "Could not load expiry"
                accountStatus[key] = e.message ?: "Connection error"
            }
        }
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
        val t = CatalogCache.time(sourceCacheKey(s), kind)
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
                        val c = CatalogCache.read(sourceCacheKey(s), k)
                        if (c != null && src()?.let { sourceCacheKey(it) } == sourceCacheKey(s)) {
                            catalog[k] = c
                            updatedAt[k] = CatalogCache.time(sourceCacheKey(s), k)
                        }
                    }
                }
                if (force || !catalog.containsKey(kind) || stale(s, kind)) {
                    if (s.type == "xtream") {
                        val list = Xtream.catalog(s, kind)
                        CatalogCache.write(sourceCacheKey(s), kind, list)
                        if (src()?.let { sourceCacheKey(it) } == sourceCacheKey(s)) {
                            catalog[kind] = list
                            updatedAt[kind] = System.currentTimeMillis()
                        }
                    } else {
                        val t = Net.text(s.url.trim(), 180)
                        val r = M3u.parse(t, s.id)
                        CatalogCache.write(sourceCacheKey(s), "live", r.first)
                        CatalogCache.write(sourceCacheKey(s), "vod", r.second)
                        CatalogCache.write(sourceCacheKey(s), "series", emptyList())
                        if (src()?.let { sourceCacheKey(it) } == sourceCacheKey(s)) {
                            catalog["live"] = r.first
                            catalog["vod"] = r.second
                            catalog["series"] = emptyList()
                            val now = System.currentTimeMillis()
                            updatedAt["live"] = now; updatedAt["vod"] = now; updatedAt["series"] = now
                        }
                    }
                }
            } catch (e: Exception) {
                if (src()?.let { sourceCacheKey(it) } == sourceCacheKey(s)) {
                    if (!catalog.containsKey(kind)) catalog[kind] = emptyList()
                    if (force || (catalog[kind]?.isEmpty() != false)) message = e.message ?: "Network error"
                }
            }
            if (src()?.let { sourceCacheKey(it) } == sourceCacheKey(s)) loading[key] = false
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
        releasePlayer()
        playing = null
        full = false
        minimized = false
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
            startPlayback(req)
            return
        }
        message = "Connecting to TV…"
        viewModelScope.launch {
            val ok = TvLink.sendWhenReachable("cast", castData(req, 0))
            if (ok) {
                message = "Sent to TV"
            } else {
                startPlayback(req)
                message = "TV is offline, playing on this phone"
            }
        }
    }

    fun startPlayback(req: PlayReq) {
        playing = req
        minimized = false
        ensurePlayer(req)
    }

    /** Builds one player per video. Playback state is kept here, not in the screen. */
    fun ensurePlayer(req: PlayReq) {
        if (exo != null && playerUrl == req.url) return
        releasePlayer()
        val ctx = getApplication<Application>()
        val ds = DefaultDataSource.Factory(ctx, OkHttpDataSource.Factory(Net.client).setUserAgent(Net.UA))
        val p = ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(ds)).build()
        playerUrl = req.url
        playerItem = req.item
        playerKind = req.kind
        playerRetries = 0
        resumed = false
        playerError = ""
        resumeAt = if (req.kind == "live" || !resumeOn) 0L else (req.item?.let { progressOf(it) } ?: 0L)
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    if (!resumed) {
                        resumed = true
                        if (resumeAt > 0L) p.seekTo(resumeAt)
                    }
                    playerError = ""
                    playerRetries = 0
                }
            }

            override fun onIsPlayingChanged(v: Boolean) {
                isPlaying = v
            }

            override fun onPlayerError(error: PlaybackException) {
                // Keep retries asynchronous so the UI thread stays responsive, and show a
                // useful reason once automatic retries have been exhausted.
                playerError = friendlyPlaybackError(error)
                if (playerRetries < 3) {
                    playerRetries++
                    val wait = (900L * playerRetries).coerceAtMost(3000L)
                    viewModelScope.launch {
                        delay(wait)
                        if (exo === p && playerError.isNotEmpty()) try {
                            playerError = "Reconnecting…"
                            p.prepare()
                            p.play()
                        } catch (_: Exception) {
                            playerError = "Unable to reconnect. Check your connection and try again."
                        }
                    }
                } else {
                    buffering = false
                }
            }
        })
        p.setMediaItem(MediaItem.fromUri(req.url))
        p.playWhenReady = true
        p.prepare()
        exo = p
    }

    fun releasePlayer() {
        saveCurrentProgress()
        exo?.release()
        exo = null
        playerUrl = ""
        isPlaying = false
        buffering = true
        playerError = ""
    }

    fun saveCurrentProgress() {
        val p = exo ?: return
        val item = playerItem ?: return
        if (playerKind != "live") saveProgress(item, p.currentPosition, p.duration.coerceAtLeast(0L))
    }

    fun togglePlay() {
        val p = exo ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun retryPlayback() {
        val req = playing ?: return
        releasePlayer()
        ensurePlayer(req)
    }

    /** Back button while playing: the video moves to a small bar at the bottom and keeps playing. */
    fun minimize() {
        if (playing == null) return
        full = false
        minimized = true
    }

    fun expand() {
        minimized = false
    }

    fun closeDetail() {
        releasePlayer()
        playing = null
        detail = null
        full = false
        minimized = false
        meta = null
        detailJob?.cancel()
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
            if (ok && closePlayer) { releasePlayer(); playing = null; full = false; minimized = false }
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

    // ---------- offline downloads: own resumable downloader (tap to pause, tap again to resume) ----------
    private val dlJobs = ConcurrentHashMap<String, Job>()
    private val dlCalls = ConcurrentHashMap<String, Call>()
    private val dlPaused: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun dlDir(): File {
        val app = getApplication<Application>()
        return (app.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(app.filesDir, "movies")).apply { mkdirs() }
    }

    /** After a restart nothing is running: unfinished downloads show as paused and continue from the saved part. */
    fun restoreDownloads() {
        downloads.removeAll { x -> x.path.isEmpty() }
        for (d in downloads) {
            val f = File(d.path)
            val size = if (f.exists()) f.length() else 0L
            dlInfo[d.item.key] = if (d.done && f.exists()) {
                DlInfo("Ready offline", 100, size, size, true, false)
            } else {
                val pct = if (d.total > 0L) ((size * 100) / d.total).toInt().coerceIn(0, 100) else 0
                DlInfo("Paused · tap to resume", pct, size, d.total, false, false, true)
            }
        }
    }

    /** The download button: starts, pauses while running, resumes when paused or failed. */
    fun download(item: Item) {
        val key = item.key
        val d = downloads.firstOrNull { x -> x.item.key == key }
        when {
            d == null -> {
                if ((srcOf(item.srcId) ?: src()) == null) return
                val ext = item.ext.ifBlank { "mp4" }
                val base = item.name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(60).ifBlank { "video" }
                val file = File(dlDir(), base + "_" + item.kind + item.id + "_" + (item.srcId.hashCode() and 0x7fffffff) + "." + ext)
                val nd = Download(item, System.currentTimeMillis(), file.absolutePath)
                downloads.add(0, nd)
                Store.saveDownloads(downloads)
                startDownload(nd)
                message = "Download started"
            }
            d.done -> message = "Already downloaded"
            dlJobs[key]?.isActive == true -> pauseDownload(d)
            else -> {
                startDownload(d)
                message = "Download resumed"
            }
        }
    }

    private fun startDownload(d: Download) {
        val key = d.item.key
        val s = srcOf(d.item.srcId) ?: src()
        if (s == null) {
            setDl(key, DlInfo("Source missing", 0, 0L, 0L, false, true))
            return
        }
        dlPaused.remove(key)
        dlJobs[key]?.cancel()
        val url = Xtream.resolve(s, d.item)
        val file = File(d.path)
        dlJobs[key] = viewModelScope.launch(Dispatchers.IO) { runDownload(d, url, file) }
    }

    private suspend fun runDownload(d: Download, url: String, file: File) {
        val key = d.item.key
        var attempt = 0
        while (true) {
            try {
                downloadOnce(d, url, file, key)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dlCalls.remove(key)
                if (dlPaused.contains(key)) return
                attempt++
                val size = if (file.exists()) file.length() else 0L
                if (attempt >= 8) {
                    setDl(key, DlInfo("Failed (${e.message ?: "error"}) · tap to retry", 0, size, d.total, false, true))
                    return
                }
                setDl(key, progressInfo(size, d.total).copy(status = "Connection lost, retrying ($attempt)…"))
                delay(2000L * attempt)
            }
        }
    }

    private suspend fun downloadOnce(d: Download, url: String, file: File, key: String) {
        val have = if (file.exists()) file.length() else 0L
        setDl(key, progressInfo(have, d.total).copy(status = "Connecting…"))
        val req = Request.Builder().url(url).header("User-Agent", Net.UA)
            .apply { if (have > 0L) header("Range", "bytes=$have-") }
            .build()
        val call = Net.client.newCall(req)
        dlCalls[key] = call
        call.execute().use { r ->
            if (r.code == 416 && have > 0L) {
                val range = r.header("Content-Range").orEmpty()
                val match = Regex("""bytes \*/(\d+)""").find(range)
                val serverTotal = match?.groupValues?.get(1)?.toLongOrNull()
                if (serverTotal != null && have == serverTotal) {
                    finishDownload(d, have)
                    return
                }
                throw IOException("Invalid resume range")
            }
            if (!r.isSuccessful) throw IOException(if (r.code == 401 || r.code == 403) "Access denied" else "HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty response")
            val resumed = r.code == 206 && have > 0L
            val start = if (resumed) have else 0L
            val expected = body.contentLength()
            val total = if (expected > 0L) expected + start else 0L
            var done = start
            var last = 0L
            FileOutputStream(file, resumed).use { out ->
                body.byteStream().use { inp ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 500L) {
                            last = now
                            setDl(key, progressInfo(done, total))
                        }
                    }
                }
            }
            if (total > 0L && done < total) throw IOException("Connection lost")
            finishDownload(d, done)
        }
    }

    private fun pauseDownload(d: Download) {
        val key = d.item.key
        dlPaused.add(key)
        dlCalls.remove(key)?.cancel()
        dlJobs.remove(key)?.cancel()
        val f = File(d.path)
        val size = if (f.exists()) f.length() else 0L
        val total = dlInfo[key]?.total?.takeIf { it > 0L } ?: d.total
        setDl(key, progressInfo(size, total).copy(status = "Paused · tap to resume", paused = true))
        message = "Download paused"
    }

    private fun progressInfo(done: Long, total: Long): DlInfo {
        val pct = if (total > 0L) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
        return DlInfo(if (total > 0L) "Downloading $pct%" else "Downloading…", pct, done, total, false, false, false)
    }

    private fun setDl(key: String, info: DlInfo) {
        viewModelScope.launch {
            if (dlPaused.contains(key) && !info.paused) return@launch
            if (downloads.any { x -> x.item.key == key }) dlInfo[key] = info
        }
    }

    private fun finishDownload(d: Download, size: Long) {
        val key = d.item.key
        dlCalls.remove(key)
        dlJobs.remove(key)
        dlPaused.remove(key)
        viewModelScope.launch {
            val i = downloads.indexOfFirst { x -> x.item.key == key }
            if (i >= 0) {
                downloads[i] = downloads[i].copy(total = size, done = true)
                Store.saveDownloads(downloads)
            }
            dlInfo[key] = DlInfo("Ready offline", 100, size, size, true, false)
        }
    }

    fun playOffline(d: Download) {
        val f = File(d.path)
        if (!f.exists()) {
            message = "The file is missing"
            return
        }
        showSettings = false
        showDownloads = false
        detailJob?.cancel()
        detail = d.item
        meta = null
        episodes = emptyList()
        playList = emptyList()
        startPlayback(PlayReq(Uri.fromFile(f).toString(), d.item.name, d.item.kind, d.item, srcOf(d.item.srcId)))
    }

    fun deleteDownload(d: Download) {
        val key = d.item.key
        dlJobs.remove(key)?.cancel()
        dlCalls.remove(key)?.cancel()
        dlPaused.remove(key)
        try {
            File(d.path).delete()
        } catch (_: Exception) {
        }
        downloads.removeAll { x -> x.item.key == key }
        dlInfo.remove(key)
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

    // ---------- startup and background work ----------
    init {
        restoreDownloads()
        viewModelScope.launch {
            var n = 0
            while (true) {
                delay(1000L)
                n++
                if (n % 5 == 0) saveCurrentProgress()
            }
        }
    }
}
