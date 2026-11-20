package com.yang.player.ui.components

import android.os.SystemClock
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.yang.player.R
import com.yang.player.model.Song
import com.yang.player.online.YouTubeVideo
import com.yang.player.online.OnlinePlayback
import com.yang.player.online.YouTubeVideoQuality
import com.yang.player.player.VideoClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal class YouTubeVideoState {
    var qualities by mutableStateOf(emptyList<YouTubeVideoQuality>())
    var quality by mutableStateOf<YouTubeVideoQuality?>(null)
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var player by mutableStateOf<Player?>(null)
    var waitingForFrame by mutableStateOf(false)
    var renderedQuality by mutableStateOf<YouTubeVideoQuality?>(null)
    var syncingAudio by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var aspectRatio by mutableStateOf(16f / 9f)
    var request by mutableIntStateOf(0)
        private set

    fun retry() {
        qualities = emptyList()
        quality = null
        error = null
        notice = null
        request++
    }

    fun select(quality: YouTubeVideoQuality) {
        error = null
        notice = null
        if (this.quality == quality) return
        renderedQuality = null
        this.quality = quality
    }

    fun lowerQuality(current: YouTubeVideoQuality, reason: String): Boolean {
        if (quality != current) return false
        val index = qualities.indexOf(current)
        if (index < 0) return false
        val next = qualities.getOrNull(index + 1) ?: return false
        quality = next
        notice = "Calidad ajustada a ${next.label} $reason."
        return true
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberYouTubeVideo(
    song: Song,
    index: Int,
    visible: Boolean,
    readClock: (String) -> VideoClock?,
    holdAudio: (String) -> (() -> Unit)?
): YouTubeVideoState {
    val state = remember(song.id, song.mediaUri, index) { YouTubeVideoState() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestClock by rememberUpdatedState(readClock)
    val latestHold by rememberUpdatedState(holdAudio)
    LaunchedEffect(state, state.request) {
        try {
            state.qualities = YouTubeVideo.qualities(song.mediaUri, refresh = state.request != 0)
            state.quality = state.qualities.firstOrNull { it.resolution <= 720 }
                ?: state.qualities.lastOrNull()
            if (state.quality == null) {
                state.error = "No hay calidades de video compatibles."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.error = "No se pudo cargar el video de YouTube."
        }
    }
    LaunchedEffect(state, lifecycle, visible) {
        if (!visible) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            awaitVideoBuffer(state, clock = { latestClock(song.id) }, holdAudio = { latestHold(song.id) })
        }
    }
    LaunchedEffect(state, state.quality, lifecycle) {
        val quality = state.quality ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val app = context.applicationContext
            val dataSource = withContext(Dispatchers.IO) { OnlinePlayback.videoFactory(app) }
            val renderers = DefaultRenderersFactory(app).setEnableDecoderFallback(true)
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 35_000, 2_500, 3_500)
                .setBackBuffer(5_000, true)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            val video = ExoPlayer.Builder(app, renderers).setLoadControl(loadControl).build()
            try {
                state.waitingForFrame = true
                state.renderedQuality = null
                video.volume = 0f
                video.trackSelectionParameters = video.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                video.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (state.player === video && videoSize.width > 0 && videoSize.height > 0) {
                            state.aspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        if (state.player === video) {
                            state.renderedQuality = quality
                            state.waitingForFrame = false
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (state.player === video) state.buffering = playbackState == Player.STATE_BUFFERING
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (state.player === video && state.quality == quality &&
                            !state.lowerQuality(quality, "tras un error de reproducción")
                        ) {
                            state.error = "No se pudo reproducir el video."
                            state.quality = null
                        }
                    }
                })
                val item = MediaItem.Builder().setUri(quality.uri).setMimeType(quality.mimeType)
                    .setCustomCacheKey(quality.itag.takeIf { it > 0 }?.let { "yt-video:${song.mediaUri}:$it" })
                    .build()
                val source = ProgressiveMediaSource.Factory(dataSource).createMediaSource(item)
                video.setMediaSource(source, latestClock(song.id)?.positionMs ?: 0L)
                state.player = video
                video.prepare()
                syncVideo(video, clock = { latestClock(song.id) }) { reason ->
                    state.lowerQuality(quality, reason)
                }
            } finally {
                if (state.player === video) {
                    state.player = null
                    state.waitingForFrame = false
                    state.buffering = false
                }
                video.release()
            }
        }
    }
    return state
}

@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun awaitVideoBuffer(
    state: YouTubeVideoState,
    clock: () -> VideoClock?,
    holdAudio: () -> (() -> Unit)?
) {
    var resumeAudio: (() -> Unit)? = null
    var watchedPlayer: Player? = null
    var lastBufferedPosition = 0L
    var lastBufferProgressAt = 0L
    try {
        while (true) {
            val audio = clock()
            val video = state.player
            val quality = state.quality
            val now = SystemClock.elapsedRealtime()
            if (watchedPlayer !== video || resumeAudio == null) {
                watchedPlayer = video
                lastBufferedPosition = video?.bufferedPosition ?: 0L
                lastBufferProgressAt = now
            }
            if (audio?.playRequested != true) {
                resumeAudio?.invoke()
                resumeAudio = null
            } else {
                val target = video?.duration?.takeIf { it > 0 }?.let { audio.positionMs.coerceAtMost(it) }
                    ?: audio.positionMs
                val reachedEnd = video?.playbackState == Player.STATE_ENDED &&
                    video.duration > 0 && target >= video.duration - 200L
                val hasFrame = quality != null && state.renderedQuality == quality
                val canPlay = reachedEnd || (video != null && hasFrame && video.playbackState == Player.STATE_READY)
                if (!canPlay && resumeAudio == null) resumeAudio = holdAudio()
                if (resumeAudio != null && video != null && quality != null) {
                    val buffered = video.bufferedPosition
                    if (reachedEnd || (canPlay && abs(video.currentPosition - target) <= 180L &&
                        hasVideoReserve(target, buffered, video.duration))
                    ) {
                        resumeAudio?.invoke()
                        resumeAudio = null
                    } else if (abs(buffered - lastBufferedPosition) >= 250L) {
                        lastBufferProgressAt = now
                        lastBufferedPosition = buffered
                    } else if (now - lastBufferProgressAt >= 15_000L) {
                        lastBufferProgressAt = now
                        if (!state.lowerQuality(quality, "por carga lenta")) {
                            state.error = "No se pudo acumular suficiente video. Reintenta o vuelve al minirreproductor."
                            state.quality = null
                        }
                    }
                }
            }
            state.syncingAudio = resumeAudio != null
            delay(75L)
        }
    } finally {
        state.syncingAudio = false
        resumeAudio?.invoke()
    }
}

internal fun hasVideoReserve(positionMs: Long, bufferedPositionMs: Long, durationMs: Long): Boolean {
    val remainingMs = if (durationMs > 0L) (durationMs - positionMs - 150L).coerceAtLeast(0L) else Long.MAX_VALUE
    return bufferedPositionMs >= positionMs && bufferedPositionMs - positionMs >= minOf(3_500L, remainingMs)
}

@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun syncVideo(
    video: ExoPlayer,
    clock: () -> VideoClock?,
    lowerQuality: (String) -> Boolean
) {
    var lastSeekAt = SystemClock.elapsedRealtime()
    var previousAudioPosition: Long? = null
    var sampleTime = SystemClock.elapsedRealtime()
    var lastDropped = 0
    var lastRendered = 0
    var correctionWindow = 0L
    var corrections = 0
    while (true) {
        val audio = clock()
        if (audio == null) {
            video.pause()
        } else {
            val target = video.duration.takeIf { it > 0 }?.let { audio.positionMs.coerceAtMost(it) }
                ?: audio.positionMs
            val now = SystemClock.elapsedRealtime()
            val drift = target - video.currentPosition
            val jumped = previousAudioPosition?.let { abs(audio.positionMs - it) >= 1_250L } == true
            previousAudioPosition = audio.positionMs
            if (jumped) {
                corrections = 0
            }
            val canSeek = video.playbackState == Player.STATE_READY || video.playbackState == Player.STATE_ENDED
            val seekThreshold = if (audio.playing) 1_000L else 150L
            if (jumped || (canSeek && abs(drift) > seekThreshold && now - lastSeekAt >= 3_000L)) {
                if (!jumped && audio.playing && drift > 0) {
                    if (corrections == 0 || now - correctionWindow > 15_000L) {
                        correctionWindow = now
                        corrections = 0
                    }
                    corrections++
                    if (corrections >= 3 && lowerQuality("para evitar tirones")) return
                }
                video.seekTo(target)
                lastSeekAt = now
            }
            if (now - sampleTime >= 3_000L) {
                video.videoDecoderCounters?.let { counters ->
                    counters.ensureUpdated()
                    val dropped = counters.droppedBufferCount - lastDropped
                    val rendered = counters.renderedOutputBufferCount - lastRendered
                    lastDropped = counters.droppedBufferCount
                    lastRendered = counters.renderedOutputBufferCount
                    if (video.isPlaying && now - lastSeekAt >= 3_000L &&
                        dropped >= 12 && rendered >= 0 && dropped * 4 >= rendered &&
                        lowerQuality("por pérdida de cuadros")
                    ) return
                }
                sampleTime = now
            }
            val speed = if (audio.playing && abs(drift) in 100L..seekThreshold) {
                audio.speed * if (drift > 0) 1.05f else 0.95f
            } else {
                audio.speed
            }
            if (video.playbackParameters.speed != speed) video.setPlaybackSpeed(speed)
            video.playWhenReady = audio.playing
        }
        delay(150)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun YouTubeVideoSurface(state: YouTubeVideoState, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        state.player?.let { player ->
            AndroidView(
                factory = { context ->
                    LayoutInflater.from(context).inflate(R.layout.youtube_video_surface, null) as PlayerView
                },
                onReset = null,
                onRelease = { it.player = null },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (state.error == null && (state.player == null || state.waitingForFrame || state.buffering || state.syncingAudio)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                if (state.syncingAudio) Text("Cargando video…", color = Color.White)
            }
        }
    }
}
