package com.yang.player.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yang.player.model.Song
import com.yang.player.ui.components.SongListItem
import com.yang.player.ui.components.SongOptionsMenuModal
import com.yang.player.ui.theme.BackgroundDark
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.rememberArtworkPalette

@Composable
fun CollectionDetailScreen(
    title: String, isArtist: Boolean, tracks: List<Song>, favoriteIds: Set<String>, currentSongId: String?, isPlaying: Boolean,
    bottomContentPadding: Dp, onBack: () -> Unit, onPlay: (Song, List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit, onAddToQueue: (Song) -> Unit, onFavorite: (Song) -> Unit, onAddCollection: (List<Song>) -> Unit
) {
    key(title, isArtist) {
        val cover = tracks.firstOrNull { it.artworkUri != null } ?: tracks.firstOrNull()
        val palette = rememberArtworkPalette(cover)
        val body = lerp(BackgroundDark, palette.midColor, 0.40f)
        val context = LocalContext.current
        var options by remember { mutableStateOf<Song?>(null) }
        val minutes = remember(tracks) { tracks.sumOf { it.durationMs } / 60_000 }
        val albumCredit = remember(tracks) { com.yang.player.data.LibraryGrouping(tracks).let { groups -> tracks.firstOrNull()?.let(groups::albumCredit).orEmpty() } }
        CompositionLocalProvider(LocalArtworkAccent provides palette.accentColor) {
            Box(Modifier.fillMaxSize().background(body)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomContentPadding)) {
                    item {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                            Icon(Icons.Rounded.Album, null, tint = palette.accentColor.copy(alpha = 0.2f), modifier = Modifier.align(Alignment.Center).size(100.dp))
                            AsyncImage(cover?.artworkUri, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxWidth().height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 48.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent))))
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to body)))
                        }
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            if (!isArtist) Text(albumCredit, color = Color.White.copy(alpha = 0.65f), fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
                            Text("${tracks.size} ${if (tracks.size == 1) "canción" else "canciones"} · $minutes min", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                            Row(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilledIconButton(onClick = { val shuffled = tracks.shuffled(); shuffled.firstOrNull()?.let { onPlay(it, shuffled) } }, enabled = tracks.isNotEmpty(),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.07f), contentColor = palette.accentColor), modifier = Modifier.size(50.dp)) {
                                    Icon(Icons.Rounded.Shuffle, "Reproducir aleatoriamente")
                                }
                                Button(onClick = { tracks.firstOrNull()?.let { onPlay(it, tracks) } }, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f).height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentColor, contentColor = BackgroundDark)) {
                                    Icon(Icons.Rounded.PlayArrow, null)
                                    Text("Reproducir", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                                }
                                FilledIconButton(onClick = { onAddCollection(tracks); Toast.makeText(context, "${tracks.size} canciones añadidas a la cola", Toast.LENGTH_SHORT).show() }, enabled = tracks.isNotEmpty(),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.07f), contentColor = palette.accentColor), modifier = Modifier.size(50.dp)) {
                                    Icon(Icons.Rounded.Add, "Añadir a la cola")
                                }
                            }
                        }
                    }
                    item(key = "wikipedia") {
                        com.yang.player.ui.components.WikipediaCard(title, albumCredit, isArtist)
                    }
                    items(tracks, key = { it.id }) { song ->
                        SongListItem(song, song.id == currentSongId, onClick = { onPlay(song, tracks) }, onOptionsClick = { options = song }, isPlaybackActive = isPlaying)
                    }
                }
                // Drawn after the list, so artwork and scrolling content cannot cover the back target.
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)
                    .size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = Color.White)
                }
            }
            options?.let { song -> SongOptionsMenuModal(song, song.id in favoriteIds, onDismiss = { options = null },
                onPlayNext = onPlayNext, onAddToQueue = onAddToQueue, onToggleFavorite = onFavorite) }
        }
    }
}
