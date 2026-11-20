package com.yang.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yang.player.online.YouTubeComment
import com.yang.player.online.YouTubeVideo
import kotlinx.coroutines.CancellationException
import org.schabi.newpipe.extractor.Page

internal class VideoComments {
    var comments by mutableStateOf(emptyList<YouTubeComment>())
    var next by mutableStateOf<Page?>(null)
    var request by mutableIntStateOf(0)
    var loading by mutableStateOf(true)
    var failed by mutableStateOf(false)
    var disabled by mutableStateOf(false)
}

@Composable
internal fun rememberVideoComments(uri: String, visible: Boolean): VideoComments {
    val state = remember(uri) { VideoComments() }
    var opened by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) opened = true }
    LaunchedEffect(uri, opened, state.request) {
        if (!opened) return@LaunchedEffect
        state.loading = true
        state.failed = false
        try {
            val page = YouTubeVideo.comments(uri, state.next)
            val knownIds = state.comments.mapNotNull { it.id }.toMutableSet()
            state.comments += page.comments.filter { it.id == null || knownIds.add(it.id) }
            state.next = page.next
            state.disabled = page.disabled
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.failed = true
        } finally {
            state.loading = false
        }
    }
    return state
}

internal fun LazyListScope.videoComments(state: VideoComments, accent: Color) {
    items(state.comments) { comment ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(comment.author.trim().take(1).uppercase().ifEmpty { "•" }, color = accent)
                if (comment.avatar != null) {
                    AsyncImage(comment.avatar, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    listOfNotNull(comment.author.ifBlank { null }, comment.date).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                Text(comment.text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .92f))
            }
        }
    }
    item {
        when {
            state.loading -> VideoFeedStatus("Cargando comentarios…", accent, loading = true)
            state.failed -> VideoFeedStatus("No se pudieron cargar los comentarios.", accent, action = "Reintentar") { state.request++ }
            state.disabled -> VideoFeedStatus("Los comentarios de este video están desactivados.", accent)
            state.next != null -> VideoFeedStatus(null, accent, action = "Cargar más") { state.request++ }
            state.comments.isEmpty() -> VideoFeedStatus("Todavía no hay comentarios.", accent)
        }
    }
}
