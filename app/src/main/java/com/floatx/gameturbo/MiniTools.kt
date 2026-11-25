package com.floatx.gameturbo

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MiniBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x44338BFF)).clickable { onClick() }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = C.Text, fontSize = 13.sp, textAlign = TextAlign.Center) }
}

@Composable
fun NotesMini(prefs: Prefs) {
    var text by remember { mutableStateOf(prefs.note) }
    Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33000000)).padding(10.dp)) {
        if (text.isEmpty()) Text("Tulis catatan...", color = C.Dim, fontSize = 14.sp)
        BasicTextField(
            text, { text = it; prefs.note = it },
            textStyle = TextStyle(color = C.Text, fontSize = 14.sp),
            cursorBrush = SolidColor(C.Cyan), modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun CalcMini() {
    var disp by remember { mutableStateOf("0") }
    var acc by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf(true) }
    fun fmt(d: Double) = if (d.isNaN() || d.isInfinite()) "Error" else if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
    fun compute(): Double? {
        val a = acc ?: return null
        val b = disp.toDoubleOrNull() ?: return null
        return when (op) { "+" -> a + b; "-" -> a - b; "×" -> a * b; "÷" -> a / b; else -> null }
    }
    fun press(k: String) {
        when (k) {
            "C" -> { disp = "0"; acc = null; op = null; fresh = true }
            "+", "-", "×", "÷" -> {
                val r = if (!fresh) compute() else null
                if (r != null) disp = fmt(r)
                acc = disp.toDoubleOrNull(); op = k; fresh = true
            }
            "=" -> { compute()?.let { disp = fmt(it) }; acc = null; op = null; fresh = true }
            "." -> { if (fresh) { disp = "0."; fresh = false } else if (!disp.contains('.')) disp += "." }
            else -> { if (fresh || disp == "0") disp = k else disp += k; fresh = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(disp, color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(8.dp), maxLines = 1)
        listOf(listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"), listOf("1", "2", "3", "-"), listOf("C", "0", ".", "+")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { k -> MiniBtn(k, Modifier.weight(1f)) { press(k) } } }
        }
        MiniBtn("=", Modifier.fillMaxWidth()) { press("=") }
    }
}

@Composable
fun ClockMini() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now), color = C.Text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text(SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(now), color = C.Dim, fontSize = 13.sp)
    }
}

@Composable
fun MusicMini() {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Kontrol pemutar musik yang sedang aktif", color = C.Dim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniBtn("Sebelumnya", Modifier.weight(1f)) { scope.launch(Dispatchers.IO) { Tools.media(88) } }
            MiniBtn("Putar/Jeda", Modifier.weight(1f)) { scope.launch(Dispatchers.IO) { Tools.media(85) } }
            MiniBtn("Berikutnya", Modifier.weight(1f)) { scope.launch(Dispatchers.IO) { Tools.media(87) } }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserMini() {
    var url by remember { mutableStateOf("https://www.google.com") }
    var web by remember { mutableStateOf<WebView?>(null) }
    fun go() {
        val u = url.trim()
        web?.loadUrl(if (u.startsWith("http")) u else if (u.contains(".") && !u.contains(" ")) "https://$u" else "https://www.google.com/search?q=" + java.net.URLEncoder.encode(u, "UTF-8"))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0x33000000)).padding(10.dp)) {
                BasicTextField(
                    url, { url = it }, singleLine = true,
                    textStyle = TextStyle(color = C.Text, fontSize = 12.sp), cursorBrush = SolidColor(C.Cyan),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() })
                )
            }
            MiniBtn("Buka", Modifier.width(56.dp)) { go() }
        }
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(url); web = this
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp))
        )
    }
}

@Composable
fun Hud(s: Snap?) {
    Glass(corner = 16.dp) {
        Row(Modifier.padding(14.dp, 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HudCell("FPS", s?.fps?.toString() ?: "--", if ((s?.fps ?: 60) >= 50) C.Green else Color(0xFFFFC857))
            HudCell("CPU", s?.cpu?.let { "$it%" } ?: "--", C.Text)
            HudCell("GPU", s?.gpu ?: "--", C.Text)
            HudCell("Ping", s?.ping?.let { "$it ms" } ?: "--", if ((s?.ping ?: 0) in 1..80) C.Green else Color(0xFFFFC857))
            HudCell("Suhu", s?.temp?.let { "%.0f°C".format(it) } ?: "--", C.Text)
        }
    }
}

@Composable
private fun HudCell(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = C.Dim, fontSize = 9.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TimerMini() {
    var left by remember { mutableIntStateOf(0) }
    var run by remember { mutableStateOf(false) }
    LaunchedEffect(run) {
        while (run && left > 0) { delay(1000); left-- }
        if (run && left <= 0) {
            run = false
            runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100).startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("%02d:%02d".format(left / 60, left % 60), color = C.Text, fontSize = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 5, 10).forEach { m -> MiniBtn("+$m mnt", Modifier.weight(1f)) { left += m * 60 } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniBtn(if (run) "Jeda" else "Mulai", Modifier.weight(1f)) { if (left > 0) run = !run }
            MiniBtn("Reset", Modifier.weight(1f)) { run = false; left = 0 }
        }
    }
}

@Composable
fun VideoMini(prefs: Prefs) {
    val uri = prefs.video
    if (uri.isEmpty()) {
        Text("Belum ada video. Buka app Game Turbo → tab Setelan → Pilih Video.", color = C.Dim, fontSize = 12.sp)
        return
    }
    AndroidView(
        factory = { c ->
            android.widget.VideoView(c).apply {
                val mc = android.widget.MediaController(c); mc.setAnchorView(this); setMediaController(mc)
                setVideoURI(android.net.Uri.parse(uri)); setOnPreparedListener { start() }
            }
        },
        onRelease = { it.stopPlayback() },
        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))
    )
}

@Composable
fun GameClock() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var bat by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))?.let {
                bat = it.getIntExtra("level", 0) * 100 / it.getIntExtra("scale", 100).coerceAtLeast(1)
            }
            delay(1000)
        }
    }
    Glass(corner = 14.dp) {
        Column(Modifier.padding(12.dp, 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Baterai $bat%", color = C.Dim, fontSize = 10.sp)
        }
    }
}
