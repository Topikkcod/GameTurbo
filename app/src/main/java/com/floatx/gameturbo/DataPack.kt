package com.floatx.gameturbo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Paket data (wallpaper, dll) diunduh di HP pengguna saat pertama kali app dibuka, bukan dibundel di APK. */
object DataPack {
    fun dir(ctx: Context) = File(ctx.filesDir, "packs")
    const val VERSION = "2"
    fun ready(ctx: Context) = runCatching { File(dir(ctx), "ready").readText().trim() == VERSION }.getOrDefault(false)

    fun sounds(ctx: Context): List<File> =
        File(dir(ctx), "sounds").listFiles { f -> f.name.endsWith(".wav") }?.sortedBy { it.name }.orEmpty()

    fun themes(ctx: Context): List<Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>> = try {
        val arr = org.json.JSONArray(File(dir(ctx), "themes/themes.json").readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Triple(
                o.getString("name"),
                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(o.getString("a"))),
                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(o.getString("b")))
            )
        }
    } catch (e: Throwable) { emptyList() }

    fun walls(ctx: Context): List<File> =
        File(dir(ctx), "wallpapers").listFiles { f -> f.name.startsWith("w") && f.name.endsWith(".jpg") }
            ?.sortedBy { it.name }.orEmpty()

    fun thumb(w: File) = File(w.parentFile, "t" + w.name.substring(1))

    /** Mengembalikan null kalau sukses, atau pesan error. */
    suspend fun download(ctx: Context, onProgress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            val url = BuildConfig.DATA_URL
            if (url.isEmpty()) return@withContext "Alamat paket data belum diatur."
            val tmp = File(ctx.cacheDir, "pack.zip")
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = true
            if (c.responseCode !in 200..299) return@withContext "Server menjawab ${c.responseCode}. Pastikan release 'data' sudah ada di GitHub."
            val total = c.contentLengthLong
            c.inputStream.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) {
                        val n = ins.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (total > 0) onProgress(done.toFloat() / total * 0.9f)
                    }
                }
            }
            val dest = dir(ctx); dest.deleteRecursively(); dest.mkdirs()
            ZipInputStream(tmp.inputStream().buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val f = File(dest, e.name)
                    if (!f.canonicalPath.startsWith(dest.canonicalPath)) throw SecurityException("zip tidak valid")
                    if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                    e = z.nextEntry
                }
            }
            tmp.delete()
            File(dest, "ready").writeText(VERSION)
            onProgress(1f)
            null
        } catch (e: Throwable) { "Gagal: ${e.message}" }
    }
}
