package com.floatx.gameturbo

import android.app.WallpaperManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

val XhColors = listOf(Color(0xFFFF3B3B), Color(0xFF39FF88), Color(0xFF33C4FF), Color.White, Color(0xFFFFE04A))
val XhStyles = listOf("Titik", "Plus", "Lingkaran", "Siku")

@Composable
fun CrosshairView(style: Int, color: Color, sizeDp: Int) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val c = center
        val r = size.minDimension / 2
        val w = 2.dp.toPx()
        when (style) {
            0 -> drawCircle(color, w * 1.8f, c)
            1 -> {
                drawLine(color, Offset(c.x - r, c.y), Offset(c.x - r * 0.25f, c.y), w)
                drawLine(color, Offset(c.x + r * 0.25f, c.y), Offset(c.x + r, c.y), w)
                drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y - r * 0.25f), w)
                drawLine(color, Offset(c.x, c.y + r * 0.25f), Offset(c.x, c.y + r), w)
            }
            2 -> { drawCircle(color, r * 0.8f, c, style = Stroke(w)); drawCircle(color, w * 1.5f, c) }
            else -> {
                val l = r * 0.45f
                listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
                    val p = Offset(c.x + sx * r * 0.7f, c.y + sy * r * 0.7f)
                    drawLine(color, p, Offset(p.x - sx * l, p.y), w)
                    drawLine(color, p, Offset(p.x, p.y - sy * l), w)
                }
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) C.Blue.copy(alpha = 0.55f) else Color(0x22FFFFFF))
            .clickable { onClick() }.padding(12.dp, 7.dp)
    ) { Text(label, color = C.Text, fontSize = 12.sp) }
}

private class Shard(val c: Int, val r: Int, val sx: Float, val sy: Float, val rot: Float, val dl: Float)
private class Spark(val a: Float, val s: Float, val z: Float)

private const val IDUR = 6500
private const val IMPACT = 0.5f

/** Suara intro dibuat lewat kode (tanpa file audio): desing naik, dentuman bass, lonceng. Plus getar. */
object IntroFx {
    private const val SR = 22050
    private fun buf(sec: Float, gen: (Double) -> Double) =
        ShortArray((sec * SR).toInt()) { i -> (gen(i.toDouble() / SR).coerceIn(-1.0, 1.0) * 30000).toInt().toShort() }

    private fun play(d: ShortArray) {
        try {
            val at = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(d.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            at.write(d, 0, d.size); at.play()
        } catch (e: Throwable) { }
    }

    fun whoosh() {
        val tt = 2.7; val kk = 15.0
        play(buf(tt.toFloat()) { t ->
            val ph = 2 * Math.PI * 70 * tt / Math.log(kk) * (Math.pow(kk, t / tt) - 1)
            Math.sin(ph) * Math.pow(t / tt, 2.0) * 0.35
        })
    }

    fun impact(ctx: Context) {
        try { ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 30, 140), -1)) } catch (e: Throwable) { }
        val pf = Prefs(ctx).introSound
        if (pf.isNotEmpty() && SoundFx.play(SoundFx.file(ctx, pf))) return
        val tt = 0.9; val rr = Random(1)
        play(buf(1.7f) { t ->
            val boom = if (t < tt) Math.sin(2 * Math.PI * (150 * tt / Math.log(36.0 / 150.0)) * (Math.pow(36.0 / 150.0, t / tt) - 1)) * Math.exp(-t * 4.5) * 0.9 else 0.0
            val noise = (rr.nextDouble() * 2 - 1) * Math.exp(-t * 9) * 0.5
            fun bell(f: Double, st: Double) = if (t >= st) Math.sin(2 * Math.PI * f * (t - st)) * Math.exp(-(t - st) * 5) * 0.25 else 0.0
            boom + noise + bell(880.0, 0.45) + bell(1320.0, 0.57)
        })
    }
}

/** Intro v2: logo terpecah 64 kepingan, menyatu, ledakan + getar + suara, judul menghantam masuk. Tap untuk lewati. */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val dn = LocalDensity.current.density
    val bmp = ImageBitmap.imageResource(R.drawable.logo)
    val t = remember { Animatable(0f) }
    val shards = remember {
        val rr = Random(11)
        List(64) { i -> val a = rr.nextFloat() * 6.2832f; val d = 220f + rr.nextFloat() * 380f
            Shard(i % 8, i / 8, cos(a) * d, sin(a) * d, (rr.nextFloat() - 0.5f) * 540f, rr.nextFloat()) }
    }
    val sparks = remember { val rr = Random(5); List(80) { Spark(rr.nextFloat() * 6.2832f, 110f + rr.nextFloat() * 260f, 1f + rr.nextFloat() * 2f) } }
    val dots = remember { val rr = Random(3); List(60) { Offset(rr.nextFloat(), rr.nextFloat()) } }
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) { delay(500); IntroFx.whoosh() }
        launch { delay((IDUR * IMPACT).toLong()); IntroFx.impact(ctx) }
        t.animateTo(1f, tween(IDUR, easing = LinearEasing)); onDone()
    }
    val tv = t.value
    val k = ((tv - IMPACT) * IDUR / 1000f).coerceAtLeast(0f)
    val sh = if (k > 0.001f && k < 0.8f) 11f * (1f - k / 0.8f) else 0f
    val fade = if (tv > 0.92f) ((1f - tv) / 0.08f).coerceIn(0f, 1f) else 1f
    fun ease(x: Float) = 1f - (1f - x).pow(3)
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = fade }.background(C.Bg)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDone() }
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = sin(k * 90f) * sh * dn; translationY = cos(k * 73f) * sh * dn }) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2 - 30.dp.toPx())
                val L = 130.dp.toPx(); val ts = L / 8f
                dots.forEachIndexed { i, d ->
                    drawCircle(Color(0xFFA0C8FF).copy(alpha = 0.25f + 0.25f * sin(tv * 40f + i)), 1.2f * dn, Offset(d.x * size.width, d.y * size.height))
                }
                val ge = if (tv < IMPACT) (tv / IMPACT).pow(2) * 0.5f else 0.5f * (0.7f + 0.3f * sin(k * 3f))
                val gr = size.minDimension * 0.7f
                drawCircle(Brush.radialGradient(listOf(C.Blue.copy(alpha = ge), Color.Transparent), c, gr), gr, c)
                if (tv < IMPACT) {
                    shards.forEach { s ->
                        val p = ease(((tv - 0.08f - s.dl * 0.22f) / 0.2f).coerceIn(0f, 1f))
                        val tx = (s.c + 0.5f - 4f) * ts; val ty = (s.r + 0.5f - 4f) * ts
                        val cxp = c.x + (s.sx * dn) + (tx - s.sx * dn) * p
                        val cyp = c.y + (s.sy * dn) + (ty - s.sy * dn) * p
                        withTransform({ rotate(s.rot * (1f - p), Offset(cxp, cyp)) }) {
                            drawImage(
                                bmp, srcOffset = IntOffset(s.c * 64, s.r * 64), srcSize = IntSize(64, 64),
                                dstOffset = IntOffset((cxp - ts / 2).roundToInt(), (cyp - ts / 2).roundToInt()),
                                dstSize = IntSize(ts.roundToInt() + 1, ts.roundToInt() + 1),
                                alpha = (p * 1.5f).coerceIn(0f, 1f)
                            )
                        }
                    }
                } else {
                    val sz = L * (1f + 0.14f * exp(-k * 5f) * cos(k * 22f))
                    drawImage(
                        bmp, srcOffset = IntOffset.Zero, srcSize = IntSize(512, 512),
                        dstOffset = IntOffset((c.x - sz / 2).roundToInt(), (c.y - sz / 2).roundToInt()), dstSize = IntSize(sz.roundToInt(), sz.roundToInt())
                    )
                    for (i in 0..1) {
                        val kk = k - i * 0.14f
                        if (kk > 0f && kk < 1.1f) drawCircle((if (i == 0) C.Cyan else C.Blue).copy(alpha = (1f - kk / 1.1f) * 0.8f), kk * size.width * 1.1f, c, style = Stroke((3 - i) * dn))
                    }
                    if (k < 1.2f) sparks.forEach { s ->
                        val d = s.s * dn * k * (1f - k * 0.35f)
                        val dx = cos(s.a); val dy = sin(s.a)
                        drawLine((if (s.z > 2f) Color.White else C.Cyan).copy(alpha = (1f - k / 1.2f).coerceIn(0f, 1f)),
                            Offset(c.x + dx * d, c.y + dy * d), Offset(c.x + dx * (d - 10 * dn), c.y + dy * (d - 10 * dn)), s.z * dn, cap = StrokeCap.Round)
                    }
                    if (k < 0.2f) drawRect(Color.White.copy(alpha = (1f - k / 0.2f) * 0.9f))
                }
            }
            Column(Modifier.align(Alignment.Center).offset(y = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    "GAME TURBO".forEachIndexed { i, ch ->
                        val la = ((k - 0.3f - i * 0.08f) / 0.25f).coerceIn(0f, 1f)
                        Text(
                            ch.toString(), color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp,
                            modifier = Modifier.graphicsLayer { alpha = la; val sc = 1f + (1f - la) * 1.3f; scaleX = sc; scaleY = sc }
                        )
                    }
                }
                Text(
                    "Lebih Lancar, Lebih Produktif", color = C.Cyan, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).graphicsLayer { alpha = ((k - 1.5f) / 0.5f).coerceIn(0f, 1f) }
                )
            }
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp).width(180.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))
            ) { Box(Modifier.fillMaxHeight().fillMaxWidth(tv).background(Brush.horizontalGradient(listOf(C.Blue, C.Cyan)))) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = C.Text, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
fun SettingsScreen(haze: HazeState, shizuku: Boolean) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var hud by remember { mutableStateOf(false) }
    var xhStyle by remember { mutableIntStateOf(prefs.xhStyle) }
    var xhColor by remember { mutableIntStateOf(prefs.xhColor) }
    var xhSize by remember { mutableFloatStateOf(prefs.xhSize.toFloat()) }
    var winSize by remember { mutableIntStateOf(prefs.winSize) }
    var engineSel by remember { mutableIntStateOf(prefs.engine) }
    var killClose by remember { mutableStateOf(prefs.killOnClose) }
    var walls by remember { mutableStateOf(DataPack.walls(ctx)) }
    var dlMsg by remember { mutableStateOf("") }
    var sounds by remember { mutableStateOf(DataPack.sounds(ctx)) }
    var introSnd by remember { mutableStateOf(prefs.introSound) }
    var toolSnd by remember { mutableStateOf(prefs.toolSound) }
    var msg by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            prefs.video = uri.toString(); msg = "Video dipilih. Buka bubble → Jendela Mini → Video."
        }
    }
    fun svc(action: String) = ctx.startForegroundService(Intent(ctx, OverlayService::class.java).setAction(action))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Glass(Modifier.fillMaxWidth(), haze) {
            Section("Mode aman") {
                Chip(if (C.safe) "Mode aman AKTIF (ketuk untuk matikan)" else "Aktifkan mode aman", C.safe) {
                    val v = !C.safe; prefs.safe = v; C.safe = v; (ctx as? android.app.Activity)?.recreate()
                }
                Text("Mematikan intro, efek kaca/blur, dan unduhan data supaya app lebih ringan. Aktif otomatis kalau app macet saat dibuka.", color = C.Dim, fontSize = 12.sp)
            }
        }
        Glass(Modifier.fillMaxWidth(), haze) {
            Section("Monitor FPS, CPU, GPU, Ping, Suhu") {
                Chip(if (hud) "Matikan Monitor" else "Nyalakan Monitor", hud) { hud = !hud; svc(if (hud) "hud_on" else "hud_off") }
                Text("FPS dibaca dari SurfaceFlinger via Shizuku. GPU tampil sebagai frekuensi. Nilai '--' berarti HP tidak mengizinkan.", color = C.Dim, fontSize = 12.sp)
            }
        }
        Glass(Modifier.fillMaxWidth(), haze) {
            Section("Tema warna") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    C.all.forEachIndexed { i, (name, b, c) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(b, c)))
                                    .border(if (C.themeIdx == i) 2.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { C.themeIdx = i; prefs.theme = i }
                            )
                            Text(name, color = C.Dim, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Glass(Modifier.fillMaxWidth(), haze) {
            Section("Wallpaper") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(62.dp, 110.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FFFFFF))
                            .border(if (C.wallIdx == 0) 2.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
                            .clickable { C.wallIdx = 0; prefs.wall = 0 },
                        contentAlignment = Alignment.Center
                    ) { Text("Default", color = C.Text, fontSize = 11.sp) }
                    walls.forEachIndexed { i, f ->
                        FileImage(
                            DataPack.thumb(f), 1,
                            Modifier.size(62.dp, 110.dp).clip(RoundedCornerShape(10.dp))
                                .border(if (C.wallIdx == i + 1) 2.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
                                .clickable { C.wallIdx = i + 1; prefs.wall = i + 1 }
                        )
                    }
                }
                if (walls.isEmpty()) Text("Wallpaper belum diunduh. Buka bagian Paket data di bawah.", color = C.Dim, fontSize = 12.sp)
                Chip("Pasang di layar HP", false) {
                    if (C.wallIdx in 1..walls.size) scope.launch(Dispatchers.IO) {
                        runCatching { java.io.FileInputStream(walls[C.wallIdx - 1]).use { WallpaperManager.getInstance(ctx).setStream(it) } }
                            .onSuccess { msg = "Wallpaper terpasang." }.onFailure { msg = "Gagal: ${it.message}" }
                    } else msg = "Pilih wallpaper dulu."
                }
            }
        }
        Glass(Modifier.fillMaxWidth(), haze) {
            Section("Suara") {
                if (sounds.isEmpty()) Text("Belum ada suara. Unduh paket data dulu.", color = C.Dim, fontSize = 12.sp)
                sounds.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(SoundFx.label(f), color = C.Text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Chip("Putar", false) { scope.launch(Dispatchers.IO) { SoundFx.play(f) } }
                        Chip("Intro", introSnd == f.name) { introSnd = if (introSnd == f.name) "" else f.name; prefs.introSound = introSnd }
                        Chip("Aksi", toolSnd == f.name) { toolSnd = if (toolSnd == f.name) "" else f.name; prefs.toolSound = toolSnd }
                    }
                }
                Text("Intro = suara dentuman saat logo muncul. Aksi = suara saat Optimalkan atau Mulai bermain selesai.", color = C.Dim, fontSize = 12.sp)
            }
        }
        Glass(Modifier.fillMaxWidth(), haze) {
            Section("Paket data") {
                Text(if (walls.isNotEmpty()) "Terpasang: ${walls.size} wallpaper, ${C.extraThemes.size} tema, ${sounds.size} suara" else "Belum diunduh", color = C.Text, fontSize = 13.sp)
                Chip(if (walls.isNotEmpty()) "Perbarui paket data" else "Unduh paket data", false) {
                    dlMsg = "Mengunduh..."
                    scope.launch { val r = DataPack.download(ctx) { }; dlMsg = r ?: "Selesai."; walls = DataPack.walls(ctx); sounds = DataPack.sounds(ctx); C.extraThemes = DataPack.themes(ctx) }
                }
                if (dlMsg.isNotEmpty()) Text(dlMsg, color = C.Cyan, fontSize = 12.sp)
                Text("Data diunduh ke HP ini dari GitHub Release (perlu internet).", color = C.Dim, fontSize = 12.sp)
            }
        }
        Glass(Modifier.fillMaxWidth(), haze) {
            Section("Mesin jendela mengambang") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Overlay (disarankan)", engineSel == 0) { engineSel = 0; prefs.engine = 0 }
                    Chip("Freeform Android", engineSel == 1) { engineSel = 1; prefs.engine = 1 }
                }
                Chip(if (killClose) "Hentikan app saat jendela ditutup: AKTIF" else "Hentikan app saat jendela ditutup: MATI", killClose) {
                    killClose = !killClose; prefs.killOnClose = killClose
                }
                Text("Overlay: app jalan di layar virtual, selalu di atas game dan tidak memunculkan tombol navigasi. Freeform: jendela asli Android, bisa tertutup game.", color = C.Dim, fontSize = 12.sp)
            }
        }
        Glass(Modifier.fillMaxWidth(), haze) {
            Section("Ukuran jendela mengambang") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Kecil", "Sedang", "Besar").forEachIndexed { i, n -> Chip(n, winSize == i) { winSize = i; prefs.winSize = i } }
                }
                Text("Ukuran awal saat app dibuka. Geser bar judul untuk pindah, tarik pojok kanan bawah untuk ubah ukuran.", color = C.Dim, fontSize = 12.sp)
            }
        }
        Glass(Modifier.fillMaxWidth(), haze) {
            Section("Crosshair") {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CrosshairView(xhStyle, XhColors[xhColor], xhSize.toInt())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XhStyles.forEachIndexed { i, n -> Chip(n, xhStyle == i) { xhStyle = i; prefs.xhStyle = i } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    XhColors.forEachIndexed { i, col ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(col)
                                .border(if (xhColor == i) 2.dp else 0.dp, C.Cyan, CircleShape)
                                .clickable { xhColor = i; prefs.xhColor = i }
                        )
                    }
                }
                Slider(
                    xhSize, { xhSize = it; prefs.xhSize = it.toInt() }, valueRange = 20f..120f,
                    colors = SliderDefaults.colors(thumbColor = C.Cyan, activeTrackColor = C.Blue)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Tampilkan", false) { svc("xh_on") }
                    Chip("Sembunyikan", false) { svc("xh_off") }
                }
                Text("Crosshair tampil di tengah layar dan tidak menghalangi sentuhan.", color = C.Dim, fontSize = 12.sp)
            }
        }
        Glass(Modifier.fillMaxWidth(), haze) {
            Section("Pemutar video mengambang") {
                BigButton("Pilih Video dari HP") { pick.launch(arrayOf("video/*")) }
                Text("Putar lewat bubble → Jendela Mini → Video.", color = C.Dim, fontSize = 12.sp)
            }
        }
        Glass(Modifier.fillMaxWidth(), haze) {
            Section("Lainnya") {
                Chip("Reset Aplikasi Favorit", false) { prefs.favs = emptySet(); msg = "Favorit direset" }
                if (msg.isNotEmpty()) Text(msg, color = C.Cyan, fontSize = 13.sp)
                Text("Game Turbo 1.3.0. Shizuku: " + if (shizuku) "aktif" else "mati", color = C.Dim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun CrashScreen(text: String, onContinue: () -> Unit) {
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(Modifier.fillMaxSize().background(C.Bg).systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Game Turbo mengalami error", color = C.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Tekan Salin lalu kirim teks ini ke Claude supaya bisa diperbaiki.", color = C.Dim, fontSize = 13.sp)
        androidx.compose.foundation.text.selection.SelectionContainer(Modifier.weight(1f)) {
            Text(text.take(6000), color = C.Text, fontSize = 11.sp, modifier = Modifier.verticalScroll(rememberScrollState()))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(C.Blue).clickable { clip.setText(androidx.compose.ui.text.AnnotatedString(text)) }.padding(14.dp), contentAlignment = Alignment.Center) { Text("Salin", color = Color.White) }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0x33FFFFFF)).clickable { onContinue() }.padding(14.dp), contentAlignment = Alignment.Center) { Text("Coba buka lagi", color = C.Text) }
        }
    }
}

/** Memuat gambar dari file dengan ukuran diperkecil (hemat memori). */
@Composable
fun FileImage(file: java.io.File, sample: Int, modifier: Modifier) {
    val bmp by produceState<ImageBitmap?>(null, file.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    bmp?.let { Image(it, null, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop) } ?: Box(modifier.background(Color(0x22FFFFFF)))
}

/** Layar "menyiapkan data" saat pertama kali dibuka: mengunduh paket data ke HP. */
@Composable
fun DataScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Mengunduh paket data...") }
    var error by remember { mutableStateOf(false) }
    fun start() {
        error = false; status = "Mengunduh paket data..."
        scope.launch { val r = DataPack.download(ctx) { progress = it }; if (r == null) onDone() else { error = true; status = r } }
    }
    LaunchedEffect(Unit) { start() }
    Column(
        Modifier.fillMaxSize().background(C.Bg).systemBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)))
        Spacer(Modifier.height(20.dp))
        Text("Menyiapkan data", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Pertama kali dibuka, Game Turbo mengunduh wallpaper dan paket tema ke HP ini. Sebaiknya pakai WiFi.", color = C.Dim, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Brush.horizontalGradient(listOf(C.Blue, C.Cyan))))
        }
        Text(status, color = if (error) C.Red else C.Cyan, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (error) Chip("Coba lagi", true) { start() }
            Chip("Lewati dulu", false) { onDone() }
        }
    }
}

@Composable
fun SafeBanner() {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 6.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33FFC857)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Mode aman aktif: intro dan efek kaca dimatikan.", color = Color(0xFFFFC857), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Chip("Coba normal", false) { Prefs(ctx).safe = false; C.safe = false; (ctx as? android.app.Activity)?.recreate() }
    }
}
