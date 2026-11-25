package com.floatx.gameturbo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lc = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    private val vms = ViewModelStore()
    override val lifecycle: Lifecycle get() = lc
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = vms

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var root: View? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private var allApps: List<AppItem>? = null
    private val favs = mutableStateOf<List<AppItem>>(emptyList())
    private val snap = mutableStateOf<Snap?>(null)
    private var hudView: View? = null
    private var hudJob: kotlinx.coroutines.Job? = null
    private var recording = false

    private fun owners(v: ComposeView) = v.apply {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
        setViewTreeViewModelStoreOwner(this@OverlayService)
    }

    private fun setFocusable(on: Boolean) {
        lp.flags = if (on) lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        root?.let { runCatching { wm.updateViewLayout(it, lp) } }
    }

    private class WinState(val id: Int, val pkg: String, val label: String, val rect: android.graphics.Rect, val normal: android.graphics.Rect) {
        var mode = 0
        var bar: View? = null
        var handle: View? = null
        var barLp: WindowManager.LayoutParams? = null
        var handleLp: WindowManager.LayoutParams? = null
    }
    private val wins = mutableListOf<WinState>()
    private val winList = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    private val dn get() = resources.displayMetrics.density
    private fun publish() { winList.value = wins.map { it.id to it.label } }

    private fun openWindow(pkg: String, label: String) = scope.launch {
        val dm = resources.displayMetrics
        val f = floatArrayOf(0.58f, 0.74f, 0.92f)[Prefs(this@OverlayService).winSize.coerceIn(0, 2)]
        val w = (dm.widthPixels * f).toInt()
        val h = (dm.heightPixels * f * 0.7f).toInt()
        val idx = wins.size % 4
        val l = (dm.widthPixels - w) / 2 + idx * 36
        val t = (dm.heightPixels * 0.14f).toInt() + idx * 64
        val r = android.graphics.Rect(l, t, l + w, t + h)
        val id = Freeform.launchBounds(this@OverlayService, pkg, r)
        if (id > 0) main.post { addControls(WinState(id, pkg, label, r, android.graphics.Rect(r))) }
        else toast("Jendela mengambang belum aktif. Tekan Aktifkan Freeform di tab Jendela, lalu restart HP.")
    }

    private fun addControls(ws: WinState) {
        val barH = (40 * dn).toInt(); val hs = (44 * dn).toInt()
        val fl = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val ty = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val blp = WindowManager.LayoutParams(ws.rect.width(), barH, ty, fl, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = ws.rect.left; y = ws.rect.top - barH }
        val hlp = WindowManager.LayoutParams(hs, hs, ty, fl, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = ws.rect.right - hs; y = ws.rect.bottom - hs }
        ws.barLp = blp; ws.handleLp = hlp
        val bar = owners(ComposeView(this)).apply {
            setContent {
                BarUI(ws.label, { setMode(ws, 1) }, { setMode(ws, 2) }, { closeWin(ws) },
                    { dx, dy -> ws.rect.offset(dx.toInt(), dy.toInt()); layoutControls(ws) }, { applyBounds(ws) })
            }
        }
        val handle = owners(ComposeView(this)).apply {
            setContent {
                HandleUI({ dx, dy ->
                    ws.rect.right = maxOf(ws.rect.left + (200 * dn).toInt(), ws.rect.right + dx.toInt())
                    ws.rect.bottom = maxOf(ws.rect.top + (240 * dn).toInt(), ws.rect.bottom + dy.toInt())
                    layoutControls(ws)
                }, { applyBounds(ws) })
            }
        }
        runCatching { wm.addView(bar, blp); wm.addView(handle, hlp) }
        ws.bar = bar; ws.handle = handle
        wins.add(ws); publish()
    }

    private fun layoutControls(ws: WinState) {
        val barH = (40 * dn).toInt(); val hs = (44 * dn).toInt()
        ws.barLp?.apply { width = ws.rect.width(); x = ws.rect.left; y = ws.rect.top - barH }
        ws.handleLp?.apply { x = ws.rect.right - hs; y = ws.rect.bottom - hs }
        runCatching { wm.updateViewLayout(ws.bar, ws.barLp); wm.updateViewLayout(ws.handle, ws.handleLp) }
    }

    private fun applyBounds(ws: WinState) {
        val r = android.graphics.Rect(ws.rect)
        scope.launch { Shell.run("am task resize ${ws.id} ${r.left},${r.top},${r.right},${r.bottom}") }
    }

    private fun setMode(ws: WinState, m: Int) {
        val dm = resources.displayMetrics
        val target = when {
            ws.mode == m -> ws.normal
            m == 2 -> android.graphics.Rect(0, (40 * dn).toInt(), dm.widthPixels, dm.heightPixels)
            else -> android.graphics.Rect(ws.normal.left, ws.normal.top, ws.normal.left + (dm.widthPixels * 0.45f).toInt(), ws.normal.top + (dm.heightPixels * 0.28f).toInt())
        }
        ws.mode = if (ws.mode == m) 0 else m
        ws.rect.set(target); layoutControls(ws); applyBounds(ws)
    }

    private fun closeWin(ws: WinState) {
        ws.bar?.let { runCatching { wm.removeView(it) } }
        ws.handle?.let { runCatching { wm.removeView(it) } }
        wins.remove(ws); publish()
        scope.launch { Shell.run("am force-stop ${ws.pkg}") }
    }

    private val extra = HashMap<String, View>()

    private fun toggleExtra(key: String, on: Boolean?, gravity: Int, x: Int = 0, y: Int = 0, content: @Composable () -> Unit) {
        val has = extra.containsKey(key)
        val want = on ?: !has
        extra.remove(key)?.let { runCatching { wm.removeView(it) } }
        if (want) {
            val l = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { this.gravity = gravity; this.x = x; this.y = y }
            val v = owners(ComposeView(this)).apply { setContent { content() } }
            runCatching { wm.addView(v, l); extra[key] = v }
        }
    }

    private fun showXh(on: Boolean?) {
        val p = Prefs(this)
        toggleExtra("xh", on, Gravity.CENTER) { CrosshairView(p.xhStyle, XhColors[p.xhColor % XhColors.size], p.xhSize) }
    }

    private fun showClock(on: Boolean?) = toggleExtra("clock", on, Gravity.TOP or Gravity.END, 16, 60) { GameClock() }

    private fun toggleHud(on: Boolean? = null) {
        val want = on ?: (hudView == null)
        if (want && hudView == null) {
            val hlp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 40 }
            val v = owners(ComposeView(this)).apply { setContent { Hud(snap.value) } }
            runCatching { wm.addView(v, hlp); hudView = v }
            hudJob = scope.launch {
                Stats.start()
                var last = System.currentTimeMillis()
                while (hudView != null) {
                    kotlinx.coroutines.delay(1000)
                    val now = System.currentTimeMillis()
                    snap.value = Stats.sample(this@OverlayService, (now - last) / 1000.0)
                    last = now
                }
            }
        } else if (!want) {
            hudView?.let { runCatching { wm.removeView(it) } }
            hudView = null; hudJob?.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ssc.performAttach()
        ssc.performRestore(null)
        lc.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gt", "Game Turbo", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "gt")
            .setContentTitle("Game Turbo aktif")
            .setContentText("Bubble jendela mengambang berjalan")
            .setSmallIcon(R.mipmap.ic_launcher).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        wm = getSystemService(WindowManager::class.java)
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 300 }

        val cv = owners(ComposeView(this)).apply {
            setContent {
                OverlayUI(
                    favs = favs.value,
                    winItems = winList.value,
                    onCloseWin = { id -> main.post { wins.firstOrNull { it.id == id }?.let { closeWin(it) } } },
                    onOpen = { refreshFavs() },
                    onDrag = { dx, dy -> lp.x += dx.toInt(); lp.y += dy.toInt(); wm.updateViewLayout(root, lp) },
                    onLaunch = { pkg, label -> openWindow(pkg, label) },
                    onTool = { t ->
                        when (t) {
                            "hud" -> main.post { toggleHud() }
                            "xh" -> main.post { showXh(null) }
                            "clk" -> main.post { showClock(null) }
                            else -> scope.launch { toast(runTool(t)) }
                        }
                    },
                    onFocus = { main.post { setFocusable(it) } },
                    onClose = { stopSelf() }
                )
            }
        }
        root = cv
        wm.addView(cv, lp)
        lc.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lc.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun refreshFavs() = scope.launch {
        val all = allApps ?: Apps.load(this@OverlayService).also { allApps = it }
        val f = Prefs(this@OverlayService).favs
        favs.value = all.filter { it.pkg in f }
    }

    private fun runTool(t: String): String = when (t) {
        "ram" -> Tools.cleanMemory().ifBlank { "Memori dibersihkan" }
        "dnd" -> Tools.dnd(true).ifBlank { "Jangan Ganggu aktif" }
        "rec" -> { recording = !recording; (if (recording) Tools.recStart() else Tools.recStop()).ifBlank { if (recording) "Merekam (maks 3 menit)" else "Rekaman disimpan di Movies" } }
        else -> Tools.screenshot().ifBlank { "Screenshot disimpan di Pictures" }
    }

    private fun toast(s: String) = main.post { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        when (i?.action) {
            "open" -> openWindow(i.getStringExtra("pkg") ?: "", i.getStringExtra("label") ?: "")
            "hud_on" -> toggleHud(true)
            "hud_off" -> toggleHud(false)
            "hud_toggle" -> toggleHud()
            "xh_on" -> showXh(true)
            "xh_off" -> showXh(false)
            "xh_toggle" -> showXh(null)
            "clock_toggle" -> showClock(null)
        }
        return START_STICKY
    }
    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        toggleHud(false)
        wins.toList().forEach { w -> w.bar?.let { runCatching { wm.removeView(it) } }; w.handle?.let { runCatching { wm.removeView(it) } } }
        extra.values.forEach { runCatching { wm.removeView(it) } }; extra.clear()
        root?.let { runCatching { wm.removeView(it) } }
        lc.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        vms.clear()
        scope.cancel()
        super.onDestroy()
    }
}

@Composable
private fun OverlayUI(
    favs: List<AppItem>,
    winItems: List<Pair<Int, String>>,
    onCloseWin: (Int) -> Unit,
    onOpen: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onLaunch: (String, String) -> Unit,
    onTool: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var mini by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { Prefs(ctx) }
    Column {
        Image(
            painterResource(R.drawable.logo), null,
            Modifier.size(54.dp).clip(RoundedCornerShape(15.dp))
                .pointerInput(Unit) { detectTapGestures { open = !open; if (open) onOpen() else { mini = null; onFocus(false) } } }
                .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); onDrag(d.x, d.y) } }
        )
        if (open) {
            Glass(Modifier.padding(top = 8.dp).width(300.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Aplikasi Favorit", color = C.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("Tutup", color = C.Red, fontSize = 12.sp, modifier = Modifier.clickable { onClose() })
                    }
                    if (favs.isEmpty()) {
                        Text("Belum ada favorit. Buka app Game Turbo → tab Aplikasi → tandai bintang.", color = C.Dim, fontSize = 12.sp)
                    }
                    favs.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { a ->
                                Column(
                                    Modifier.width(62.dp).clickable { onLaunch(a.pkg, a.label); open = false },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Image(a.icon, a.label, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                                    Text(a.label, color = C.Text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    listOf(
                        listOf("ram" to "Bersihkan RAM", "dnd" to "Jangan Ganggu", "shot" to "Screenshot"),
                        listOf("rec" to "Rekam Layar", "hud" to "Monitor", "xh" to "Crosshair"),
                        listOf("clk" to "Jam Gaming")
                    ).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (k, l) -> MiniBtn(l, Modifier.weight(1f)) { onTool(k) } }
                        }
                    }
                    Text("Jendela Mini", color = C.Dim, fontSize = 12.sp)
                    listOf(
                        listOf("notes" to "Catatan", "calc" to "Kalkulator", "clock" to "Jam"),
                        listOf("web" to "Browser", "music" to "Musik", "timer" to "Timer"),
                        listOf("video" to "Video")
                    ).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (k, l) ->
                                MiniBtn(l, Modifier.weight(1f).background(if (mini == k) Color(0x552B7BFF) else Color.Transparent)) {
                                    mini = if (mini == k) null else k
                                    onFocus(mini == "notes" || mini == "web")
                                }
                            }
                        }
                    }
                    when (mini) {
                        "notes" -> NotesMini(prefs)
                        "calc" -> CalcMini()
                        "clock" -> ClockMini()
                        "web" -> BrowserMini()
                        "music" -> MusicMini()
                        "timer" -> TimerMini()
                        "video" -> VideoMini(prefs)
                    }
                    if (winItems.isNotEmpty()) {
                        Text("Jendela aktif (${winItems.size})", color = C.Dim, fontSize = 12.sp)
                        winItems.forEach { (id, name) ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF)).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, color = C.Text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("Tutup", color = C.Red, fontSize = 12.sp, modifier = Modifier.clickable { onCloseWin(id) })
                            }
                        }
                    }
                }
            }
        }
    }
}
