package com.yang.player.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.yang.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class OfficialAlbumTrack(
    val id: Long,
    val position: Int,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val previewUrl: String? = null
)

class AlbumTracksRepository private constructor(private val context: Context) {
    private val cacheDir = File(context.cacheDir, "album_tracklists").apply { mkdirs() }
    private val memoryCache = ConcurrentHashMap<String, List<OfficialAlbumTrack>>()

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun cleanTitle(title: String): String {
        val withoutParentheses = title.replace(Regex("\\s*[\\[\\(].*?[\\]\\)]"), "").trim()
        return LibraryGrouping.normalized(withoutParentheses.ifBlank { title })
    }

    suspend fun getMissingTracks(
        albumName: String,
        artistName: String,
        localTracks: List<Song>
    ): List<OfficialAlbumTrack> = withContext(Dispatchers.IO) {
        val normAlbum = LibraryGrouping.normalized(albumName)
        val normArtist = LibraryGrouping.normalized(artistName)
        if (normAlbum.isBlank() || normArtist.isBlank()) return@withContext emptyList()

        val official = getOfficialTracks(albumName, artistName)
        if (official.isEmpty()) return@withContext emptyList()

        val localCleanTitles = localTracks.map { cleanTitle(it.title) }.toSet()
        val localNormalizedTitles = localTracks.map { LibraryGrouping.normalized(it.title) }.toSet()

        official.filter { track ->
            val cleanOfficial = cleanTitle(track.title)
            val normOfficial = LibraryGrouping.normalized(track.title)
            val isPresent = localCleanTitles.contains(cleanOfficial) ||
                    localNormalizedTitles.contains(normOfficial) ||
                    localTracks.any { local ->
                        val normLocal = LibraryGrouping.normalized(local.title)
                        normLocal.contains(cleanOfficial) || cleanOfficial.contains(normLocal)
                    }
            !isPresent
        }
    }

    private suspend fun getOfficialTracks(albumName: String, artistName: String): List<OfficialAlbumTrack> {
        val cacheKey = "${LibraryGrouping.normalized(albumName)}::${LibraryGrouping.normalized(artistName)}"
        memoryCache[cacheKey]?.let { return it }

        val diskFile = File(cacheDir, "album_${md5(cacheKey)}.json")
        if (diskFile.exists() && diskFile.length() > 0) {
            val cached = parseTracksJson(diskFile.readText())
            if (cached.isNotEmpty()) {
                memoryCache[cacheKey] = cached
                return cached
            }
        }

        val fetched = fetchAlbumTracksFromDeezer(albumName, artistName)
        if (fetched.isNotEmpty()) {
            memoryCache[cacheKey] = fetched
            runCatching {
                val array = JSONArray()
                fetched.forEach { t ->
                    val obj = JSONObject()
                    obj.put("id", t.id)
                    obj.put("position", t.position)
                    obj.put("title", t.title)
                    obj.put("artist", t.artist)
                    obj.put("duration", t.durationSec)
                    obj.put("preview", t.previewUrl.orEmpty())
                    array.put(obj)
                }
                diskFile.writeText(array.toString())
            }
        }
        return fetched
    }

    private fun parseTracksJson(jsonStr: String): List<OfficialAlbumTrack> {
        return try {
            val array = JSONArray(jsonStr)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                OfficialAlbumTrack(
                    id = obj.optLong("id"),
                    position = obj.optInt("position", i + 1),
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    durationSec = obj.optInt("duration"),
                    previewUrl = obj.optString("preview").takeIf { it.isNotBlank() }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchAlbumTracksFromDeezer(albumName: String, artistName: String): List<OfficialAlbumTrack> {
        return try {
            val query = "$albumName $artistName".trim()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = URL("https://api.deezer.com/search/album?q=$encoded")
            val conn = (searchUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
                setRequestProperty("User-Agent", "YangPlayer/1.0 (Android; Audiophile Music Player)")
                setRequestProperty("Accept", "application/json")
            }
            val albumId: Long = try {
                if (conn.responseCode != 200) return emptyList()
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                val data = root.optJSONArray("data") ?: return emptyList()
                if (data.length() == 0) return emptyList()

                val targetAlbum = LibraryGrouping.normalized(albumName)
                var bestId: Long = data.optJSONObject(0)?.optLong("id") ?: return emptyList()
                for (i in 0 until minOf(data.length(), 6)) {
                    val item = data.optJSONObject(i) ?: continue
                    val titleNorm = LibraryGrouping.normalized(item.optString("title"))
                    if (titleNorm == targetAlbum) {
                        bestId = item.optLong("id")
                        break
                    }
                }
                bestId
            } finally {
                conn.disconnect()
            }

            if (albumId <= 0) return emptyList()

            // Fetch tracks for this album
            val tracksUrl = URL("https://api.deezer.com/album/$albumId/tracks")
            val tracksConn = (tracksUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
                setRequestProperty("User-Agent", "YangPlayer/1.0 (Android; Audiophile Music Player)")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (tracksConn.responseCode != 200) return emptyList()
                val json = tracksConn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                val data = root.optJSONArray("data") ?: return emptyList()

                val result = mutableListOf<OfficialAlbumTrack>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val artistObj = item.optJSONObject("artist")
                    val trackArtist = artistObj?.optString("name")?.takeIf { it.isNotBlank() } ?: artistName
                    result.add(
                        OfficialAlbumTrack(
                            id = item.optLong("id"),
                            position = item.optInt("track_position", i + 1),
                            title = item.optString("title"),
                            artist = trackArtist,
                            durationSec = item.optInt("duration"),
                            previewUrl = item.optString("preview").takeIf { it.isNotBlank() }
                        )
                    )
                }
                result
            } finally {
                tracksConn.disconnect()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AlbumTracksRepository? = null

        fun get(context: Context): AlbumTracksRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlbumTracksRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

@Composable
fun rememberMissingTracks(
    albumName: String,
    artistName: String,
    localTracks: List<Song>
): State<List<OfficialAlbumTrack>> {
    val context = LocalContext.current
    val repository = remember { AlbumTracksRepository.get(context) }
    val result = remember(albumName, artistName, localTracks.size) {
        mutableStateOf<List<OfficialAlbumTrack>>(emptyList())
    }

    LaunchedEffect(albumName, artistName, localTracks.size) {
        if (albumName.isNotBlank() && artistName.isNotBlank()) {
            val missing = repository.getMissingTracks(albumName, artistName, localTracks)
            result.value = missing
        } else {
            result.value = emptyList()
        }
    }

    return result
}
