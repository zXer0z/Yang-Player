package com.yang.player.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yang.player.data.rememberArtistPhoto
import com.yang.player.data.rememberMissingTracks
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
    onPlayNext: (Song) -> Unit, onAddToQueue: (Song) -> Unit, onFavorite: (Song) -> Unit, onAddCollection: (List<Song>) -> Unit,
    onStartRadio: ((Song) -> Unit)? = null,
    cloudPlaylists: List<com.yang.player.data.CloudPlaylistSummary> = emptyList(),
    onCreateCloudPlaylist: ((String, List<Song>) -> Unit)? = null,
    onAddToCloudPlaylist: ((String, List<Song>) -> Unit)? = null,
) {
    key(title, isArtist) {
        val cover = tracks.firstOrNull { it.artworkUri != null } ?: tracks.firstOrNull()
        val artistPhoto = rememberArtistPhoto(if (isArtist) title else null)
        val effectiveCoverUri = if (isArtist && !artistPhoto.isNullOrBlank()) artistPhoto else cover?.artworkUri
        val palette = rememberArtworkPalette(effectiveCoverUri, cover)
        val body = lerp(BackgroundDark, palette.midColor, 0.40f)
        val context = LocalContext.current
        var options by remember { mutableStateOf<Song?>(null) }
        val minutes = remember(tracks) { tracks.sumOf { it.durationMs } / 60_000 }
        val albumCredit = remember(tracks) { com.yang.player.data.LibraryGrouping(tracks).let { groups -> tracks.firstOrNull()?.let(groups::albumCredit).orEmpty() } }
        val missingTracks by rememberMissingTracks(if (!isArtist) title else "", if (!isArtist) albumCredit else "", tracks)
        CompositionLocalProvider(LocalArtworkAccent provides palette.accentColor) {
            Box(Modifier.fillMaxSize().background(body)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomContentPadding)) {
                    item {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                            Icon(
                                if (isArtist) Icons.Rounded.Person else Icons.Rounded.Album,
                                null,
                                tint = palette.accentColor.copy(alpha = 0.2f),
                                modifier = Modifier.align(Alignment.Center).size(100.dp)
                            )
                            AsyncImage(effectiveCoverUri, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxWidth().height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 48.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent))))
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to body)))
                        }
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            if (!isArtist) Text(albumCredit, color = Color.White.copy(alpha = 0.65f), fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
                            val completionText = if (!isArtist && missingTracks.isNotEmpty()) " · ${tracks.size} de ${tracks.size + missingTracks.size} canciones" else " · $minutes min"
                            Text("${tracks.size} ${if (tracks.size == 1) "canción" else "canciones"}$completionText", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
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
                    if (!isArtist && missingTracks.isNotEmpty()) {
                        item(key = "missing_section_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Faltantes",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${missingTracks.size} canciones faltantes",
                                    color = palette.accentColor.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        items(missingTracks, key = { "missing_${it.id}_${it.position}" }) { missing ->
                            MissingTrackRow(
                                track = missing,
                                coverUri = cover?.artworkUri,
                                accentColor = palette.accentColor,
                                onClick = {
                                    if (!missing.previewUrl.isNullOrBlank()) {
                                        val previewSong = Song(
                                            id = "preview_${missing.id}",
                                            title = missing.title,
                                            artist = missing.artist,
                                            album = title,
                                            durationMs = missing.durationSec * 1000L,
                                            mediaUri = missing.previewUrl,
                                            artworkUri = cover?.artworkUri,
                                            format = com.yang.player.model.AudioFormat.MP3
                                        )
                                        Toast.makeText(context, "Reproduciendo muestra oficial (30s) · ${missing.title}", Toast.LENGTH_SHORT).show()
                                        onPlay(previewSong, listOf(previewSong))
                                    } else {
                                        Toast.makeText(context, "Pista faltante: ${missing.title}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
                // Drawn after the list, so artwork and scrolling content cannot cover the back target.
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)
                    .size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = Color.White)
                }
            }
            options?.let { song -> SongOptionsMenuModal(song, song.id in favoriteIds, onDismiss = { options = null },
                onPlayNext = onPlayNext, onAddToQueue = onAddToQueue, onToggleFavorite = onFavorite,
                onStartRadio = onStartRadio, cloudPlaylists = cloudPlaylists,
                onCreateCloudPlaylist = onCreateCloudPlaylist, onAddToCloudPlaylist = onAddToCloudPlaylist) }
        }
    }
}

@Composable
private fun MissingTrackRow(
    track: com.yang.player.data.OfficialAlbumTrack,
    coverUri: String?,
    accentColor: Color,
    onClick: () -> Unit
) {
    val durationStr = remember(track.durationSec) {
        val m = track.durationSec / 60
        val s = track.durationSec % 60
        "%d:%02d".format(m, s)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            if (coverUri != null) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.55f),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Rounded.Album,
                    null,
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = track.artist,
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = durationStr,
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 12.5.sp
        )
    }
}
