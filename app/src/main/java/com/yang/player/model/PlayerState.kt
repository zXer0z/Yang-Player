package com.yang.player.model

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val isShuffle: Boolean = false,
    val isRepeatOne: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val playbackError: String? = null,
    val repeatMode: Int = 0,
    val nextIndex: Int = -1,
    val previousIndex: Int = -1
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

