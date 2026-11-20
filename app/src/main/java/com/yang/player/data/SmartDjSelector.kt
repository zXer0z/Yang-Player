package com.yang.player.data

import com.yang.player.model.Song

object SmartDjSelector {

    data class TransitionPlan(
        val nextSong: Song,
        val fadeDurationSeconds: Float,
        val applyBassDip: Boolean,
        val rationale: String
    )

    fun planNextTrack(
        currentSong: Song,
        library: List<Song>,
        recentSongIds: Set<String>,
        isSmartMode: Boolean,
        defaultFadeDuration: Float,
        respectGapless: Boolean
    ): TransitionPlan? {
        val candidates = library.filter { it.id !in recentSongIds && it.id != currentSong.id }
        if (candidates.isEmpty()) return null

        val scored = candidates.map { candidate ->
            var score = 0

            // 1. Same artist or collaboration
            if (candidate.artist.equals(currentSong.artist, ignoreCase = true)) score += 30
            else if (candidate.albumArtist.isNotBlank() && candidate.albumArtist.equals(currentSong.albumArtist, ignoreCase = true)) score += 25
            else if (candidate.artist.contains(currentSong.artist, ignoreCase = true) || currentSong.artist.contains(candidate.artist, ignoreCase = true)) score += 15

            // 2. Same or compatible genre
            if (candidate.genre.isNotBlank() && currentSong.genre.isNotBlank()) {
                if (candidate.genre.equals(currentSong.genre, ignoreCase = true)) score += 20
                else if (candidate.genre.contains(currentSong.genre, ignoreCase = true) || currentSong.genre.contains(candidate.genre, ignoreCase = true)) score += 12
            }

            // 3. Acoustic energy & mood similarity
            val highEnergyWords = setOf("remix", "club", "dance", "electro", "party", "energy", "vip", "mix", "dub", "house", "edm")
            val chillWords = setOf("acoustic", "unplugged", "piano", "slow", "ambient", "calm", "relax", "ballad", "lounge")

            val currentHigh = highEnergyWords.any { currentSong.title.contains(it, true) || currentSong.genre.contains(it, true) }
            val currentChill = chillWords.any { currentSong.title.contains(it, true) || currentSong.genre.contains(it, true) }

            val candidateHigh = highEnergyWords.any { candidate.title.contains(it, true) || candidate.genre.contains(it, true) }
            val candidateChill = chillWords.any { candidate.title.contains(it, true) || candidate.genre.contains(it, true) }

            if (currentHigh && candidateHigh) score += 16
            if (currentChill && candidateChill) score += 16
            if (currentHigh && candidateChill) score -= 12
            if (currentChill && candidateHigh) score -= 12

            // 4. Subtle jitter for variety
            val randomJitter = (0..6).random()
            score += randomJitter

            candidate to score
        }.sortedByDescending { it.second }

        val bestCandidate = scored.firstOrNull()?.first ?: candidates.random()

        // Determine Fade Duration & Bass Dip
        val isSameAlbum = respectGapless && currentSong.album.isNotBlank() && currentSong.album.equals(bestCandidate.album, ignoreCase = true)
        val fadeDuration = when {
            isSameAlbum -> 0f
            !isSmartMode -> defaultFadeDuration
            else -> {
                if (currentSong.durationMs > 240_000) 6.0f
                else if (currentSong.durationMs < 120_000) 2.5f
                else 4.5f
            }
        }

        val rationale = when {
            isSameAlbum -> "Álbum continuo · Reproducción directa sin pausas"
            bestCandidate.artist.equals(currentSong.artist, ignoreCase = true) -> "Mismo artista · Transición armónica (${fadeDuration}s)"
            bestCandidate.genre.equals(currentSong.genre, ignoreCase = true) -> "Mismo género (${bestCandidate.genre}) · ${fadeDuration}s"
            else -> "Similitud acústica y ritmo · ${fadeDuration}s"
        }

        return TransitionPlan(
            nextSong = bestCandidate,
            fadeDurationSeconds = fadeDuration,
            applyBassDip = !isSameAlbum,
            rationale = rationale
        )
    }
}
