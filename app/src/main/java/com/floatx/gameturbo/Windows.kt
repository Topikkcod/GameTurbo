package com.floatx.gameturbo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bar judul jendela mengambang: geser untuk pindah, tombol kecilkan / penuh / tutup. */
@Composable
fun BarUI(
    label: String, onMin: () -> Unit, onMax: () -> Unit, onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit, onEnd: () -> Unit
) {
    Glass(Modifier.fillMaxSize(), corner = 14.dp) {
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onEnd() }, onDrag = { c, d -> c.consume(); onDrag(d.x, d.y) })
                },
                contentAlignment = Alignment.CenterStart
            ) { Text(label, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
            BarIcon(Icons.Rounded.Remove, C.Dim, onMin)
            BarIcon(Icons.Rounded.CropSquare, C.Dim, onMax)
            BarIcon(Icons.Rounded.Close, C.Red, onClose)
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Pegangan pojok kanan bawah untuk mengubah ukuran jendela. */
@Composable
fun HandleUI(onDrag: (Float, Float) -> Unit, onEnd: () -> Unit) {
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(onDragEnd = { onEnd() }, onDrag = { c, d -> c.consume(); onDrag(d.x, d.y) })
        },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(C.Blue.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.OpenInFull, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
