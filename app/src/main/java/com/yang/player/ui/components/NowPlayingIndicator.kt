package com.yang.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.sin

/** A lightweight playback indicator, not a visualization of audio samples. */
@Composable
fun NowPlayingIndicator(isPlaying: Boolean, color: Color, modifier: Modifier = Modifier) {
    val phase = if (isPlaying) {
        rememberInfiniteTransition(label = "playingBars").animateFloat(
            initialValue = 0f,
            targetValue = (PI * 2).toFloat(),
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "playingBarsPhase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Canvas(modifier.semantics {
        contentDescription = if (isPlaying) "Reproduciendo" else "En pausa"
    }) {
        // Animation is read only during drawing, on the one visible active row.
        val position = phase.value
        val barWidth = size.width * 0.13f
        val spacing = size.width * 0.12f
        val startX = (size.width - (barWidth * 4 + spacing * 3)) / 2
        repeat(4) { index ->
            val fraction = if (isPlaying) {
                0.25f + 0.65f * ((sin(position + index * 1.7f) + 1f) / 2f)
            } else {
                if (index == 1 || index == 2) 0.42f else 0.24f
            }
            val height = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + index * (barWidth + spacing), (size.height - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}
