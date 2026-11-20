package com.yang.player.online

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

private val contentRange = Regex("""bytes (\d+)-(\d+)/(\d+)""")

internal suspend fun downloadAudioRanges(uri: Uri, file: File, progress: (Int) -> Unit): Boolean = coroutineScope {
    val probe = openAudioRange(uri, 0, 0) ?: return@coroutineScope false
    val total = try {
        probe.getHeaderField("Content-Range")?.let(contentRange::matchEntire)?.groupValues?.get(3)?.toLongOrNull()
            ?: return@coroutineScope false
    } finally {
        probe.disconnect()
    }
    if (total < 1_048_576L || total > 128L * 1024 * 1024) return@coroutineScope false

    RandomAccessFile(file, "rw").use { it.setLength(total) }
    val ranges = audioDownloadRanges(total)
    var downloaded = 0L
    val lock = Any()
    progress(0)
    ranges.map { range ->
        async {
            val connection = openAudioRange(uri, range.first, range.last)
                ?: throw IOException("YouTube no entregó el tramo solicitado.")
            try {
                if (connection.getHeaderField("Content-Range") != "bytes ${range.first}-${range.last}/$total") {
                    throw IOException("Tramo de audio inválido.")
                }
                RandomAccessFile(file, "rw").use { target ->
                    target.seek(range.first)
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(96 * 1024)
                        var remaining = range.last - range.first + 1
                        while (remaining > 0) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                            if (count <= 0) throw IOException("Tramo de audio incompleto.")
                            target.write(buffer, 0, count)
                            remaining -= count
                            synchronized(lock) {
                                downloaded += count
                                progress((downloaded * 100 / total).toInt())
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }.awaitAll()
    true
}

internal fun audioDownloadRanges(total: Long): List<LongRange> =
    (0L until 3L).map { index -> (total * index / 3L) until (total * (index + 1) / 3L) }

private fun openAudioRange(uri: Uri, first: Long, last: Long): HttpURLConnection? {
    var url = URL(uri.toString())
    repeat(5) {
        if (!YouTubeDownloadOptions.isYouTubeMediaUri(Uri.parse(url.toString()))) return null
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Range", "bytes=$first-$last")
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("User-Agent", OnlineAudio.UA)
        var accepted = false
        try {
            val status = connection.responseCode
            if (status == 206) {
                accepted = true
                return connection
            }
            if (status !in 300..399) return null
            url = URL(url, connection.getHeaderField("Location") ?: return null)
        } finally {
            if (!accepted) connection.disconnect()
        }
    }
    return null
}
