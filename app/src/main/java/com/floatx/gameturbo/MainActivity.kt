package com.floatx.gameturbo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pf = Prefs(this)
        val boot = java.io.File(filesDir, "boot")
        if (boot.exists()) pf.safe = true   // start sebelumnya tidak selesai -> mode aman
        C.safe = pf.safe
        val crashFile = java.io.File(filesDir, "crash.txt")
        if (crashFile.exists()) {
            val txt = runCatching { crashFile.readText() }.getOrDefault("(tidak bisa membaca log)")
            setContent { CrashScreen(txt) { crashFile.delete(); recreate() } }
            return
        }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        boot.writeText("1")
        GTApp.startWatchdog(this)
        setContent { App { boot.delete() } }
    }
}

@Composable
fun App(onStable: () -> Unit = {}) {
    val ctx = LocalContext.current
    val haze = remember { HazeState() }
    var tab by remember { mutableIntStateOf(0) }
    var intro by remember { mutableStateOf(!C.safe) }
    var gate by remember { mutableStateOf(!C.safe && !DataPack.ready(ctx) && BuildConfig.DATA_URL.isNotEmpty()) }
    remember { val pf = Prefs(ctx); C.extraThemes = DataPack.themes(ctx); C.themeIdx = pf.theme; C.wallIdx = pf.wall; 0 }
    var shizuku by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(false) }
    var ram by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { GTApp.markDrawn(ctx); delay(4000); onStable() }
    LaunchedEffect(Unit) {
        while (true) {
            shizuku = Shell.ready()
            overlay = Settings.canDrawOverlays(ctx)
            ram = Tools.ram(ctx)
            delay(1500)
        }
    }
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Box(Modifier.fillMaxSize().let { if (C.safe) it else it.hazeSource(haze) }) { Backdrop() }
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Game Turbo", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Game Booster & Floating Window", color = C.Dim, fontSize = 12.sp)
                }
                Glass(corner = 14.dp) {
                    Text(
                        if (shizuku) "Shizuku aktif" else "Shizuku mati",
                        color = if (shizuku) C.Green else C.Red, fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp, 6.dp)
                    )
                }
            }
            if (C.safe) SafeBanner()
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> TurboScreen(haze, shizuku, ram)
                    1 -> AppsScreen(shizuku)
                    2 -> WindowScreen(haze, shizuku, overlay)
                    3 -> ToolsScreen(haze, shizuku)
                    else -> SettingsScreen(haze, shizuku)
                }
            }
            Glass(Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 12.dp), haze, 26.dp) {
                Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    listOf(
                        Icons.Rounded.SportsEsports to "Turbo", Icons.Rounded.Apps to "Aplikasi",
                        Icons.Rounded.PictureInPictureAlt to "Jendela", Icons.Rounded.Build to "Tools",
                        Icons.Rounded.Settings to "Setelan"
                    ).forEachIndexed { i, (ic, l) -> NavItem(ic, l, tab == i) { tab = i } }
                }
            }
        }
        if (intro) IntroScreen { intro = false }
        else if (gate) DataScreen { gate = false; C.extraThemes = DataPack.themes(ctx); C.themeIdx = Prefs(ctx).theme }
    }
}

@Composable
fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0x552B7BFF) else Color.Transparent)
            .clickable { onClick() }.padding(12.dp, 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (selected) C.Cyan else C.Dim, modifier = Modifier.size(24.dp))
        Text(label, color = if (selected) C.Text else C.Dim, fontSize = 11.sp)
    }
}

@Composable
fun Backdrop() {
    val ctx = LocalContext.current
    val wall by produceState<ImageBitmap?>(null, C.wallIdx) {
        value = if (C.safe) null else withContext(Dispatchers.IO) {
            DataPack.walls(ctx).getOrNull(C.wallIdx - 1)?.let { f ->
                runCatching {
                    android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        val w = wall
        if (C.wallIdx > 0 && w != null) {
            Image(w, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0x77020510)))
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF040B22), Color(0xFF020510))))
                val r = size.width * 0.8f
                val a = Offset(size.width * 0.15f, size.height * 0.2f)
                val b = Offset(size.width * 0.9f, size.height * 0.75f)
                drawCircle(Brush.radialGradient(listOf(C.Blue.copy(alpha = 0.67f), Color.Transparent), a, r), r, a)
                drawCircle(Brush.radialGradient(listOf(C.Cyan.copy(alpha = 0.53f), Color.Transparent), b, r), r, b)
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = C.Text, modifier = Modifier.weight(1f))
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = C.Blue, checkedThumbColor = Color.White)
        )
    }
}

@Composable
fun TurboScreen(haze: HazeState, shizuku: Boolean, ram: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableIntStateOf(1) }
    var dnd by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var games by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    LaunchedEffect(Unit) { games = withContext(Dispatchers.Default) { Apps.load(ctx).filter { it.game } } }
    fun setMode(m: Int) {
        mode = m
        if (!shizuku) { msg = "Aktifkan Shizuku dulu (tab Jendela)"; return }
        scope.launch(Dispatchers.IO) {
            Tools.batterySaver(m == 0); Tools.performance(m == 2)
            msg = listOf("Mode Hemat aktif", "Mode Seimbang aktif", "Mode Performa aktif")[m]
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Glass(Modifier.fillMaxWidth(), haze) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(150.dp).clip(CircleShape).clickable {
                        if (!shizuku) msg = "Aktifkan Shizuku dulu (tab Jendela)"
                        else { msg = "Mengoptimalkan..."; scope.launch(Dispatchers.IO) { Tools.cleanMemory(); msg = "Performa dioptimalkan. Memori latar dibersihkan."; SoundFx.playTool(ctx) } }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                        drawArc(Brush.sweepGradient(listOf(C.Cyan, C.Blue, C.Cyan)), -90f, 300f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.SportsEsports, null, tint = C.Cyan, modifier = Modifier.size(42.dp))
                        Text("Optimalkan", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(ram, color = C.Dim, fontSize = 13.sp)
                if (msg.isNotEmpty()) Text(msg, color = C.Cyan, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Hemat", "Seimbang", "Performa").forEachIndexed { i, n ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (mode == i) C.Blue else Color(0x22FFFFFF)).clickable { setMode(i) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(n, color = C.Text, fontSize = 13.sp) }
            }
        }
        Text("Game terdeteksi (${games.size})", color = C.Dim, fontSize = 12.sp)
        if (games.isEmpty()) Text("Belum ada game terdeteksi. Semua app tetap bisa dibuka di tab Aplikasi.", color = C.Dim, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            games.forEach { g ->
                Column(
                    Modifier.width(64.dp).clickable { ctx.packageManager.getLaunchIntentForPackage(g.pkg)?.let { ctx.startActivity(it) } },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(g.icon, g.label, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                    Text(g.label, color = C.Text, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        Glass(Modifier.fillMaxWidth(), haze) {
            ToggleRow("Blokir Notifikasi (Jangan Ganggu)", dnd) {
                dnd = it; scope.launch(Dispatchers.IO) { Tools.dnd(it) }
            }
        }
        BigButton("Mulai bermain") {
            if (!shizuku) msg = "Aktifkan Shizuku dulu (tab Jendela)"
            else {
                mode = 2; dnd = true; msg = "Mode bermain aktif: memori dibersihkan, DND, performa, monitor."
                scope.launch(Dispatchers.IO) { Tools.cleanMemory(); Tools.dnd(true); Tools.performance(true); SoundFx.playTool(ctx) }
                ctx.startForegroundService(Intent(ctx, OverlayService::class.java).setAction("hud_on"))
            }
        }
    }
}

@Composable
fun AppsScreen(shizuku: Boolean) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var favs by remember { mutableStateOf(prefs.favs) }
    var status by remember { mutableStateOf("") }
    var onlyGames by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { Apps.load(ctx) }; onlyGames = apps.any { it.game } }
    val shown = if (onlyGames) apps.filter { it.game } else apps
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Game terdeteksi (${apps.count { it.game }})", onlyGames) { onlyGames = true }
            Chip("Semua app", !onlyGames) { onlyGames = false }
        }
        Text(
            if (status.isNotEmpty()) status else "Tandai bintang untuk masuk ke bubble. Tombol Buka = jendela mengambang.",
            color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp)
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.pkg }) { a ->
                Glass(Modifier.fillMaxWidth(), corner = 18.dp) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(a.icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(a.label, color = C.Text, modifier = Modifier.weight(1f), maxLines = 1)
                        val fav = a.pkg in favs
                        Icon(
                            if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorit",
                            tint = if (fav) Color(0xFFFFC857) else C.Dim,
                            modifier = Modifier.size(28.dp).clickable {
                                favs = if (fav) favs - a.pkg else favs + a.pkg
                                prefs.favs = favs
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(C.Blue)
                                .clickable {
                                    if (!shizuku) status = "Aktifkan Shizuku dulu (tab Jendela)"
                                    else if (Settings.canDrawOverlays(ctx)) {
                                        ctx.startForegroundService(Intent(ctx, OverlayService::class.java).setAction("open").putExtra("pkg", a.pkg).putExtra("label", a.label))
                                        status = "Membuka ${a.label} sebagai jendela mengambang..."
                                    } else scope.launch(Dispatchers.IO) { status = Freeform.launch(ctx, a.pkg).take(120) }
                                }.padding(14.dp, 8.dp)
                        ) { Text("Buka", color = Color.White, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun StepRow(ok: Boolean, title: String, desc: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.Check else Icons.Rounded.Close, null,
            tint = if (ok) C.Green else C.Red, modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontWeight = FontWeight.SemiBold)
            Text(desc, color = C.Dim, fontSize = 12.sp)
        }
        if (action != null && !ok) {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Blue).clickable { onClick() }.padding(12.dp, 8.dp)) {
                Text(action, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun WindowScreen(haze: HazeState, shizuku: Boolean, overlay: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Glass(Modifier.fillMaxWidth(), haze) {
            Column {
                StepRow(
                    shizuku, "1. Shizuku",
                    "Buka app Shizuku → Start via Wireless Debugging, lalu izinkan Game Turbo.", "Izinkan"
                ) { runCatching { Shizuku.requestPermission(100) }.onFailure { msg = "Shizuku belum berjalan. Start dulu di app Shizuku." } }
                StepRow(overlay, "2. Tampil di atas app lain", "Dibutuhkan untuk bubble & panel mengambang.", "Buka") {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        Glass(Modifier.fillMaxWidth(), haze) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("3. Nyalakan fitur jendela mengambang", color = C.Text, fontWeight = FontWeight.SemiBold)
                Text("Mengaktifkan freeform window di sistem. Di Android 16 kadang perlu restart HP sekali.", color = C.Dim, fontSize = 12.sp)
                BigButton("Aktifkan Freeform") {
                    if (!shizuku) msg = "Aktifkan Shizuku dulu"
                    else scope.launch(Dispatchers.IO) { Freeform.enable(); msg = "Freeform diaktifkan. Coba buka app dari tab Aplikasi." }
                }
                BigButton("Mulai Bubble Mengambang") {
                    if (!overlay) msg = "Izin tampil di atas app lain belum aktif"
                    else { ctx.startForegroundService(Intent(ctx, OverlayService::class.java)); msg = "Bubble F muncul. Tap untuk buka panel." }
                }
                BigButton("Hentikan Bubble") { ctx.stopService(Intent(ctx, OverlayService::class.java)); msg = "Bubble dihentikan." }
                if (msg.isNotEmpty()) Text(msg, color = C.Cyan, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(C.Blue, C.Cyan)))
            .clickable { onClick() }.padding(14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun ToolsScreen(haze: HazeState, shizuku: Boolean) {
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    var rec by remember { mutableStateOf(false) }
    val tools = listOf(
        Triple(Icons.Rounded.Videocam, "Perekam Layar") { rec = !rec; (if (rec) Tools.recStart() else Tools.recStop()).ifBlank { if (rec) "Merekam (maks 3 menit)" else "Rekaman disimpan di Movies" } },
        Triple(Icons.Rounded.Bolt, "Crosshair") {
            ctx.startForegroundService(Intent(ctx, OverlayService::class.java).setAction("xh_toggle")); "Crosshair diubah"
        },
        Triple(Icons.Rounded.Bedtime, "Jam Gaming") {
            ctx.startForegroundService(Intent(ctx, OverlayService::class.java).setAction("clock_toggle")); "Jam diubah"
        },
        Triple(Icons.Rounded.Speed, "Monitor FPS/CPU/GPU") {
            ctx.startForegroundService(Intent(ctx, OverlayService::class.java).setAction("hud_toggle")); "Monitor diubah"
        },
        Triple(Icons.Rounded.Memory, "Pembersih Memori") { Tools.cleanMemory() },
        Triple(Icons.Rounded.CropFree, "Screenshot") { Tools.screenshot() },
        Triple(Icons.Rounded.Bedtime, "Mode Jangan Ganggu") { Tools.dnd(true) },
        Triple(Icons.Rounded.BatterySaver, "Penghemat Baterai") { Tools.batterySaver(true) },
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tools.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (ic, name, act) ->
                    Glass(Modifier.weight(1f).clickable {
                        if (!shizuku) msg = "Aktifkan Shizuku dulu"
                        else scope.launch(Dispatchers.IO) { msg = act().ifBlank { "$name: selesai" } }
                    }, haze) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(ic, null, tint = C.Cyan, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(name, color = C.Text, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        if (msg.isNotEmpty()) Text(msg, color = C.Cyan, fontSize = 13.sp)
    }
}
