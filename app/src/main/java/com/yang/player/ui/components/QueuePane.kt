package com.yang.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.yang.player.model.Song
import com.yang.player.ui.theme.LocalArtworkAccent
import kotlinx.coroutines.isActive

private data class QueueRow(val key: Long, val song: Song)

@Composable
fun QueuePane(
    queue: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onSelect: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val accent = LocalArtworkAccent.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val list = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    var rows by remember { mutableStateOf(queue.mapIndexed { index, song -> QueueRow(index.toLong(), song) }) }
    var nextKey by remember { mutableLongStateOf(queue.size.toLong()) }
    var draggedKey by remember { mutableStateOf<Long?>(null) }
    var playingKeyDuringDrag by remember { mutableStateOf<Long?>(null) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    val latestIndex by rememberUpdatedState(currentIndex)
    val move by rememberUpdatedState(onMove)

    LaunchedEffect(queue) {
        if (rows.map { it.song } != queue) {
            draggedKey = null
            val available = rows.groupBy { it.song.id to it.song.mediaUri }
                .mapValues { ArrayDeque(it.value) }
            rows = queue.map { song ->
                available[song.id to song.mediaUri]?.removeFirstOrNull() ?: QueueRow(nextKey++, song)
            }
        }
    }
    BackHandler(onBack = onClose)

    fun moveToHoveredRow() {
        val key = draggedKey ?: return
        val from = rows.indexOfFirst { it.key == key }
        if (from < 0) return
        val layout = list.layoutInfo
        val center = dragCenter.coerceIn(layout.viewportStartOffset.toFloat() + 1, layout.viewportEndOffset.toFloat().coerceAtLeast(2f) - 1)
        val hovered = layout.visibleItemsInfo.firstOrNull {
            it.key != key && center >= it.offset && center < it.offset + it.size
        } ?: return
        val to = rows.indexOfFirst { it.key == hovered.key }
        // Wait for the new layout after each swap; stale offsets must not undo it.
        if (to < 0 || to == from || hovered.index != to) return
        val dragged = layout.visibleItemsInfo.firstOrNull { it.key == key }
        if (dragged != null && dragged.index != from) return
        rows = rows.toMutableList().apply { add(to, removeAt(from)) }
        move(from, to)
    }

    LaunchedEffect(draggedKey) {
        if (draggedKey != null) {
            val edge = with(density) { 64.dp.toPx() }
            val maximumStep = with(density) { 12.dp.toPx() }
            var previousFrame = 0L
            while (isActive) {
                val frame = withFrameNanos { it }
                val delta = if (previousFrame == 0L) 1f else ((frame - previousFrame) / 16_666_667f).coerceIn(0.2f, 2f)
                previousFrame = frame
                val info = list.layoutInfo
                val step = when {
                    dragCenter < info.viewportStartOffset + edge -> -((info.viewportStartOffset + edge - dragCenter) / edge).coerceIn(0f, 1f) * maximumStep
                    dragCenter > info.viewportEndOffset - edge -> ((dragCenter - info.viewportEndOffset + edge) / edge).coerceIn(0f, 1f) * maximumStep
                    else -> 0f
                }
                if (step != 0f) list.scrollBy(step * delta)
                moveToHoveredRow()
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 10.dp, bottom = 8.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp)
            .clip(CircleShape).background(Color.White.copy(alpha = 0.38f)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver al reproductor", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Cola de reproducción", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("${rows.size} canciones · Mantén y arrastra el asa", color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp)
            }
        }
        if (rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No hay canciones en la cola", color = Color.White.copy(alpha = 0.65f))
            }
        } else {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp)
            ) {
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    val dragging = row.key == draggedKey
                    val current = if (draggedKey != null) row.key == playingKeyDuringDrag else index == currentIndex
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragging) list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.key }
                                    ?.let { dragCenter - (it.offset + it.size / 2f) } ?: 0f else 0f
                                scaleX = if (dragging) 1.02f else 1f
                                scaleY = if (dragging) 1.02f else 1f
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (dragging) Color(0xED1A1C22) else Color.Transparent)
                            .clickable(enabled = draggedKey == null) { onSelect(rows.indexOfFirst { it.key == row.key }) }
                            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.20f), modifier = Modifier.size(20.dp))
                            AsyncImage(row.song.artworkUri ?: row.song.mediaUri, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (current) {
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.50f)))
                                NowPlayingIndicator(isPlaying, accent, Modifier.size(24.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.song.title, color = if (current) accent else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.song.artist, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(48.dp)
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction("Subir en la cola") {
                                        val from = rows.indexOfFirst { it.key == row.key }
                                        if (from > 0) { move(from, from - 1); true } else false
                                    },
                                    CustomAccessibilityAction("Bajar en la cola") {
                                        val from = rows.indexOfFirst { it.key == row.key }
                                        if (from >= 0 && from < rows.lastIndex) { move(from, from + 1); true } else false
                                    }
                                )
                            }
                            .pointerInput(row.key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.key }
                                        if (visible != null) {
                                            playingKeyDuringDrag = rows.getOrNull(latestIndex)?.key
                                            dragCenter = visible.offset + visible.size / 2f
                                            draggedKey = row.key
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        if (draggedKey == row.key) { dragCenter += delta.y; moveToHoveredRow() }
                                    },
                                    onDragEnd = { draggedKey = null },
                                    onDragCancel = { draggedKey = null }
                                )
                            }, contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.DragHandle, "Mantén y arrastra para ordenar", tint = if (dragging) accent else Color.White.copy(alpha = 0.40f))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onClose).padding(vertical = 8.dp)) {
                Text(queue.getOrNull(currentIndex)?.title ?: "Volver al reproductor", color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(queue.getOrNull(currentIndex)?.artist.orEmpty(), color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, maxLines = 1)
            }
            IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pausar" else "Reproducir", tint = Color.White) }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Siguiente", tint = Color.White) }
        }
    }
}
