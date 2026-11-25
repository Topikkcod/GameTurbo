package com.floatx.gameturbo

import android.app.Application
import android.content.Context
import android.os.Looper
import android.util.Log
import java.io.File

/** Menyimpan error ke file saat crash, dan mendeteksi app yang macet (layar pertama tidak tergambar). */
class GTApp : Application() {
    companion object {
        @Volatile var drawn = false

        fun startWatchdog(ctx: Context) {
            val dir = ctx.filesDir
            Thread {
                try { Thread.sleep(7000) } catch (_: Throwable) { }
                if (!drawn) {
                    try {
                        File(dir, "crash.txt").writeText(
                            "HANG: layar pertama tidak tergambar dalam 7 detik\nHP: ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}\n" +
                                "Posisi main thread:\n" + Looper.getMainLooper().thread.stackTrace.joinToString("\n") { "  at $it" }
                        )
                    } catch (_: Throwable) { }
                }
            }.apply { isDaemon = true }.start()
        }

        fun markDrawn(ctx: Context) {
            drawn = true
            val f = File(ctx.filesDir, "crash.txt")
            if (f.exists() && runCatching { f.readText().startsWith("HANG") }.getOrDefault(false)) f.delete()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(filesDir, "crash.txt").writeText(
                    "HP: ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}\nThread: ${t.name}\n" + Log.getStackTraceString(e)
                )
            } catch (_: Throwable) { }
            old?.uncaughtException(t, e)
        }
    }
}
