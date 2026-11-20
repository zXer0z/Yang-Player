package com.yang.player.data

import com.yang.player.model.Song
import org.junit.Assert.*
import org.junit.Test

class HomeRecommendationsTest {
    private fun song(id: Int, artist: String = "Artist $id", album: String = "Album $id") = Song(
        id = id.toString(), title = "Track $id", artist = artist, album = album,
        durationMs = 200_000, mediaUri = "content://music/$id"
    )
    private val library = (1..30).map { song(it) }

    @Test fun `empty and one-song libraries remain usable`() {
        assertTrue(HomeRecommendations.select(emptyList(), ListeningProfile()).featured.isEmpty())
        val only = song(1)
        val result = HomeRecommendations.select(listOf(only), ListeningProfile(), only.id)
        assertEquals(listOf(only), result.featured)
        assertEquals(1, result.albums.size)
    }

    @Test fun `cold start is stable within a day and rotates on another day`() {
        val profile = ListeningProfile()
        val first = HomeRecommendations.select(library, profile, now = 1000, day = 42)
        assertEquals(first, HomeRecommendations.select(library, profile, now = 1000, day = 42))
        assertNotEquals(first.featured, HomeRecommendations.select(library, profile, now = 1000, day = 43).featured)
        assertNotEquals(library.take(6), first.featured)
    }

    @Test fun `unfinished listening is prioritized and can resume`() {
        val track = song(28)
        val profile = ListeningProfile(mapOf(track.id to TrackListeningStats(1, 45_000, 900, 45_000)))
        val result = HomeRecommendations.select(library, profile, now = 1000, day = 42)
        assertEquals(track, result.featured.first())
        assertEquals(45_000L, HomeRecommendations.resumePosition(track, profile))
    }

    @Test fun `seeking without listening and almost completed songs do not resume`() {
        val track = song(1)
        val seekOnly = ListeningProfile(mapOf(track.id to TrackListeningStats(resumePositionMs = 80_000)))
        val finished = ListeningProfile(mapOf(track.id to TrackListeningStats(1, 185_000, 900, 185_000)))
        assertEquals(0L, HomeRecommendations.resumePosition(track, seekOnly))
        assertEquals(0L, HomeRecommendations.resumePosition(track, finished))
    }

    @Test fun `recommendations exclude the current track when alternatives exist`() {
        val result = HomeRecommendations.select(library, ListeningProfile(), currentSongId = "1", day = 42)
        assertFalse(result.featured.any { it.id == "1" })
        assertFalse(result.suggestions.any { it.id == "1" })
    }

    @Test fun `artist variety wins over filling the shelf with one artist`() {
        val songs = (1..12).map { song(it, "Same artist") } + (13..30).map { song(it) }
        val profile = ListeningProfile(favoriteIds = (1..12).map { it.toString() }.toSet())
        val result = HomeRecommendations.select(songs, profile, day = 42)
        assertTrue(result.featured.count { it.artist == "Same artist" } <= 2)
        assertTrue(result.albums.count { it.artist == "Same artist" } <= 2)
    }

    @Test fun `album names shared by different artists do not merge`() {
        val songs = listOf(song(1, "A", "Greatest Hits"), song(2, "B", "Greatest Hits"), song(3, "A", "Greatest Hits"))
        val albums = HomeRecommendations.buildAlbums(songs)
        assertEquals(2, albums.size)
        assertEquals(2, albums.first { it.artist == "A" }.songCount)
        assertNotEquals(albums[0].id, albums[1].id)
    }

    @Test fun `stale history and duplicate library entries cannot produce missing or duplicate recommendations`() {
        val profile = ListeningProfile(mapOf("deleted" to TrackListeningStats(100, 9_000_000, 999, 50_000)), setOf("deleted"))
        val result = HomeRecommendations.select(library + library, profile, day = 42)
        assertEquals(result.featured.size, result.featured.distinctBy { it.id }.size)
        assertEquals(result.albums.size, result.albums.distinctBy { it.id }.size)
        assertFalse(result.featured.any { it.id == "deleted" })
    }
}
