package com.yang.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.lerp
import com.yang.player.ui.theme.BackgroundDark
import com.yang.player.ui.components.PlaylistPickerSheet
import androidx.compose.ui.platform.LocalContext
import java.text.Normalizer
import java.util.Locale
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.player.model.Song
import com.yang.player.ui.components.SongListItem
import com.yang.player.ui.components.SongOptionsMenuModal
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.SurfaceCard
import com.yang.player.ui.theme.TextDim
import com.yang.player.ui.theme.TextMuted
import com.yang.player.ui.theme.TextWhite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsLibraryScreen(
    songs: List<Song>,
    favoriteSongs: List<Song> = emptyList(),
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongSelected: (Song, List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onToggleFavorite: (Song) -> Unit = {},
    onViewAlbum: (String) -> Unit = {},
    onViewArtist: (String) -> Unit = {},
    requestSearchFocus: Boolean = false,
    onSearchFocusHandled: () -> Unit = {},
    onAudioSettings: () -> Unit = {}
) {
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(requestSearchFocus) {
        if (requestSearchFocus) {
            searchFocus.requestFocus()
            keyboard?.show()
            onSearchFocusHandled()
        }
    }
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("song_list_options", android.content.Context.MODE_PRIVATE) }
    var sortKey by remember { mutableStateOf(preferences.getString("sort", "title") ?: "title") }
    var descending by remember { mutableStateOf(preferences.getBoolean("descending", false)) }
    var onlyFavorites by remember { mutableStateOf(preferences.getBoolean("favorites", false)) }
    var onlyHiRes by remember { mutableStateOf(preferences.getBoolean("hires", false)) }
    var showListOptions by remember { mutableStateOf(false) }
    var createPlaylist by remember { mutableStateOf(false) }
    fun saveOptions() { preferences.edit().putString("sort",sortKey).putBoolean("descending",descending).putBoolean("favorites",onlyFavorites).putBoolean("hires",onlyHiRes).apply() }
    fun normalized(value: String) = Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    fun indexLabel(song: Song) = when(sortKey) { "artist" -> song.artist; "album" -> song.album; else -> song.title }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val accent = LocalArtworkAccent.current

    var activeLetter by remember { mutableStateOf<String?>(null) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubberTouchYPx by remember { mutableFloatStateOf(0f) }
    var scrubberHeightPx by remember { mutableFloatStateOf(1f) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    val favoriteIds = remember(favoriteSongs) { favoriteSongs.map { it.id }.toSet() }
    val filteredSongs = remember(songs, searchQuery, favoriteIds, onlyFavorites, onlyHiRes, sortKey, descending) {
        val matching = songs.filter { song ->
            (!onlyFavorites || song.id in favoriteIds) && (!onlyHiRes || song.isHiRes) &&
                (searchQuery.isBlank() || listOf(song.title, song.artist, song.album).any { it.contains(searchQuery, ignoreCase = true) })
        }
        val order = when(sortKey) {
            "artist" -> compareBy<Song> { normalized(it.artist) }.thenBy { normalized(it.title) }
            "album" -> compareBy<Song> { normalized(it.album) }.thenBy { normalized(it.title) }
            "duration" -> compareBy<Song> { it.durationMs }.thenBy { normalized(it.title) }
            else -> compareBy<Song> { normalized(it.title) }
        }.thenBy { it.id }
        matching.sortedWith(if(descending) order.reversed() else order)
    }
    LaunchedEffect(searchQuery, sortKey, descending, onlyFavorites, onlyHiRes) { listState.scrollToItem(0) }

    // Alphabet index list
    val alphabet = remember { listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#") }

    fun findIndexForLetter(letter: String): Int {
        if (filteredSongs.isEmpty()) return -1
        if (letter == "#") {
            val numIdx = filteredSongs.indexOfFirst { normalized(indexLabel(it)).firstOrNull()?.isLetter() != true }
            return if (numIdx >= 0) numIdx else 0
        }
        val targetChar = letter.first().uppercaseChar()
        val exact = filteredSongs.indexOfFirst {
            normalized(indexLabel(it)).firstOrNull()?.uppercaseChar() == targetChar
        }
        if (exact >= 0) return exact
        val nextIdx = filteredSongs.indexOfFirst {
            normalized(indexLabel(it)).firstOrNull()?.let { first -> first.isLetter() && first.uppercaseChar() > targetChar } == true
        }
        return nextIdx
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            // Header: Title with top action buttons (Sort & Tune)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Canciones",
                        color = TextWhite,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if(onlyFavorites || onlyHiRes || searchQuery.isNotBlank()) "${filteredSongs.size} de ${songs.size} pistas" else "${filteredSongs.size} pistas disponibles",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                    IconButton(
                        onClick = { showListOptions = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Ordenar y filtrar canciones",
                            tint = if(onlyFavorites || onlyHiRes || sortKey != "title" || descending) accent else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if(showListOptions) ModalBottomSheet(onDismissRequest = { showListOptions = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = lerp(BackgroundDark, accent, .08f)) {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                        Text("Organizar canciones", color = TextWhite, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                        Text("Ordenar por", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        listOf("title" to "Título", "artist" to "Artista", "album" to "Álbum", "duration" to "Duración").forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label, color = if(sortKey == key) accent else TextWhite) }, onClick = { sortKey = key; saveOptions(); showListOptions = false }, trailingIcon = { if(sortKey == key) Icon(Icons.Default.Check, null, tint = accent) })
                        }
                        DropdownMenuItem(text = { Text("Orden descendente", color = TextWhite) }, onClick = { descending = !descending; saveOptions() }, trailingIcon = { if(descending) Icon(Icons.Default.Check, null, tint = accent) })
                        HorizontalDivider(color = Color.White.copy(alpha = .1f))
                        DropdownMenuItem(text = { Text("Solo favoritos", color = TextWhite) }, onClick = { onlyFavorites = !onlyFavorites; saveOptions() }, trailingIcon = { if(onlyFavorites) Icon(Icons.Default.Check, null, tint = accent) })
                        DropdownMenuItem(text = { Text("Solo Hi-Res", color = TextWhite) }, onClick = { onlyHiRes = !onlyHiRes; saveOptions() }, trailingIcon = { if(onlyHiRes) Icon(Icons.Default.Check, null, tint = accent) })
                        HorizontalDivider(color = Color.White.copy(alpha = .1f))
                        DropdownMenuItem(text = { Text("Crear playlist", color = accent) }, onClick = { showListOptions = false; createPlaylist = true })
                        }
                    }
                    }
                    IconButton(
                        onClick = onAudioSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Ajustes de audio",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact Search Bar (Smaller height ~40dp as requested)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(color = TextWhite, fontSize = 13.sp,
                            lineHeight = 18.sp, fontWeight = FontWeight.Normal),
                        cursorBrush = SolidColor(accent), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.weight(1f).focusRequester(searchFocus),
                        decorationBox = { field ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) Text("Buscar canciones, artistas o álbumes",
                                    color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                field()
                            }
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fast-fling optimized LazyColumn with 185dp bottom content padding
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 185.dp)
                ) {
                    if(filteredSongs.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No hay canciones con estas opciones", color = TextMuted)
                            TextButton(onClick = { searchQuery = ""; onlyFavorites = false; onlyHiRes = false; saveOptions() }) { Text("Mostrar todas", color = accent) }
                        }
                    }
                    items(
                        items = filteredSongs,
                        key = { it.id },
                        contentType = { "song_row" }
                    ) { song ->
                        SongListItem(
                            song = song,
                            isPlayingThisSong = song.id == currentPlayingSongId,
                            isPlaybackActive = isPlaying,
                            onClick = { onSongSelected(song, filteredSongs) },
                            onOptionsClick = { selectedSongForOptions = song }
                        )
                    }
                }

                // Fast alphabet jump scrubber
                if (searchQuery.isEmpty() && sortKey != "duration" && !descending && filteredSongs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp, top = 8.dp, bottom = 190.dp)
                            .fillMaxHeight()
                            .onGloballyPositioned { coordinates ->
                                scrubberHeightPx = coordinates.size.height.toFloat()
                            }
                            .pointerInput(sortKey, alphabet, filteredSongs) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    hideJob?.cancel()
                                    isScrubbing = true

                                    fun handleTouch(y: Float) {
                                        scrubberTouchYPx = y
                                        if (scrubberHeightPx > 0f) {
                                            val clampedY = y.coerceIn(0f, scrubberHeightPx - 1f)
                                            val itemHeight = scrubberHeightPx / alphabet.size
                                            val index = (clampedY / itemHeight).toInt().coerceIn(0, alphabet.lastIndex)
                                            val letter = alphabet[index]
                                            if (letter != activeLetter) {
                                                activeLetter = letter
                                                try {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                } catch (_: Exception) {}
                                                val targetIdx = findIndexForLetter(letter)
                                                if (targetIdx >= 0) {
                                                    scope.launch { listState.scrollToItem(targetIdx) }
                                                }
                                            }
                                        }
                                    }

                                    handleTouch(down.position.y)

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!pointer.pressed) break
                                        pointer.consume()
                                        handleTouch(pointer.position.y)
                                    }

                                    hideJob = scope.launch {
                                        delay(250)
                                        isScrubbing = false
                                        activeLetter = null
                                    }
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(22.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            alphabet.forEach { letter ->
                                val isSelected = isScrubbing && activeLetter == letter
                                Text(
                                    text = letter,
                                    color = if (isSelected) accent else Color.White.copy(alpha = 0.40f),
                                    fontSize = if (isSelected) 10.sp else 8.5.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Letter Popup Bubble centered vertically next to the scrubber
        androidx.compose.animation.AnimatedVisibility(
            visible = isScrubbing && activeLetter != null,
            enter = fadeIn(tween(80)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 42.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF14171D))
                    .border(2.dp, accent, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeLetter ?: "",
                    color = accent,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if(createPlaylist) PlaylistPickerSheet(emptyList()) { createPlaylist = false }
        // Song Options ModalBottomSheet (All actions copied from reference)
        if (selectedSongForOptions != null) {
            val song = selectedSongForOptions!!
            val isFav = favoriteSongs.any { it.id == song.id }

            SongOptionsMenuModal(
                song = song,
                isFavorite = isFav,
                onDismiss = { selectedSongForOptions = null },
                onPlayNext = { s -> onPlayNext(s) },
                onAddToQueue = { s -> onAddToQueue(s) },
                onToggleFavorite = { s -> onToggleFavorite(s) },
                onViewAlbum = { albumName ->
                    onViewAlbum(albumName)
                },
                onViewArtist = { artistName ->
                    onViewArtist(artistName)
                }
            )
        }
    }
}
