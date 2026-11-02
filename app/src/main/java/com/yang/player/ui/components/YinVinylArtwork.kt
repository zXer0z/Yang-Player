package com.yang.player.ui.components

import android.os.SystemClock
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.yang.player.R
import com.yang.player.audio.MusicSpectrum
import com.yang.player.audio.SpectrumFrame
import com.yang.player.model.Song
import kotlin.math.*

/** A rotating album disc with a mirrored, real-frequency contour. No touch handlers: pager owns swipes. */
@Composable
fun YinVinylArtwork(song: Song, playing: Boolean, colors: List<Color>, modifier: Modifier = Modifier) {
    val color = colors.firstOrNull() ?: Color(0xFFB4BECF)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val rotation = remember(song.id) { mutableFloatStateOf(0f) }
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing, lifecycle) {
        if (playing) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                if (previous != 0L) rotation.floatValue = (rotation.floatValue + (now - previous) / 1_000_000_000f * 48f) % 360f
                previous = now; clock.longValue = now
            }
        }
    }
    val spectrum = if (playing) MusicSpectrum.frame.collectAsState() else remember { mutableStateOf(SpectrumFrame()) }
    val levels = remember { FloatArray(32) }
    val amplitudes = remember { FloatArray(32) }
    val segments = 128
    val fallbackPath = remember { Path() }
    val last = remember { longArrayOf(0L) }
    val meshes = remember { Array(3) { FloatArray((segments + 1) * 4) } }
    val meshPaint = remember { android.graphics.Paint().apply { this.color = android.graphics.Color.WHITE } }
    val angles = remember { FloatArray(segments + 1) { -PI.toFloat() / 2 + it * 2 * PI.toFloat() / segments } }
    val cosines = remember { FloatArray(segments + 1) { cos(angles[it]) } }
    val sines = remember { FloatArray(segments + 1) { sin(angles[it]) } }
    val sin3 = remember { FloatArray(segments + 1) { sin(angles[it] * 3) } }
    val cos3 = remember { FloatArray(segments + 1) { cos(angles[it] * 3) } }
    val sin5 = remember { FloatArray(segments + 1) { sin(angles[it] * 5) } }
    val cos5 = remember { FloatArray(segments + 1) { cos(angles[it] * 5) } }
    val meshColors = remember(colors) { Array(3) { layer ->
        val tint = colors.getOrElse(layer) { color }
        val next = colors.getOrElse((layer + 1) % 3) { color }
        IntArray((segments + 1) * 2) { vertex ->
            val mix = (sines[vertex / 2] + 1f) * .20f
            lerp(tint, next, mix).copy(alpha = if (vertex % 2 == 0) .78f else .65f).toArgb()
        }
    } }
    val context = LocalContext.current
    val artwork = remember(song.id, song.artworkUri, song.mediaUri) {
        ImageRequest.Builder(context).data(song.artworkUri ?: song.mediaUri).size(700, 700)
            .precision(Precision.INEXACT).crossfade(false).build()
    }
    Box(modifier.fillMaxSize().semantics { contentDescription = "Vinilo de ${song.title}, con espectro de la canción" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val now = clock.longValue
            val elapsed = if (last[0] == 0L) 1f / 60 else ((now - last[0]) / 1_000_000_000f).coerceIn(.001f, .05f)
            last[0] = now
            val frame = spectrum.value
            val fresh = playing && SystemClock.elapsedRealtime() - frame.capturedAt < 180
            for (band in levels.indices) {
                val target = if (fresh) frame.detailBands[band] else 0f
                val speed = 1f - exp(-elapsed / if (target > levels[band]) .007f else .025f)
                levels[band] = if (!playing) 0f else levels[band] + (target - levels[band]) * speed
                amplitudes[band] = levels[band].pow(1.1f)
            }
            val dimension = size.minDimension
            val center = Offset(size.width / 2, size.height / 2)
            val baseline = dimension * .335f
            val pulse = if (fresh) frame.bassPulse else 0f
            var bass = 0f
            for (i in 0 until 8) bass += amplitudes[i] / 8
            val motion = rotation.floatValue * PI.toFloat() / 180
            // Triangle strips avoid re-tessellating three large, changing filled paths every frame.
            for (layer in 0..2) {
                val phase = motion + layer * .85f
                val sp = sin(phase); val cp = cos(phase)
                val sp5 = sin(-motion * 2f + layer * .65f)
                val cp5 = cos(-motion * 2f + layer * .65f)
                val vertices = meshes[layer]
                for (point in 0..segments) {
                    val bin = point.toFloat() / segments * 32
                    val first = bin.toInt() % 32
                    val next = (first + 1) % 32
                    val smoothA = (amplitudes[(first + 31) % 32] + amplitudes[first] * 2 + amplitudes[next]) / 4
                    val smoothB = (amplitudes[first] + amplitudes[next] * 2 + amplitudes[(next + 1) % 32]) / 4
                    val amount = smoothA + (smoothB - smoothA) * (bin - bin.toInt())
                    val broad = (sin3[point] * cp + cos3[point] * sp + 1f) / 2
                    val fold = (sin5[point] * cp5 + cos5[point] * sp5 + 1f) / 2
                    val expansion = .028f * amount + (.055f + .055f * pulse) * broad * (.35f + .65f * bass) + .018f * fold * amount
                    val outer = (baseline + dimension * expansion).coerceAtMost(dimension * .487f)
                    val index = point * 4
                    vertices[index] = center.x + cosines[point] * baseline
                    vertices[index + 1] = center.y + sines[point] * baseline
                    vertices[index + 2] = center.x + cosines[point] * outer
                    vertices[index + 3] = center.y + sines[point] * outer
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawVertices(android.graphics.Canvas.VertexMode.TRIANGLE_STRIP,
                            vertices.size, vertices, 0, null, 0, meshColors[layer], 0, null, 0, 0, meshPaint)
                    }
                } else {
                    fallbackPath.reset()
                    fallbackPath.moveTo(vertices[2], vertices[3])
                    for (point in 1..segments) fallbackPath.lineTo(vertices[point * 4 + 2], vertices[point * 4 + 3])
                    fallbackPath.close()
                    drawPath(fallbackPath, colors.getOrElse(layer) { color }.copy(alpha = .7f))
                }
            }
            drawCircle(Color(0xFF101216), baseline, center)
            drawCircle(color.copy(alpha = .35f), baseline, center, style = Stroke(.7.dp.toPx()))
        }
        Box(Modifier.fillMaxSize(.64f).graphicsLayer { rotationZ = rotation.floatValue }
            .clip(CircleShape).background(Color(0xFF101216)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = color.copy(alpha = .4f), modifier = Modifier.fillMaxSize(.25f))
            AsyncImage(artwork, song.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                for (groove in 0..12) drawCircle(Color.Black.copy(alpha = .09f), radius * (.45f + groove * .042f), style = Stroke(.6.dp.toPx()))
                drawCircle(Color.White.copy(alpha = .22f), radius - .8.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
            Image(painterResource(R.drawable.vinyl_hub), null, Modifier.fillMaxSize(.24f))
        }
    }
}

/** Draw-only sheen on the filled track; the existing controls retain all input handling. */
@Composable
fun PlayerBarShimmer(color: Color, modifier: Modifier = Modifier) {
    val phase = rememberInfiniteTransition(label = "yinPlayerTrack").animateFloat(-.35f, 1.35f,
        infiniteRepeatable(tween(2100, easing = LinearEasing), RepeatMode.Restart), label = "trackHighlight")
    Canvas(modifier) {
        val x = phase.value * size.width
        val width = size.width.coerceAtLeast(1f) * .25f
        drawRect(Brush.linearGradient(listOf(color.copy(alpha = 0f), Color.White.copy(alpha = .95f), color.copy(alpha = 0f)),
            Offset(x - width, 0f), Offset(x + width, 0f)))
    }
}

/** The artwork-colored background blooms on measured bass onsets, with a soft decay. */
@Composable
fun YinBeatAtmosphere(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val envelope = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, lifecycle) {
        if (!playing) envelope.floatValue = 0f
        else lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                val elapsed = if (previous == 0L) 1f / 60 else ((now - previous) / 1_000_000_000f).coerceIn(.001f, .05f)
                previous = now
                val current = MusicSpectrum.frame.value
                val target = if (SystemClock.elapsedRealtime() - current.capturedAt < 180) current.bassPulse else 0f
                val speed = 1f - exp(-elapsed / if (target > envelope.floatValue) .015f else .12f)
                envelope.floatValue += (target - envelope.floatValue) * speed
            }
        }
    }
    Box(modifier.fillMaxSize().graphicsLayer {
        alpha = envelope.floatValue
        scaleX = 1f + .08f * envelope.floatValue
        scaleY = scaleX
    }.drawWithCache {
        val brush = Brush.radialGradient(listOf(color.copy(alpha = .55f), color.copy(alpha = .18f), Color.Transparent),
            center = Offset(size.width * .65f, size.height * .26f), radius = size.width * 1.1f)
        onDrawBehind { drawRect(brush) }
    })
}
