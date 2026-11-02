package com.yang.player.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.yang.player.data.ListeningHistoryStore

/** Counts actual playing time in the service, including when the activity is closed. */
@UnstableApi
class ServiceListeningTracker(context: Context, private val player: Player) : Player.Listener {
    private val history = ListeningHistoryStore.get(context)
    private val handler = Handler(Looper.getMainLooper())
    private var id = ""
    private var duration = 0L
    private var position = 0L
    private var total = 0L
    private var pending = 0L
    private var qualified = false
    private var wasPlaying = false
    private var lastTick = SystemClock.elapsedRealtime()
    private val tick = object : Runnable { override fun run() { sample(); schedule() } }
    init { active = this; player.addListener(this); sample(); schedule() }

    private fun schedule() {
        handler.removeCallbacks(tick)
        if (player.isPlaying) handler.postDelayed(tick, 1000)
    }
    private fun sample(forceNew: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val delta = if (wasPlaying && id.isNotBlank()) (now - lastTick).coerceIn(0L, 5000L) else 0L
        val currentId = player.currentMediaItem?.mediaId.orEmpty()
        val changed = currentId != id || forceNew
        total += delta; pending += delta
        position = if (!changed) player.currentPosition.coerceAtLeast(0) else (position + delta).let { if (duration > 0) minOf(it, duration) else it }
        lastTick = now
        if (changed) {
            flushPending()
            id = currentId; total = 0; pending = 0; qualified = false
            position = player.currentPosition.coerceAtLeast(0)
        }
        duration = player.duration.takeIf { it > 0 } ?: player.currentMediaItem?.mediaMetadata?.extras?.getLong("yangDuration")?.takeIf { it > 0 } ?: 0L
        wasPlaying = player.isPlaying
        val threshold = if (duration > 0) minOf(30_000L, (duration / 2).coerceAtLeast(1000L)) else 30_000L
        if (!wasPlaying || pending >= 15_000L || (!qualified && total >= threshold)) flushPending()
    }
    private fun flushPending() {
        if (id.isBlank() || total <= 0) return
        val threshold = if (duration > 0) minOf(30_000L, (duration / 2).coerceAtLeast(1000L)) else 30_000L
        val qualifies = !qualified && total >= threshold
        val positionChanged = history.profile.value.tracks[id]?.resumePositionMs != position
        if (pending > 0 || qualifies || positionChanged) {
            history.record(id, pending, position, qualifies)
            qualified = qualified || qualifies
            pending = 0
        }
    }
    fun flush() { sample(); flushPending() }
    override fun onEvents(player: Player, events: Player.Events) { sample(); schedule() }
    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
        sample(forceNew = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ||
            (item?.mediaId == id && reason in setOf(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)))
    }
    fun close() { flush(); handler.removeCallbacks(tick); player.removeListener(this); if (active === this) active = null }
    companion object {
        private var active: ServiceListeningTracker? = null
        fun flushActive() { active?.flush() }
    }
}
