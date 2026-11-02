package com.yang.player.lyrics

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.flac.VorbisComment
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.yang.player.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
class LyricsRepository(context: Context) {
    private val context = context.applicationContext
    private val preferences = context.getSharedPreferences("lyrics_files", Context.MODE_PRIVATE)

    suspend fun load(song: Song): LyricsDocument? {
        val key = "${song.id}:${song.mediaUri}"
        synchronized(cache) { cache.get(key) }?.let { return it }
        val candidates = mutableListOf<LyricsDocument>()
        withContext(Dispatchers.IO) {
            preferences.getString(song.id, null)?.let { uri ->
                safely { context.contentResolver.openInputStream(Uri.parse(uri))?.use { parseStream(it, song.durationMs) } }
                    ?.let(candidates::add)
            }
            if (candidates.isEmpty() && song.dataPath.isNotBlank()) {
                val audio = File(song.dataPath)
                val parent = audio.parentFile
                val base = audio.nameWithoutExtension
                parent?.listFiles()?.filter {
                    it.nameWithoutExtension.equals(base, true) && it.extension.lowercase() in setOf("lrc", "ttml", "srt", "txt")
                }?.take(8)?.forEach { file ->
                    safely { file.inputStream().use { parseStream(it, song.durationMs) } }?.let(candidates::add)
                }
            }
        }
        // An explicitly selected file takes precedence over embedded tags.
        if (preferences.contains(song.id) && candidates.isNotEmpty()) return candidates.maxBy(::quality).also {
            synchronized(cache) { cache.put(key, it) }
        }
        if (song.isOnline) return candidates.maxByOrNull(::quality)
        try {
            withTimeout(10_000) {
                val tracks = withContext(Dispatchers.Main) {
                    MetadataRetriever.retrieveMetadata(context, MediaItem.fromUri(song.mediaUri)).await()
                }
                for (group in 0 until tracks.length) {
                    val formats = tracks[group]
                    for (format in 0 until formats.length) {
                        val metadata = formats.getFormat(format).metadata ?: continue
                        for (entryIndex in 0 until metadata.length()) {
                            val entry = metadata[entryIndex]
                            val parsed = safely {
                                when (entry) {
                                    is VorbisComment -> if (isLyricsKey(entry.key)) LyricsParser.parse(entry.value, song.durationMs) else null
                                    is TextInformationFrame -> if (isLyricsKey(entry.id) || isLyricsKey(entry.description.orEmpty())) {
                                        LyricsParser.parse(entry.values.joinToString("\n"), song.durationMs)
                                    } else null
                                    is BinaryFrame -> when (entry.id) {
                                        "USLT", "ULT" -> LyricsParser.parseId3Lyrics(entry.data, false, song.durationMs)
                                        "SYLT", "SLT" -> LyricsParser.parseId3Lyrics(entry.data, true, song.durationMs)
                                        else -> null
                                    }
                                    else -> null
                                }
                            }
                            parsed?.let(candidates::add)
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            // A song change must cancel extraction instead of attaching stale lyrics.
            if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
        } catch (_: Exception) { /* Available sidecars can still be used. */ }
        return candidates.maxByOrNull(::quality)?.also { synchronized(cache) { cache.put(key, it) } }
    }

    suspend fun importFile(song: Song, uri: Uri): LyricsDocument = withContext(Dispatchers.IO) {
        val document = context.contentResolver.openInputStream(uri)?.use { parseStream(it, song.durationMs) }
            ?: throw IllegalArgumentException("El archivo no contiene letras legibles")
        preferences.edit().putString(song.id, uri.toString()).apply()
        synchronized(cache) { cache.put("${song.id}:${song.mediaUri}", document) }
        document
    }

    /** The caller checks existing embedded/imported lyrics before offering this action. */
    suspend fun saveOnline(song: Song, text: String): LyricsDocument = withContext(Dispatchers.IO) {
        require(text.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024) { "La letra es demasiado grande." }
        val document = LyricsParser.parse(text, song.durationMs)
            ?: throw IllegalArgumentException("La letra recibida no es válida.")
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(song.mediaUri.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val file = File(context.filesDir, "online_lyrics/$hash.lrc")
        file.parentFile!!.mkdirs()
        com.yang.player.data.OnlineMetadata.writeAtomic(file, text.toByteArray(Charsets.UTF_8))
        require(preferences.edit().putString(song.id, Uri.fromFile(file).toString()).commit()) { "No se pudo guardar la letra." }
        synchronized(cache) { cache.put("${song.id}:${song.mediaUri}", document) }
        document
    }

    private fun parseStream(input: InputStream, durationMs: Long): LyricsDocument? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 2 * 1024 * 1024) { "El archivo de letras es demasiado grande" }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return LyricsParser.parse(bytes.toString(charset), durationMs)
    }

    private fun isLyricsKey(key: String) = key.uppercase().let { it.contains("LYRIC") || it in setOf("USLT", "SYLT", "TTML", "LRC", "©LYR") }
    private fun quality(document: LyricsDocument) = when { document.enhanced -> 3; document.synchronized -> 2; else -> 1 }
    private inline fun <T> safely(block: () -> T): T? = try { block() } catch (_: Exception) { null }

    companion object { private val cache = LruCache<String, LyricsDocument>(8) }
}

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener({
        try { continuation.resume(get()) } catch (error: Exception) { continuation.resumeWithException(error) }
    }, MoreExecutors.directExecutor())
    continuation.invokeOnCancellation { cancel(true) }
}
