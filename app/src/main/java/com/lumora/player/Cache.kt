package com.lumora.player

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Keeps the downloaded channel / movie / series lists on the phone so the app opens instantly,
 * even without a connection. Files are tab separated, versioned, and written through a temp file
 * so a crash mid-write can never leave a half list behind.
 */
object CatalogCache {
    private const val HEADER = "#lumora-catalog-v2"
    private var dir: File? = null

    fun init(c: Context) {
        if (dir == null) dir = File(c.filesDir, "catalog").apply { mkdirs() }
    }

    fun ready(): Boolean = dir != null

    private fun safe(v: String) = v.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)

    private fun hash(v: String): String = try {
        MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).take(4)
            .joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        Integer.toHexString(v.hashCode())
    }

    /** Stable file prefix for one source, so old credential hashes can be removed together. */
    private fun prefix(src: String) = safe(src) + "_" + hash(src) + "_"

    private fun file(src: String, kind: String): File? {
        val d = dir ?: return null
        return File(d, prefix(src) + kind + ".tsv")
    }

    fun time(src: String, kind: String): Long {
        val f = file(src, kind) ?: return 0L
        return if (f.exists()) f.lastModified() else 0L
    }

    private fun clean(v: String) = v.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    suspend fun write(src: String, kind: String, list: List<Item>) = withContext(Dispatchers.IO) {
        val f = file(src, kind) ?: return@withContext
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.bufferedWriter().use { w ->
                w.write(HEADER); w.newLine()
                for (it in list) {
                    w.write(
                        listOf(
                            clean(it.id), clean(it.name), clean(it.group), clean(it.logo), it.kind, clean(it.ext),
                            if (it.url == it.id) "@" else clean(it.url), it.srcId, it.season.toString(), it.num.toString(),
                            it.added.toString(), it.rating.toString().replace(',', '.')
                        ).joinToString("\t")
                    )
                    w.newLine()
                }
            }
            if (!tmp.renameTo(f)) {
                tmp.copyTo(f, overwrite = true)
                tmp.delete()
            }
        } catch (_: Exception) {
            // A cache write must never break playback; the network list is still in memory.
        }
    }

    suspend fun read(src: String, kind: String): List<Item>? = withContext(Dispatchers.IO) {
        val f = file(src, kind) ?: return@withContext null
        if (!f.exists() || f.length() == 0L) return@withContext null
        try {
            val out = ArrayList<Item>()
            f.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isBlank() || line.startsWith("#")) continue
                    val p = line.split('\t')
                    if (p.size < 12) continue
                    val id = p[0]
                    out.add(
                        Item(
                            id, p[1], p[2], p[3], p[4], p[5], if (p[6] == "@") id else p[6], p[7],
                            p[8].toIntOrNull() ?: 0, p[9].toIntOrNull() ?: 0,
                            p[10].toLongOrNull() ?: 0L, p[11].replace(',', '.').toDoubleOrNull() ?: 0.0
                        )
                    )
                }
            }
            // An empty but valid file means "this account has no titles of that kind"; it must not
            // force a network refresh on every visit.
            out
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        dir?.listFiles()?.forEach { runCatching { it.delete() } }
    }

    /** Removes every cached list that belongs to one source (all kinds and old credential hashes). */
    fun clearSource(srcId: String) {
        val p = prefix(srcId)
        dir?.listFiles()?.forEach { if (it.name.startsWith(p)) runCatching { it.delete() } }
    }
}
