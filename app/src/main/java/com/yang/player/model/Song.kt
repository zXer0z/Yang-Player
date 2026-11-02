package com.yang.player.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mediaUri: String,
    val artworkUri: String? = null,
    val dataPath: String = "",
    val format: AudioFormat = AudioFormat.FLAC,
    val sampleRateHz: Int = 44100,
    val bitDepth: Int = 16,
    val isHiRes: Boolean = false,
    val isFavorite: Boolean = false,
    val albumArtist: String = ""
) {
    val isOnline: Boolean get() = mediaUri.startsWith("yangyoutube://")
    // Evaluated once at object creation - avoids Formatter/Regex overhead during 120fps fling
    val formattedDuration: String = run {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val secStr = if (seconds < 10) "0$seconds" else "$seconds"
        "$minutes:$secStr"
    }

    val audioQualityBadge: String = when {
        isOnline -> "YouTube · Audio"
        sampleRateHz <= 0 || bitDepth <= 0 -> format.displayName
        format == AudioFormat.DSD -> "DSD · ${(sampleRateHz / 1000000.0).let { String.format("%.1f", it) }} MHz"
        else -> {
            val kHz = if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000}" else String.format("%.1f", sampleRateHz / 1000.0)
            "${format.displayName} · ${bitDepth}-bit · $kHz kHz"
        }
    }
}
