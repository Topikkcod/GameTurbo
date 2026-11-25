package com.floatx.gameturbo

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class Snap(val fps: Int?, val cpu: Int?, val gpu: String, val temp: Float?, val ram: String, val ping: Int?)

/**
 * Pembaca statistik lewat Shizuku (cara yang dipakai app seperti FPS Meter / TakoStats):
 * - FPS : dumpsys SurfaceFlinger --timestats (frame per layer app yang sedang di depan)
 * - CPU : selisih /proc/stat
 * - GPU : frekuensi dari /sys/class/devfreq atau /sys/class/kgsl (beban GPU diblokir di banyak chipset Mali/Unisoc)
 * - Suhu: baterai (BatteryManager)
 */
object Stats {
    private var pTotal = 0L
    private var pIdle = 0L
    private var fgPkg = ""
    private var fgAt = 0L
    private val system = Regex("StatusBar|NavigationBar|Wallpaper|ScreenDecor|Bubble|Game Turbo|gameturbo|InputMethod|Dim|pip-dismiss|SurfaceView\\[com\\.android\\.systemui")

    fun start() { Shell.run("dumpsys SurfaceFlinger --timestats -enable -clear") }

    private fun cpu(): Int? {
        val v = Regex("\\d+").findAll(Shell.run("head -1 /proc/stat")).map { it.value.toLong() }.toList()
        if (v.size < 8) return null
        val total = v.take(8).sum()
        val idle = v[3] + v[4]
        val dT = total - pTotal; val dI = idle - pIdle
        val first = pTotal == 0L
        pTotal = total; pIdle = idle
        return if (first || dT <= 0) null else (100 * (dT - dI) / dT).toInt()
    }

    private fun gpu(): String {
        val raw = Shell.run("cat /sys/class/devfreq/*gpu*/cur_freq /sys/class/devfreq/*mali*/cur_freq /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null | head -1")
        val hz = raw.trim().toLongOrNull() ?: return "--"
        val mhz = if (hz > 100000) hz / 1_000_000 else hz
        return "$mhz MHz"
    }

    private fun fps(dtSec: Double): Int? {
        val now = System.currentTimeMillis()
        if (now - fgAt > 3000) {
            fgPkg = Regex("([a-zA-Z0-9_.]+)/").find(Shell.run("dumpsys activity activities | grep -m1 topResumedActivity"))
                ?.groupValues?.get(1) ?: ""
            fgAt = now
        }
        val out = Shell.run("dumpsys SurfaceFlinger --timestats -dump -clear")
        val layers = Regex("layerName\\s*=\\s*(.+)\\s+totalFrames\\s*=\\s*(\\d+)").findAll(out)
            .map { it.groupValues[1] to it.groupValues[2].toInt() }.toList()
        if (layers.isEmpty()) return null
        val match = layers.filter { fgPkg.isNotEmpty() && it.first.contains(fgPkg) }.maxOfOrNull { it.second }
            ?: layers.filter { !system.containsMatchIn(it.first) }.maxOfOrNull { it.second }
            ?: return null
        return Math.round(match / dtSec).toInt().coerceIn(0, 240)
    }

    private var pingAt = 0L
    private var lastPing: Int? = null
    private fun ping(): Int? {
        val n = System.currentTimeMillis()
        if (n - pingAt < 3000) return lastPing
        pingAt = n
        lastPing = try {
            val t = System.nanoTime()
            java.net.Socket().use { it.connect(java.net.InetSocketAddress("8.8.8.8", 53), 1000) }
            ((System.nanoTime() - t) / 1_000_000).toInt()
        } catch (e: Throwable) { null }
        return lastPing
    }

    fun sample(ctx: Context, dtSec: Double): Snap {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val t = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.takeIf { it > 0 }?.let { it / 10f }
        return Snap(fps(dtSec), cpu(), gpu(), t, Tools.ram(ctx).removePrefix("RAM "), ping())
    }
}
