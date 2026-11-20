package com.yang.player.online

import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

internal enum class DownloadKind { AUDIO, VIDEO }

internal data class YouTubeDownloadOption(
    val id: String,
    val kind: DownloadKind,
    val quality: Int,
    val rate: Int,
    val extension: String,
    val mimeType: String,
    val uri: Uri,
    val audioUri: Uri? = null,
    val muxFormat: Int = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
) {
    val label: String get() = when (kind) {
        DownloadKind.AUDIO -> if (quality > 0) "$quality kbps" else "Tasa variable"
        DownloadKind.VIDEO -> if (rate > 0) "${quality}p · $rate fps" else "${quality}p"
    }
}

internal object YouTubeDownloadOptions {
    private val resolution = Regex("""(\d+)p(?:\d+)?""")

    suspend fun load(uri: String, refresh: Boolean = false): List<YouTubeDownloadOption> = withContext(Dispatchers.IO) {
        val identity = Uri.parse(uri)
        if (refresh) OnlineAudio.invalidateInfo(identity)
        val info = OnlineAudio.info(identity)
        if (info.streamType != StreamType.VIDEO_STREAM) return@withContext emptyList()
        val audio = info.audioStreams.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .mapNotNull(::audio).sortedByDescending { it.quality }.distinctBy { it.extension to it.quality }
        val videos = (info.videoStreams + info.videoOnlyStreams)
            .mapNotNull { video(it, audio) }
            .sortedWith(compareByDescending<YouTubeDownloadOption> { it.quality }.thenByDescending { it.rate })
            .distinctBy { Triple(it.quality, it.rate, it.extension) }
        audio + videos
    }

    private fun audio(stream: AudioStream): YouTubeDownloadOption? {
        val uri = mediaUri(stream) ?: return null
        val (extension, mime) = when (stream.format) {
            MediaFormat.M4A -> "m4a" to "audio/mp4"
            MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS -> "webm" to "audio/webm"
            else -> return null
        }
        return YouTubeDownloadOption(
            "a:${stream.itag}:${stream.audioTrackId.orEmpty()}", DownloadKind.AUDIO,
            stream.averageBitrate.takeIf { it > 0 } ?: (stream.bitrate / 1000).coerceAtLeast(0), 0, extension, mime, uri
        )
    }

    private fun video(stream: VideoStream, audio: List<YouTubeDownloadOption>): YouTubeDownloadOption? {
        val uri = mediaUri(stream) ?: return null
        val (extension, mime) = when (stream.format) {
            MediaFormat.MPEG_4 -> "mp4" to "video/mp4"
            MediaFormat.WEBM -> "webm" to "video/webm"
            else -> return null
        }
        val height = resolution.matchEntire(stream.getResolution().orEmpty())?.groupValues?.get(1)?.toIntOrNull()
            ?: minOf(stream.width, stream.height).takeIf { it > 0 } ?: return null
        val codec = stream.codec.orEmpty().substringBefore('.')
        val companion = if (stream.isVideoOnly()) {
            when {
                extension == "mp4" && (codec in setOf("avc1", "avc3", "hev1", "hvc1") ||
                    (codec == "av01" && Build.VERSION.SDK_INT >= 34)) -> audio.firstOrNull { it.extension == "m4a" }
                extension == "webm" && Build.VERSION.SDK_INT >= 29 && codec in setOf("vp8", "vp9", "vp09") ->
                    audio.firstOrNull { it.extension == "webm" }
                else -> null
            } ?: return null
        } else null
        return YouTubeDownloadOption(
            "v:${stream.itag}", DownloadKind.VIDEO, height, stream.fps.coerceAtLeast(0), extension, mime,
            uri, companion?.uri, if (extension == "webm") MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
    }

    private fun mediaUri(stream: Stream): Uri? =
        if (stream.isUrl && stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP) {
            Uri.parse(stream.content).takeIf(::isYouTubeMediaUri)
        } else null

    fun isYouTubeMediaUri(uri: Uri): Boolean =
        uri.scheme == "https" && uri.host?.endsWith(".googlevideo.com") == true
}
