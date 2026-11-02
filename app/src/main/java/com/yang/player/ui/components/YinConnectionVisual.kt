package com.yang.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
        if (ready) { intro.animateTo(1f, tween(1250)); settled = true }
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
    val wave = remember { Path() }
    val blue = Color(0xFF8DD4EE)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(176.dp).semantics {
            contentDescription = if (!ready) "Yin esperando conexión" else if (!settled) "Yin y Yang conectándose" else if (playing) "Espectro de frecuencias de la canción" else "Visualizador en pausa"
        }) {
            clock.longValue
            val progress = intro.value
            val center = Offset(size.width / 2, size.height / 2)
            if (!settled && ready) {
                val radius = 29.dp.toPx()
                val orbit = radius * (2.1f - progress * 1.1f)
                val angle = progress * PI * 2
                val x = cos(angle).toFloat() * orbit
                val y = sin(angle).toFloat() * orbit * .45f
                drawCircle(blue.copy(alpha = .12f), radius * 1.25f, center + Offset(-x, -y))
                drawCircle(color.copy(alpha = .12f), radius * 1.25f, center + Offset(x, y))
                drawCircle(blue, radius, center + Offset(-x, -y))
                drawCircle(color, radius, center + Offset(x, y))
                drawCircle(color, radius * .2f, center + Offset(-x, -y))
                drawCircle(blue, radius * .2f, center + Offset(x, y))
            } else {
                val current = frame.value
                val fresh = ready && playing && SystemClock.elapsedRealtime() - current.capturedAt < 650
                val margin = 8.dp.toPx()
                val step = (size.width - margin * 2) / 25
                for (i in levels.indices) {
                    val target = if (fresh) current.bands[i] else 0f
                    levels[i] = if (!fresh) 0f else levels[i] + (target - levels[i]) * if (target > levels[i]) .32f else .10f
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
                    drawPath(wave, (if (layer == 1) blue else color).copy(alpha = if (layer == 0) .9f else .28f),
                        style = Stroke((if (layer == 0) 2f else 1.2f).dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        Text(if (!ready) "Esperando a Yin" else if (!settled) "Yin + Yang" else "Yin conectado",
            color = if (ready) color else Color.White.copy(alpha = .6f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(if (!ready) "Reconectando el motor de sonido" else if (!settled) "Dos mitades, un sonido" else if (playing) "Frecuencias de la canción · antes de los efectos" else "Listo para reproducir",
            color = Color.White.copy(alpha = .45f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
    }
}
