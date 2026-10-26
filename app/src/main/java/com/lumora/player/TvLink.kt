package com.lumora.player

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Phone <-> TV link over the local network (same Wi-Fi).
 * The phone runs a tiny HTTP server (port 8765). The TV app finds it on the subnet and long-polls it:
 *   GET  /lumora/ping?tv=<id>            -> {lumora,phone,known}
 *   POST /lumora/sync?tv=<id>&ack=&ep=   -> long-poll, returns queued messages for the TV (body = TV status)
 *   POST /lumora/status?tv=<id>          -> TV status / hello_ack
 */
object TvLink {
    data class Tv(val id: String, val name: String)
    data class TvView(val id: String, val label: String, val name: String, val online: Boolean, val active: Boolean, val status: JSONObject?)
    private class Q(val seq: Long, val msg: JSONObject, val at: Long)

    const val PORT = 8765

    val version = mutableIntStateOf(0)

    private val tvs = java.util.concurrent.CopyOnWriteArrayList<Tv>()
    private var active = ""
    private val seen = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val status = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()
    private val queues = HashMap<String, MutableList<Q>>()
    private var seq = 0
    private var qseq = 0L
    private val epoch = System.currentTimeMillis().toString(36)
    private val lock = ReentrantLock()
    private val cond = lock.newCondition()
    private var server: ServerSocket? = null
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
        } catch (_: Exception) {
        }
        startServer()
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
        Store.putString("pair", JSONObject().put("tvs", a).put("active", active).toString())
    }

    private fun emit() {
        version.intValue = version.intValue + 1
    }

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

    /** Queues a message for the TV; the TV picks it up on its next long-poll. */
    private fun enqueue(id: String, msg: JSONObject) {
        lock.withLock {
            val now = System.currentTimeMillis()
            val q = queues.getOrPut(id) { mutableListOf() }
            q.removeAll { now - it.at > 90000 }
            q.add(Q(++qseq, msg, now))
            cond.signalAll()
        }
    }

    fun sendTo(id: String, op: String, data: JSONObject? = null, onDone: (Boolean) -> Unit = {}) {
        val ok = lock.withLock { tvs.any { it.id == id } } && (op == "hello" || op == "bye" || isOnline(id))
        enqueue(id, mk(op, data))
        scope.launch { onDone(ok) }
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
        lock.withLock {
            val i = tvs.indexOfFirst { it.id == id }
            if (i < 0) tvs.add(Tv(id, nm)) else if (name.isNotBlank()) tvs[i] = Tv(id, nm)
            active = id
        }
        save(); ping(id); emit()
        return id
    }

    /** Sends hello and waits for the TV to pick it up on the local network. Returns "ok" or "tv" (no answer from TV). */
    suspend fun verify(id: String, ms: Long = 15000): String {
        val t0 = System.currentTimeMillis()
        enqueue(id, mk("hello", JSONObject().put("device", "phone").put("name", "Lumora Phone")))
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
        enqueue(id, mk("bye"))
        lock.withLock {
            tvs.removeAll { it.id == id }
            if (active == id) active = ""
            seen.remove(id); status.remove(id)
        }
        save(); emit()
    }

    /* ---------- local HTTP server ---------- */

    private fun startServer() {
        if (server != null) return
        thread(isDaemon = true, name = "lumora-lan") {
            while (true) {
                try {
                    val ss = ServerSocket()
                    ss.reuseAddress = true
                    ss.bind(InetSocketAddress(PORT))
                    server = ss
                    while (true) {
                        val s = ss.accept()
                        thread(isDaemon = true, name = "lumora-lan-conn") {
                            try { handle(s) } catch (_: Exception) { } finally { try { s.close() } catch (_: Exception) { } }
                        }
                    }
                } catch (_: Exception) {
                }
                server = null
                try { Thread.sleep(3000) } catch (_: InterruptedException) { }
            }
        }
    }

    private fun readLine(inp: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = inp.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > 8192) return null
        }
        return sb.toString()
    }

    private fun handle(sock: Socket) {
        sock.soTimeout = 20000
        sock.tcpNoDelay = true
        val inp = java.io.BufferedInputStream(sock.getInputStream())
        val first = readLine(inp) ?: return
        val parts = first.split(" ")
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        val target = parts[1]
        var len = 0
        while (true) {
            val h = readLine(inp) ?: break
            if (h.isEmpty()) break
            if (h.startsWith("content-length:", ignoreCase = true)) len = h.substring(15).trim().toIntOrNull() ?: 0
        }
        len = len.coerceIn(0, 65536)
        var body = ""
        if (len > 0) {
            val buf = ByteArray(len)
            var n = 0
            while (n < len) {
                val r = inp.read(buf, n, len - n)
                if (r < 0) break
                n += r
            }
            body = String(buf, 0, n, Charsets.UTF_8)
        }
        val path = target.substringBefore('?')
        val q = HashMap<String, String>()
        target.substringAfter('?', "").split('&').forEach { kv ->
            if (kv.isNotEmpty()) {
                val k = kv.substringBefore('=')
                val v = kv.substringAfter('=', "")
                try { q[k] = URLDecoder.decode(v, "UTF-8") } catch (_: Exception) { q[k] = v }
            }
        }
        val tv = q["tv"] ?: ""
        val known = lock.withLock { tvs.any { it.id == tv } }
        val out: JSONObject = when {
            method == "OPTIONS" -> JSONObject().put("ok", true)
            path == "/lumora/ping" -> {
                if (known) { seen[tv] = System.currentTimeMillis(); emit() }
                JSONObject().put("lumora", 1).put("phone", true).put("known", known).put("name", "Lumora Phone")
            }
            path == "/lumora/status" -> {
                if (known) onTvMessage(tv, body)
                JSONObject().put("lumora", 1).put("known", known)
            }
            path == "/lumora/sync" -> sync(tv, known, q["ack"]?.toLongOrNull() ?: 0L, q["ep"] ?: "", body)
            else -> JSONObject().put("lumora", 1).put("error", "not found")
        }
        val bytes = out.toString().toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: *\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n" +
            "Content-Length: ${bytes.size}\r\n\r\n"
        val os = sock.getOutputStream()
        os.write(head.toByteArray(Charsets.UTF_8))
        os.write(bytes)
        os.flush()
    }

    private fun onTvMessage(id: String, text: String) {
        if (text.isBlank()) { seen[id] = System.currentTimeMillis(); emit(); return }
        try {
            val m = JSONObject(text)
            if (m.optInt("lumora") != 1) return
            seen[id] = System.currentTimeMillis()
            val op = m.optString("op")
            if (op == "status" || op == "hello_ack") {
                m.put("_rx", System.currentTimeMillis())
                status[id] = m
            }
            emit()
        } catch (_: Exception) {
        }
    }

    private fun sync(id: String, known: Boolean, ackIn: Long, ep: String, body: String): JSONObject {
        val res = JSONObject().put("lumora", 1).put("known", known).put("epoch", epoch).put("name", "Lumora Phone")
        if (!known) return res.put("msgs", JSONArray())
        onTvMessage(id, body)
        val ack = if (ep == epoch) ackIn else 0L
        val deadline = System.currentTimeMillis() + 8000
        val arr = JSONArray()
        lock.withLock {
            val q = queues.getOrPut(id) { mutableListOf() }
            q.removeAll { it.seq <= ack }
            while (q.isEmpty() && System.currentTimeMillis() < deadline) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) break
                cond.await(left, TimeUnit.MILLISECONDS)
                q.removeAll { it.seq <= ack }
                if (!tvs.any { it.id == id }) break
            }
            q.forEach { arr.put(JSONObject().put("seq", it.seq).put("m", it.msg)) }
        }
        seen[id] = System.currentTimeMillis()
        emit()
        return res.put("msgs", arr)
    }
}
