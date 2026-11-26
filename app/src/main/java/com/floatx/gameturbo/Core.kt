package com.floatx.gameturbo

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import rikka.shizuku.Shizuku

val BuiltinThemes = listOf(
    Triple("Biru", Color(0xFF1E6BFF), Color(0xFF33C4FF)),
    Triple("Ungu", Color(0xFF7B4DFF), Color(0xFFC084FC)),
    Triple("Hijau", Color(0xFF10B981), Color(0xFF6EE7B7)),
    Triple("Merah", Color(0xFFEF4444), Color(0xFFFB923C))
)

object C {
    var extraThemes by androidx.compose.runtime.mutableStateOf<List<Triple<String, Color, Color>>>(emptyList())
    val all: List<Triple<String, Color, Color>> get() = BuiltinThemes + extraThemes
    var safe by androidx.compose.runtime.mutableStateOf(false)
    var themeIdx by mutableIntStateOf(0)
    var wallIdx by mutableIntStateOf(0)
    val Bg = Color(0xFF030814)
    val Blue: Color get() = all.getOrElse(themeIdx) { BuiltinThemes[0] }.second
    val Cyan: Color get() = all.getOrElse(themeIdx) { BuiltinThemes[0] }.third
    val Text = Color(0xFFEAF4FF)
    val Dim = Color(0xFF8DA6C9)
    val Green = Color(0xFF34D399)
    val Red = Color(0xFFFF5C6C)
}

/** Menjalankan perintah shell lewat Shizuku (hak akses shell). Panggil dari thread background. */
object Shell {
    fun ready(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun run(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        p.waitFor()
        (out + err).trim()
    } catch (e: Throwable) { "ERR: ${e.message}" }
}

object Freeform {
    /** Menyalakan dukungan freeform window di sistem (Android 16 mungkin perlu reboot sekali). */
    fun enable(): String = Shell.run(
        "settings put global enable_freeform_support 1; " +
        "settings put global force_resizable_activities 1; " +
        "settings put global freeform_window_management 1"
    )

    /** Buka app sebagai jendela mengambang (windowing mode 5 = freeform). */
    fun launch(ctx: Context, pkg: String): String {
        val comp = ctx.packageManager.getLaunchIntentForPackage(pkg)?.component ?: return "ERR: app tidak ditemukan"
        return Shell.run("am start -n ${comp.flattenToShortString()} --windowingMode 5 --display 0")
    }
}

/** Cara yang dipakai Taskbar/Mi-Freeform: mode windowing 5 (freeform) + bounds. Di sini lewat shell Shizuku. */
fun Freeform.launchBounds(ctx: Context, pkg: String, r: android.graphics.Rect): Int {
    val comp = ctx.packageManager.getLaunchIntentForPackage(pkg)?.component ?: return -1
    Shell.run("am start -n ${comp.flattenToShortString()} --windowingMode 5 --display 0")
    var id = -1
    repeat(8) {
        if (id < 0) {
            Thread.sleep(300)
            val lines = Shell.run("dumpsys activity activities | grep 'Task{'").lines().filter { it.contains(":$pkg") }
            val line = lines.firstOrNull { it.contains("freeform") } ?: lines.firstOrNull()
            id = line?.let { Regex("#(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: -1
        }
    }
    if (id > 0) Freeform.resize(id, r)
    return id
}

/** Android 14+ memakai format 4 argumen terpisah (LEFT TOP RIGHT BOTTOM); format lama pakai koma. Coba keduanya. */
fun Freeform.resize(id: Int, r: android.graphics.Rect): String {
    val a = Shell.run("am task resize $id ${r.left} ${r.top} ${r.right} ${r.bottom}")
    if (a.isBlank()) return ""
    val b = Shell.run("am task resize $id ${r.left},${r.top},${r.right},${r.bottom}")
    return if (b.isBlank()) "" else a
}

object Tools {
    fun cleanMemory() = Shell.run("am kill-all")
    fun dnd(on: Boolean) = Shell.run("cmd notification set_dnd " + if (on) "priority" else "off")
    fun batterySaver(on: Boolean) = Shell.run("settings put global low_power " + if (on) 1 else 0)
    fun screenshot() = Shell.run("screencap -p /sdcard/Pictures/GameTurbo_${System.currentTimeMillis()}.png")

    fun recStart() = Shell.run("nohup screenrecord --time-limit 180 /sdcard/Movies/GameTurbo_${System.currentTimeMillis()}.mp4 >/dev/null 2>&1 &")
    fun recStop() = Shell.run("pkill -2 screenrecord")
    fun performance(on: Boolean) = Shell.run("cmd power set-fixed-performance-mode-enabled " + if (on) "true" else "false")
    fun media(code: Int) = Shell.run("input keyevent $code")

    fun ram(ctx: Context): String {
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        val gb = 1024.0 * 1024 * 1024
        return "RAM %.1f GB / %.1f GB".format((mi.totalMem - mi.availMem) / gb, mi.totalMem / gb)
    }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("gt", 0)
    var favs: Set<String>
        get() = sp.getStringSet("favs", emptySet())!!.toSet()
        set(v) { sp.edit().putStringSet("favs", v).apply() }
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) { sp.edit().putInt("theme", v).apply() }
    var wall: Int
        get() = sp.getInt("wall", 0)
        set(v) { sp.edit().putInt("wall", v).apply() }
    var video: String
        get() = sp.getString("video", "")!!
        set(v) { sp.edit().putString("video", v).apply() }
    var xhStyle: Int
        get() = sp.getInt("xhs", 1)
        set(v) { sp.edit().putInt("xhs", v).apply() }
    var xhColor: Int
        get() = sp.getInt("xhc", 0)
        set(v) { sp.edit().putInt("xhc", v).apply() }
    var xhSize: Int
        get() = sp.getInt("xhz", 48)
        set(v) { sp.edit().putInt("xhz", v).apply() }
    var winSize: Int
        get() = sp.getInt("wsz", 1)
        set(v) { sp.edit().putInt("wsz", v).apply() }
    var introSound: String
        get() = sp.getString("isnd", "")!!
        set(v) { sp.edit().putString("isnd", v).apply() }
    var toolSound: String
        get() = sp.getString("tsnd", "")!!
        set(v) { sp.edit().putString("tsnd", v).apply() }
    var safe: Boolean
        get() = sp.getBoolean("safe", false)
        set(v) { sp.edit().putBoolean("safe", v).apply() }
    var engine: Int
        get() = sp.getInt("eng", 0)
        set(v) { sp.edit().putInt("eng", v).apply() }
    var killOnClose: Boolean
        get() = sp.getBoolean("kill", true)
        set(v) { sp.edit().putBoolean("kill", v).apply() }
    var note: String
        get() = sp.getString("note", "")!!
        set(v) { sp.edit().putString("note", v).apply() }
}

class AppItem(val pkg: String, val label: String, val icon: ImageBitmap, val game: Boolean = false)

object Apps {
    fun load(ctx: Context): List<AppItem> = try { loadInner(ctx) } catch (e: Throwable) { emptyList() }

    private fun loadInner(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map {
                val ai = it.activityInfo.applicationInfo
                @Suppress("DEPRECATION")
                val game = (ai.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0 ||
                    ai.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
                AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm).toBitmap(96, 96).asImageBitmap(), game)
            }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }
}

/** Memutar file suara dari paket data. */
object SoundFx {
    fun play(f: java.io.File): Boolean {
        if (!f.exists()) return false
        return try {
            val mp = android.media.MediaPlayer()
            mp.setDataSource(f.path)
            mp.setOnCompletionListener { it.release() }
            mp.setOnErrorListener { m, _, _ -> m.release(); true }
            mp.prepare(); mp.start(); true
        } catch (e: Throwable) { false }
    }
    fun file(ctx: Context, name: String) = java.io.File(java.io.File(DataPack.dir(ctx), "sounds"), name)
    fun playTool(ctx: Context) { val n = Prefs(ctx).toolSound; if (n.isNotEmpty()) play(file(ctx, n)) }
    fun label(f: java.io.File) = f.nameWithoutExtension.substringAfter('_').replace('-', ' ').replaceFirstChar { it.uppercase() }
}
