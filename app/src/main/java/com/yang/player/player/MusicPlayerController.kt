package com.yang.player.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import androidx.core.content.ContextCompat
import kotlinx.coroutines.cancel
import com.yang.player.model.PlayerState
import com.yang.player.model.Song
import com.yang.player.data.ListeningHistoryStore
import com.yang.player.online.OnlinePlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class MusicPlayerController(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressPollingJob: Job? = null
    private val completion = com.yang.player.data.OnlineMetadata.get(context)
    private var sessionSong: Song? = null
    private var sessionPositionMs = 0L
    private fun beginListeningSession(song: Song, force: Boolean = false) {
        if (!force && sessionSong?.id == song.id) return
        flushListeningHistory()
        sessionSong = song
    }
    fun flushListeningHistory() = ServiceListeningTracker.flushActive()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingPlayback: (() -> Unit)? = null

    private val savedPlayback = PlaybackSessionStore(context).load()
    private var released = false

    // Stable state (song, playing status, queue) - does NOT update every 300ms
    private val _playerState = MutableStateFlow(savedPlayback?.let { saved ->
        val song = completion.apply(saved.songs[saved.index])
        PlayerState(currentSong = song, queue = saved.songs.map(completion::apply), currentIndex = saved.index, durationMs = song.durationMs,
            currentPositionMs = saved.positionMs, isShuffle = saved.shuffle, isRepeatOne = saved.repeat == Player.REPEAT_MODE_ONE, repeatMode = saved.repeat)
    } ?: PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // Isolated high-frequency flows for the progress bars ONLY (prevents full-screen recompositions)
    private val _playbackPositionMs = MutableStateFlow(savedPlayback?.positionMs ?: 0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _playbackProgress = MutableStateFlow(_playerState.value.progress.coerceIn(0f, 1f))
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val playerListener = PlayerEventListener()
    init {
        initMediaController()
    }

    private fun initMediaController() {
        if (released) return
        val sessionToken = SessionToken(context, ComponentName(context, YangAudioService::class.java))
        val future = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (mediaController !== controller || released) return
                    mediaController = null
                    stopProgressTracking()
                    _playerState.update { it.copy(isPlaying = false, isBuffering = false) }
                    scope.launch { delay(1000); if (!released && mediaController == null) initMediaController() }
                }
            }).buildAsync()
        controllerFuture = future
        future.addListener({
            if (released || controllerFuture !== future) return@addListener
            try {
                val connected = future.get()
                mediaController = connected
                connected.addListener(playerListener)
                synchronize(connected)
                pendingPlayback?.also { pendingPlayback = null }?.invoke()
            } catch (e: Exception) {
                Log.e("YangPlayer", "Error initializing MediaController", e)
                scope.launch { delay(1000); if (!released && mediaController == null) initMediaController() }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Hydrate the existing session immediately; events are not replayed on bind. */
    private fun synchronize(controller: MediaController) {
        val previous = _playerState.value
        val known = previous.queue.associateBy { it.id }
        val queue = if (controller.mediaItemCount == 0) previous.queue else (0 until controller.mediaItemCount).mapNotNull { i ->
            val item = controller.getMediaItemAt(i)
            PlaybackSessionStore.song(item, known[item.mediaId], if (i == controller.currentMediaItemIndex) controller.duration else 0)?.let(completion::apply)
        }
        val item = controller.currentMediaItem
        val index = controller.currentMediaItemIndex.takeIf { it in queue.indices && queue[it].id == item?.mediaId }
            ?: queue.indexOfFirst { it.id == item?.mediaId }.takeIf { it >= 0 } ?: previous.currentIndex
        val current = if (item != null) queue.getOrNull(index) ?: PlaybackSessionStore.song(item, previous.currentSong, controller.duration)?.let(completion::apply) else previous.currentSong?.let(completion::apply)
        val duration = controller.duration.takeIf { it > 0 } ?: current?.durationMs ?: 0
        val position = if (item != null) controller.currentPosition.coerceAtLeast(0) else previous.currentPositionMs
        if (item != null && current != null) { beginListeningSession(current); sessionPositionMs = position }
        _playerState.value = previous.copy(currentSong = current, queue = queue, currentIndex = index,
            isPlaying = controller.isPlaying, isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            durationMs = duration, currentPositionMs = position, isShuffle = controller.shuffleModeEnabled,
            isRepeatOne = controller.repeatMode == Player.REPEAT_MODE_ONE, repeatMode = controller.repeatMode,
            nextIndex = controller.nextMediaItemIndex, previousIndex = controller.previousMediaItemIndex,
            playbackError = controller.playerError?.let { if (current?.isOnline == true) "No se pudo reproducir el audio de YouTube. Revisa tu conexión o reintenta la canción." else "No se pudo reproducir esta canción." })
        _playbackPositionMs.value = position
        _playbackProgress.value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        if (controller.isPlaying) startProgressTracking() else stopProgressTracking()
    }

    /** Replace only MediaMetadata, keeping the source URI, position and audio configuration. */
    fun refreshCompletedMetadata() {
        val previous = _playerState.value
        _playerState.value = previous.copy(currentSong = previous.currentSong?.let(completion::apply),
            queue = previous.queue.map(completion::apply))
        mediaController?.let { controller ->
            for (index in 0 until controller.mediaItemCount) {
                val item = controller.getMediaItemAt(index)
                val song = PlaybackSessionStore.song(item) ?: continue
                val updated = completion.apply(song)
                if (updated != song) controller.replaceMediaItem(index,
                    item.buildUpon().setMediaMetadata(PlaybackSessionStore.item(updated).mediaMetadata).build())
            }
        }
    }

    fun playSong(sourceSong: Song, sourceQueue: List<Song> = listOf(sourceSong), startPositionMs: Long = 0L, queueIndex: Int? = null) {
        val song = completion.apply(sourceSong)
        val queue = sourceQueue.map(completion::apply)
        if (mediaController == null) {
            pendingPlayback = { playSong(song, queue, startPositionMs, queueIndex) }
            return
        }
        beginListeningSession(song, force = true)
        val start = startPositionMs.coerceIn(0L, song.durationMs.coerceAtLeast(0L))
        sessionPositionMs = start
        val index = queueIndex?.takeIf { it in queue.indices && queue[it].id == song.id }
            ?: queue.indexOfFirst { it.id == song.id }.let { if (it >= 0) it else 0 }

        _playerState.update {
            it.copy(
                currentSong = song,
                queue = queue,
                currentIndex = index,
                isPlaying = true,
                durationMs = song.durationMs,
                currentPositionMs = start,
                isBuffering = true,
                playbackError = null
            )
        }
        _playbackPositionMs.value = start
        _playbackProgress.value = if (song.durationMs > 0) start.toFloat() / song.durationMs else 0f

        startProgressTracking()

        mediaController?.let { controller ->
            controller.stop()
            controller.clearMediaItems()

            val mediaItems = queue.map(PlaybackSessionStore::item)

            controller.setMediaItems(mediaItems, index, start)
            controller.prepare()
            controller.play()
            queue.getOrNull(index + 1)?.takeIf { it.isOnline }?.let { OnlinePlayback.prefetch(context, it.mediaUri) }
        }
    }

    fun togglePlayPause() {
        val controller = mediaController
        if (controller == null) { pendingPlayback = { togglePlayPause() }; return }
        if (controller.currentMediaItem == null) {
            val state = _playerState.value
            state.currentSong?.let { playSong(it, state.queue.ifEmpty { listOf(it) }, state.currentPositionMs, state.currentIndex) }
            return
        }
        if (controller.playWhenReady && controller.playbackState != Player.STATE_ENDED) controller.pause()
        else {
            val duration = controller.duration.takeIf { it > 0 } ?: _playerState.value.durationMs
            if (controller.playbackState == Player.STATE_ENDED || duration > 0 && controller.currentPosition >= duration) controller.seekTo(0)
            if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
            controller.play()
        }
        synchronize(controller)
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        synchronize(controller)
    }
    fun cycleRepeat() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        synchronize(controller)
    }
    fun skipToNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) { controller.seekToNextMediaItem(); controller.prepare(); controller.play() }
    }
    fun skipToPrevious() {
        val controller = mediaController ?: return
        if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem() else controller.seekTo(0)
        controller.prepare(); controller.play()
    }

    fun seekTo(positionMs: Long) {
        sessionPositionMs = positionMs.coerceAtLeast(0)
        _playbackPositionMs.value = positionMs
        val dur = _playerState.value.durationMs
        if (dur > 0) {
            _playbackProgress.value = (positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        }
        mediaController?.seekTo(positionMs)
    }

    fun playNext(song: Song) {
        val currentState = _playerState.value
        val currentQueue = currentState.queue.toMutableList()
        val insertIndex = if (currentQueue.isEmpty()) 0 else (currentState.currentIndex + 1).coerceAtMost(currentQueue.size)
        currentQueue.add(insertIndex, song)
        _playerState.update { it.copy(queue = currentQueue) }

        mediaController?.let { controller ->
            val mediaItem = PlaybackSessionStore.item(song)
            controller.addMediaItem(insertIndex, mediaItem)
        }
    }

    fun addToQueue(song: Song) {
        val currentState = _playerState.value
        val currentQueue = currentState.queue.toMutableList()
        currentQueue.add(song)
        _playerState.update { it.copy(queue = currentQueue) }

        mediaController?.let { controller ->
            val mediaItem = PlaybackSessionStore.item(song)
            controller.addMediaItem(mediaItem)
        }
    }

    fun addSongsToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        _playerState.update { it.copy(queue = it.queue + songs) }
        mediaController?.addMediaItems(songs.map(PlaybackSessionStore::item))
    }

    fun removeDeletedSong(deleted: Song) {
        val previous = _playerState.value
        val remaining = previous.queue.filterNot { com.yang.player.data.sameSongFile(it, deleted) }
        val removedCurrent = previous.currentSong?.let { com.yang.player.data.sameSongFile(it, deleted) } == true
        if (removedCurrent) { flushListeningHistory(); sessionSong = null }
        pendingPlayback = null
        val currentIndex = if (removedCurrent) previous.queue.take(previous.currentIndex.coerceAtLeast(0))
            .count { !com.yang.player.data.sameSongFile(it, deleted) }.coerceAtMost(remaining.lastIndex)
            else remaining.indexOfFirst { it.id == previous.currentSong?.id }
        val current = remaining.getOrNull(currentIndex)
        _playerState.value = previous.copy(queue = remaining, currentIndex = currentIndex, currentSong = current,
            isPlaying = previous.isPlaying && current != null, isBuffering = false,
            currentPositionMs = if (removedCurrent) 0 else previous.currentPositionMs,
            durationMs = current?.durationMs ?: 0, playbackError = null)
        if (removedCurrent || remaining.isEmpty()) { _playbackPositionMs.value = 0; _playbackProgress.value = 0f }
        mediaController?.let { controller ->
            for (index in controller.mediaItemCount - 1 downTo 0) {
                val item = PlaybackSessionStore.song(controller.getMediaItemAt(index)) ?: continue
                if (com.yang.player.data.sameSongFile(item, deleted)) controller.removeMediaItem(index)
            }
            if (controller.mediaItemCount == 0) { controller.stop(); stopProgressTracking() }
            else synchronize(controller)
        }
        PlaybackSessionStore(context).removeSongFile(deleted)
    }

    fun selectQueueItem(index: Int) {
        val state = _playerState.value
        val song = state.queue.getOrNull(index) ?: return
        if (index == state.currentIndex) {
            if (!state.isPlaying) togglePlayPause()
        } else {
            val controller = mediaController
            val queuedItem = controller?.takeIf { it.mediaItemCount == state.queue.size }?.getMediaItemAt(index)
            val queuedSong = queuedItem?.let { PlaybackSessionStore.song(it) }
            if (controller != null && queuedSong?.mediaUri == song.mediaUri && queuedSong.id == song.id) {
                // Reuse the prepared playlist instead of rebuilding thousands of MediaItems on the UI thread.
                beginListeningSession(song, force = true)
                _playerState.value = state.copy(currentSong = song, currentIndex = index, currentPositionMs = 0,
                    durationMs = song.durationMs, isPlaying = true, isBuffering = true, playbackError = null)
                _playbackPositionMs.value = 0; _playbackProgress.value = 0f
                controller.seekToDefaultPosition(index)
                controller.prepare()
                controller.play()
                startProgressTracking()
                state.queue.getOrNull(index + 1)?.takeIf { it.isOnline }?.let { OnlinePlayback.prefetch(context, it.mediaUri) }
            } else playSong(song, state.queue, queueIndex = index)
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        val previous = _playerState.value
        val reordered = QueueOrder.move(previous, from, to)
        if (reordered === previous) return
        // Moving a MediaItem preserves the current decoder, playhead and pause state.
        _playerState.value = reordered
        mediaController?.moveMediaItem(from, to)
    }

    private fun startProgressTracking() {
        if (progressPollingJob?.isActive == true) return
        progressPollingJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val position = controller.currentPosition
                    val duration = controller.duration.takeIf { it > 0 } ?: _playerState.value.durationMs
                    _playbackPositionMs.value = position
                    _playbackProgress.value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                }
                delay(350)
            }
        }
    }

    private fun stopProgressTracking() {
        flushListeningHistory()
        progressPollingJob?.cancel()
        progressPollingJob = null
    }

    fun release() {
        if (released) return
        released = true
        stopProgressTracking()
        mediaController?.removeListener(playerListener)
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        pendingPlayback = null
        scope.cancel()
    }

    private inner class PlayerEventListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            mediaController?.let(::synchronize)
        }
        // ExoPlayer owns automatic advancement and honors repeat OFF/ALL/ONE.
        // Do not restart the queue when playback reaches STATE_ENDED.
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ||
                (mediaController?.currentMediaItemIndex != _playerState.value.currentIndex && mediaItem?.mediaId == sessionSong?.id)) {
                flushListeningHistory()
                sessionSong = null
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            Log.e("YangPlayer", "Playback error: ${error.errorCodeName}")
            stopProgressTracking()
        }
    }
}
