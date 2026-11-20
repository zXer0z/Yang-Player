package com.yang.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.yang.player.online.YouTubeDownloads
import com.yang.player.ui.theme.LocalArtworkAccent

@UnstableApi
@Composable
internal fun DownloadsScreen(bottomPadding: Dp, onPlayAudio: (Uri) -> Unit, sourceSelector: @Composable () -> Unit) {
    val context = LocalContext.current
    val downloads = remember(context) { YouTubeDownloads.get(context) }
    val history by downloads.history.collectAsState()
    val status by downloads.status.collectAsState()
    val accent = LocalArtworkAccent.current
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Descargadas", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            sourceSelector()
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            status?.takeIf { it.running }?.let { active ->
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = .1f)).padding(14.dp)) {
                        Text(active.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(active.stage, color = accent, style = MaterialTheme.typography.labelMedium)
                        val progress = active.progress
                        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = accent)
                        else LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth().padding(top = 10.dp), color = accent)
                        TextButton(onClick = downloads::cancel) { Text("Cancelar", color = accent) }
                    }
                }
            }
            message?.let { item { Text(it, color = accent) } }
            if (history.isEmpty() && status?.running != true) {
                item { Text("Todavía no hay archivos descargados.", color = Color.White.copy(alpha = .70f), modifier = Modifier.padding(vertical = 20.dp)) }
            }
            items(history, key = { it.uri.toString() }) { file ->
                val video = file.mimeType.startsWith("video/")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .06f))
                        .clickable {
                            message = null
                            if (!video) onPlayAudio(file.uri)
                            else try {
                                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, file.mimeType)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (_: Exception) {
                                message = "No se pudo abrir el video. Comprueba el archivo y su carpeta."
                            }
                        }.padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (video) Icons.Rounded.VideoFile else Icons.Rounded.AudioFile, contentDescription = null, tint = accent)
                    Column {
                        Text(file.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (video) "Video" else "Audio", color = accent, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
