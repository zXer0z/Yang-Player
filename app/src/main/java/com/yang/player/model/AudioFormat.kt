package com.yang.player.model

enum class AudioFormat(val displayName: String, val isLossless: Boolean) {
    DSD("DSD", true),
    FLAC("FLAC", true),
    ALAC("ALAC", true),
    WAV("WAV", true),
    AIFF("AIFF", true),
    APE("APE", true),
    OGG("OGG", false),
    OPUS("Opus", false),
    AAC("AAC", false),
    M4A("M4A", false),
    MP3("MP3", false),
    WMA("WMA", false),
    UNKNOWN("AUDIO", false);

    companion object {
        fun fromPathOrMime(path: String?, mime: String?): AudioFormat {
            val extension = path?.substringAfterLast('.', "")?.lowercase() ?: ""
            return when {
                extension in listOf("dsf", "dff") || mime?.contains("dsd") == true -> DSD
                extension == "flac" || mime?.contains("flac") == true -> FLAC
                extension in listOf("alac") || (extension == "m4a" && mime?.contains("alac") == true) -> ALAC
                extension == "wav" || mime?.contains("wav") == true -> WAV
                extension in listOf("aiff", "aif") || mime?.contains("aiff") == true -> AIFF
                extension in listOf("ape", "mac") || mime?.contains("ape") == true || mime?.contains("monkeys-audio") == true -> APE
                extension in listOf("ogg", "oga") || mime?.contains("ogg") == true || mime?.contains("vorbis") == true -> OGG
                extension in listOf("opus") || mime?.contains("opus") == true -> OPUS
                extension in listOf("aac") || mime?.contains("aac") == true -> AAC
                extension in listOf("m4a", "mp4", "m4b") || mime?.contains("mp4") == true || mime?.contains("m4a") == true -> M4A
                extension == "mp3" || mime?.contains("mpeg") == true -> MP3
                extension in listOf("wma") || mime?.contains("wma") == true -> WMA
                else -> UNKNOWN
            }
        }
    }
}

