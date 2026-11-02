package com.yang.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.yang.player.ui.theme.ArtworkPalette
import com.yang.player.ui.theme.BackgroundDark

/** Two soft radial gradients: no bitmap snapshots or full-screen blur per frame. */
@Composable
fun ArtworkAtmosphere(
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
    moving: Boolean = false
) {
    val primary by animateColorAsState(palette.topColor, tween(700), label = "homeAmbientPrimary")
    val secondary by animateColorAsState(palette.midColor, tween(700), label = "homeAmbientSecondary")
    val drift = if (moving) {
        rememberInfiniteTransition(label = "artworkLightDrift").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(16000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "haloPosition"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Canvas(modifier.fillMaxSize()) {
        // Read animation state during drawing: no layout or composition per frame.
        val travel = drift.value
        drawRect(BackgroundDark)
        drawRect(Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.85f), primary.copy(alpha = 0.25f), Color.Transparent),
            center = Offset(size.width * (0.85f - travel * 0.22f), size.height * (0.12f + travel * 0.07f)),
            radius = size.width * 1.1f
        ))
        drawRect(Brush.radialGradient(
            colors = listOf(secondary.copy(alpha = 0.55f), Color.Transparent),
            center = Offset(size.width * (-0.12f + travel * 0.25f), size.height * (0.60f - travel * 0.08f)),
            radius = size.width * 0.95f
        ))
    }
}
