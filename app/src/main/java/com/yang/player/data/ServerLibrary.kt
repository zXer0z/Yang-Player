package com.yang.player.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.yang.player.model.AudioFormat
import com.yang.player.model.Song
import com.yang.player.BuildConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

enum class CloudStreamQuality(val key: String, val label: String, val format: String?, val maxBitRate: Int?) {
    RAW("raw", "Original", "raw", null),
    OPUS_192("opus192", "Opus 192 kbps", "opus", 192),
    MP3_128("mp3128", "MP3 128 kbps", "mp3", 128),
    OPUS_96("opus96", "Ahorro · Opus 96 kbps", "opus", 96),
    OPUS_64("opus64", "Conexión lenta · Opus 64 kbps", "opus", 64);

    companion object {
        fun fromKey(key: String?): CloudStreamQuality = entries.firstOrNull { it.key == key } ?: RAW
    }
}

data class CloudPlaylistSummary(val id: String, val name: String, val songIds: List<String>)
internal data class LibraryTrack(val song: Song, val filename: String, val mime: String, val navidromeId: String)
internal data class LibraryPage(val tracks: List<LibraryTrack>, val nextOffset: Int?)

internal class LibraryLogin(val base: String, val user: String, val password: String) {
    val id = digest("SHA-256", "$base\n$user").take(16)

    fun endpoint(method: String, vararg arguments: Pair<String, String>): Uri =
        endpointList(method, arguments.toList())

    fun endpointList(method: String, arguments: List<Pair<String, String>>): Uri {
        val salt = UUID.randomUUID().toString().replace("-", "")
        val builder = Uri.parse("$base/rest/$method.view").buildUpon()
            .appendQueryParameter("u", user).appendQueryParameter("t", digest("MD5", password + salt))
            .appendQueryParameter("s", salt).appendQueryParameter("v", "1.16.1")
            .appendQueryParameter("c", "YANG").appendQueryParameter("f", "json")
        arguments.forEach { (key, value) -> builder.appendQueryParameter(key, value) }
        return builder.build()
    }

    private fun digest(algorithm: String, input: String) = MessageDigest.getInstance(algorithm)
        .digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
}

internal object ServerLibrary {
    const val SCHEME = "yanglibrary"
    const val DEFAULT_URL = ""
    const val COVER_AUTHORITY = BuildConfig.APPLICATION_ID + ".serverart"
    const val modePreferenceKey = "public_server"
    private const val PAGE_SIZE = 60

    private fun preferences(context: Context) = context.noBackupFilesDir.resolve("navidrome-public-login.json")

    fun saved(context: Context): LibraryLogin? = runCatching {
        val json = JSONObject(preferences(context).readText())
        login(json.getString("base"), json.getString("user"), json.getString("password"))
    }.getOrNull()

    fun login(address: String, user: String, password: String): LibraryLogin {
        val base = address.trim().trimEnd('/')
        val uri = runCatching { URI(base) }.getOrNull()
        val validHost = uri?.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null
        require(validHost && uri?.scheme == "https") { "Ingresa una URL HTTPS válida para tu servidor Navidrome." }
        require(user.isNotBlank() && password.isNotBlank()) { "Ingresa el usuario y la contraseña de Navidrome." }
        return LibraryLogin(base, user.trim(), password)
    }

    fun connect(context: Context, profile: LibraryLogin): LibraryPage {
        val firstPage = songs(profile, "", 0)
        val file = preferences(context)
        val atomic = android.util.AtomicFile(file)
        val writer = atomic.startWrite()
        try {
            writer.write(JSONObject().put("base", profile.base).put("user", profile.user)
                .put("password", profile.password).toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(writer)
        } catch (error: Exception) {
            atomic.failWrite(writer)
            throw error
        }
        return firstPage
    }

    fun songs(profile: LibraryLogin, term: String, offset: Int): LibraryPage {
        val data = query(profile, "search3", "query" to term, "artistCount" to "0", "albumCount" to "0",
            "songCount" to PAGE_SIZE.toString(), "songOffset" to offset.toString())
        val rows = data.optJSONObject("searchResult3")?.optJSONArray("song")
        val count = rows?.length() ?: 0
        return LibraryPage(tracks(profile, rows), (offset + count).takeIf { count >= PAGE_SIZE })
    }

    private fun tracks(profile: LibraryLogin, rows: JSONArray?) = objects(rows).mapNotNull { row ->
        val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val title = row.optString("title", "Sin título")
        val artist = row.optString("displayArtist").ifBlank { row.optString("artist") }
        val suffix = row.optString("suffix").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }.orEmpty()
        val filename = listOf(artist, title).filter(String::isNotBlank).joinToString(" - ")
            .replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_").take(140).trim().ifBlank { "Pista" } +
            if (suffix.isBlank()) "" else ".$suffix"
        val media = Uri.Builder().scheme(SCHEME).authority(profile.id).appendPath(id).appendPath(filename).build().toString()
        val mime = row.optString("contentType", "audio/*").takeIf { it.startsWith("audio/") || it == "application/ogg" } ?: "audio/*"
        val albumArtist = row.optString("displayAlbumArtist").ifBlank {
            objects(row.optJSONArray("albumArtists")).mapNotNull { it.optString("name").takeIf(String::isNotBlank) }.joinToString(", ")
        }.ifBlank { row.optString("albumArtist") }
        val sampleRate = row.optInt("samplingRate")
        val bitDepth = row.optInt("bitDepth")
        val created = runCatching { java.time.Instant.parse(row.optString("created")).toEpochMilli() }.getOrDefault(0)
        LibraryTrack(Song(media, title, artist, row.optString("album"),
            row.optLong("duration") * 1000, media, artworkUri = coverUri(profile, row.optString("coverArt")),
            format = AudioFormat.fromPathOrMime(filename, mime), sampleRateHz = sampleRate, bitDepth = bitDepth,
            isHiRes = sampleRate > 48000 || bitDepth > 16, albumArtist = albumArtist, dateAdded = created,
            genre = row.optString("genre"), trackNumber = row.optInt("track"), discNumber = row.optInt("discNumber", 1)), filename, mime, id)
    }.distinctBy { it.song.id }

    private fun coverUri(profile: LibraryLogin, id: String): String? = id.takeIf(String::isNotBlank)?.let {
        Uri.Builder().scheme("content").authority(COVER_AUTHORITY).appendPath(profile.id).appendPath(it).build().toString()
    }

    fun cover(context: Context, uri: Uri): ByteArray {
        val profile = saved(context) ?: throw IOException("Navidrome no está conectado.")
        val segments = uri.pathSegments
        if (uri.authority != COVER_AUTHORITY || segments.size != 2 || segments.first() != profile.id) {
            throw IOException("Portada de otra conexión de Navidrome.")
        }
        val request = connection(profile.endpoint("getCoverArt", "id" to segments.last(), "size" to "600"))
        try {
            if (request.responseCode != 200 || request.contentType?.startsWith("image/") != true) throw IOException("Portada no disponible.")
            return request.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                val cover = java.io.ByteArrayOutputStream()
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (cover.size() + count > 2 * 1024 * 1024) throw IOException("Portada demasiado grande.")
                    cover.write(buffer, 0, count)
                }
                cover.toByteArray()
            }
        } finally {
            request.disconnect()
        }
    }

    private fun objects(rows: JSONArray?) = (0 until (rows?.length() ?: 0)).mapNotNull { rows?.optJSONObject(it) }

    fun streamQuality(context: Context): CloudStreamQuality = CloudStreamQuality.fromKey(
        context.applicationContext.getSharedPreferences("library_source", Context.MODE_PRIVATE).getString("stream_quality", null)
    )

    fun setStreamQuality(context: Context, quality: CloudStreamQuality) {
        context.applicationContext.getSharedPreferences("library_source", Context.MODE_PRIVATE)
            .edit().putString("stream_quality", quality.key).apply()
    }

    fun trackId(mediaUri: String): String? {
        val uri = Uri.parse(mediaUri)
        if (uri.scheme != SCHEME || uri.pathSegments.size != 2) return null
        return uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    fun stream(context: Context, media: Uri): Uri {
        val profile = saved(context) ?: throw IOException("Configura Navidrome en Ajustes.")
        val q = streamQuality(context)
        val args = mutableListOf<Pair<String, String>>()
        q.format?.let { args.add("format" to it) }
        q.maxBitRate?.let { args.add("maxBitRate" to it.toString()) }
        return trackEndpoint(profile, media, "stream", *args.toTypedArray())
    }

    private fun trackEndpoint(profile: LibraryLogin, media: Uri, method: String, vararg extra: Pair<String, String>): Uri {
        if (media.scheme != SCHEME || media.host != profile.id || media.pathSegments.size != 2) {
            throw IOException("La canción pertenece a otra conexión de Navidrome.")
        }
        return profile.endpoint(method, "id" to media.pathSegments.first(), *extra)
    }

    fun starredTrackIds(profile: LibraryLogin): Set<String> {
        val data = query(profile, "getStarred2")
        val songs = data.optJSONObject("starred2")?.optJSONArray("song")
        return objects(songs).mapNotNull { it.optString("id").takeIf(String::isNotBlank) }.toSet()
    }

    fun setStarred(profile: LibraryLogin, trackId: String, starred: Boolean) {
        query(profile, if (starred) "star" else "unstar", "id" to trackId)
    }

    fun scrobble(profile: LibraryLogin, trackId: String, submission: Boolean) {
        query(profile, "scrobble", "id" to trackId, "submission" to submission.toString())
    }

    fun albumKeys(profile: LibraryLogin, type: String, size: Int = 12): List<String> {
        val data = query(profile, "getAlbumList2", "type" to type, "size" to size.toString())
        val rows = data.optJSONObject("albumList2")?.optJSONArray("album")
        return objects(rows).mapNotNull { row ->
            val name = row.optString("name").ifBlank { row.optString("album") }
            val artist = row.optString("artist")
            if (name.isBlank()) null else "${name.trim().lowercase()}|${artist.trim().lowercase()}"
        }
    }

    fun similarTrackIds(profile: LibraryLogin, trackId: String, count: Int = 24): List<String> {
        val data = query(profile, "getSimilarSongs2", "id" to trackId, "count" to count.toString())
        val rows = data.optJSONObject("similarSongs2")?.optJSONArray("song")
        return objects(rows).mapNotNull { it.optString("id").takeIf(String::isNotBlank) }
    }

    fun playlists(profile: LibraryLogin): List<CloudPlaylistSummary> {
        val listData = query(profile, "getPlaylists")
        val rows = listData.optJSONObject("playlists")?.optJSONArray("playlist")
        return objects(rows).mapNotNull { row ->
            val id = row.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val name = row.optString("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val detail = runCatching { query(profile, "getPlaylist", "id" to id) }.getOrNull()
            val entries = detail?.optJSONObject("playlist")?.optJSONArray("entry")
            val songIds = objects(entries).mapNotNull { it.optString("id").takeIf(String::isNotBlank) }
            CloudPlaylistSummary(id, name, songIds)
        }
    }

    fun createPlaylist(profile: LibraryLogin, name: String, trackIds: List<String>): CloudPlaylistSummary {
        val args = mutableListOf("name" to name.trim())
        trackIds.distinct().forEach { args.add("songId" to it) }
        val data = queryList(profile, "createPlaylist", args)
        val pl = data.optJSONObject("playlist")
        val id = pl?.optString("id")?.takeIf(String::isNotBlank) ?: ""
        return CloudPlaylistSummary(id, name.trim(), trackIds.distinct())
    }

    fun addToPlaylist(profile: LibraryLogin, playlistId: String, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        val args = mutableListOf("playlistId" to playlistId)
        trackIds.distinct().forEach { args.add("songIdToAdd" to it) }
        queryList(profile, "updatePlaylist", args)
    }

    fun lyrics(context: Context, mediaUri: String, artist: String, title: String): String? {
        val profile = saved(context) ?: return null
        val parsed = Uri.parse(mediaUri)
        if (parsed.scheme != SCHEME || parsed.host != profile.id || parsed.pathSegments.size != 2) return null
        val trackId = parsed.pathSegments.first()
        runCatching {
            val data = query(profile, "getLyricsBySongId", "id" to trackId)
            val list = data.optJSONObject("lyricsList")?.optJSONArray("structuredLyrics")
            val blocks = objects(list)
            val picked = blocks.firstOrNull { it.optBoolean("synced") } ?: blocks.firstOrNull()
            val lines = objects(picked?.optJSONArray("line"))
            if (lines.isNotEmpty()) {
                val synced = picked?.optBoolean("synced") == true
                return lines.mapNotNull { line ->
                    val text = line.optString("value")
                    if (synced && line.has("start")) {
                        val ms = line.optLong("start").coerceAtLeast(0L)
                        val totalCs = ms / 10L
                        val cs = totalCs % 100L
                        val totalSec = totalCs / 100L
                        val sec = totalSec % 60L
                        val min = totalSec / 60L
                        "[%02d:%02d.%02d]%s".format(java.util.Locale.US, min, sec, cs, text)
                    } else text
                }.joinToString("\n").takeIf { it.isNotBlank() }
            }
        }
        return runCatching {
            val data = query(profile, "getLyrics", "artist" to artist, "title" to title)
            data.optJSONObject("lyrics")?.optString("value")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun query(profile: LibraryLogin, method: String, vararg arguments: Pair<String, String>): JSONObject =
        queryList(profile, method, arguments.toList())

    private fun queryList(profile: LibraryLogin, method: String, arguments: List<Pair<String, String>>): JSONObject {
        val connection = connection(profile.endpointList(method, arguments))
        try {
            if (connection.responseCode != 200) throw IOException("Navidrome respondió HTTP ${connection.responseCode}.")
            val buffer = ByteArray(2 * 1024 * 1024 + 1)
            val count = connection.inputStream.use { source ->
                var offset = 0
                while (offset < buffer.size) {
                    val read = source.read(buffer, offset, buffer.size - offset)
                    if (read < 0) break
                    offset += read
                }
                offset
            }
            if (count == buffer.size) throw IOException("La respuesta del servidor es demasiado grande.")
            val response = JSONObject(String(buffer, 0, count, Charsets.UTF_8)).getJSONObject("subsonic-response")
            if (response.optString("status") != "ok") throw IOException("Navidrome rechazó la consulta. Revisa la cuenta y sus permisos.")
            return response
        } finally {
            connection.disconnect()
        }
    }

    suspend fun save(context: Context, profile: LibraryLogin, track: LibraryTrack, document: Uri? = null) {
        val resolver = context.contentResolver
        val connection = connection(trackEndpoint(profile, Uri.parse(track.song.mediaUri), "download"))
        var target = document
        var complete = false
        try {
            if (connection.responseCode != 200 || connection.contentType.orEmpty().let { "json" in it || "xml" in it || "text/" in it }) {
                throw IOException("Navidrome no permitió descargar el archivo. Revisa el permiso de descarga.")
            }
            if (target == null && Build.VERSION.SDK_INT >= 29) target = createAudio(context, track)
            val destination = target ?: throw IOException("Selecciona dónde guardar la canción.")
            val output = resolver.openOutputStream(destination, "w") ?: throw IOException("No se pudo abrir el destino.")
            output.use { sink ->
                connection.inputStream.use { source ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        total += read
                    }
                    val expected = connection.getHeaderField("Content-Length")?.toLongOrNull()
                    if (total == 0L || (expected != null && total != expected)) throw IOException("La descarga quedó incompleta.")
                }
            }
            if (document == null && Build.VERSION.SDK_INT >= 29) {
                resolver.update(destination, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }
            complete = true
        } finally {
            connection.disconnect()
            if (!complete) target?.let { failed -> runCatching {
                if (document == null) resolver.delete(failed, null, null)
                else DocumentsContract.deleteDocument(resolver, failed)
            } }
        }
    }

    @RequiresApi(29)
    private fun createAudio(context: Context, track: LibraryTrack): Uri? = context.contentResolver.insert(
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, track.filename)
            put(MediaStore.Audio.Media.MIME_TYPE, track.mime.takeUnless { it == "audio/*" } ?: "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/YANG")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        })

    private fun connection(uri: Uri) = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
        connectTimeout = 6_000
        readTimeout = 15_000
        instanceFollowRedirects = false
    }
}
