package com.yang.player.online

import android.media.MediaCodecList
import android.media.MediaFormat as AndroidMediaFormat
import android.net.Uri
import android.os.Build
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

import java.io.IOException

internal data class YouTubeVideoQuality(
    val resolution: Int,
    val fps: Int,
    val uri: Uri,
    val mimeType: String,
    val hardwareDecoded: Boolean,
    val itag: Int
) {
    val label: String get() = if (fps > 0) "${resolution}p · $fps fps" else "${resolution}p"
}

internal data class YouTubeComment(
    val id: String?,
    val author: String,
    val text: String,
    val date: String?,
    val avatar: String?
)

internal data class YouTubeCommentsPage(
    val comments: List<YouTubeComment>,
    val next: Page?,
    val disabled: Boolean
)

internal object YouTubeVideo {
    private val resolutionLabel = Regex("""(\d+)p(?:\d+)?""")
    private val codecs by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS) }

    suspend fun qualities(uri: String, refresh: Boolean): List<YouTubeVideoQuality> = withContext(Dispatchers.IO) {
        val videoUri = Uri.parse(uri)
        if (refresh) OnlineAudio.invalidateInfo(videoUri)
        val info = OnlineAudio.info(videoUri)
        if (info.streamType != StreamType.VIDEO_STREAM) return@withContext emptyList()
        (info.videoOnlyStreams + info.videoStreams)
            .mapNotNull { stream -> quality(stream)?.let { it to stream } }
            .sortedWith(
                compareByDescending<Pair<YouTubeVideoQuality, VideoStream>> { it.first.resolution }
                    .thenByDescending { it.first.fps }
                    .thenByDescending { it.first.hardwareDecoded }
                    .thenByDescending { it.second.isVideoOnly() }
                    .thenByDescending { it.second.codec.orEmpty().startsWith("avc1") }
                    .thenBy { it.second.bitrate.takeIf { bitrate -> bitrate > 0 } ?: Int.MAX_VALUE }
            )
            .distinctBy { it.first.resolution to it.first.fps }
            .map { it.first }
    }

    private fun quality(stream: VideoStream): YouTubeVideoQuality? {
        if (!stream.isUrl || stream.deliveryMethod != DeliveryMethod.PROGRESSIVE_HTTP) return null
        val container = stream.format ?: return null
        if (container != MediaFormat.MPEG_4 && container != MediaFormat.WEBM) return null
        val codec = stream.codec.orEmpty().substringBefore('.')
        val decoderMime = when (codec) {
            "avc1", "avc3" -> AndroidMediaFormat.MIMETYPE_VIDEO_AVC
            "vp9", "vp09" -> AndroidMediaFormat.MIMETYPE_VIDEO_VP9
            "av01" -> AndroidMediaFormat.MIMETYPE_VIDEO_AV1
            else -> return null
        }
        val uri = Uri.parse(stream.content)
        if (uri.scheme != "https" || uri.host?.endsWith(".googlevideo.com") != true) return null
        val resolution = resolutionLabel.matchEntire(stream.getResolution().orEmpty())
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: minOf(stream.width, stream.height).takeIf { it > 0 }
            ?: return null
        val format = if (stream.width > 0 && stream.height > 0) {
            AndroidMediaFormat.createVideoFormat(decoderMime, stream.width, stream.height)
        } else {
            AndroidMediaFormat().apply { setString(AndroidMediaFormat.KEY_MIME, decoderMime) }
        }
        val fps = stream.fps.coerceAtLeast(0)
        if (fps > 0) format.setInteger(AndroidMediaFormat.KEY_FRAME_RATE, fps)
        val decoder = codecs.codecInfos.asSequence()
            .filter { !it.isEncoder && decoderMime in it.supportedTypes }
            .filter { runCatching { it.getCapabilitiesForType(decoderMime).isFormatSupported(format) }.getOrDefault(false) }
            .map { Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || it.isHardwareAccelerated }
            .maxByOrNull { if (it) 1 else 0 } ?: return null
        if (!decoder && (resolution > 720 || fps > 30)) return null
        return YouTubeVideoQuality(resolution, fps, uri, container.mimeType, decoder, stream.itag)
    }

    suspend fun comments(uri: String, page: Page?): YouTubeCommentsPage = withContext(Dispatchers.IO) {
        OnlineAudio.init()
        val service = NewPipe.getService(0)
        val url = OnlineAudio.watch(Uri.parse(uri))
        val first = if (page == null) CommentsInfo.getInfo(service, url) else null
        val more = page?.let { CommentsInfo.getMoreItems(service, url, it) }
        val disabled = first?.isCommentsDisabled == true
        val items = first?.relatedItems ?: more?.items.orEmpty()
        val errors = first?.errors ?: more?.errors.orEmpty()
        if (!disabled && items.isEmpty() && errors.isNotEmpty()) {
            throw IOException("No se pudieron obtener los comentarios.", errors.first())
        }
        val comments = items.mapNotNull { item ->
            val description = item.commentText ?: return@mapNotNull null
            val content = description.content.orEmpty()
            val text = if (description.type == Description.HTML) {
                Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            } else {
                content.trim()
            }
            if (text.isBlank()) return@mapNotNull null
            val avatar = item.uploaderAvatars.orEmpty().firstOrNull()?.url?.takeIf { it.startsWith("https://") }
            YouTubeComment(item.commentId, item.uploaderName.orEmpty(), text, item.textualUploadDate, avatar)
        }
        val next = (first?.nextPage ?: more?.nextPage)?.takeIf { Page.isValid(it) }
        YouTubeCommentsPage(comments, next, disabled)
    }

}
