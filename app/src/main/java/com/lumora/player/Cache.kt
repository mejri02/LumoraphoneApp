package com.lumora.player

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Keeps the downloaded channel / movie / series lists on the phone so the app opens instantly (even offline). */
object CatalogCache {
    private var dir: File? = null

    fun init(c: Context) {
        if (dir == null) dir = File(c.filesDir, "catalog").apply { mkdirs() }
    }

    private fun safe(v: String) = v.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun file(src: String, kind: String): File? {
        val d = dir ?: return null
        return File(d, safe(src) + "_" + kind + ".tsv")
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
                val sb = StringBuilder()
                for (it in list) {
                    sb.setLength(0)
                    sb.append(clean(it.id)).append('\t').append(clean(it.name)).append('\t').append(clean(it.group)).append('\t')
                        .append(clean(it.logo)).append('\t').append(it.kind).append('\t').append(clean(it.ext)).append('\t')
                        .append(if (it.url == it.id) "@" else clean(it.url)).append('\t').append(it.srcId).append('\t')
                        .append(it.season).append('\t').append(it.num).append('\t').append(it.added).append('\t').append(it.rating.toString().replace(',', '.'))
                    sb.append('\n')
                    w.write(sb.toString())
                }
            }
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        } catch (_: Exception) {
        }
    }

    suspend fun read(src: String, kind: String): List<Item>? = withContext(Dispatchers.IO) {
        val f = file(src, kind) ?: return@withContext null
        if (!f.exists()) return@withContext null
        try {
            val out = ArrayList<Item>()
            f.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val p = line.split('\t')
                    if (p.size < 12) continue
                    val id = p[0]
                    out.add(
                        Item(
                            id, p[1], p[2], p[3], p[4], p[5], if (p[6] == "@") id else p[6], p[7],
                            p[8].toIntOrNull() ?: 0, p[9].toIntOrNull() ?: 0, p[10].toLongOrNull() ?: 0L, p[11].replace(',', '.').toDoubleOrNull() ?: 0.0
                        )
                    )
                }
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        dir?.listFiles()?.forEach { it.delete() }
    }

    /** Removes every cached list that belongs to one source (all kinds and old credential hashes). */
    fun clearSource(srcId: String) {
        val prefix = safe(srcId) + "_"
        dir?.listFiles()?.forEach { if (it.name.startsWith(prefix)) it.delete() }
    }
}
