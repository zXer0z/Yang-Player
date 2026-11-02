package com.yang.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yang.player.model.Song
import com.yang.player.online.OnlineCoordinator
import com.yang.player.online.OnlinePlaylist
import com.yang.player.ui.components.HeroContinueListeningCarousel
import com.yang.player.ui.components.SongListItem
import com.yang.player.ui.components.SongOptionsMenuModal
import com.yang.player.ui.theme.LocalArtworkAccent

@Composable
fun OnlineScreen(
    online: OnlineCoordinator,
    currentSongId: String?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    onPlay: (Song, List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onFavorite: (Song) -> Unit,
    onAccount: () -> Unit,
    bottomContentPadding: Dp,
    playbackError: String? = null,
    onRetryPlayback: () -> Unit = {}
) {
    val state by online.state.collectAsState()
    val account by online.account.collectAsState()
    val accent = LocalArtworkAccent.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(Unit) { online.loadHome() }
    fun search(value: String = state.query) { online.search(value); keyboard?.hide(); focus.clearFocus() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 20.dp, bottom = bottomContentPadding)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Online", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(if (account.connected) "Tu música en YouTube" else "Descubre música en YouTube", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
                IconButton(onClick = { online.loadHome(force = true) }) { Icon(Icons.Rounded.Refresh, "Actualizar", tint = accent) }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.query, onValueChange = online::changeQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).onFocusChanged { focused = it.isFocused },
                placeholder = { Text("Canción, artista o enlace de YouTube", fontSize = 13.sp) },
                leadingIcon = { IconButton(onClick = { search() }) { Icon(Icons.Rounded.Search, "Buscar") } },
                trailingIcon = { if (state.query.isNotBlank()) IconButton(onClick = online::showHome) { Icon(Icons.Rounded.Clear, "Limpiar búsqueda") } },
                singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent, unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = Color.White.copy(alpha = 0.07f), unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = accent,
                    focusedPlaceholderColor = Color.White.copy(alpha = 0.4f), unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f)
                )
            )
        }
        if (focused && state.suggestions.isNotEmpty()) items(state.suggestions, key = { "suggest:$it" }) { suggestion ->
            Row(Modifier.fillMaxWidth().clickable { search(suggestion) }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = accent.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(suggestion, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (state.error != null || playbackError != null) item {
            Column(Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.08f)).padding(14.dp)) {
                Text(playbackError ?: state.error.orEmpty(), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                TextButton(onClick = { if (playbackError != null) onRetryPlayback() else if (state.showingResults) search() else online.loadHome(force = true) }) { Text("Reintentar", color = accent) }
            }
        }
        if (state.showingResults) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = online::showHome) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver a Online", tint = accent) }
                    Text(state.resultTitle, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2)
                }
                if (state.correctedQuery.isNotBlank()) Text("Resultados para «${state.correctedQuery}»", color = accent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
                if (state.searching) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp)) }
                else if (state.results.isEmpty() && state.error == null) Text("No se encontraron canciones", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(24.dp))
            }
            itemsIndexed(state.results, key = { index, song -> "result:$index:${song.id}" }) { _, song ->
                SongListItem(song, song.id == currentSongId, onClick = { onPlay(song, state.results) }, onOptionsClick = { options = song }, isPlaybackActive = isPlaying)
            }
        } else {
            if (!account.connected) item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onAccount).background(Color.White.copy(alpha = 0.04f)).padding(14.dp)) {
                    Text("Conecta tu cuenta para ver tus listas y recomendaciones", color = accent, fontSize = 13.sp)
                }
            }
            if (state.loadingHome && !state.cacheAvailable) item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp)) }
            }
            if (state.playlists.isNotEmpty()) {
                item { OnlineSectionTitle("Tus listas") }; item { PlaylistShelf(state.playlists, online::openPlaylist) }
            }
            state.shelves.forEachIndexed { index, shelf ->
                item(key = "section:$index") { OnlineSectionTitle(shelf.title) }
                if (shelf.songs.isNotEmpty()) item(key = "songs:$index") {
                    HeroContinueListeningCarousel(shelf.songs.distinctBy { it.id }, currentSongId, isPlaying,
                        onSongClick = { onPlay(it, shelf.songs) }, onPlayToggle = { onPlay(it, shelf.songs) })
                }
                if (shelf.playlists.isNotEmpty()) item(key = "playlists:$index") { PlaylistShelf(shelf.playlists, online::openPlaylist) }
            }
        }
    }
    options?.let { song ->
        SongOptionsMenuModal(song, song.id in favoriteIds, onDismiss = { options = null }, onPlayNext = onPlayNext, onAddToQueue = onAddToQueue, onToggleFavorite = onFavorite)
    }
}

@Composable private fun OnlineSectionTitle(title: String) {
    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp))
}

@Composable private fun PlaylistShelf(playlists: List<OnlinePlaylist>, onClick: (OnlinePlaylist) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(playlists, key = { index, item -> "$index:${item.id}" }) { _, item ->
            Column(Modifier.width(155.dp).clickable { onClick(item) }) {
                AsyncImage(item.image, item.title, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)), contentScale = ContentScale.Crop)
                Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text(item.subtitle, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
