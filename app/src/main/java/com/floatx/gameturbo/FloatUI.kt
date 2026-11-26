package com.floatx.gameturbo

import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun normalizeUrl(s: String): String {
    val u = s.trim()
    return if (u.startsWith("http://") || u.startsWith("https://")) u
    else if (u.contains(".") && !u.contains(" ")) "https://$u"
    else "https://www.google.com/search?q=" + java.net.URLEncoder.encode(u, "UTF-8")
}

/** Isi satu jendela mengambang: konten + bar judul (auto-sembunyi) + pegangan resize, atau bola kecil. */
@Composable
fun FloatWinUI(w: FloatWin, eng: FloatEngine) {
    val shape = RoundedCornerShape(14.dp)
    LaunchedEffect(w.barTick, w.typing, w.minimized) {
        w.barVisible = true
        if (!w.typing && !w.minimized) { delay(3000); w.barVisible = false }
    }
    Box(Modifier.fillMaxSize()) {
        // Konten selalu tersusun (ukuran 1dp saat jadi bola) supaya layar virtual dan tab browser tidak mati.
        Box(
            (if (w.minimized) Modifier.size(1.dp) else Modifier.fillMaxSize())
                .clip(shape).background(Color(0xFF060C1E)).border(1.dp, C.Cyan.copy(alpha = 0.6f), shape)
        ) {
            if (w.kind == 0) VdContent(w, eng) else w.browser?.let { BrowserBody(it, w, eng) }
        }
        if (w.minimized) {
            Ball(w, eng)
        } else {
            if (w.barVisible || w.typing) TitleBar(w, eng) else GrabStrip(w, eng)
            if (w.typing && w.kind == 0) TypeRow(w, eng)
            Box(
                Modifier.align(Alignment.BottomEnd).size(40.dp).pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { eng.resizeEnd(w) }, onDrag = { c, d -> c.consume(); eng.resizeBy(w, d.x.toInt(), d.y.toInt()) })
                },
                contentAlignment = Alignment.BottomEnd
            ) { Icon(Icons.Rounded.OpenInFull, null, tint = C.Cyan, modifier = Modifier.padding(8.dp).size(16.dp)) }
        }
    }
}

@Composable
private fun VdContent(w: FloatWin, eng: FloatEngine) {
    AndroidView(
        factory = { c ->
            TextureView(c).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) { eng.onSurface(w, st, width, height) }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) { }
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = false
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) { }
                }
                setOnTouchListener { v, e -> eng.touch(w, e, v.width, v.height); true }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun FIcon(icon: ImageVector, tint: Color = Color(0xFFCFE3FF), onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun BoxScope.TitleBar(w: FloatWin, eng: FloatEngine) {
    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(36.dp)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(Color(0xF2102A63)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) {
                detectDragGestures(onDragStart = { w.barTick++ }, onDrag = { c, d -> c.consume(); eng.moveBy(w, d.x.toInt(), d.y.toInt()) })
            }.padding(start = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) { Text(w.title, color = C.Text, fontSize = 13.sp, maxLines = 1) }
        if (w.kind == 0) {
            FIcon(Icons.Rounded.ArrowBack) { w.barTick++; eng.key(w, 4) }
            FIcon(Icons.Rounded.Keyboard) { w.typing = !w.typing }
        }
        FIcon(Icons.Rounded.VerticalSplit) { w.barTick++; eng.cycleSnap(w) }
        FIcon(Icons.Rounded.Remove) { eng.minimize(w) }
        FIcon(Icons.Rounded.Close, C.Red) { eng.close(w) }
    }
}

@Composable
private fun BoxScope.GrabStrip(w: FloatWin, eng: FloatEngine) {
    Box(
        Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(72.dp, 12.dp).clip(RoundedCornerShape(6.dp))
            .background(C.Cyan.copy(alpha = 0.75f))
            .pointerInput(Unit) { detectTapGestures { w.barTick++ } }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { w.barTick++ }, onDrag = { c, d -> c.consume(); eng.moveBy(w, d.x.toInt(), d.y.toInt()) })
            }
    )
}

@Composable
private fun BoxScope.Ball(w: FloatWin, eng: FloatEngine) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF102A63)).border(2.dp, C.Cyan, CircleShape)
            .pointerInput(Unit) { detectTapGestures { eng.restore(w) } }
            .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); eng.moveBy(w, d.x.toInt(), d.y.toInt()) } },
        contentAlignment = Alignment.Center
    ) {
        val ic = w.icon
        if (ic != null) Image(ic, null, Modifier.size(34.dp).clip(CircleShape))
        else Icon(Icons.Rounded.Public, null, tint = C.Cyan, modifier = Modifier.size(28.dp))
    }
}

/** Kotak ketik untuk app di layar virtual (keyboard HP tidak muncul di layar virtual). */
@Composable
private fun BoxScope.TypeRow(w: FloatWin, eng: FloatEngine) {
    var text by remember { mutableStateOf("") }
    val kb = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    Row(
        Modifier.align(Alignment.TopCenter).padding(top = 38.dp).fillMaxWidth().background(Color(0xF2102A63)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0x33000000)).padding(8.dp)) {
            BasicTextField(
                text, { text = it }, singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 13.sp), cursorBrush = SolidColor(C.Cyan),
                modifier = Modifier.fillMaxWidth().onFocusChanged { st ->
                    if (st.isFocused) { eng.setFocusable(w, true); scope.launch { delay(250); kb?.show() } } else eng.setFocusable(w, false)
                }
            )
        }
        MiniBtn("Kirim", Modifier.width(52.dp)) { if (text.isNotEmpty()) { eng.type(w, text); text = "" } }
        MiniBtn("DEL", Modifier.width(40.dp)) { eng.key(w, 67) }
        MiniBtn("OK", Modifier.width(36.dp)) { eng.key(w, 66) }
    }
}

@Composable
private fun NavIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = C.Text, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TabChip(title: String, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) C.Blue.copy(alpha = 0.6f) else Color(0x22FFFFFF))
            .clickable { onClick() }.padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title.take(12), color = C.Text, fontSize = 11.sp, maxLines = 1)
        Box(Modifier.size(24.dp).clickable { onClose() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, null, tint = C.Dim, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun BrowserBody(b: BrowserState, w: FloatWin, eng: FloatEngine) {
    val cur = b.tabs.getOrNull(b.current)
    var urlText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val kb = LocalSoftwareKeyboardController.current
    val fm = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(cur?.url, cur?.id) { if (!editing) urlText = cur?.url.orEmpty() }
    LaunchedEffect(cur?.title) { cur?.title?.let { w.title = it } }
    fun go() {
        val u = urlText.trim()
        if (u.isEmpty()) return
        cur?.web?.loadUrl(normalizeUrl(u))
        kb?.hide(); fm.clearFocus()
    }
    Column(Modifier.fillMaxSize().padding(top = if (w.barVisible) 38.dp else 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            NavIcon(Icons.Rounded.ArrowBack) { cur?.web?.goBack() }
            NavIcon(Icons.Rounded.ArrowForward) { cur?.web?.goForward() }
            NavIcon(Icons.Rounded.Refresh) { cur?.web?.reload() }
            Box(
                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    urlText, { urlText = it }, singleLine = true,
                    textStyle = TextStyle(color = C.Text, fontSize = 12.sp), cursorBrush = SolidColor(C.Cyan),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { st ->
                        editing = st.isFocused
                        if (st.isFocused) { eng.setFocusable(w, true); scope.launch { delay(250); kb?.show() } } else eng.setFocusable(w, false)
                    }
                )
            }
            NavIcon(if (b.desktop) Icons.Rounded.DesktopWindows else Icons.Rounded.PhoneAndroid) { b.desktop = !b.desktop; b.applyDesktop() }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            b.tabs.forEachIndexed { i, t -> TabChip(t.title, i == b.current, { b.current = i }, { b.close(i) }) }
            if (b.tabs.size < 6) NavIcon(Icons.Rounded.Add) { b.add("https://www.google.com") }
        }
        if (cur != null && cur.progress < 100) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0x22FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(cur.progress / 100f).background(C.Cyan))
            }
        }
        if (cur != null) {
            key(cur.id) {
                AndroidView(
                    factory = { _ -> (cur.web.parent as? ViewGroup)?.removeView(cur.web); cur.web },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}
