package com.yang.player.data

import com.yang.player.model.Album
import com.yang.player.model.Song
import java.time.LocalDate
import java.util.Locale
import java.util.Random
import kotlin.math.ln

data class HomeSelection(val featured: List<Song>, val suggestions: List<Song>, val albums: List<Album>)

/** Deterministic within a day, with listening affinity and room for discovery. */
object HomeRecommendations {
    private fun normalized(value: String) = value.trim().lowercase(Locale.ROOT)
    fun albumKey(song: Song) = "${normalized(song.album)}::${normalized(song.artist)}"

    fun buildAlbums(songs: List<Song>): List<Album> = songs
        .filter { normalized(it.album) !in setOf("", "álbum desconocido", "unknown", "<unknown>") }
        .groupBy(::albumKey).map { (key, tracks) ->
            val first = tracks.first()
            Album(key, first.album, first.artist,
                tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                tracks.size, tracks.firstOrNull { it.isHiRes }?.format ?: first.format)
        }.sortedBy { normalized(it.name) }

    fun resumePosition(song: Song, profile: ListeningProfile): Long {
        val stats = profile.tracks[song.id] ?: return 0
        return stats.resumePositionMs.takeIf {
            stats.listenedMs >= 10_000 && it >= 10_000 && it < song.durationMs * 0.90
        } ?: 0
    }

    fun select(
        songs: List<Song>,
        profile: ListeningProfile,
        currentSongId: String? = null,
        now: Long = System.currentTimeMillis(),
        day: Long = LocalDate.now().toEpochDay()
    ): HomeSelection {
        val library = songs.distinctBy { it.id }
        if (library.isEmpty()) return HomeSelection(emptyList(), emptyList(), emptyList())
        val affinity = library.groupBy { normalized(it.artist) }.mapValues { (_, tracks) ->
            tracks.sumOf { song ->
                val stats = profile.tracks[song.id]
                ln(1.0 + (stats?.playCount ?: 0)) * 2 +
                    ln(1.0 + (stats?.listenedMs ?: 0) / 60_000.0) +
                    if (song.id in profile.favoriteIds) 4.0 else 0.0
            }
        }
        fun noise(id: String) = Random(
            (day * 6364136223846793005L) xor (id.hashCode().toLong() * 1442695040888963407L)
        ).nextDouble()
        fun score(song: Song): Double {
            val stats = profile.tracks[song.id]
            val recentlyPlayed = stats != null && stats.lastPlayedAt > 0 && now - stats.lastPlayedAt < 12 * 60 * 60_000L
            return ln(1 + (affinity[normalized(song.artist)] ?: 0.0)) * 3 +
                (if (song.id in profile.favoriteIds) 3.0 else 0.0) +
                (if ((stats?.playCount ?: 0) == 0) 4.0 else 0.0) + noise(song.id) * 8 -
                (if (recentlyPlayed) 7.0 else 0.0)
        }
        val pool = library.filter { it.id != currentSongId }.ifEmpty { library }
        val discovery = pool.sortedByDescending(::score)
        val unfinished = pool.filter { resumePosition(it, profile) > 0 }
            .sortedByDescending { profile.tracks[it.id]?.lastPlayedAt }.take(2)
        val recent = pool.filter { (profile.tracks[it.id]?.playCount ?: 0) > 0 }
            .sortedByDescending { profile.tracks[it.id]?.lastPlayedAt }.take(1)
        fun diverse(candidates: List<Song>, count: Int): List<Song> {
            val selected = mutableListOf<Song>()
            candidates.distinctBy { it.id }.forEach { song ->
                if (selected.size < count && selected.count { normalized(it.artist) == normalized(song.artist) } < 2 &&
                    selected.none { albumKey(it) == albumKey(song) }) selected.add(song)
            }
            candidates.distinctBy { it.id }.forEach { song ->
                if (selected.size < count && selected.none { it.id == song.id }) selected.add(song)
            }
            return selected
        }
        val featured = diverse(unfinished + recent + discovery, 6)
        val suggestions = diverse(discovery.filter { song -> featured.none { it.id == song.id } } + discovery, 4)
        val albumTracks = library.groupBy(::albumKey)
        val rankedAlbums = buildAlbums(library).sortedByDescending { album ->
            val tracks = albumTracks[album.id].orEmpty()
            tracks.maxOfOrNull(::score)?.plus(noise(album.id) * 5) ?: 0.0
        }
        val albums = mutableListOf<Album>()
        rankedAlbums.forEach { album ->
            if (albums.size < 10 && albums.count { normalized(it.artist) == normalized(album.artist) } < 2) albums.add(album)
        }
        rankedAlbums.forEach { album ->
            if (albums.size < 10 && albums.none { it.id == album.id }) albums.add(album)
        }
        return HomeSelection(featured, suggestions, albums)
    }
}
