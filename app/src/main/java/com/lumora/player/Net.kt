package com.lumora.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object Net {
    const val UA = "VLC/3.0.20 LibVLC/3.0.20"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    // Every `newBuilder()` would create a new connection pool and dispatcher thread pool, so the
    // few read-timeout variants are cached instead. Catalog refreshes no longer leak threads.
    private val shortRead = ConcurrentHashMap<Long, OkHttpClient>()

    fun clientFor(readSec: Long): OkHttpClient {
        if (readSec <= 0 || readSec == 60L) return client
        return shortRead.getOrPut(readSec) {
            client.newBuilder().readTimeout(readSec, TimeUnit.SECONDS).build()
        }
    }

    private fun request(url: String): Request = try {
        Request.Builder().url(url).header("User-Agent", UA).build()
    } catch (_: IllegalArgumentException) {
        throw IOException("Invalid URL")
    }

    suspend fun text(url: String, readSec: Long = 60): String = withContext(Dispatchers.IO) {
        try {
            clientFor(readSec).newCall(request(url)).execute().use { r ->
                if (!r.isSuccessful) throw IOException(httpMsg(r.code))
                r.body?.string() ?: ""
            }
        } catch (e: IOException) {
            if (isServerMessage(e)) throw e
            throw IOException(friendly(e))
        }
    }

    /** Streams the response body to [block] without loading it all in memory (for huge catalogs). */
    suspend fun <T> stream(url: String, readSec: Long = 60, block: (java.io.Reader) -> T): T = withContext(Dispatchers.IO) {
        try {
            clientFor(readSec).newCall(request(url)).execute().use { r ->
                if (!r.isSuccessful) throw IOException(httpMsg(r.code))
                val body = r.body ?: throw IOException("Invalid response from server")
                block(body.charStream().buffered(64 * 1024))
            }
        } catch (e: IOException) {
            if (isServerMessage(e) || e.message == "Invalid response from server") throw e
            throw IOException(friendly(e))
        }
    }

    fun isServerMessage(e: IOException): Boolean {
        val m = e.message ?: return false
        return m.startsWith("HTTP") || m.contains("denied") || m.contains("Not found") ||
            m.contains("Server error") || m.contains("Invalid URL") || m.contains("too large") ||
            m.contains("Invalid response")
    }

    /** True for failures the user can fix by checking the network/address (used for friendly retries). */
    fun isNetworkError(e: Throwable): Boolean = e is IOException

    /** Turns low-level socket failures into messages a viewer can understand. */
    fun friendly(e: IOException): String {
        val m = (e.message ?: "").lowercase()
        return when {
            e is java.net.UnknownHostException -> "Cannot find the server. Check the address"
            e is java.net.SocketTimeoutException || "timeout" in m || "timed out" in m ->
                "The server took too long to respond"
            e is java.net.ConnectException -> "Cannot connect to the server"
            e is javax.net.ssl.SSLException -> "The secure connection failed"
            m.isNotBlank() -> e.message ?: "Network error"
            else -> "Network error"
        }
    }

    fun httpMsg(code: Int): String = when {
        code == 401 || code == 403 -> "Access denied (HTTP $code)"
        code == 404 -> "Not found (HTTP $code)"
        code == 429 -> "Too many requests (HTTP 429)"
        code >= 500 -> "Server error (HTTP $code)"
        else -> "HTTP $code"
    }
}
