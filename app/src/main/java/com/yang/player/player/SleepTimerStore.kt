package com.yang.player.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SleepMode { OFF, MINUTES, END_OF_TRACK }
data class SleepTimerState(val mode: SleepMode = SleepMode.OFF, val remainingMs: Long = 0, val track: String = "")

/** Owned by the playback service, so leaving the screen does not stop the timer. */
@UnstableApi
object SleepTimerStore {
    private val handler = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(SleepTimerState())
    val state = mutable.asStateFlow()
    private var player: ExoPlayer? = null
    private var deadline = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (mutable.value.mode != SleepMode.MINUTES) return
            val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            if (remaining == 0L) { player?.pause(); cancel() }
            else { mutable.value = mutable.value.copy(remainingMs = remaining); handler.postDelayed(this, minOf(1000L, remaining)) }
        }
    }
    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && mutable.value.mode == SleepMode.END_OF_TRACK) cancel()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && mutable.value.mode == SleepMode.END_OF_TRACK) { player?.pause(); cancel() }
        }
        override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
            // Choosing another song explicitly cancels "end of this song".
            if (mutable.value.mode == SleepMode.END_OF_TRACK && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) cancel()
        }
    }
    fun attach(value: ExoPlayer) {
        player?.removeListener(listener)
        player = value
        value.addListener(listener)
    }
    fun detach(value: ExoPlayer) {
        if (player !== value) return
        cancel(); value.removeListener(listener); player = null
    }
    fun setMinutes(minutes: Int): Boolean {
        if (player?.currentMediaItem == null || minutes !in 1..240) return false
        cancel()
        deadline = SystemClock.elapsedRealtime() + minutes * 60_000L
        mutable.value = SleepTimerState(SleepMode.MINUTES, minutes * 60_000L)
        handler.post(tick)
        return true
    }
    fun atEndOfTrack(): Boolean {
        val current = player ?: return false
        val item = current.currentMediaItem ?: return false
        cancel()
        mutable.value = SleepTimerState(SleepMode.END_OF_TRACK, track = item.mediaMetadata.title?.toString().orEmpty())
        current.pauseAtEndOfMediaItems = true
        return true
    }
    fun cancel() {
        handler.removeCallbacks(tick)
        player?.pauseAtEndOfMediaItems = false
        deadline = 0
        mutable.value = SleepTimerState()
    }
}
