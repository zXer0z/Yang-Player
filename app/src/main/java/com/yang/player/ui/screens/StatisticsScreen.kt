package com.yang.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.yang.player.data.ListeningHistoryStore
import com.yang.player.model.Song
import com.yang.player.ui.theme.LocalArtworkAccent
import java.util.Locale

private data class ArtistListening(val name: String, val ms: Long, val plays: Long)

@Composable
fun StatisticsScreen(songs: List<Song>, bottomPadding: Dp, onBack: () -> Unit, onPlay: (Song) -> Unit) {
    val context = LocalContext.current
    val profile by remember { ListeningHistoryStore.get(context) }.profile.collectAsState()
    val accent = LocalArtworkAccent.current
    val byId = remember(songs) { songs.associateBy { it.id } }
    val totalTime = remember(profile) { profile.tracks.values.sumOf { it.listenedMs } }
    val plays = remember(profile) { profile.tracks.values.sumOf { it.playCount.toLong() } }
    val topSongs = remember(profile, byId) { profile.tracks.entries.filter { it.value.listenedMs > 0 && it.key in byId }
        .sortedWith(compareByDescending<Map.Entry<String, com.yang.player.data.TrackListeningStats>> { it.value.playCount }
            .thenByDescending { it.value.listenedMs }).take(10) }
    val artists = remember(profile, byId) {
        profile.tracks.entries.filter { it.value.listenedMs > 0 && it.key in byId }
            .groupBy { byId.getValue(it.key).artist.trim().lowercase(Locale.ROOT) }
            .map { (_, entries) -> ArtistListening(byId.getValue(entries.first().key).artist,
                entries.sumOf { it.value.listenedMs }, entries.sumOf { it.value.playCount.toLong() }) }
            .sortedByDescending { it.ms }.take(10)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = accent) }
                Text("Tu música en números", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text("Historial registrado en Yang", color = Color.White.copy(alpha = .45f), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(accent.copy(alpha = .10f)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Text(listeningTime(totalTime), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("de música escuchada", color = Color.White.copy(alpha = .6f), fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    Column { Text(plays.toString(), color = accent, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("reproducciones", color = Color.White.copy(alpha = .5f), fontSize = 12.sp) }
                    Column { Text(profile.tracks.values.count { it.listenedMs > 0 }.toString(), color = accent, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("canciones", color = Color.White.copy(alpha = .5f), fontSize = 12.sp) }
                }
            }
        }
        if (totalTime == 0L) item { Text("Escucha tu primera canción para empezar a construir tu historial.", color = Color.White.copy(alpha = .55f), fontSize = 15.sp) }
        if (topSongs.isNotEmpty()) item { Text("Canciones más escuchadas", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
        itemsIndexed(topSongs, key = { _, entry -> "song:${entry.key}" }) { index, entry ->
            val song = byId.getValue(entry.key)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPlay(song) }
                .background(Color.White.copy(alpha = .035f)).padding(15.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(song.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Color.White.copy(alpha = .5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entry.value.playCount} reproducciones · ${listeningTime(entry.value.listenedMs)}", color = accent.copy(alpha = .8f), fontSize = 11.sp)
                }
            }
        }
        if (artists.isNotEmpty()) item { Text("Artistas que más escuchas", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp)) }
        itemsIndexed(artists, key = { _, artist -> "artist:${artist.name}" }) { index, artist ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("${index + 1}. ${artist.name}", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listeningTime(artist.ms), color = accent, fontSize = 12.sp)
                }
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .06f))) {
                    Box(Modifier.fillMaxWidth((artist.ms.toFloat() / artists.first().ms.coerceAtLeast(1)).coerceIn(0f, 1f)).fillMaxHeight().background(accent.copy(alpha = .6f)))
                }
            }
        }
        item { Text("Una reproducción se cuenta al escuchar 30 segundos o la mitad de una pista corta. Los rankings usan canciones reconocidas en tu biblioteca. Los totales corresponden al historial conservado en Yang.", color = Color.White.copy(alpha = .35f), fontSize = 11.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 12.dp)) }
    }
}

private fun listeningTime(ms: Long): String {
    if (ms in 1..59_999) return "<1 min"
    val minutes = ms / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}
