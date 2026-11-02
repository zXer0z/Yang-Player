package com.yang.player.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.yang.player.model.AudioFormat
import com.yang.player.model.Song
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

/** Read-only access to folders selected through Android's document picker. */
class DocumentAudioLibrary(private val context: Context) {
    private val preferences = context.getSharedPreferences("music_folders", Context.MODE_PRIVATE)
    private val resolver = context.contentResolver
    private val cacheFile = context.cacheDir.resolve("document_audio_v1.json")
    fun folders(): List<String> = preferences.getStringSet("trees", emptySet()).orEmpty().sorted()
    fun folderLabel(value: String): String = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(value)).substringAfter(':').ifBlank { "Almacenamiento" } }.getOrDefault("Carpeta de música")
    fun addFolder(uri: Uri) {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        preferences.edit().putStringSet("trees", (folders() + uri.toString()).toSet()).apply()
    }

    suspend fun scan(existing: List<Song>): Pair<List<Song>, Int> {
        val result = mutableListOf<Song>()
        val paths = existing.mapNotNull { it.dataPath.takeIf(String::isNotBlank) }.toMutableSet()
        val visited = mutableSetOf<String>()
        val cached = runCatching { JSONObject(cacheFile.readText()) }.getOrDefault(JSONObject())
        val fresh = JSONObject()
        var failed = 0
        for (folder in folders()) {
            currentCoroutineContext().ensureActive()
            val tree = Uri.parse(folder)
            val pending = ArrayDeque<String>()
            pending.add(DocumentsContract.getTreeDocumentId(tree))
            try {
                while (pending.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val parent = pending.removeFirst()
                    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
                    resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val id = cursor.getString(0)
                            if (!visited.add("${tree.authority}:$id")) continue
                            val name = cursor.getString(1).orEmpty()
                            val mime = cursor.getString(2).orEmpty()
                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { pending.add(id); continue }
                            if (!mime.startsWith("audio/") && AudioFormat.fromPathOrMime(name, mime) == AudioFormat.UNKNOWN) continue
                            val path = storagePath(tree, id)
                            if (path != null && !paths.add(path)) continue
                            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                            val stamp = "${cursor.getLong(3)}:${cursor.getLong(4)}"
                            val entry = cached.optJSONObject(uri.toString())?.takeIf { cursor.getLong(3) > 0 && it.optString("stamp") == stamp && it.has("rate") && it.has("albumArtist") }
                            val song = runCatching { if (entry != null) decode(entry, uri) else readSong(uri, name, mime) }.getOrNull()
                            if (song != null) {
                                result.add(song)
                                fresh.put(uri.toString(), encode(song).put("stamp", stamp))
                            } else failed++
                        }
                    } ?: throw IOException("No se pudo leer la carpeta")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { failed++ }
        }
        runCatching { cacheFile.writeText(fresh.toString()) }
        return result to failed
    }

    fun open(uri: Uri): Song {
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Audio"
        return readSong(uri, name, resolver.getType(uri))
    }

    private fun readSong(uri: Uri, filename: String, mime: String?): Song {
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(context, uri)
            fun tag(key: Int) = reader.extractMetadata(key)?.trim()?.takeIf { it.isNotBlank() && it != "<unknown>" }
            val duration = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            if (duration <= 0) throw IOException("No se pudo leer este audio")
            val format = AudioFormat.fromPathOrMime(filename, mime)
            val source = AudioSourceMetadata.read(context, uri)
            return Song(id(uri), tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: filename.substringBeforeLast('.'),
                tag(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Desconocido",
                tag(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Álbum desconocido", duration, uri.toString(), uri.toString(),
                format = format, sampleRateHz = source.rate, bitDepth = source.bits, isHiRes = source.rate > 48000 || source.bits > 16,
                albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).orEmpty())
        } finally { reader.release() }
    }
    private fun id(uri: Uri): String {
        val identity = runCatching { "${uri.authority}:${DocumentsContract.getDocumentId(uri)}" }.getOrDefault(uri.toString())
        return "doc:" + MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun storagePath(tree: Uri, id: String): String? {
        if (tree.authority != "com.android.externalstorage.documents" || !id.contains(':')) return null
        val volume = id.substringBefore(':')
        return (if (volume.equals("primary", true)) "/storage/emulated/0/" else "/storage/$volume/") + id.substringAfter(':')
    }
    private fun encode(song: Song) = JSONObject().put("title", song.title).put("artist", song.artist).put("album", song.album)
        .put("albumArtist", song.albumArtist).put("duration", song.durationMs).put("format", song.format.name).put("rate", song.sampleRateHz).put("bits", song.bitDepth)
    private fun decode(json: JSONObject, uri: Uri): Song {
        val format = AudioFormat.valueOf(json.getString("format"))
        return Song(id(uri), json.getString("title"), json.getString("artist"), json.getString("album"), json.getLong("duration"),
            uri.toString(), uri.toString(), format = format, sampleRateHz = json.optInt("rate"), bitDepth = json.optInt("bits"), isHiRes = json.optInt("rate") > 48000 || json.optInt("bits") > 16, albumArtist = json.optString("albumArtist"))
    }
}
