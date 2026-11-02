package com.yang.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yang.player.lyrics.InstrumentalBreak
import com.yang.player.lyrics.LyricEffects
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun InstrumentalDots(pause: InstrumentalBreak, clock: State<Long>, accent: Color) {
    Canvas(Modifier.fillMaxWidth().height(72.dp).semantics {
        contentDescription = "Interludio instrumental, la letra continúa después"
    }) {
        val position = clock.value
        val fraction = ((position - pause.startMs).toFloat() / (pause.endMs - pause.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
        val lastStage = ((fraction - 2f / 3f) * 3f).coerceIn(0f, 1f)
        val breathing = 0.97f + 0.03f * sin(position / 2200f * (PI * 2).toFloat())
        val envelope = LyricEffects.smooth((position - pause.startMs).toFloat() / 600) *
            LyricEffects.smooth((pause.endMs - position).toFloat() / 700)
        val stageDuration = (pause.endMs - pause.startMs) / 3f
        repeat(3) { index ->
            val stageStart = pause.startMs + stageDuration * index
            val stageEnd = stageStart + stageDuration
            val entering = LyricEffects.smooth((position - stageStart) / 500)
            val leaving = LyricEffects.smooth((position - stageEnd + 500) / 500)
            val current = entering * (1 - leaving)
            val completed = entering * leaving
            val brightness = (0.22f + 0.72f * current * breathing + 0.48f * completed) * envelope
            val center = Offset(7.dp.toPx() + index * 20.dp.toPx(), size.height / 2)
            if (current > 0) {
                val glow = (if (index == 2) 0.12f + 0.10f * lastStage else 0.10f) * current * envelope
                val radius = if (index == 2) (9 + 1.5f * lastStage).dp.toPx() else 9.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent.copy(alpha = glow * breathing), Color.Transparent), center, radius),
                    radius = radius,
                    center = center
                )
            }
            val radius = if (index == 2) (5.5f + 0.3f * lastStage * current).dp.toPx() else 5.5.dp.toPx()
            drawCircle(Color.White.copy(alpha = brightness), radius, center)
        }
    }
}
