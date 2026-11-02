package com.yang.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Decorative wave. Cache its geometry and move a drawing layer instead of rebuilding paths every frame. */
@Composable
fun HiFiWave(playing: Boolean, color: Color) {
    val phase = if (playing) rememberInfiniteTransition(label = "hifiWave").animateFloat(0f, 1f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "wavePhase")
        else remember { mutableFloatStateOf(0f) }
    val amplitude = animateFloatAsState(if (playing) 1f else .22f, tween(450), label = "waveAmplitude")
    Box(Modifier.size(112.dp, 52.dp).clipToBounds().semantics {
        contentDescription = if (playing) "Animación de reproducción" else "Reproducción pausada"
    }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = -phase.value * size.width
            scaleY = amplitude.value
        }.drawWithCache {
            val paths = (0..1).map { layer ->
                Path().apply {
                    for (step in 0..128) {
                        val x = size.width * 2 * step / 128f
                        val y = size.height / 2 + sin(step / 128f * 4 * PI + layer * .8).toFloat() * size.height * .30f
                        if (step == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
            }
            val mainStroke = Stroke(2.3.dp.toPx(), cap = StrokeCap.Round)
            val secondaryStroke = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round)
            onDrawBehind {
                drawPath(paths[1], color.copy(alpha = .25f), style = secondaryStroke)
                drawPath(paths[0], color.copy(alpha = .9f), style = mainStroke)
            }
        })
    }
}
