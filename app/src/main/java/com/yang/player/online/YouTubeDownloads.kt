package com.yang.player.online

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import com.yang.player.data.CompletionKind
import com.yang.player.data.DocumentAudioLibrary
import com.yang.player.data.MetadataMatch
import com.yang.player.data.OnlineMetadata
import com.yang.player.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal data class YouTubeDownloadStatus(
    val title: String,
    val stage: String,
    val running: Boolean,
    val progress: Int? = null
)

internal data class DownloadedMedia(val uri: Uri, val title: String, val mimeType: String)

@UnstableApi
internal class YouTubeDownloads private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("youtube_downloads", Context.MODE_PRIVATE)
    private val resolver = context.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _status = MutableStateFlow<YouTubeDownloadStatus?>(null)
    val status = _status.asStateFlow()
    private val _audioDocuments = MutableStateFlow(prefs.getStringSet("audio_files", emptySet()).orEmpty().toSet())
    val audioDocuments = _audioDocuments.asStateFlow()
    private val _history = MutableStateFlow(readHistory())
    val history = _history.asStateFlow()
    private data class Request(val song: Song, val optionId: String, val folder: Uri, val complete: Boolean)
    private var pending: Request? = null
    private var job: Job? = null
    private var lastProgressAt = 0L

    fun folder(): Uri? = prefs.getString("folder", null)?.let(Uri::parse)?.takeIf { uri ->
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
    }

    private fun readHistory(): List<DownloadedMedia> {
        val rows = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrDefault(JSONArray())
        val recorded = (0 until rows.length()).mapNotNull {
            val row = rows.optJSONObject(it) ?: return@mapNotNull null
            val uri = Uri.parse(row.optString("uri"))
            if (uri.scheme != "content") null else DownloadedMedia(uri, row.optString("title"), row.optString("mime"))
        }
        val legacy = audioDocuments.value.mapNotNull { value ->
            val uri = Uri.parse(value)
            if (uri.scheme == "content") {
                val name = runCatching { DocumentsContract.getDocumentId(uri).substringAfterLast('/').substringAfterLast(':') }.getOrNull()
                DownloadedMedia(uri, name ?: "Audio descargado", "audio/*")
            } else null
        }
        return (recorded + legacy).distinctBy { it.uri }
    }

    private fun record(item: DownloadedMedia) {
        val updated = (listOf(item) + _history.value).distinctBy { it.uri }
        val entries = JSONArray()
        updated.forEach { entries.put(JSONObject().put("uri", it.uri.toString()).put("title", it.title).put("mime", it.mimeType)) }
        prefs.edit().putString("history", entries.toString()).apply()
        _history.value = updated
    }

    fun chooseFolder(uri: Uri) {
        resolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString("folder", uri.toString()).apply()
    }

    fun cancel() {
        val waiting = pending
        if (waiting == null) {
            job?.cancel()
        } else {
            pending = null
            update(waiting.song, "Descarga cancelada", running = false)
        }
    }

    fun start(song: Song, optionId: String, folder: Uri, complete: Boolean) {
        if (_status.value?.running == true) return
        pending = Request(song, optionId, folder, complete)
        _status.value = YouTubeDownloadStatus(song.title, "Preparando descarga…", running = true)
        try {
            ContextCompat.startForegroundService(context, Intent(context, YouTubeDownloadService::class.java))
        } catch (_: Exception) {
            pending = null
            update(song, "No se pudo iniciar la descarga", running = false)
        }
    }

    fun runPending(onFinished: () -> Unit): Boolean {
        val request = pending ?: return false
        pending = null
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val song = request.song
            try {
                val completed = withContext(Dispatchers.IO) { save(song, request.optionId, request.folder, request.complete) }
                update(song, completed, running = false)
            } catch (_: CancellationException) {
                val alreadySaved = _status.value?.stage == "Completando metadatos y letras…"
                update(song, if (alreadySaved) "Descarga lista · autocompletado cancelado" else "Descarga cancelada", running = false)
            } catch (_: Exception) {
                update(song, "No se pudo guardar la descarga", running = false)
            } finally {
                onFinished()
            }
        }
        return true
    }

    suspend fun downloadedSongs(): List<Song> {
        val library = DocumentAudioLibrary(context)
        return audioDocuments.value.mapNotNull { value ->
            currentCoroutineContext().ensureActive()
            try {
                library.openDownload(Uri.parse(value))
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun save(song: Song, optionId: String, folder: Uri, complete: Boolean): String {
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val work = mutableListOf<File>()
        fun temp(): File = File.createTempFile("yt-download-", ".tmp", context.cacheDir).also(work::add)
        var document: Uri? = null
        var saved = false
        try {
            val main = temp()
            val companion = temp()
            var option = loadOption(song, optionId)
            try {
                fetchMedia(song, option, main, companion)
            } catch (error: HttpDataSource.InvalidResponseCodeException) {
                if (error.responseCode !in setOf(403, 410)) throw error
                option = loadOption(song, optionId, refresh = true)
                fetchMedia(song, option, main, companion)
            }
            val media = if (option.audioUri != null) {
                update(song, "Uniendo video y audio…")
                temp().also { merge(main, companion, it, option.muxFormat) }
            } else main
            val baseName = safeName(listOf(song.title, song.artist).filter(String::isNotBlank).joinToString(" - "))
            val quality = if (option.kind == DownloadKind.VIDEO) " (${option.quality}p)" else ""
            val filename = "$baseName$quality.${option.extension}"
            update(song, "Guardando archivo…")
            document = DocumentsContract.createDocument(resolver, parent, option.mimeType, filename)
                ?: throw IOException("No se pudo crear el archivo de destino.")
            resolver.openOutputStream(document, "w")?.use { output ->
                media.inputStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("No se pudo escribir en la carpeta.")
            saved = true
            record(DownloadedMedia(document, song.title, option.mimeType))
            if (option.kind == DownloadKind.AUDIO) {
                try {
                    if (complete) {
                        update(song, "Completando metadatos y letras…")
                        val enriched = optional { completeMetadata(song, document) } == true
                        return if (enriched) "Descarga lista · datos guardados en Yang" else "Descarga lista · sin coincidencias de metadatos"
                    }
                } finally {
                    val files = audioDocuments.value + document.toString()
                    prefs.edit().putStringSet("audio_files", files).apply()
                    _audioDocuments.value = files
                }
            }
            return "Descarga lista"
        } finally {
            if (!saved) document?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            work.forEach(File::delete)
        }
    }

    private suspend fun loadOption(song: Song, id: String, refresh: Boolean = false): YouTubeDownloadOption =
        YouTubeDownloadOptions.load(song.mediaUri, refresh).firstOrNull { it.id == id }
            ?: throw IOException("Esta calidad dejó de estar disponible.")

    private suspend fun fetchMedia(song: Song, option: YouTubeDownloadOption, main: File, companion: File) {
        val stage = if (option.kind == DownloadKind.AUDIO) "Descargando canción…" else "Descargando video…"
        lastProgressAt = 0
        if (option.kind == DownloadKind.AUDIO) downloadAudio(option.uri, main) { updateProgress(song, stage, it) }
        else download(option.uri, main) { updateProgress(song, stage, it) }
        option.audioUri?.let { uri ->
            lastProgressAt = 0
            downloadAudio(uri, companion) { updateProgress(song, "Descargando audio del video…", it) }
        }
    }

    private suspend fun downloadAudio(uri: Uri, destination: File, progress: (Int?) -> Unit) {
        progress(null)
        try {
            if (downloadAudioRanges(uri, destination, progress)) return
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // Some CDN responses do not support independent ranges.
        }
        lastProgressAt = 0L
        download(uri, destination, progress)
    }

    private suspend fun download(uri: Uri, destination: File, onProgress: (Int?) -> Unit) {
        val source = DefaultHttpDataSource.Factory().setUserAgent(OnlineAudio.UA)
            .setConnectTimeoutMs(12_000).setReadTimeoutMs(15_000).createDataSource()
        try {
            onProgress(null)
            val expected = source.open(DataSpec(uri))
            if (source.uri?.let(YouTubeDownloadOptions::isYouTubeMediaUri) != true) throw IOException("Origen no válido.")
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(128 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer, 0, buffer.size)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    total += count
                    onProgress(if (expected > 0) (total * 100 / expected).toInt().coerceIn(0, 100) else null)
                }
                if (total == 0L || (expected > 0 && total != expected)) throw IOException("Descarga incompleta.")
            }
        } finally {
            source.close()
        }
    }

    private suspend fun merge(video: File, audio: File, output: File, format: Int) {
        val picture = MediaExtractor()
        val sound = MediaExtractor()
        try {
            picture.setDataSource(video.absolutePath)
            sound.setDataSource(audio.absolutePath)
            val videoFormat = selectTrack(picture, "video/")
            val audioFormat = selectTrack(sound, "audio/")
            val muxer = MediaMuxer(output.absolutePath, format)
            try {
                val videoIndex = muxer.addTrack(videoFormat)
                val audioIndex = muxer.addTrack(audioFormat)
                if (format == MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 &&
                    videoFormat.containsKey(MediaFormat.KEY_ROTATION)
                ) {
                    muxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION))
                }
                muxer.start()
                var buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
                val info = MediaCodec.BufferInfo()
                while (picture.sampleTime >= 0 || sound.sampleTime >= 0) {
                    currentCoroutineContext().ensureActive()
                    val useVideo = picture.sampleTime >= 0 && (sound.sampleTime < 0 || picture.sampleTime <= sound.sampleTime)
                    val extractor = if (useVideo) picture else sound
                    buffer.clear()
                    if (Build.VERSION.SDK_INT >= 28 && extractor.sampleSize > buffer.capacity()) {
                        val size = extractor.sampleSize
                        require(size <= 32L * 1024 * 1024) { "Cuadro de video demasiado grande." }
                        buffer = ByteBuffer.allocateDirect(size.toInt())
                    }
                    val size = extractor.readSampleData(buffer, 0)
                    if (size <= 0 || size > buffer.capacity()) throw IOException("Muestra multimedia incompleta.")
                    val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0
                    info.set(0, size, extractor.sampleTime, flags)
                    muxer.writeSampleData(if (useVideo) videoIndex else audioIndex, buffer, info)
                    extractor.advance()
                }
                muxer.stop()
            } finally {
                muxer.release()
            }
        } finally {
            picture.release()
            sound.release()
        }
    }

    private fun selectTrack(extractor: MediaExtractor, type: String): MediaFormat {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith(type) == true) {
                extractor.selectTrack(index)
                return format
            }
        }
        throw IOException("Falta una pista para completar el video.")
    }

    private suspend fun completeMetadata(source: Song, document: Uri): Boolean = coroutineScope {
        val catalog = OnlineMetadata.get(context)
        val local = DocumentAudioLibrary(context).open(document)
        val identity = source.copy(durationMs = local.durationMs)
        val artist = cleanDownloadArtist(source.artist)
        val title = cleanDownloadTitle(source.title)
        val titleKey = normalized(title)
        val artistKey = normalized(artist)
        val query = if (titleKey.startsWith("$artistKey ") || titleKey.endsWith(" $artistKey")) title else "$title $artist".trim()
        val apple = async { optional { catalog.search(query, CompletionKind.METADATA, identity).firstOrNull { matchesDownloadedTrack(identity, it) } } }
        val lyrics = async { optional { catalog.search(query, CompletionKind.LYRICS, identity).firstOrNull { matchesDownloadedTrack(identity, it) } } }
        val tags = apple.await()
        val lyric = lyrics.await()
        val tagsSaved = tags?.let {
            val saved = optional { catalog.saveCustomTags(local, it.title, it.artist, it.album) } != null
            if (it.artwork.isNotBlank()) optional { catalog.save(local, it, CompletionKind.ARTWORK) }
            saved
        } == true
        val lyricsSaved = lyric?.let { optional { catalog.saveLyrics(local, it.lyrics) } != null } == true
        tagsSaved || lyricsSaved
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun safeName(value: String): String = value.map { character ->
        if (character.isISOControl() || character in "<>:\"/\\|?*") '_' else character
    }.joinToString("").trim(' ', '.').take(100).ifBlank { "Descarga de Yang" }

    @Synchronized
    private fun updateProgress(song: Song, stage: String, progress: Int?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProgressAt < 750 && progress != 100) return
        lastProgressAt = now
        update(song, stage, progress = progress)
    }

    private fun update(song: Song, stage: String, running: Boolean = true, progress: Int? = null) {
        _status.value = YouTubeDownloadStatus(song.title, stage, running, progress)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification()) }
    }

    fun notification(): Notification {
        val current = status.value
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Descargas de Yang", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(if (current?.running == true) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
            .setContentTitle(current?.title ?: "Descargas de Yang")
            .setContentText(current?.stage)
            .setOnlyAlertOnce(true)
            .setOngoing(current?.running == true)
            .setAutoCancel(current?.running != true)
            .apply { if (current?.running == true) setProgress(100, current.progress ?: 0, current.progress == null) }
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 17842
        private const val CHANNEL = "yang_downloads"
        @Volatile private var instance: YouTubeDownloads? = null
        fun get(context: Context): YouTubeDownloads = instance ?: synchronized(this) {
            instance ?: YouTubeDownloads(context.applicationContext).also { instance = it }
        }
    }
}

private val downloadSymbols = Regex("[\\p{So}\\p{Sk}\\x{FE0F}\\x{200D}\\x{20E3}]+")
private val titleNoise = Regex(
    """(?i)(?:\s*[\[({]\s*|(?:\s*[-–—|·•]+\s*|\s+)|^)(?:video\s+musical\s+oficial|video\s+oficial|audio\s+oficial|letra\s+oficial|official\s+music\s+video|official\s+lyric\s+video|official\s+video|official\s+audio|lyric\s+video|video\s+con\s+letra|con\s+letra|lyrics?|letra)\s*[\])}]?\s*$"""
)

internal fun cleanDownloadTitle(value: String): String {
    var title = value.replace(downloadSymbols, " ").replace(Regex("\\s+"), " ").trim()
    while (true) {
        val cleaned = title.replace(titleNoise, "").trim(' ', '-', '–', '—', '|', '·', '•')
        if (cleaned == title) return title.ifBlank { value.trim() }
        title = cleaned
    }
}

internal fun cleanDownloadArtist(value: String): String =
    value.substringBefore(" • ").substringBefore(" · ").removeSuffix(" - Topic")
        .replace(downloadSymbols, " ").replace(Regex("\\s+"), " ").trim()

internal fun matchesDownloadedTrack(song: Song, candidate: MetadataMatch): Boolean {
    if (song.durationMs <= 0 || candidate.durationMs <= 0 || abs(song.durationMs - candidate.durationMs) > 4_000) return false
    val sourceArtist = normalized(cleanDownloadArtist(song.artist))
    val artist = normalized(candidate.artist)
    val title = normalized(song.title)
    val cleanTitle = normalized(cleanDownloadTitle(song.title))
    val track = normalized(candidate.title)
    if (artist.isEmpty() || track.isEmpty()) return false
    return (sourceArtist == artist && (title == track || cleanTitle == track || cleanTitle == "$track $artist")) ||
        title == "$artist $track" || cleanTitle == "$artist $track"
}

private fun normalized(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
