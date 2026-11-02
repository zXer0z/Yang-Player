package com.yang.player.ui.screens

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.yang.player.model.Song
import com.yang.player.ui.components.SongOptionsMenuModal
import com.yang.player.ui.theme.SurfaceCard
import com.yang.player.ui.theme.ArtworkPalette
import com.yang.player.ui.theme.BackgroundDark
import com.yang.player.ui.components.ArtworkAtmosphere
import com.yang.player.ui.components.LyricsPane
import com.yang.player.ui.components.QueuePane
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlin.math.abs
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerModal(
    song: Song?,
    queue: List<Song> = emptyList(),
    currentIndex: Int = -1,
    nextSong: Song? = null,
    previousSong: Song? = null,
    isPlaying: Boolean,
    progress: Float,
    currentPositionMs: Long,
    durationMs: Long,
    isFavorite: Boolean = false,
    isShuffle: Boolean = false,
    repeatMode: Int = 0,
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {},
    palette: ArtworkPalette = ArtworkPalette(),
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onQueueSelect: (Int) -> Unit = {},
    onQueueMove: (Int, Int) -> Unit = { _, _ -> },
    onToggleFavorite: (Song) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {}
) {
    if (song == null) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // Prepare stable queue and index for the pager
    val effectiveQueue = remember(queue, song) {
        if (queue.isNotEmpty()) queue else listOf(song)
    }
    val effectiveIndex = remember(effectiveQueue, currentIndex, song.id) {
        if (currentIndex in effectiveQueue.indices && effectiveQueue[currentIndex].id == song.id) {
            currentIndex
        } else {
            val idx = effectiveQueue.indexOfFirst { it.id == song.id }
            if (idx >= 0) idx else 0
        }
    }

    val isLoopable = effectiveQueue.size > 1
    val loopMultiplier = 1000
    val totalPages = if (isLoopable) effectiveQueue.size * loopMultiplier else 1
    val initialPage = remember(effectiveQueue.size) {
        if (isLoopable) (loopMultiplier / 2) * effectiveQueue.size + effectiveIndex else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { totalPages }
    )

    // Sync pager when song/index changes externally (e.g. from buttons or auto-next)
    LaunchedEffect(effectiveIndex, song.id, effectiveQueue.size) {
        if (isLoopable) {
            val currentMod = pagerState.currentPage % effectiveQueue.size
            if (currentMod != effectiveIndex && !pagerState.isScrollInProgress) {
                val diff = effectiveIndex - currentMod
                val targetPage = pagerState.currentPage + diff
                if (abs(diff) <= 2) {
                    pagerState.animateScrollToPage(targetPage, animationSpec = tween(260, easing = FastOutSlowInEasing))
                } else {
                    pagerState.scrollToPage(targetPage)
                }
            }
        }
    }

    // When the user swipes and the pager settles on a page, notify playback
    LaunchedEffect(pagerState.settledPage) {
        if (isLoopable && !showQueue && !showLyrics) {
            val settledIndex = pagerState.settledPage % effectiveQueue.size
            if (settledIndex != effectiveIndex && settledIndex in effectiveQueue.indices) {
                onQueueSelect(settledIndex)
            }
        }
    }

    val handleNext = onNext
    val handlePrevious = onPrevious

    // Hardware volume control via AudioManager
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f) }
    var currentVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = BackgroundDark,
        scrimColor = Color.Black.copy(alpha = 0.30f),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        // Force white status bar & navigation bar icons inside the full-screen modal sheet
        val view = LocalView.current
        SideEffect {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window
            if (dialogWindow != null) {
                val controller = WindowCompat.getInsetsController(dialogWindow, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            ArtworkAtmosphere(palette, moving = true)

            if (showLyrics) {
                LyricsPane(
                    song = song,
                    positionMs = currentPositionMs,
                    isPlaying = isPlaying,
                    onClose = { showLyrics = false },
                    onSeek = onSeek,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext
                )
            } else if (showQueue) {
                QueuePane(
                    queue = queue,
                    currentIndex = currentIndex,
                    isPlaying = isPlaying,
                    onClose = { showQueue = false },
                    onSelect = onQueueSelect,
                    onMove = onQueueMove,
                    onPlayPause = onPlayPause,
                    onNext = onNext
                )
            } else
            // Read the usable height AFTER system insets. Artwork must leave room for controls.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp)
                    .padding(top = 10.dp, bottom = 14.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                PlayerLayout(
                    viewportHeight = maxHeight,
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    handle = {
                    // Top sheet grabber pill
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.38f))
                    )

                    },
                    artwork = {
                        if (isLoopable) {
                            HorizontalPager(
                                state = pagerState,
                                key = { page -> "${effectiveQueue[page % effectiveQueue.size].id}_$page" },
                                modifier = Modifier.fillMaxSize(),
                                pageSpacing = 16.dp,
                                beyondViewportPageCount = 1
                            ) { page ->
                                val pageSong = effectiveQueue[page % effectiveQueue.size]

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            // Observe the gesture in the layer phase, not in composition.
                                            val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                                            val scale = 1f - (distance * 0.08f).coerceIn(0f, 0.08f)
                                            scaleX = scale
                                            scaleY = scale
                                            alpha = 1f - (distance * 0.35f).coerceIn(0f, 0.35f)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    ArtworkCard(song = pageSong)
                                }
                            }
                        } else {
                            ArtworkCard(song = song)
                        }
                    },
                    controls = {
                    // 2. Metadata Row: Title + Artist on left, circular translucent Favorite & More buttons on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Text follows exactly the same position as the artwork; there is no second transition.
                        val titlePage by remember(pagerState) { derivedStateOf { pagerState.currentPage } }
                        Box(Modifier.weight(1f).padding(end = 14.dp).heightIn(min = 56.dp).clipToBounds(),
                            contentAlignment = Alignment.CenterStart) {
                            val pages = if (isLoopable) (titlePage - 1..titlePage + 1).filter { it in 0 until totalPages } else listOf(0)
                            pages.forEach { page -> key(page) {
                                val targetSong = effectiveQueue[page % effectiveQueue.size]
                                Column(Modifier.fillMaxWidth().graphicsLayer {
                                    val offset = if (isLoopable) (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction else 0f
                                    translationX = offset * size.width
                                    alpha = (1f - abs(offset)).coerceIn(0f, 1f)
                                }) {
                                    Text(targetSong.title, color = Color.White, fontSize = 21.sp,
                                        fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, maxLines = 1,
                                        modifier = Modifier.basicMarquee(iterations = if (!pagerState.isScrollInProgress && page == pagerState.settledPage) 3 else 0))
                                    Spacer(Modifier.height(2.dp))
                                    Text(targetSong.artist, color = Color.White.copy(alpha = 0.68f), fontSize = 18.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            CircleGlassButton(onClick = { onToggleFavorite(song) }) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Favorito",
                                    tint = if (isFavorite) Color(0xFFFF2D55) else Color.White.copy(alpha = 0.90f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            CircleGlassButton(onClick = { showOptionsMenu = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = "Opciones",
                                    tint = Color.White.copy(alpha = 0.90f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Smooth Pill Progress Bar (expands on scrub, no protruding thumb) + Timestamps
                    Column(modifier = Modifier.fillMaxWidth()) {
                        var isSeeking by remember { mutableStateOf(false) }
                        var seekFraction by remember { mutableFloatStateOf(0f) }
                        val currentFraction = (if (isSeeking) seekFraction else progress).coerceIn(0f, 1f)
                        val barHeight by animateDpAsState(
                            targetValue = if (isSeeking) 11.dp else 6.dp,
                            animationSpec = tween(160),
                            label = "seekBarHeight"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .pointerInput(durationMs) {
                                    detectTapGestures { offset ->
                                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                                        onSeek((frac * durationMs).toLong())
                                    }
                                }
                                .pointerInput(durationMs) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { offset ->
                                            isSeeking = true
                                            seekFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                        },
                                        onDragEnd = {
                                            onSeek((seekFraction * durationMs).toLong())
                                            isSeeking = false
                                        },
                                        onDragCancel = { isSeeking = false },
                                        onHorizontalDrag = { change, _ ->
                                            change.consume()
                                            seekFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                        }
                                    )
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(barHeight)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.24f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(currentFraction)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = if (isSeeking) 0.98f else 0.82f))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Timestamps row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val curSec = if (isSeeking) {
                                (seekFraction * durationMs / 1000).toLong()
                            } else {
                                currentPositionMs / 1000
                            }
                            val remSec = (durationMs / 1000 - curSec).coerceAtLeast(0)

                            Text(
                                text = String.format("%d:%02d", curSec / 60, curSec % 60),
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = song.audioQualityBadge,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                textAlign = TextAlign.Center,
                                color = Color.White.copy(alpha = 0.68f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format("-%d:%02d", remSec / 60, remSec % 60),
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Playback Controls (Rewind, Play/Pause, FastForward)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { handlePrevious() },
                            modifier = Modifier.size(68.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FastRewind,
                                contentDescription = "Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(50.dp)
                            )
                        }

                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(80.dp).semantics {
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir"
                            }
                        ) {
                            if (isPlaying) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 11.dp, height = 38.dp)
                                            .clip(RoundedCornerShape(3.5.dp))
                                            .background(Color.White)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(width = 11.dp, height = 38.dp)
                                            .clip(RoundedCornerShape(3.5.dp))
                                            .background(Color.White)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Reproducir",
                                    tint = Color.White,
                                    modifier = Modifier.size(66.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { handleNext() },
                            modifier = Modifier.size(68.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FastForward,
                                contentDescription = "Siguiente",
                                tint = Color.White,
                                modifier = Modifier.size(50.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. Smooth Pill Volume Slider (expands while dragging, no thumb)
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VolumeDown,
                            contentDescription = "Bajar volumen",
                            tint = Color.White.copy(alpha = 0.58f),
                            modifier = Modifier.size(20.dp)
                        )

                        var volFraction by remember(currentVolume) { mutableFloatStateOf(currentVolume) }
                        var isVolDragging by remember { mutableStateOf(false) }
                        val volBarHeight by animateDpAsState(
                            targetValue = if (isVolDragging) 11.dp else 6.dp,
                            animationSpec = tween(160),
                            label = "volBarHeight"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .padding(horizontal = 12.dp)
                                .pointerInput(maxVolume) {
                                    detectTapGestures { offset ->
                                        volFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                        audioManager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            (volFraction * maxVolume).toInt(),
                                            0
                                        )
                                    }
                                }
                                .pointerInput(maxVolume) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { isVolDragging = true },
                                        onDragEnd = { isVolDragging = false },
                                        onDragCancel = { isVolDragging = false },
                                        onHorizontalDrag = { change, _ ->
                                            change.consume()
                                            volFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                            audioManager.setStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                (volFraction * maxVolume).toInt(),
                                                0
                                            )
                                        }
                                    )
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(volBarHeight)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.24f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(volFraction)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = if (isVolDragging) 0.98f else 0.82f))
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Rounded.VolumeUp,
                            contentDescription = "Subir volumen",
                            tint = Color.White.copy(alpha = 0.58f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 6. Bottom Bar (omitting unused AirPlay/SharePlay pill from reference)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showLyrics = true }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "Letras",
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "A CONTINUACIÓN",
                                color = Color.White.copy(alpha = 0.48f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = nextSong?.let { "${it.title} · ${it.artist}" }
                                    ?: "Sin canciones en cola",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { showQueue = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                contentDescription = "Cola",
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    }
                )
            }
        }
    }

    if (showOptionsMenu) {
        SongOptionsMenuModal(
            song = song,
            isFavorite = isFavorite,
            onDismiss = { showOptionsMenu = false },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onToggleFavorite = { onToggleFavorite(it) },
            playbackControls = { com.yang.player.ui.components.PlaybackModesRow(isShuffle, repeatMode, onShuffle, onRepeat) }
        )
    }
}

/** Measures the real controls first, including the device font scale. The footer
 * ends at the safe bottom edge. Artwork and handle follow the reference's upper
 * anchors independently, without moving or stretching the controls.
 */
@Composable
private fun PlayerLayout(
    viewportHeight: Dp,
    modifier: Modifier = Modifier,
    handle: @Composable () -> Unit,
    artwork: @Composable () -> Unit,
    controls: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            Box(contentAlignment = Alignment.Center) { handle() }
            Box { artwork() }
            Column { controls() }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val handlePlaceable = measurables[0].measure(loose)
        val controlsPlaceable = measurables[2].measure(Constraints.fixedWidth(width))
        val gap = 24.dp.roundToPx()
        val viewport = viewportHeight.roundToPx()
        val artworkSize = minOf(
            width,
            360.dp.roundToPx(),
            (viewport - handlePlaceable.height - controlsPlaceable.height - gap * 2)
                .coerceAtLeast(120.dp.roundToPx())
        )
        val artworkPlaceable = measurables[1].measure(Constraints.fixed(artworkSize, artworkSize))
        val contentHeight = handlePlaceable.height + artworkSize + controlsPlaceable.height + gap * 2
        val height = constraints.constrainHeight(maxOf(viewport, contentHeight))
        val latestArtworkTop = height - controlsPlaceable.height - gap - artworkSize
        val artworkTop = (viewport * 0.105f).toInt()
            .coerceIn(handlePlaceable.height + gap, latestArtworkTop)
        val handleTop = (viewport * 0.015f).toInt()
            .coerceIn(0, artworkTop - handlePlaceable.height - gap)
        layout(width, height) {
            handlePlaceable.placeRelative((width - handlePlaceable.width) / 2, handleTop)
            artworkPlaceable.placeRelative((width - artworkSize) / 2, artworkTop)
            controlsPlaceable.placeRelative(0, height - controlsPlaceable.height)
        }
    }
}

@Composable
private fun CircleGlassButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun ArtworkCard(
    song: Song?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val artworkRequest = remember(song?.id, song?.artworkUri, song?.mediaUri) {
        if (song != null) {
            ImageRequest.Builder(context)
                .data(song.artworkUri ?: song.mediaUri)
                .size(700, 700)
                .precision(Precision.INEXACT)
                .allowHardware(false)
                .crossfade(false)
                .memoryCacheKey("artwork_${song.id}_${song.artworkUri ?: song.mediaUri}")
                .diskCacheKey("artwork_${song.id}_${song.artworkUri ?: song.mediaUri}")
                .build()
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        var isLoaded by remember(song?.id, song?.artworkUri) { mutableStateOf(false) }

        if (!isLoaded) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(72.dp)
            )
        }

        if (artworkRequest != null && song != null) {
            AsyncImage(
                model = artworkRequest,
                contentDescription = song.title,
                onSuccess = { isLoaded = true },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

