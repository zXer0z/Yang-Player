package com.yang.player.model

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artworkUri: String?,
    val songCount: Int,
    val format: AudioFormat = AudioFormat.FLAC
) {
    val subtitle: String
        get() = "$artist · $songCount ${if (songCount == 1) "canción" else "canciones"}"
}

