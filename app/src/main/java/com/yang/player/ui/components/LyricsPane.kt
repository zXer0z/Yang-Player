package com.yang.player.ui.components

import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.yang.player.lyrics.LyricLine
import com.yang.player.lyrics.LyricEffects
import com.yang.player.lyrics.LyricsDocument
import com.yang.player.lyrics.LyricsRepository
import com.yang.player.model.Song
import com.yang.player.ui.theme.LocalArtworkAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private data class LyricClockAnchor(val position: Long, val capturedAt: Long)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LyricsPane(
    song: Song,
    positionMs: Long,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { LyricsRepository(context) }
    val completionRepository = remember { com.yang.player.data.OnlineMetadata.get(context) }
    val completionRevision by completionRepository.changes.collectAsState()
    var onlineSearch by remember(song.mediaUri) { mutableStateOf(false) }
    if (onlineSearch) MetadataCompletionSheet(song, onDismiss = { onlineSearch = false },
        initialKind = com.yang.player.data.CompletionKind.LYRICS, onSaved = { onlineSearch = false })
    val scope = rememberCoroutineScope()
    val accent = LocalArtworkAccent.current
    var document by remember(song.id) { mutableStateOf<LyricsDocument?>(null) }
    var loading by remember(song.id) { mutableStateOf(true) }
    var error by remember(song.id) { mutableStateOf<String?>(null) }
    var loadVersion by remember(song.id) { mutableStateOf(0) }
    val currentSong by rememberUpdatedState(song)
    var pickerSong by remember { mutableStateOf<Song?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val selectedSong = pickerSong ?: currentSong
            scope.launch {
                try {
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    repository.importFile(selectedSong, uri)
                    if (currentSong.id == selectedSong.id) loadVersion++
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (exception: Exception) {
                    if (currentSong.id == selectedSong.id) error = exception.message ?: "No se pudo leer el archivo"
                }
            }
        }
    }
    LaunchedEffect(song.id, song.mediaUri, loadVersion, completionRevision) {
        loading = true
        error = null
        try {
            document = repository.load(song)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "No se pudieron leer las letras de esta canción" }
        finally { loading = false }
    }
    BackHandler(onBack = onClose)
    val anchor by rememberUpdatedState(remember(positionMs, isPlaying) {
        LyricClockAnchor(positionMs, SystemClock.elapsedRealtime())
    })
    val clock = remember(song.id) { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying) { if (!isPlaying) clock.longValue = positionMs }
    LaunchedEffect(song.id, isPlaying, document?.enhanced) {
        if (isPlaying) {
            while (isActive) withFrameNanos {
                val sample = anchor
                // Bound extrapolation so buffering cannot run lyrics far ahead.
                clock.longValue = sample.position + (SystemClock.elapsedRealtime() - sample.capturedAt).coerceIn(0, 500)
            }
        }
    }
    val activeBreak by remember(document) { derivedStateOf { document?.instrumentalBreakAt(clock.longValue) } }
    val activeIndex by remember(document) { derivedStateOf {
        if (document?.instrumentalBreakAt(clock.longValue) != null) -1 else document?.activeIndex(clock.longValue) ?: -1
    } }
    val list = rememberLazyListState()
    val dragging by list.interactionSource.collectIsDraggedAsState()
    var follow by remember(song.id) { mutableStateOf(true) }
    LaunchedEffect(dragging) { if (dragging) follow = false }
    LaunchedEffect(activeIndex, activeBreak, follow, document) {
        val target = activeBreak?.nextLineIndex ?: activeIndex
        if (follow && target >= 0) {
            list.animateScrollToItem(target, -(list.layoutInfo.viewportEndOffset * 0.28f).toInt())
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 10.dp, bottom = 8.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp)
            .clip(CircleShape).background(Color.White.copy(alpha = 0.38f)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver al reproductor", tint = Color.White) }
            Text(
                text = when { document?.enhanced == true -> "Palabra por palabra"; document?.synchronized == true -> "Letras sincronizadas"; else -> "Letras" },
                color = Color.White.copy(alpha = 0.70f), fontSize = 13.sp,
                modifier = Modifier.weight(1f), maxLines = 1
            )
            IconButton(onClick = { pickerSong = song; picker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Rounded.FolderOpen, "Elegir archivo de letras", tint = Color.White.copy(alpha = 0.75f))
            }
        }
        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent, modifier = Modifier.size(30.dp))
            }
        } else if (document == null) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.Center) {
                Text("Esta canción no tiene letras disponibles", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(error ?: "Puedes elegir un archivo LRC, TTML, SRT o de texto.", color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp)
                if (!song.isOnline) TextButton(onClick = { onlineSearch = true }) { Text("Buscar letras en Internet", color = accent) }
                TextButton(onClick = { pickerSong = song; picker.launch(arrayOf("*/*")) }) { Text("Elegir archivo", color = accent) }
            }
        } else {
            val lyrics = document!!
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 140.dp)
            ) {
                itemsIndexed(lyrics.lines, key = { index, _ -> index }) { index, line ->
                    AnimatedVisibility(
                        visible = activeBreak?.nextLineIndex == index,
                        enter = fadeIn(tween(600)) + expandVertically(tween(550)),
                        exit = fadeOut(tween(600)) + shrinkVertically(tween(550))
                    ) {
                        // Keep the outgoing content alive during its fade-out.
                        lyrics.instrumentalBreaks.firstOrNull { it.nextLineIndex == index }?.let { pause ->
                            InstrumentalDots(pause, clock, accent)
                        }
                    }
                    LyricLineText(
                        line = line,
                        active = index == activeIndex,
                        past = activeIndex > index,
                        clock = clock,
                        isPlaying = isPlaying,
                        accent = accent,
                        onClick = {
                            line.startMs?.let { follow = true; onSeek(it) }
                        }
                    )
                }
            }
            if (!follow && lyrics.synchronized) {
                TextButton(onClick = { follow = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Seguir la canción", color = accent)
                }
            }
            if (error != null) Text(error!!, color = accent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClickLabel = "Volver al reproductor", onClick = onClose).padding(vertical = 8.dp)) {
                Text(song.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = Color.White) }
            IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pausar" else "Reproducir", tint = Color.White) }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Siguiente", tint = Color.White) }
        }
    }
}

@Composable
private fun LyricLineText(
    line: LyricLine,
    active: Boolean,
    past: Boolean,
    clock: State<Long>,
    isPlaying: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val density = LocalDensity.current
    // Keep the full paragraph's shaping and wrapping, but paint only the held
    // segment. Shadows belong to its glyph span, never a rectangular clip.
    val glowMasks = remember(line, active, layout?.size, layout?.layoutInput?.constraints, measurer, density, accent) {
        val original = layout
        if (!active || original == null) emptyList() else line.segments.mapNotNull { segment ->
            val range = LyricEffects.heldRange(line, segment) ?: return@mapNotNull null
            val text = buildAnnotatedString {
                append(line.text)
                addStyle(SpanStyle(color = Color.Transparent), 0, length)
                addStyle(SpanStyle(
                    color = Color.White.copy(alpha = 0.20f),
                    shadow = Shadow(accent.copy(alpha = 0.45f), Offset.Zero, with(density) { 5.dp.toPx() })
                ), range.first, range.last + 1)
            }
            segment to measurer.measure(
                text = text,
                style = original.layoutInput.style.copy(color = Color.Transparent, shadow = null),
                constraints = original.layoutInput.constraints,
                layoutDirection = original.layoutInput.layoutDirection
            )
        }
    }
    val baseColor by animateColorAsState(
        when { active && line.segments.isEmpty() -> Color.White; active -> Color.White.copy(alpha = 0.30f); past -> Color.White.copy(alpha = 0.42f); else -> Color.White.copy(alpha = 0.25f) },
        tween(250), label = "lyricLineColor"
    )
    Text(
        text = line.text,
        color = baseColor,
        fontSize = 28.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        onTextLayout = { layout = it },
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = line.startMs != null, onClick = onClick)
            .padding(vertical = 20.dp)
            .drawWithContent {
                drawContent()
                val measured = layout
                if (active && line.segments.isNotEmpty() && measured != null) {
                    val time = clock.value
                    val path = Path()
                    line.segments.forEach { segment ->
                        val duration = (segment.endMs - segment.startMs).coerceAtLeast(1)
                        val sustained = duration >= 900
                        val revealDuration = if (sustained) minOf(duration, 650L) else duration
                        val fraction = ((time - segment.startMs).toFloat() / revealDuration).coerceIn(0f, 1f)
                        val chars = (segment.until - segment.from) * fraction
                        for (index in segment.from until segment.until) {
                            val fill = (chars - (index - segment.from)).coerceIn(0f, 1f)
                            if (fill > 0) {
                                val bounds = measured.getBoundingBox(index)
                                val rtl = measured.getBidiRunDirection(index) == ResolvedTextDirection.Rtl
                                path.addRect(if (rtl) bounds.copy(left = bounds.right - bounds.width * fill) else bounds.copy(right = bounds.left + bounds.width * fill))
                            }
                        }
                    }
                    clipPath(path) { drawText(measured, color = Color.White) }
                    if (isPlaying) glowMasks.forEach { (segment, mask) ->
                        val strength = LyricEffects.heldGlow(segment, time)
                        if (strength > 0) {
                            // Do not override color or shadow here: that would
                            // recolor the transparent spans and light the whole line.
                            drawText(mask, alpha = strength)
                        }
                    }
                }
            }
    )
}
