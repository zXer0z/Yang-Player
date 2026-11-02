package com.yang.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.yang.player.model.Song
import com.yang.player.ui.components.SongListItem
import com.yang.player.ui.components.SongOptionsMenuModal
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.viewmodel.PlayerViewModel

@UnstableApi
@Composable
fun YouScreen(viewModel: PlayerViewModel, bottomContentPadding: Dp, onSongs: () -> Unit, onRescan: () -> Unit) {
    val account by viewModel.online.account.collectAsState()
    val songs by viewModel.allSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val scanning by viewModel.isScanning.collectAsState()
    val message by viewModel.libraryMessage.collectAsState()
    val folders by viewModel.musicFolders.collectAsState()
    val player by viewModel.playerState.collectAsState()
    val destination by viewModel.libraryDestination.collectAsState()
    val accent = LocalArtworkAccent.current
    var page by rememberSaveable { mutableStateOf(destination?.page.orEmpty()) }
    var options by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(destination) {
        destination?.takeIf { it.key.isBlank() }?.let { page = it.page; viewModel.consumeLibraryDestination() }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::addMusicFolder) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::openAudio) }
    fun back() { page = "" }
    BackHandler(page.isNotBlank()) { back() }
    val artists = collections.artists
    val tracks = if (page == "favorites") favorites else emptyList()
    val listState = rememberSaveable(page, saver = LazyListState.Saver) { LazyListState() }
    if(page == "playlists") {
        PlaylistsScreen(songs,favorites.map { it.id }.toSet(),player.currentSong?.id,player.isPlaying,bottomContentPadding,onBack={page=""},onPlay=viewModel::playSong,onNext=viewModel::playNext,onQueue=viewModel::addToQueue,onFavorite=viewModel::toggleFavorite)
        return
    }
    if (page == "statistics") {
        val known by viewModel.online.knownTracks.collectAsState()
        LaunchedEffect(Unit) { viewModel.refreshHomeRecommendations() }
        StatisticsScreen((songs + known).distinctBy { it.id }, bottomContentPadding, onBack = { back() },
            onPlay = { viewModel.playSong(it); viewModel.showFullPlayer() })
        return
    }
    if (page == "account") {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { back() }, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = accent)
                Text("Volver a Tú", color = accent, modifier = Modifier.padding(start = 8.dp))
            }
            AccountScreen(viewModel.online, bottomContentPadding)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        if (page.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = accent) }
                Text(when (page) { "albums" -> "Álbumes"; "artists" -> "Artistas"; else -> "Favoritos" },
                    color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = if (page.isBlank()) 24.dp else 0.dp, bottom = bottomContentPadding)) {
            if (page.isBlank()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (account.avatar != null) AsyncImage(account.avatar, "Tu avatar", Modifier.size(96.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.Person, null, Modifier.size(96.dp).clip(CircleShape).background(accent.copy(alpha = 0.1f)).padding(24.dp), tint = accent)
                        Text(account.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 22.dp))
                        if (account.handle.isNotBlank()) Text(account.handle, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                        TextButton(onClick = { page = "account" }, modifier = Modifier.padding(top = 8.dp)) {
                            Text(if (account.connected) "Gestionar cuenta" else "Conectar cuenta", color = accent, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.045f)).padding(vertical = 18.dp)) {
                        LibraryStat(songs.size, "Canciones", Modifier.weight(1f), onSongs)
                        LibraryStat(albums.size, "Álbumes", Modifier.weight(1f)) { page = "albums" }
                        LibraryStat(favorites.size, "Favoritos", Modifier.weight(1f)) { page = "favorites" }
                    }
                    YouSection("Tu biblioteca")
                    Column(Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.035f))) {
                        YouAction(Icons.Rounded.Album, "Álbumes", "Explora tus discos") { page = "albums" }
                        YouAction(Icons.Rounded.Mic, "Artistas", "${artists.size} artistas en tu biblioteca") { page = "artists" }
                        YouAction(Icons.Rounded.QueueMusic, "Playlists", "Crea y organiza tus listas") { page = "playlists" }
                        YouAction(Icons.Rounded.FavoriteBorder, "Favoritos", "Las canciones que guardaste") { page = "favorites" }
                        YouAction(Icons.Rounded.BarChart, "Estadísticas", "Tu tiempo de escucha, canciones y artistas") { page = "statistics" }
                    }
                    YouSection("Archivos y biblioteca")
                    Column(Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.035f))) {
                        YouAction(Icons.Rounded.Refresh, if (scanning) "Buscando música…" else "Reescanear biblioteca", "Actualiza las canciones del teléfono", enabled = !scanning, onClick = onRescan)
                        YouAction(Icons.Rounded.CreateNewFolder, "Agregar carpeta", "Incluye su música y subcarpetas", enabled = !scanning) { folderPicker.launch(null) }
                        YouAction(Icons.Rounded.AudioFile, "Abrir un audio", "Elige un archivo para reproducir") { audioPicker.launch(arrayOf("audio/*", "application/ogg", "application/x-flac")) }
                    }
                    if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp), color = accent, trackColor = Color.White.copy(alpha = 0.06f))
                    message?.let { Text(it, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
                    if (folders.isNotEmpty()) {
                        YouSection("Carpetas agregadas")
                        folders.forEach { name ->
                            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Folder, null, tint = accent.copy(alpha = 0.65f), modifier = Modifier.size(18.dp))
                                Text(name, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            } else {
                if (page == "favorites") {
                    if (tracks.isEmpty()) item { EmptyLibrary(if (page == "favorites") "Guarda canciones con el corazón para verlas aquí." else "Todavía no hay canciones aquí.") }
                    items(tracks, key = { it.id }) { song ->
                        SongListItem(song, song.id == player.currentSong?.id, onClick = { viewModel.playSong(song, tracks) },
                            onOptionsClick = { options = song }, isPlaybackActive = player.isPlaying)
                    }
                } else if (page == "albums") {
                    if (albums.isEmpty()) item { EmptyLibrary("Los álbumes aparecerán cuando agregues música con sus etiquetas.") }
                    items(albums, key = { it.id }) { album ->
                        LibraryCollection(album.name, album.subtitle, album.artworkUri, Icons.Rounded.Album) { viewModel.openAlbum(album) }
                    }
                } else {
                    if (artists.isEmpty()) item { EmptyLibrary("Agrega música para explorar tus artistas.") }
                    items(artists, key = { it.id }) { artist ->
                        val artistSongs = artist.songs
                        LibraryCollection(artist.name, "${artistSongs.size} ${if (artistSongs.size == 1) "canción" else "canciones"}", artistSongs.firstOrNull { it.artworkUri != null }?.artworkUri, Icons.Rounded.Mic) { viewModel.openArtist(artist.name) }
                    }
                }
            }
        }
    }
    options?.let { song -> SongOptionsMenuModal(song, favorites.any { it.id == song.id }, onDismiss = { options = null },
        onPlayNext = viewModel::playNext, onAddToQueue = viewModel::addToQueue, onToggleFavorite = viewModel::toggleFavorite) }
}

@Composable private fun LibraryStat(count: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
@Composable private fun YouSection(title: String) {
    Text(title, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 32.dp, bottom = 14.dp, start = 4.dp))
}
@Composable private fun YouAction(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    val accent = LocalArtworkAccent.current
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent.copy(alpha = if (enabled) 0.85f else 0.3f), modifier = Modifier.size(23.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, color = Color.White.copy(alpha = if (enabled) 0.95f else 0.4f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(19.dp))
    }
}
@Composable private fun LibraryCollection(title: String, subtitle: String, artwork: String?, fallback: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
            Icon(fallback, null, tint = LocalArtworkAccent.current.copy(alpha = 0.5f))
            if (artwork != null) AsyncImage(artwork, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 5.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.3f))
    }
}
@Composable private fun EmptyLibrary(message: String) { Text(message, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(16.dp), fontSize = 14.sp) }
