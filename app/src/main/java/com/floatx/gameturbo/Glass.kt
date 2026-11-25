package com.floatx.gameturbo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/** Kaca cair: blur backdrop (Haze) + gradasi biru + tepi bercahaya. Tanpa haze = versi solid (untuk overlay/list). */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    haze: HazeState? = null,
    corner: Dp = 22.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    val fill = if (haze != null)
        Brush.verticalGradient(listOf(C.Blue.copy(alpha = 0.33f), Color(0x66071230)))
    else
        Brush.verticalGradient(listOf(C.Blue.copy(alpha = 0.25f).compositeOver(Color(0xEE071230)), Color(0xF2071230)))
    Box(
        modifier
            .clip(shape)
            .let { m -> if (haze != null) m.hazeEffect(haze) { blurRadius = 24.dp } else m }
            .background(fill)
            .border(
                1.dp,
                Brush.linearGradient(listOf(C.Cyan.copy(alpha = 0.8f), C.Blue.copy(alpha = 0.15f), C.Blue.copy(alpha = 0.8f))),
                shape
            ),
        content = content
    )
}
