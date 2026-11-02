package com.yang.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.yang.player.audio.MusicSpectrum
import android.os.SystemClock
import kotlin.math.*

/** Connection intro stays local to this visual; spectrum frames invalidate drawing, not the HiFi screen. */
@Composable
fun YinConnectionVisual(ready: Boolean, playing: Boolean, color: Color) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val intro = remember { Animatable(0f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(ready) {
        settled = false
        intro.snapTo(0f)
        if (ready) { intro.animateTo(1f, tween(2400, easing = LinearEasing)); settled = true }
    }
    LaunchedEffect(lifecycle, ready, playing) {
        if (ready && playing) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { MusicSpectrum.observe() }
    }
    val frame = MusicSpectrum.frame.collectAsState()
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(lifecycle, ready, playing) {
        if (ready && playing) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { withFrameNanos { clock.longValue = it } }
        }
    }
    val levels = remember { FloatArray(24) }
    val lastDraw = remember { longArrayOf(0L) }
    val wave = remember { Path() }
    val blue = Color(0xFF8DD4EE)
    val ivory = Color(0xFFF3EADD)
    val ink = Color(0xFF202834)
    val amber = Color(0xFFE4AE58)
    val lightHalf = remember { Path() }
    val darkHalf = remember { Path() }
    fun stage(progress: Float, start: Float, end: Float): Float {
        val value = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        return value * value * (3f - 2f * value)
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(176.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription = if (!ready) "Yin esperando conexión" else if (!settled) "Yin y Yang conectándose" else if (playing) "Espectro de frecuencias de la canción" else "Visualizador en pausa"
        }) {
            val now = clock.longValue
            val elapsed = if (lastDraw[0] == 0L) 1f / 60 else ((now - lastDraw[0]) / 1_000_000_000f).coerceIn(.001f, .05f)
            lastDraw[0] = now
            val progress = intro.value
            val center = Offset(size.width / 2, size.height / 2)
            val merge = stage(progress, 0f, .42f)
            val revealSpectrum = if (ready) stage(progress, .79f, 1f) else 1f
            if (ready && revealSpectrum < 1f) {
                val radius = 40.dp.toPx()
                val origin = Offset(center.x, size.height * .43f)
                val distance = (1f - merge) * radius * 1.6f
                val orbit = (1f - merge) * PI.toFloat() * 1.4f
                val x = cos(orbit) * distance
                val y = sin(orbit) * distance * .75f
                val turn = (1f - merge) * 260f
                val pieceScale = .82f + merge * .18f
                val alpha = 1f - revealSpectrum
                // Complementary closed shapes share the same S seam when fully joined.
                // Their geometry follows the paired launcher marks, rather than two circles.
                fun half(path: Path, right: Boolean) {
                    path.reset()
                    path.moveTo(0f, -radius)
                    path.arcTo(Rect(-radius, -radius, radius, radius), -90f, if (right) 180f else -180f, false)
                    path.cubicTo(radius * .5522848f, radius, radius * .5522848f, 0f, 0f, 0f)
                    path.cubicTo(-radius * .5522848f, 0f, -radius * .5522848f, -radius, 0f, -radius)
                    path.close()
                }
                half(lightHalf, true)
                half(darkHalf, false)
                val glow = stage(progress, .37f, .51f) * (1f - stage(progress, .60f, .78f))
                drawCircle(blue.copy(alpha = .035f * glow * alpha), radius * 1.4f, origin)
                drawCircle(amber.copy(alpha = .045f * glow * alpha), radius * 1.15f, origin)
                // Opposing curved orbits and independent spins unwind into the shared S seam.
                translate(origin.x - x, origin.y - y) {
                    rotate(-turn, Offset.Zero) {
                        scale(pieceScale, pieceScale, Offset.Zero) {
                            drawPath(darkHalf, ink.copy(alpha = alpha))
                            drawPath(darkHalf, blue.copy(alpha = .45f * alpha), style = Stroke(.8.dp.toPx()))
                            drawCircle(ivory.copy(alpha = alpha), radius * .136f, Offset(0f, radius / 2))
                        }
                    }
                }
                translate(origin.x + x, origin.y + y) {
                    rotate(turn, Offset.Zero) {
                        scale(pieceScale, pieceScale, Offset.Zero) {
                            drawPath(lightHalf, ivory.copy(alpha = alpha))
                            drawPath(lightHalf, amber.copy(alpha = .5f * alpha), style = Stroke(.8.dp.toPx()))
                            drawCircle(ink.copy(alpha = alpha), radius * .136f, Offset(0f, -radius / 2))
                        }
                    }
                }
                drawCircle(ivory.copy(alpha = .15f * merge * alpha), radius,
                    origin, style = Stroke(.8.dp.toPx()))
            }
            if (revealSpectrum > 0f) {
                val current = frame.value
                val fresh = ready && playing && SystemClock.elapsedRealtime() - current.capturedAt < 180
                val margin = 8.dp.toPx()
                val step = (size.width - margin * 2) / 25
                val attack = 1f - exp(-elapsed / .009f)
                val release = 1f - exp(-elapsed / .035f)
                for (i in levels.indices) {
                    val target = if (fresh) current.bands[i] else 0f
                    levels[i] = if (!fresh) 0f else levels[i] + (target - levels[i]) * if (target > levels[i]) attack else release
                }
                for (layer in 0..2) {
                    wave.reset()
                    wave.moveTo(margin, center.y)
                    var previousX = margin
                    var previousY = center.y
                    for (i in 0..24) {
                        val x = margin + (i + 1) * step
                        val value = if (i < 24) levels[i] else 0f
                        val envelope = if (i < 24) sin(PI * (i + 1) / 25).toFloat() else 0f
                        val sign = if (i % 2 == 0) 1 else -1
                        val y = center.y + sign * value * envelope * size.height * (.33f - layer * .075f)
                        wave.cubicTo(previousX + step * .5f, previousY, x - step * .5f, y, x, y)
                        previousX = x; previousY = y
                    }
                    drawPath(wave, (if (layer == 1) blue else color).copy(alpha = (if (layer == 0) .9f else .28f) * revealSpectrum),
                        style = Stroke((if (layer == 0) 2f else 1.2f).dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        Text("YIN · YANG MUSIC", color = ivory, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            letterSpacing = 3.sp, modifier = Modifier.padding(bottom = 10.dp).graphicsLayer {
                val progress = intro.value
                alpha = if (ready) stage(progress, .38f, .49f) * (1f - stage(progress, .77f, .90f)) else 0f
                translationY = (1f - stage(progress, .38f, .49f)) * 6.dp.toPx()
            })
        }
        Text(if (!ready) "Esperando a Yin" else "Yin conectado",
            color = if (ready) color else Color.White.copy(alpha = .6f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                val reveal = if (ready) stage(intro.value, .43f, .55f) else 1f
                alpha = reveal
                translationY = (1f - reveal) * 8.dp.toPx()
                scaleX = .96f + reveal * .04f
                scaleY = scaleX
            }.drawWithContent {
                drawContent()
                val progress = intro.value
                if (ready && progress > .54f && progress < .84f) {
                    val shine = ((progress - .54f) / .30f).coerceIn(0f, 1f)
                    val x = -size.width * .4f + shine * size.width * 1.8f
                    drawRect(Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = .9f), Color.Transparent),
                        start = Offset(x - size.width * .22f, 0f), end = Offset(x + size.width * .22f, size.height)
                    ), blendMode = BlendMode.SrcAtop)
                }
            })
        Text(if (!ready) "Reconectando el motor de sonido" else if (!settled) "Dos mitades, un sonido" else if (playing) "Frecuencias de la canción · antes de los efectos" else "Listo para reproducir",
            color = Color.White.copy(alpha = .45f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
    }
}
