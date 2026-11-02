package com.yang.player.data

import com.yang.player.model.Album
import com.yang.player.model.Song
import java.text.Normalizer
import java.util.Locale

data class ArtistCollection(val id: String, val name: String, val songs: List<Song>)

/** A library snapshot shares one interpretation of credits across lists, details and queues. */
class LibraryGrouping(songs: List<Song>) {
    private val library = songs.distinctBy { it.id }
    private val credits = ArtistCredits(library.flatMap { listOf(it.artist, it.albumArtist) })
    private val keys = library.associate { it.id to albumIdentity(it) }
    val albumTracks: Map<String, List<Song>> = library.groupBy { keys.getValue(it.id) }
    val albums: List<Album> = albumTracks.mapNotNull { (key, tracks) ->
        val first = tracks.first()
        if (!knownAlbum(first.album)) return@mapNotNull null
        Album(key, first.album, albumCredit(first), tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
            tracks.size, tracks.firstOrNull { it.isHiRes }?.format ?: first.format)
    }.sortedBy { normalized(it.name) }
    val artists: List<ArtistCollection> = run {
        val names = linkedMapOf<String, String>()
        val groups = linkedMapOf<String, MutableList<Song>>()
        library.forEach { song ->
            credits.names(song.artist).forEach { name ->
                val key = normalized(name)
                names.putIfAbsent(key, name)
                groups.getOrPut(key) { mutableListOf() }.add(song)
            }
        }
        groups.map { (key, tracks) -> ArtistCollection(key, names.getValue(key), tracks.distinctBy { it.id }) }
            .sortedBy { it.id }
    }
    private val artistTracks = artists.associate { it.id to it.songs }

    fun songsByArtist(key: String): List<Song> = artistTracks[key].orEmpty()
    fun songsByAlbum(key: String): List<Song> = albumTracks[key].orEmpty()
    fun albumKey(song: Song): String = keys[song.id] ?: albumIdentity(song)
    fun primaryArtist(credit: String): String = credits.names(credit).firstOrNull().orEmpty()
    fun artistKey(credit: String): String = normalized(primaryArtist(credit))
    fun albumCredit(song: Song): String = song.albumArtist.takeIf(::knownArtist) ?: primaryArtist(song.artist)
    private fun albumIdentity(song: Song): String = "${normalized(song.album)}::${normalized(albumCredit(song))}"

    companion object {
        private val spaces = Regex("\\s+")
        fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim().replace(spaces, " ").lowercase(Locale.ROOT)
        private fun knownArtist(value: String) = normalized(value) !in setOf("", "<unknown>", "unknown", "desconocido", "artista desconocido")
        private fun knownAlbum(value: String) = normalized(value) !in setOf("", "<unknown>", "unknown", "álbum desconocido", "sin álbum")
    }

    /** Explicit featuring tags are safe. Ambiguous punctuation needs evidence from this library. */
    private class ArtistCredits(values: List<String>) {
        private val soft = Regex("\\s*(?:,|&)\\s*|\\s+[x×]\\s+", RegexOption.IGNORE_CASE)
        private val featuring = Regex("\\s*\\(?\\b(?:feat\\.?|ft\\.?|featuring)\\s+", RegexOption.IGNORE_CASE)
        private fun explicit(value: String): List<String> = value.replace(featuring, ";").split(';')
            .mapIndexed { index, part -> (if (index > 0) part.trim().trimEnd(')') else part).trim() }
            .filter(String::isNotBlank)
        private val pieces = values.flatMap(::explicit).distinctBy(::normalized)
        private val atoms = pieces.filter { !soft.containsMatchIn(it) }.map(::normalized).toSet()
        // Count distinct credit strings, rather than tracks: repeated band names remain intact.
        private val recurringLeads = pieces.filter { soft.containsMatchIn(it) }
            .filter { soft.split(it).first().trim().contains(' ') }
            .groupingBy { normalized(soft.split(it).first()) }.eachCount().filterValues { it > 1 }.keys
        private val evidence = atoms + recurringLeads
        private val opaque = pieces.filter { part ->
            soft.containsMatchIn(part) && soft.split(part).none { normalized(it) in evidence }
        }.sortedByDescending { it.length }

        private val opaqueKeys = opaque.map(::normalized).toSet()
        private val bandPatterns = opaque.map { band -> band to Regex("(?<![\\p{L}\\p{N}])${Regex.escape(band)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE) }
        private val resolved = mutableMapOf<String, List<String>>()

        fun names(value: String): List<String> = resolved.getOrPut(value) { parse(value) }
        private fun parse(value: String): List<String> = explicit(value).flatMap { part ->
            if (!soft.containsMatchIn(part) || normalized(part) in opaqueKeys) listOf(part)
            else {
                // Preserve compound band names inside a confirmed collaboration as well.
                var protected = part
                val replacements = mutableMapOf<String, String>()
                bandPatterns.forEachIndexed { index, (band, pattern) ->
                    val token = "\uE000$index\uE001"
                    if (pattern.containsMatchIn(protected)) {
                        protected = protected.replace(pattern, token)
                        replacements[token] = band
                    }
                }
                val parts = soft.split(protected).map { replacements[it.trim()] ?: it.trim() }.filter(String::isNotBlank)
                if (parts.any { normalized(it) in evidence || normalized(it) in opaqueKeys }) parts else listOf(part)
            }
        }.distinctBy(::normalized).ifEmpty { listOf("Desconocido") }
    }
}
