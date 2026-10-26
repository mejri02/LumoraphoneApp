package com.lumora.player

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Phone <-> TV link over an ntfy relay (same protocol as the Lumora TV app). */
object TvLink {
    data class Tv(val id: String, val name: String)
    data class TvView(val id: String, val label: String, val name: String, val online: Boolean, val active: Boolean, val status: JSONObject?)

    val version = mutableIntStateOf(0)

    private var tvs = mutableListOf<Tv>()
    private var active = ""
    private var relayUrl = ""
    private val seen = HashMap<String, Long>()
    private val status = HashMap<String, JSONObject>()
    private var sseUp = false
    private var job: Job? = null
    private var call: Call? = null
    private var seq = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init() {
        try {
            val o = JSONObject(Store.getString("pair", "{}"))
            val a = o.optJSONArray("tvs") ?: JSONArray()
            for (i in 0 until a.length()) {
                val t = a.getJSONObject(i)
                tvs.add(Tv(t.getString("id"), t.optString("name", "TV")))
            }
            active = o.optString("active")
            relayUrl = o.optString("relay")
        } catch (_: Exception) {
        }
        listen()
        ping(null)
        scope.launch {
            while (isActive) {
                delay(5000)
                emit()
            }
        }
    }

    private fun save() {
        val a = JSONArray()
        tvs.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
        Store.putString("pair", JSONObject().put("tvs", a).put("active", active).put("relay", relayUrl).toString())
    }

    private fun emit() {
        version.intValue = version.intValue + 1
    }

    fun relay(): String {
        val r = relayUrl.trim().ifEmpty { "https://ntfy.sh/" }
        return r.trimEnd('/') + "/"
    }

    fun setRelay(u: String) {
        relayUrl = u.trim()
        save(); listen(); emit()
    }

    private fun topic(id: String) = "lumora-$id"
    fun fmt(id: String): String = id.chunked(4).joinToString("-")

    fun parse(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return ""
        val m = Regex("[?&#]t=([A-Za-z0-9_-]+)").find(s)
        if (m != null) s = m.groupValues[1] else if (s.contains("://")) return ""
        s = s.replace(Regex("^\\s*lumora[-_ ]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^A-Za-z0-9]"), "").lowercase()
        return if (s.length in 8..40) s else ""
    }

    private fun isOnline(id: String) = System.currentTimeMillis() - (seen[id] ?: 0L) < 45000

    fun list(): List<TvView> = tvs.map {
        val s = status[it.id]
        TvView(it.id, fmt(it.id), it.name.ifBlank { s?.optString("name") ?: "TV" }, isOnline(it.id), active == it.id, s)
    }

    fun activeTv(): TvView? = list().firstOrNull { it.active }
    fun connected(): Boolean = activeTv()?.online == true
    fun paired(): Boolean = activeTv() != null

    private fun mk(op: String, data: JSONObject? = null): JSONObject {
        val m = JSONObject().put("lumora", 1).put("op", op)
            .put("id", java.lang.Long.toString(System.currentTimeMillis(), 36) + (++seq))
            .put("ts", System.currentTimeMillis())
        data?.keys()?.forEach { k -> m.put(k, data.get(k)) }
        return m
    }

    private fun post(id: String, msg: JSONObject) {
        val req = Request.Builder().url(relay() + topic(id))
            .post(msg.toString().toRequestBody("text/plain".toMediaType())).build()
        Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("Relay error (HTTP ${r.code})")
        }
    }

    fun sendTo(id: String, op: String, data: JSONObject? = null, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val ok = try {
                post(id, mk(op, data)); true
            } catch (e: Exception) {
                false
            }
            onDone(ok)
        }
    }

    fun send(op: String, data: JSONObject? = null, onDone: (Boolean) -> Unit = {}) {
        if (active.isEmpty()) {
            onDone(false); return
        }
        sendTo(active, op, data, onDone)
    }

    fun ping(id: String?) {
        val ids = if (id != null) listOf(id) else tvs.map { it.id }
        ids.forEach { sendTo(it, "hello", JSONObject().put("device", "phone").put("name", "Lumora Phone")) }
    }

    fun add(input: String, name: String): String {
        val id = parse(input)
        if (id.isEmpty()) return ""
        val nm = name.trim().take(30).ifEmpty { "TV" }
        val i = tvs.indexOfFirst { it.id == id }
        if (i < 0) tvs.add(Tv(id, nm)) else if (name.isNotBlank()) tvs[i] = Tv(id, nm)
        active = id
        save(); listen(); ping(id); emit()
        return id
    }

    /** Pings the TV and waits for a real reply. Returns "ok", "relay" (cannot reach relay) or "tv" (no answer from TV). */
    suspend fun verify(id: String, ms: Long = 12000): String {
        val t0 = System.currentTimeMillis()
        var posted = false
        try { post(id, mk("hello", JSONObject().put("device", "phone").put("name", "Lumora Phone"))); posted = true } catch (_: Exception) { }
        if (!posted) return "relay"
        while (System.currentTimeMillis() - t0 < ms) {
            if ((seen[id] ?: 0L) >= t0) return "ok"
            delay(300)
        }
        return "tv"
    }

    fun select(id: String) {
        if (tvs.none { it.id == id }) return
        active = id; save(); ping(id); emit()
    }

    fun remove(id: String) {
        sendTo(id, "bye")
        tvs.removeAll { it.id == id }
        if (active == id) active = ""
        seen.remove(id); status.remove(id)
        save(); listen(); emit()
    }

    private fun listen() {
        job?.cancel()
        try { call?.cancel() } catch (_: Exception) { }
        if (tvs.isEmpty()) return
        job = scope.launch {
            while (isActive) {
                try {
                    val topics = tvs.joinToString(",") { topic(it.id) + "-tv" }
                    val since = "30s"
                    val req = Request.Builder().url(relay() + topics + "/sse?since=" + since)
                        .header("Accept", "text/event-stream").build()
                    val c = Net.client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()
                    val cl = c.newCall(req)
                    call = cl
                    cl.execute().use { resp ->
                        if (!resp.isSuccessful) throw java.io.IOException("sse")
                        sseUp = true; emit()
                        val src = resp.body!!.source()
                        while (isActive && !src.exhausted()) {
                            val line = src.readUtf8Line() ?: break
                            if (line.startsWith("data:")) handleSse(line.substring(5).trim())
                        }
                    }
                } catch (_: Exception) {
                }
                sseUp = false
                emit()
                if (!isActive) break
                delay(4000)
            }
        }
    }

    private fun handleSse(data: String) {
        try {
            val o = JSONObject(data)
            if (o.optString("event") != "message") return
            val msg = o.optString("message")
            var t = o.optString("topic").removeSuffix("-tv").removePrefix("lumora-")
            if (t.isEmpty() && tvs.size == 1) t = tvs[0].id
            if (tvs.none { it.id == t }) return
            val m = JSONObject(msg)
            if (m.optInt("lumora") != 1) return
            seen[t] = System.currentTimeMillis()
            val op = m.optString("op")
            if (op == "status" || op == "hello_ack") {
                m.put("_rx", System.currentTimeMillis())
                status[t] = m
            }
            emit()
        } catch (_: Exception) {
        }
    }
}
