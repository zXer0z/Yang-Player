package com.yang.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.yang.player.model.AudioFormat
import com.yang.player.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudioScanner(private val context: Context) {

    private val scannerScope = CoroutineScope(Dispatchers.IO)

    // Directories that strictly contain non-music voice notes or system alerts
    private val voiceNoteKeywords = listOf(
        "whatsapp",
        "telegram audio",
        "recordings",
        "sound_recorder",
        "voice_recorder",
        "call_recordings",
        "notifications",
        "ringtones",
        "alarms"
    )

    private val voiceNotePrefixes = listOf(
        "aud-", "ptt-", "rec-", "voice-"
    )

    suspend fun scanLocalAudio(): List<Song> = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 " +
                "AND (${MediaStore.Audio.Media.IS_RINGTONE} == 0) " +
                "AND (${MediaStore.Audio.Media.IS_NOTIFICATION} == 0) " +
                "AND (${MediaStore.Audio.Media.IS_ALARM} == 0) " +
                "AND (${MediaStore.Audio.Media.IS_PODCAST} == 0)"

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cacheDir = File(context.cacheDir, "art_thumbs_v2").apply { if (!exists()) mkdirs() }
        val metadata = AudioSourceMetadata(context)

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val modifiedColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (it.moveToNext()) {
                    val durationMs = it.getLong(durationColumn)
                    if (durationMs < 20000) continue // At least 20s long

                    val dataPath = it.getString(dataColumn) ?: ""
                    val normalizedPath = dataPath.lowercase().replace('\\', '/')
                    val mimeType = it.getString(mimeColumn)
                    val format = AudioFormat.fromPathOrMime(dataPath, mimeType)

                    if (format == AudioFormat.UNKNOWN) continue

                    val isLosslessMusic = format in listOf(
                        AudioFormat.FLAC,
                        AudioFormat.DSD,
                        AudioFormat.ALAC,
                        AudioFormat.WAV
                    )

                    // Lossless audiophile files are ALWAYS kept (never filtered)
                    if (!isLosslessMusic) {
                        val isVoiceNoteDir = voiceNoteKeywords.any { kw -> normalizedPath.contains(kw) }
                        if (isVoiceNoteDir) continue

                        val filename = dataPath.substringAfterLast('/').lowercase()
                        val isVoiceNotePrefix = voiceNotePrefixes.any { p -> filename.startsWith(p) }
                        if (isVoiceNotePrefix) continue
                    } else {
                        // Even for lossless, ignore WhatsApp voice dumps
                        if (normalizedPath.contains("com.whatsapp") || normalizedPath.contains("whatsapp audio")) {
                            continue
                        }
                    }

                    val id = it.getLong(idColumn)
                    var title = it.getString(titleColumn)?.trim() ?: ""
                    var artist = it.getString(artistColumn)?.trim() ?: ""
                    var album = it.getString(albumColumn)?.trim() ?: ""

                    // Clean artist & title from filename if tags are missing (e.g. "Artist - Title.flac")
                    if (artist.isBlank() || artist.equals("<unknown>", ignoreCase = true) || artist.equals("Artista desconocido", ignoreCase = true)) {
                        val filenameWithoutExt = dataPath.substringAfterLast('/').substringBeforeLast('.')
                        if (filenameWithoutExt.contains(" - ")) {
                            val parts = filenameWithoutExt.split(" - ", limit = 2)
                            artist = parts[0].trim()
                            if (title.isBlank() || title.equals("<unknown>", ignoreCase = true)) {
                                title = parts[1].trim()
                            }
                        } else {
                            artist = "Desconocido"
                            if (title.isBlank() || title.equals("<unknown>", ignoreCase = true)) {
                                title = filenameWithoutExt
                            }
                        }
                    }

                    if (album.isBlank() || album.equals("<unknown>", ignoreCase = true)) {
                        album = "Álbum desconocido"
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val source = metadata.get(Uri.parse(contentUri), "${it.getLong(modifiedColumn)}:${it.getLong(sizeColumn)}")
                    val sampleRate = source.rate
                    val bitDepth = source.bits

                    // If disk thumbnail already exists, use direct file URI for instant Coil FileFetcher
                    // Otherwise pass contentUri so AudioCoverFetcher extracts it
                    val diskThumb = File(cacheDir, "cover_$id.jpg")
                    val artworkUri = if (diskThumb.exists()) {
                        Uri.fromFile(diskThumb).toString()
                    } else {
                        contentUri
                    }

                    songsList.add(
                        Song(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            mediaUri = contentUri,
                            artworkUri = artworkUri,
                            dataPath = dataPath,
                            format = format,
                            sampleRateHz = sampleRate,
                            bitDepth = bitDepth,
                            isHiRes = sampleRate > 48000 || bitDepth > 16,
                            isFavorite = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        metadata.save()
        // Asynchronously pre-warm L1/L2 cover art cache in background
        if (songsList.isNotEmpty()) {
            val songsCopy = ArrayList(songsList)
            scannerScope.launch {
                AudioCoverFetcher.preWarmCache(context, songsCopy)
            }
        }

        songsList
    }
}
