package com.yang.player.model

enum class AudioFormat(val displayName: String, val isLossless: Boolean) {
    DSD("DSD", true),
    FLAC("FLAC", true),
    ALAC("ALAC", true),
    WAV("WAV", true),
    M4A("M4A", false),
    MP3("MP3", false),
    UNKNOWN("AUDIO", false);

    companion object {
        fun fromPathOrMime(path: String?, mime: String?): AudioFormat {
            val extension = path?.substringAfterLast('.', "")?.lowercase() ?: ""
            return when {
                extension in listOf("dsf", "dff") || mime?.contains("dsd") == true -> DSD
                extension == "flac" || mime?.contains("flac") == true -> FLAC
                extension in listOf("alac") || (extension == "m4a" && mime?.contains("alac") == true) -> ALAC
                extension == "wav" || mime?.contains("wav") == true -> WAV
                extension == "m4a" || mime?.contains("mp4") == true || mime?.contains("m4a") == true -> M4A
                extension == "mp3" || mime?.contains("mpeg") == true -> MP3
                else -> UNKNOWN
            }
        }
    }
}

