package com.yang.player.data

data class TrackListeningStats(
    val playCount: Int = 0,
    val listenedMs: Long = 0,
    val lastPlayedAt: Long = 0,
    val resumePositionMs: Long = 0
)

data class ListeningProfile(
    val tracks: Map<String, TrackListeningStats> = emptyMap(),
    val favoriteIds: Set<String> = emptySet()
)
