package com.floatx.gameturbo

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

// PUBLIC | OWN_CONTENT_ONLY | SUPPORTS_TOUCH | DESTROY_CONTENT_ON_REMOVAL
private const val VD_FLAGS = 1 or 8 or 64 or 256

class TabState(val id: Int, val web: WebView) {
    var title by mutableStateOf("Tab baru")
    var url by mutableStateOf("")
    var progress by mutableIntStateOf(0)
}

/** Browser mini dengan banyak tab (WebView), dipakai di atas game. */
class BrowserState(private val ctx: Context) {
    val tabs = mutableStateListOf<TabState>()
    var current by mutableIntStateOf(0)
    var desktop by mutableStateOf(false)
    private var seq = 0

    init { add("https://www.google.com") }

    @SuppressLint("SetJavaScriptEnabled")
    fun add(url: String) {
        if (tabs.size >= 6) return
        val web = WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }
        val t = TabState(seq++, web)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, u: String) { t.url = u; t.title = v.title ?: u }
            override fun doUpdateVisitedHistory(v: WebView, u: String, isReload: Boolean) { t.url = u }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) { t.progress = p }
            override fun onReceivedTitle(v: WebView, title: String?) { if (!title.isNullOrBlank()) t.title = title }
        }
        web.loadUrl(url)
        tabs.add(t); current = tabs.lastIndex
    }

    fun close(i: Int) {
        if (tabs.size <= 1 || i !in tabs.indices) return
        val t = tabs.removeAt(i); t.web.destroy()
        current = current.coerceAtMost(tabs.lastIndex)
    }

    fun applyDesktop() {
        tabs.forEach {
            it.web.settings.userAgentString = if (desktop) "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" else null
            it.web.settings.useWideViewPort = desktop
            it.web.settings.loadWithOverviewMode = desktop
            it.web.reload()
        }
    }

    fun destroy() { tabs.forEach { it.web.destroy() }; tabs.clear() }
}

/** Satu jendela mengambang: kind 0 = aplikasi di layar virtual, kind 1 = browser bertab. */
class FloatWin(val id: Int, val kind: Int, val pkg: String, title0: String, val icon: ImageBitmap?) {
    var view: View? = null
    var lp: WindowManager.LayoutParams? = null
    var title by mutableStateOf(title0)
    var minimized by mutableStateOf(false)
    var barVisible by mutableStateOf(true)
    var barTick by mutableIntStateOf(0)
    var snap by mutableIntStateOf(0)
    var typing by mutableStateOf(false)
    val normal = Rect()
    val saved = Rect()
    var vd: VirtualDisplay? = null
    var surface: Surface? = null
    var st: SurfaceTexture? = null
    var vdW = 0
    var vdH = 0
    var displayId = -1
    var downTime = 0L
    var browser: BrowserState? = null
}

class FloatEngine(
    private val ctx: Context,
    private val wm: WindowManager,
    private val scope: CoroutineScope,
    private val main: Handler,
    private val owners: (ComposeView) -> ComposeView,
    private val toast: (String) -> Unit
) {
    val wins = mutableListOf<FloatWin>()
    val items = mutableStateOf<List<Triple<Int, String, Boolean>>>(emptyList())
    private var seq = 0
    private var svc: IUserService? = null
    private val sender = Executors.newSingleThreadExecutor()
    private val dn get() = ctx.resources.displayMetrics.density

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName, b: IBinder) { svc = IUserService.Stub.asInterface(b) }
        override fun onServiceDisconnected(n: ComponentName) { svc = null }
    }

    private fun ensureSvc() {
        if (svc != null) return
        try {
            val ver = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
            val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, UserService::class.java.name))
                .daemon(false).processNameSuffix("input").debuggable(false).version(ver)
            Shizuku.bindUserService(args, conn)
        } catch (e: Throwable) { toast("Layanan input gagal: ${e.message}") }
    }

    private fun publish() { items.value = wins.map { Triple(it.id, it.title, it.minimized) } }

    /** Ukuran layar saat ini (ikut rotasi, jadi landscape juga benar). */
    fun screen(): Rect {
        val p = Point()
        ctx.getSystemService(DisplayManager::class.java).getDisplay(0).getRealSize(p)
        return Rect(0, 0, p.x, p.y)
    }

    private fun update(w: FloatWin) { w.view?.let { runCatching { wm.updateViewLayout(it, w.lp) } } }

    fun openApp(pkg: String, label: String) {
        if (wins.size >= 3) { toast("Maksimal 3 jendela"); return }
        if (!Shell.ready()) { toast("Shizuku belum aktif"); return }
        ensureSvc()
        val icon = try { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() } catch (e: Throwable) { null }
        addWindow(FloatWin(--seq, 0, pkg, label, icon))
    }

    fun openBrowser() {
        if (wins.size >= 3) { toast("Maksimal 3 jendela"); return }
        val w = FloatWin(--seq, 1, "", "Browser", null)
        w.browser = BrowserState(ctx)
        addWindow(w)
    }

    private fun addWindow(w: FloatWin) {
        val sc = screen()
        val land = sc.width() > sc.height()
        val ww = if (land) (sc.width() * 0.42f).toInt() else (sc.width() * 0.86f).toInt()
        val hh = if (land) (sc.height() * 0.88f).toInt() else (sc.height() * 0.55f).toInt()
        val off = (wins.size % 3) * (28 * dn).toInt()
        val fl = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        val l = WindowManager.LayoutParams(ww, hh, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, fl, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (sc.width() - ww) / 2 + off
            y = (sc.height() - hh) / 3 + off
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        w.lp = l
        val v = owners(ComposeView(ctx)).apply { setContent { FloatWinUI(w, this@FloatEngine) } }
        w.view = v
        try { wm.addView(v, l) } catch (e: Throwable) { toast("Gagal menampilkan jendela: ${e.message}"); return }
        wins.add(w); publish()
    }

    /** Dipanggil saat TextureView siap: buat layar virtual lalu jalankan app di dalamnya. */
    fun onSurface(w: FloatWin, st: SurfaceTexture, width: Int, height: Int) {
        if (w.vd != null) return
        val vw = width and 1.inv(); val vh = height and 1.inv()
        if (vw < 64 || vh < 64) return
        w.st = st; w.vdW = vw; w.vdH = vh
        st.setDefaultBufferSize(vw, vh)
        val surf = Surface(st); w.surface = surf
        try {
            val vd = ctx.getSystemService(DisplayManager::class.java)
                .createVirtualDisplay("GameTurbo-${-w.id}", vw, vh, ctx.resources.displayMetrics.densityDpi, surf, VD_FLAGS)
            w.vd = vd; w.displayId = vd.display.displayId
        } catch (e: Throwable) {
            toast("Layar virtual gagal: ${e.message}")
            main.post { close(w) }
            return
        }
        val comp = ctx.packageManager.getLaunchIntentForPackage(w.pkg)?.component?.flattenToShortString()
        if (comp == null) { toast("App tidak ditemukan"); main.post { close(w) }; return }
        scope.launch {
            delay(400)
            val out = Shell.run("am start -n $comp --display ${w.displayId}")
            if (out.contains("Error") || out.contains("Exception")) toast(out.take(160))
        }
    }

    fun touch(w: FloatWin, e: MotionEvent, vw: Int, vh: Int) {
        val s = svc ?: return
        if (w.displayId < 0 || w.vdW == 0) return
        val act = when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> 0
            MotionEvent.ACTION_UP -> 1
            MotionEvent.ACTION_MOVE -> 2
            MotionEvent.ACTION_CANCEL -> 3
            else -> return
        }
        val x = e.x * w.vdW / vw.coerceAtLeast(1)
        val y = e.y * w.vdH / vh.coerceAtLeast(1)
        if (act == 0) w.downTime = SystemClock.uptimeMillis()
        val dt = w.downTime; val et = SystemClock.uptimeMillis(); val d = w.displayId
        sender.execute { try { s.injectMotion(d, act, x, y, dt, et) } catch (_: Throwable) { } }
    }

    fun key(w: FloatWin, code: Int) {
        val s = svc ?: return
        val d = w.displayId; if (d < 0) return
        sender.execute { try { s.injectKey(d, code, 0); s.injectKey(d, code, 1) } catch (_: Throwable) { } }
    }

    fun type(w: FloatWin, text: String) {
        val s = svc ?: return
        val d = w.displayId; if (d < 0) return
        sender.execute { try { s.injectText(d, text) } catch (_: Throwable) { } }
    }

    fun setFocusable(w: FloatWin, on: Boolean) {
        val l = w.lp ?: return
        l.flags = if (on) l.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else l.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        update(w)
    }

    fun moveBy(w: FloatWin, dx: Int, dy: Int) {
        val l = w.lp ?: return
        val sc = screen()
        l.x = (l.x + dx).coerceIn(-(l.width - (80 * dn).toInt()), sc.width() - (80 * dn).toInt())
        l.y = (l.y + dy).coerceIn(0, sc.height() - (60 * dn).toInt())
        update(w)
    }

    fun resizeBy(w: FloatWin, dx: Int, dy: Int) {
        val l = w.lp ?: return
        val sc = screen()
        l.width = (l.width + dx).coerceIn((180 * dn).toInt(), sc.width())
        l.height = (l.height + dy).coerceIn((220 * dn).toInt(), sc.height())
        w.snap = 0
        update(w)
    }

    fun resizeEnd(w: FloatWin) = resizeVd(w)

    private fun resizeVd(w: FloatWin) {
        val vd = w.vd ?: return
        val l = w.lp ?: return
        val nw = l.width and 1.inv(); val nh = l.height and 1.inv()
        if (nw < 64 || nh < 64) return
        try {
            w.st?.setDefaultBufferSize(nw, nh)
            vd.resize(nw, nh, ctx.resources.displayMetrics.densityDpi)
            w.vdW = nw; w.vdH = nh
        } catch (_: Throwable) { }
    }

    fun snap(w: FloatWin, mode: Int) {
        val l = w.lp ?: return
        val sc = screen()
        if (w.snap == 0) w.normal.set(l.x, l.y, l.x + l.width, l.y + l.height)
        when (mode) {
            0 -> { l.x = w.normal.left; l.y = w.normal.top; l.width = w.normal.width(); l.height = w.normal.height() }
            1 -> { l.x = 0; l.y = 0; l.width = sc.width() / 2; l.height = sc.height() }
            2 -> { l.x = sc.width() / 2; l.y = 0; l.width = sc.width() / 2; l.height = sc.height() }
            else -> { l.x = 0; l.y = 0; l.width = sc.width(); l.height = sc.height() }
        }
        w.snap = mode
        update(w); resizeVd(w)
    }

    fun cycleSnap(w: FloatWin) = snap(w, (w.snap + 1) % 4)

    /** Dua jendela sekaligus: yang pertama di kiri, yang kedua di kanan. */
    fun split() {
        val v = wins.filter { !it.minimized }
        if (v.size < 2) { toast("Buka minimal 2 jendela dulu"); return }
        snap(v[0], 1); snap(v[1], 2)
    }

    fun minimize(w: FloatWin) {
        val l = w.lp ?: return
        w.saved.set(l.x, l.y, l.x + l.width, l.y + l.height)
        w.minimized = true
        val b = (56 * dn).toInt()
        l.width = b; l.height = b
        update(w); publish()
    }

    fun restore(w: FloatWin) {
        val l = w.lp ?: return
        val sc = screen()
        w.minimized = false
        l.width = w.saved.width(); l.height = w.saved.height()
        l.x = l.x.coerceIn(0, (sc.width() - l.width).coerceAtLeast(0))
        l.y = l.y.coerceIn(0, (sc.height() - l.height).coerceAtLeast(0))
        w.barTick++
        update(w); publish()
    }

    private fun releaseVd(w: FloatWin) {
        runCatching { w.vd?.release() }
        runCatching { w.surface?.release() }
        runCatching { w.st?.release() }
        w.vd = null; w.surface = null; w.st = null; w.displayId = -1
    }

    fun closeById(id: Int) { wins.firstOrNull { it.id == id }?.let { close(it) } }

    fun close(w: FloatWin) {
        w.view?.let { runCatching { wm.removeView(it) } }
        w.view = null
        wins.remove(w)
        w.browser?.destroy()
        if (w.kind == 0) {
            val kill = Prefs(ctx).killOnClose
            val pkg = w.pkg
            scope.launch {
                if (kill) Shell.run("am force-stop $pkg")
                main.post { releaseVd(w) }
            }
        }
        publish()
    }

    fun destroyAll() {
        wins.toList().forEach { w ->
            w.view?.let { runCatching { wm.removeView(it) } }
            w.browser?.destroy()
            releaseVd(w)
        }
        wins.clear(); publish()
        runCatching { svc?.destroy() }
        svc = null
    }
}
