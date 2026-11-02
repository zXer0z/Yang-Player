package com.yang.player.data

import com.yang.player.model.AudioFormat
import com.yang.player.model.Song

object SampleDataProvider {

    val featuredHeroSong = Song(
        id = "hero_38",
        title = "38°",
        artist = "Walls",
        album = "38° - EP",
        durationMs = 202000L, // 3:22
        mediaUri = "https://example.com/audio/38.flac",
        artworkUri = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=600&auto=format&fit=crop", // aesthetic botanical artwork
        format = AudioFormat.FLAC,
        sampleRateHz = 96000,
        bitDepth = 24,
        isHiRes = true,
        isFavorite = true
    )

    val sampleFavoriteSongs = listOf(
        Song(
            id = "fav_21",
            title = "21",
            artist = "AREA21",
            album = "Greatest Hits Vol. 1",
            durationMs = 161000L, // 2:41
            mediaUri = "https://example.com/audio/21.flac",
            artworkUri = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?q=80&w=400&auto=format&fit=crop", // neon purple
            format = AudioFormat.FLAC,
            sampleRateHz = 48000,
            bitDepth = 24,
            isHiRes = true,
            isFavorite = true
        ),
        featuredHeroSong, // 38° - Walls (highlighted in user's image)
        Song(
            id = "fav_zedd",
            title = "I Want You To Know (fea...",
            artist = "Zedd",
            album = "True Colors",
            durationMs = 239000L, // 3:59
            mediaUri = "https://example.com/audio/zedd.m4a",
            artworkUri = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=400&auto=format&fit=crop", // colorful splash
            format = AudioFormat.ALAC,
            sampleRateHz = 96000,
            bitDepth = 24,
            isHiRes = true,
            isFavorite = true
        ),
        Song(
            id = "fav_modjo",
            title = "Lady (Hear Me Tonight)",
            artist = "Modjo",
            album = "Modjo (Remastered)",
            durationMs = 307000L, // 5:07
            mediaUri = "https://example.com/audio/modjo.dsf",
            artworkUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=400&auto=format&fit=crop",
            format = AudioFormat.DSD,
            sampleRateHz = 2822400, // DSD64 2.8MHz
            bitDepth = 1,
            isHiRes = true,
            isFavorite = true
        ),
        Song(
            id = "fav_wav_hires",
            title = "Time (2023 Master)",
            artist = "Pink Floyd",
            album = "The Dark Side of the Moon",
            durationMs = 413000L, // 6:53
            mediaUri = "https://example.com/audio/time.wav",
            artworkUri = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=400&auto=format&fit=crop",
            format = AudioFormat.WAV,
            sampleRateHz = 192000,
            bitDepth = 24,
            isHiRes = true,
            isFavorite = true
        )
    )
}

