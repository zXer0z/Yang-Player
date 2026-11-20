package com.yang.player.ui.components

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.yang.player.data.ThemeMode
import com.yang.player.model.Song
import com.yang.player.ui.theme.ArtworkPalette
import com.yang.player.ui.theme.BackgroundDark
import com.yang.player.ui.theme.LocalArtworkAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

@Composable
internal fun YouTubeVideoPlayer(
    state: YouTubeVideoState,
    song: Song,
    queue: List<Song>,
    nextSong: Song?,
    playing: Boolean,
    position: StateFlow<Long>?,
    initialPositionMs: Long,
    durationMs: Long,
    palette: ArtworkPalette,
    themeMode: ThemeMode,
    onDismiss: () -> Unit,
    loadRelated: suspend (Song) -> List<Song>,
    onPlaySuggested: (Song) -> Unit,
    onDownload: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val accent = LocalArtworkAccent.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var qualityOpen by remember(state) { mutableStateOf(false) }
    var commentsOpen by rememberSaveable(song.id) { mutableStateOf(false) }
    val onBack = {
        when {
            qualityOpen -> qualityOpen = false
            expanded -> expanded = false
            commentsOpen -> commentsOpen = false
            else -> onDismiss()
        }
    }

    BackHandler(onBack = onBack)
    VideoSystemBars(expanded)
    val view = LocalView.current
    DisposableEffect(view) {
        val keepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = keepScreenOn }
    }
    val density = LocalDensity.current
    val dismissDistance = with(density) { 96.dp.toPx() }
    val minimumFlingDistance = with(density) { 24.dp.toPx() }
    val dismissVelocity = with(density) { 850.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val slideOffset by animateFloatAsState(dragOffset, if (dragging) snap() else spring(), label = "videoDismiss")
    val dragState = rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceAtLeast(0f) }
    val swipeToDismiss = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        enabled = !qualityOpen,
        onDragStarted = { dragging = true },
        onDragStopped = { velocity ->
            val shouldDismiss = dragOffset >= dismissDistance ||
                (dragOffset >= minimumFlingDistance && velocity >= dismissVelocity)
            dragging = false
            dragOffset = 0f
            if (shouldDismiss) onDismiss()
        }
    )
    // Claim touches across the screen so gaps in the player cannot activate the library beneath it.
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent()
        }
    }) {
        ArtworkBlurBackdropContainer(
            palette, themeMode, accent, expanded,
            modifier = Modifier.offset { IntOffset(0, slideOffset.roundToInt()) }
        ) {
            YouTubeWatchScreen(
                state = state,
                song = song,
                queue = queue,
                nextSong = nextSong,
                expanded = expanded,
                playing = playing,
                position = position,
                initialPositionMs = initialPositionMs,
                durationMs = durationMs,
                commentsOpen = commentsOpen,
                onCommentsChange = { commentsOpen = it },
                onBack = onBack,
                onExpand = { expanded = !expanded },
                onQuality = { qualityOpen = true },
                onDownload = onDownload,
                swipeToDismiss = swipeToDismiss,
                loadRelated = loadRelated,
                onPlaySuggested = onPlaySuggested,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeek = onSeek
            )
            if (qualityOpen) {
                VideoQualityPanel(state, palette, themeMode, accent) { qualityOpen = false }
            }
        }
    }
}

@Composable
private fun VideoSystemBars(expanded: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    if (!expanded) return
    DisposableEffect(window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val bars = WindowInsetsCompat.Type.systemBars()
        val previousBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(bars)
        onDispose {
            controller.show(bars)
            controller.systemBarsBehavior = previousBehavior
        }
    }
}

@Composable
private fun ArtworkBlurBackdropContainer(
    palette: ArtworkPalette,
    themeMode: ThemeMode,
    accent: Color,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier.fillMaxSize().background(if (expanded) Color.Black else BackgroundDark)) {
        if (!expanded) {
            ArtworkBlurBackdrop(palette, themeMode, accent, modifier = Modifier.matchParentSize())
        }
        content()
    }
}

@Composable
private fun YouTubeWatchScreen(
    state: YouTubeVideoState,
    song: Song,
    queue: List<Song>,
    nextSong: Song?,
    expanded: Boolean,
    playing: Boolean,
    position: StateFlow<Long>?,
    initialPositionMs: Long,
    durationMs: Long,
    commentsOpen: Boolean,
    onCommentsChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onExpand: () -> Unit,
    onQuality: () -> Unit,
    onDownload: () -> Unit,
    swipeToDismiss: Modifier,
    loadRelated: suspend (Song) -> List<Song>,
    onPlaySuggested: (Song) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val accent = LocalArtworkAccent.current
    val playbackRequested = playing || state.syncingAudio
    var controlsVisible by remember(state, expanded) { mutableStateOf(true) }
    var seeking by remember(state, expanded) { mutableStateOf(false) }
    var interaction by remember(state, expanded) { mutableIntStateOf(0) }
    LaunchedEffect(playing) { if (!playing) controlsVisible = true }
    LaunchedEffect(playing, controlsVisible, seeking, interaction) {
        if (playing && controlsVisible && !seeking) {
            delay(3_500)
            controlsVisible = false
        }
    }
    val comments = rememberVideoComments(song.mediaUri, commentsOpen)
    var related by remember(song.mediaUri) { mutableStateOf(emptyList<Song>()) }
    var relatedLoading by remember(song.mediaUri) { mutableStateOf(true) }
    var relatedFailed by remember(song.mediaUri) { mutableStateOf(false) }
    var relatedRequest by remember(song.mediaUri) { mutableIntStateOf(0) }
    val latestLoader by rememberUpdatedState(loadRelated)
    LaunchedEffect(song.mediaUri, relatedRequest) {
        relatedLoading = true
        relatedFailed = false
        try {
            related = latestLoader(song)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            relatedFailed = true
        } finally {
            relatedLoading = false
        }
    }
    val suggestions = remember(song.mediaUri, nextSong, related, queue) {
        val candidates = if (related.isEmpty()) queue.filter { it.isOnline }.take(20) else related
        (listOfNotNull(nextSong?.takeIf { it.isOnline }) + candidates)
            .filter { it.mediaUri != song.mediaUri }.distinctBy { it.id }
    }
    val suggestionsScroll = key(song.id) { rememberLazyListState() }
    val commentsScroll = key(song.id) { rememberLazyListState() }

    val frameContent: @Composable (Modifier) -> Unit = { frame ->
        Box(frame.background(Color.Black)) {
            YouTubeVideoSurface(state, Modifier.matchParentSize())
            Box(
                Modifier.matchParentSize().then(swipeToDismiss).clickable(
                    indication = null,
                    interactionSource = null,
                    onClickLabel = if (controlsVisible) "Ocultar controles" else "Mostrar controles"
                ) { controlsVisible = !controlsVisible }
            )
            AnimatedVisibility(controlsVisible, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = .26f))
                        .then(if (expanded) Modifier.safeDrawingPadding() else Modifier)
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VideoOverlayButton(Icons.Rounded.SkipPrevious, "Anterior") { interaction++; onPrevious() }
                        VideoOverlayButton(
                            if (playbackRequested) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playbackRequested) "Pausar" else "Reproducir",
                            primary = true
                        ) { interaction++; onPlayPause() }
                        VideoOverlayButton(Icons.Rounded.SkipNext, "Siguiente") { interaction++; onNext() }
                    }
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(if (expanded) 12.dp else 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (expanded && state.qualities.isNotEmpty()) {
                            VideoOverlayButton(Icons.Rounded.Tune, "Calidad de video", onClick = onQuality)
                        }
                        VideoOverlayButton(
                            if (expanded) Icons.Rounded.CloseFullscreen else Icons.Rounded.Fullscreen,
                            if (expanded) "Salir de pantalla completa" else "Pantalla completa",
                            onClick = onExpand
                        )
                    }
                    key(state) {
                        VideoTimeline(
                            position, initialPositionMs, durationMs, accent, onSeek, { seeking = it },
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f))))
                                .padding(horizontal = if (expanded) 24.dp else 12.dp)
                                .padding(bottom = if (expanded) 12.dp else 8.dp)
                        )
                    }
                }
            }
            if (expanded && state.error != null) {
                Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 78.dp)) {
                    VideoFeedStatus(state.error, accent, action = "Reintentar", onAction = state::retry)
                }
            }
        }
    }

    if (expanded) {
        frameContent(Modifier.fillMaxSize())
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val sideBySide = maxWidth > maxHeight * 1.27f
        val frameHeight = (maxWidth / state.aspectRatio.coerceIn(.45f, 3f)).coerceAtMost(maxHeight * .48f)
        val wideFrameHeight = (maxWidth * (1.4f / 2.4f) / state.aspectRatio.coerceAtLeast(.45f))
            .coerceAtMost(maxHeight * .64f)
        val panel: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp)) {
                    Text(
                        song.title, color = Color.White, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist, color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp)
                    )
                }
                VideoFeedTabs(commentsOpen, onCommentsChange, accent, Modifier.padding(horizontal = 18.dp))
                LazyColumn(
                    state = if (commentsOpen) commentsScroll else suggestionsScroll,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (commentsOpen) {
                        videoComments(comments, accent)
                    } else {
                        items(suggestions, key = { it.id }) { suggestion ->
                            SuggestedVideoRow(suggestion, suggestion.id == nextSong?.id, accent) { onPlaySuggested(suggestion) }
                        }
                        item {
                            when {
                                relatedLoading -> VideoFeedStatus("Buscando videos relacionados…", accent, loading = true)
                                relatedFailed -> VideoFeedStatus("No se pudieron cargar los videos relacionados.", accent, action = "Reintentar") { relatedRequest++ }
                                suggestions.isEmpty() -> VideoFeedStatus("No hay sugerencias para este video.", accent)
                            }
                        }
                    }
                }
            }
        }
        val main: @Composable (Dp) -> Unit = { height ->
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().then(swipeToDismiss).padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VideoPill(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", null, accent, onBack)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoPill(Icons.Rounded.Download, "Descargar", null, accent, onDownload)
                        if (state.qualities.isNotEmpty()) {
                            VideoPill(Icons.Rounded.Tune, "Calidad de video", state.quality?.label ?: "Calidad", accent, onQuality)
                        }
                    }
                }
                frameContent(
                    Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(height)
                        .clip(RoundedCornerShape(18.dp))
                )
                if (state.error != null) {
                    VideoFeedStatus(state.error, accent, action = "Reintentar", onAction = state::retry)
                } else {
                    state.notice?.let {
                        Text(it, Modifier.padding(horizontal = 18.dp, vertical = 3.dp), color = accent, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (sideBySide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1.4f)) {
                    main(wideFrameHeight)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { panel() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                main(frameHeight)
                Box(Modifier.weight(1f).fillMaxWidth()) { panel() }
            }
        }
    }
}

@Composable
private fun VideoFeedTabs(
    commentsOpen: Boolean,
    onChange: (Boolean) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().clip(CircleShape).background(Color.Black.copy(alpha = .26f))
            .border(1.dp, Color.White.copy(alpha = .09f), CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(false to "Sugerencias", true to "Comentarios").forEach { (comments, label) ->
            val selected = comments == commentsOpen
            Row(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape)
                    .background(if (selected) accent.copy(alpha = .17f) else Color.Transparent)
                    .selectable(selected, role = Role.Tab) { onChange(comments) }.padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tint = if (selected) accent else Color.White.copy(alpha = .74f)
                Icon(
                    if (comments) Icons.Rounded.ChatBubbleOutline else Icons.Rounded.OndemandVideo,
                    contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(label, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SuggestedVideoRow(song: Song, isNext: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(
                Brush.horizontalGradient(listOf(accent.copy(alpha = if (isNext) .15f else .07f), Color.White.copy(alpha = .04f)))
            )
            .border(1.dp, Color.White.copy(alpha = .08f), shape)
            .clickable(role = Role.Button, onClick = onClick).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 112.dp, height = 63.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .25f))) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = accent, modifier = Modifier.align(Alignment.Center))
            AsyncImage(song.artworkUri, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            if (song.durationMs > 0) {
                Text(
                    song.formattedDuration, color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = .78f)).padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isNext) Text("Siguiente en cola", color = accent, style = MaterialTheme.typography.labelSmall)
            Text(song.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun VideoFeedStatus(
    label: String?,
    accent: Color,
    loading: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
        if (label != null) Text(label, Modifier.weight(1f), color = Color.White.copy(alpha = .77f), style = MaterialTheme.typography.bodySmall)
        if (action != null) TextButton(onClick = onAction, colors = ButtonDefaults.textButtonColors(contentColor = accent)) { Text(action) }
    }
}

@Composable
private fun VideoPill(icon: ImageVector, label: String, value: String?, accent: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = accent),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        Row(
            Modifier.clip(CircleShape).background(accent.copy(alpha = .12f))
                .border(1.dp, accent.copy(alpha = .18f), CircleShape)
                .padding(horizontal = if (value == null) 10.dp else 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
            if (value != null) Text(value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun VideoOverlayButton(icon: ImageVector, label: String, primary: Boolean = false, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(if (primary) 56.dp else 48.dp)
            .background(Color.Black.copy(alpha = if (primary) .72f else .50f), CircleShape)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(if (primary) 32.dp else 23.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoTimeline(
    position: StateFlow<Long>?,
    fallbackMs: Long,
    durationMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    onSeeking: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMs = position?.collectAsState()?.value ?: fallbackMs
    var draft by remember { mutableStateOf<Float?>(null) }
    val fraction = draft ?: if (durationMs > 0L) (currentMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val displayMs = draft?.let { (it * durationMs).toLong() } ?: currentMs
    Column(modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            enabled = durationMs > 0,
            onValueChange = {
                onSeeking(true)
                draft = it
            },
            onValueChangeFinished = {
                draft?.let { onSeek((it * durationMs).toLong()) }
                draft = null
                onSeeking(false)
            },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            thumb = { Box(Modifier.size(10.dp).background(accent, CircleShape)) },
            track = {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(accent))
                }
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(videoTime(displayMs), color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall)
            Text(videoTime(durationMs), color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun videoTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return "${seconds / 60L}:${(seconds % 60L).toString().padStart(2, '0')}"
}

@Composable
private fun VideoQualityPanel(
    state: YouTubeVideoState,
    palette: ArtworkPalette,
    themeMode: ThemeMode,
    accent: Color,
    onDismiss: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.matchParentSize().background(Color.Black.copy(alpha = .65f))
                .clickable(indication = null, interactionSource = null, onClickLabel = "Cerrar calidad", onClick = onDismiss)
        )
        val shape = RoundedCornerShape(24.dp)
        Box(
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp)
                .widthIn(max = 460.dp).fillMaxWidth().heightIn(max = maxHeight * .82f)
                .clip(shape).background(BackgroundDark).border(1.dp, accent.copy(alpha = .23f), shape)
        ) {
            ArtworkBlurBackdrop(palette, themeMode, accent, Modifier.matchParentSize(), dim = .47f)
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = accent, modifier = Modifier.padding(horizontal = 8.dp))
                    Text("Calidad del video", Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Cerrar", tint = Color.White) }
                }
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.qualities) { quality ->
                        val selected = quality == state.quality
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (selected) accent.copy(alpha = .18f) else Color.White.copy(alpha = .055f))
                                .selectable(selected, role = Role.RadioButton) { state.select(quality); onDismiss() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${quality.resolution}p", Modifier.weight(1f), color = if (selected) accent else Color.White)
                            if (quality.fps > 0) {
                                Text("${quality.fps} fps", color = Color.White.copy(alpha = .73f), style = MaterialTheme.typography.labelMedium)
                            }
                            Box(Modifier.padding(start = 8.dp).size(24.dp), contentAlignment = Alignment.Center) {
                                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = accent)
                            }
                        }
                    }
                }
            }
        }
    }
}
