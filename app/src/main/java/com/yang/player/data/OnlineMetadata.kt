package com.yang.player.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.AtomicFile
import androidx.media3.common.util.UnstableApi
import com.yang.player.lyrics.LyricsRepository
import com.yang.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

enum class CompletionKind { METADATA, ARTWORK, LYRICS }

data class MetadataMatch(
    val id: String, val title: String, val artist: String, val album: String,
    val durationMs: Long, val artwork: String = "", val lyrics: String = "",
    val synced: Boolean = false, val sourceUrl: String = ""
)

/** Explicit searches only. No audio upload and no changes to the original audio files. */
@UnstableApi
class OnlineMetadata private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("metadata_completion_v1", Context.MODE_PRIVATE)
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    private val writeLock = Mutex()

    private fun key(song: Song) = MessageDigest.getInstance("SHA-256")
        .digest(song.mediaUri.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun apply(song: Song): Song {
        if (song.isOnline) return song
        val raw = prefs.getString(key(song), null) ?: return song
        val saved = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        fun fill(original: String, field: String) = if (missing(original)) saved.optString(field).ifBlank { original } else original
        val cover = saved.optString("artwork").takeIf { it.isNotBlank() && File(it).isFile }
        return song.copy(title = fill(song.title, "title"), artist = fill(song.artist, "artist"),
            album = fill(song.album, "album"), artworkUri = cover?.let { Uri.fromFile(File(it)).toString() } ?: song.artworkUri)
    }

    suspend fun hasArtwork(song: Song): Boolean = withContext(Dispatchers.IO) {
        val current = apply(song)
        if (current.artworkUri != null && current.artworkUri != song.mediaUri) {
            val uri = Uri.parse(current.artworkUri)
            if (uri.scheme in setOf("http", "https")) return@withContext true
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) return@withContext true
        }
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(context, Uri.parse(song.mediaUri))
            reader.embeddedPicture?.isNotEmpty() == true
        } finally { reader.release() }
    }

    suspend fun search(query: String, kind: CompletionKind, song: Song): List<MetadataMatch> = withContext(Dispatchers.IO) {
        require(query.trim().length >= 2) { "Escribe al menos dos caracteres." }
        val term = URLEncoder.encode(query.trim().take(240), "UTF-8")
        val rows = if (kind == CompletionKind.LYRICS) JSONArray(String(fetch("https://lrclib.net/api/search?q=$term", 4 * 1024 * 1024), Charsets.UTF_8))
        else JSONObject(String(fetch("https://itunes.apple.com/search?media=music&entity=song&country=us&limit=25&term=$term", 2 * 1024 * 1024), Charsets.UTF_8)).getJSONArray("results")
        (0 until minOf(rows.length(), 100)).mapNotNull { index ->
            val row = rows.getJSONObject(index)
            if (kind == CompletionKind.LYRICS) {
                val synced = row.optString("syncedLyrics").takeUnless { it == "null" }.orEmpty()
                val plain = row.optString("plainLyrics").takeUnless { it == "null" }.orEmpty()
                if (synced.isBlank() && plain.isBlank()) null else MetadataMatch(
                    row.optString("id"), row.optString("trackName"), row.optString("artistName"), row.optString("albumName"),
                    (row.optDouble("duration", 0.0) * 1000).toLong(), lyrics = synced.ifBlank { plain },
                    synced = synced.isNotBlank(), sourceUrl = "https://lrclib.net/")
            } else {
                val art = row.optString("artworkUrl100").replace("http://", "https://")
                    .replace("100x100bb", "600x600bb")
                if (row.optString("kind") != "song" || (kind == CompletionKind.ARTWORK && art.isBlank())) null
                else MetadataMatch(row.optString("trackId"), row.optString("trackName"), row.optString("artistName"),
                    row.optString("collectionName"), row.optLong("trackTimeMillis"), art,
                    sourceUrl = "https://music.apple.com/us/song/" + row.optLong("trackId"))
            }
        }.distinctBy { it.id }.sortedBy { if (song.durationMs > 0 && it.durationMs > 0) abs(song.durationMs - it.durationMs) else Long.MAX_VALUE }
    }

    suspend fun save(song: Song, match: MetadataMatch, kind: CompletionKind): String = writeLock.withLock {
        withContext(Dispatchers.IO) {
            require(!song.isOnline) { "Esta opción es para canciones locales." }
            val current = apply(song)
            val saved = JSONObject(prefs.getString(key(song), "{}")!!)
            val message = when (kind) {
                CompletionKind.METADATA -> {
                    var count = 0
                    listOf(Triple("title", current.title, match.title), Triple("artist", current.artist, match.artist),
                        Triple("album", current.album, match.album)).forEach { (field, old, value) ->
                        if (missing(old) && value.isNotBlank()) { saved.put(field, value); count++ }
                    }
                    require(count > 0) { "Título, artista y álbum ya tienen datos. Se conservaron." }
                    "$count campos completados en Yang."
                }
                CompletionKind.ARTWORK -> {
                    require(!hasArtwork(current)) { "Esta canción ya tiene carátula. Se conservó." }
                    require(match.artwork.isNotBlank()) { "Este resultado no tiene carátula." }
                    val bytes = fetch(match.artwork, 8 * 1024 * 1024, artwork = true)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096) { "La imagen recibida no es válida." }
                    val file = File(context.filesDir, "completed_artwork/${key(song)}.jpg")
                    file.parentFile!!.mkdirs()
                    writeAtomic(file, bytes)
                    saved.put("artwork", file.absolutePath)
                    "Carátula guardada en Yang."
                }
                CompletionKind.LYRICS -> {
                    val repository = LyricsRepository(context)
                    require(repository.load(song) == null) { "Esta canción ya tiene letras. Se conservaron." }
                    repository.saveOnline(song, match.lyrics)
                    "Letra guardada en Yang."
                }
            }
            if (kind != CompletionKind.LYRICS) {
                require(prefs.edit().putString(key(song), saved.toString()).commit()) { "No se pudieron guardar los datos." }
            }
            revision.value++
            message
        }
    }

    private suspend fun fetch(address: String, maxBytes: Int, artwork: Boolean = false): ByteArray {
        var url = URL(address)
        repeat(5) {
            val host = url.host.lowercase()
            require(url.protocol == "https" && url.userInfo == null &&
                if (artwork) host == "mzstatic.com" || host.endsWith(".mzstatic.com")
                else host in setOf("itunes.apple.com", "lrclib.net")) { "La fuente devolvió una dirección no válida." }
            coroutineContext.ensureActive()
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "YangPlayer/1.0 (metadata lookup)")
            connection.setRequestProperty("Accept", if (artwork) "image/*" else "application/json")
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    url = URL(url, connection.getHeaderField("Location") ?: error("Redirección incompleta."))
                } else {
                    require(status == 200) { if (status == 429) "Demasiadas búsquedas. Espera un momento." else "La fuente no está disponible (HTTP $status)." }
                    require(connection.contentLengthLong <= maxBytes) { "La respuesta es demasiado grande." }
                    return connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= maxBytes) { "La respuesta es demasiado grande." }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
            } finally { connection.disconnect() }
        }
        error("La fuente redirigió demasiadas veces.")
    }

    companion object {
        @Volatile private var instance: OnlineMetadata? = null
        fun get(context: Context): OnlineMetadata = instance ?: synchronized(this) {
            instance ?: OnlineMetadata(context.applicationContext).also { instance = it }
        }
        fun missing(value: String) = value.trim().lowercase() in setOf("", "<unknown>", "unknown", "unknown artist", "unknown album", "desconocido", "artista desconocido", "álbum desconocido", "album desconocido", "sin título", "sin titulo")
        fun writeAtomic(file: File, bytes: ByteArray) {
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try { output.write(bytes); atomic.finishWrite(output) }
            catch (error: Exception) { atomic.failWrite(output); throw error }
        }
    }
}
