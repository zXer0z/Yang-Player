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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.yang.player.data.CloudPlaylistSummary
import com.yang.player.data.CloudStreamQuality
import com.yang.player.online.YouTubeDiscovery
import kotlinx.coroutines.CancellationException
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.player.model.Song
import com.yang.player.online.OnlineCoordinator
import com.yang.player.online.OnlineState
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

private val DIACRITICS_REGEX = Regex("\\p{M}+")
private val songSortOptions = listOf(
    "title" to "Título",
    "artist" to "Artista",
    "album" to "Álbum",
    "duration" to "Duración",
    "recent" to "Añadidas recientemente"
)

private enum class SongListPanel(val title: String) {
    SORT("Ordenar canciones"),
    OPTIONS("Filtros y opciones"),
    QUALITY("Calidad de reproducción")
}

private fun stripDiacritics(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return ""
    return Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        .replace(DIACRITICS_REGEX, "")
        .lowercase(Locale.ROOT)
}

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
    revealSongId: String? = null,
    onRevealSongHandled: () -> Unit = {},
    onAudioSettings: () -> Unit = {},
    cloudDownloadName: (Song) -> String? = { null },
    onCloudDownload: (Song, android.net.Uri?) -> Unit = { _, _ -> },
    onYouTubeDownload: (Song) -> Unit = {},
    isCloudMode: Boolean = false,
    isCloudRefreshing: Boolean = false,
    onCloudRefresh: () -> Unit = {},
    downloadedCloudIds: Set<String> = emptySet(),
    streamQuality: CloudStreamQuality = CloudStreamQuality.RAW,
    onStreamQualitySelected: (CloudStreamQuality) -> Unit = {},
    cloudPlaylists: List<CloudPlaylistSummary> = emptyList(),
    onCreateCloudPlaylist: ((String, List<Song>) -> Unit)? = null,
    onAddToCloudPlaylist: ((String, List<Song>) -> Unit)? = null,
    onStartRadio: ((Song) -> Unit)? = null,
    sourceSelector: @Composable () -> Unit = {},
    youtube: OnlineCoordinator? = null,
    playbackError: String? = null,
    onRetryPlayback: () -> Unit = {},
) {
    val youtubeState = youtube?.state?.collectAsState()?.value
    val isYouTube = youtube != null
    val showDiscovery = youtubeState != null && youtubeState.query.isBlank() &&
        !youtubeState.showingResults && youtubeState.activePlaylist == null
    var discoveryCategory by rememberSaveable { mutableStateOf(YouTubeDiscovery.VIDEOS) }
    var discoveredSongs by remember(youtube, discoveryCategory) { mutableStateOf(emptyList<Song>()) }
    var discoveryLoading by remember(youtube, discoveryCategory) { mutableStateOf(true) }
    var discoveryFailed by remember(youtube, discoveryCategory) { mutableStateOf(false) }
    var discoveryAttempt by remember(youtube, discoveryCategory) { mutableStateOf(0) }
    LaunchedEffect(youtube, showDiscovery, discoveryCategory, discoveryAttempt) {
        if (!showDiscovery || youtube == null) return@LaunchedEffect
        discoveryLoading = true
        discoveryFailed = false
        try {
            discoveredSongs = youtube.discover(discoveryCategory, refresh = discoveryAttempt > 0)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            discoveryFailed = true
        } finally {
            discoveryLoading = false
        }
    }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(requestSearchFocus) {
        if (requestSearchFocus) {
            searchFocus.requestFocus()
            keyboard?.show()
            onSearchFocusHandled()
        }
    }
    val context = LocalContext.current
    val preferences = remember(context, isCloudMode) {
        context.getSharedPreferences(
            if (isCloudMode) "song_list_options_cloud" else "song_list_options",
            android.content.Context.MODE_PRIVATE
        )
    }
    var sortKey by remember(preferences) { mutableStateOf(preferences.getString("sort", "title") ?: "title") }
    var descending by remember(preferences) { mutableStateOf(preferences.getBoolean("descending", false)) }
    var onlyFavorites by remember(preferences) { mutableStateOf(preferences.getBoolean("favorites", false)) }
    var onlyHiRes by remember(preferences) { mutableStateOf(preferences.getBoolean("hires", false)) }
    var onlyDownloaded by remember(preferences) { mutableStateOf(preferences.getBoolean("downloaded", false)) }
    var listPanel by remember(preferences) { mutableStateOf<SongListPanel?>(null) }
    var createPlaylist by remember { mutableStateOf(false) }
    fun saveOptions() {
        preferences.edit()
            .putString("sort", sortKey)
            .putBoolean("descending", descending)
            .putBoolean("favorites", onlyFavorites)
            .putBoolean("hires", onlyHiRes)
            .putBoolean("downloaded", onlyDownloaded)
            .apply()
    }
    fun indexLabel(song: Song) = when(sortKey) { "artist" -> song.artist; "album" -> song.album; else -> song.title }
    var localQuery by rememberSaveable { mutableStateOf("") }
    val searchQuery = youtubeState?.query ?: localQuery
    var searchFocused by remember { mutableStateOf(false) }
    fun submitYouTubeSearch(query: String) {
        if (youtube == null || query.isBlank()) return
        youtube.search(query)
        keyboard?.hide()
        focusManager.clearFocus()
    }
    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
    var pendingCloudDownload by remember { mutableStateOf<Song?>(null) }
    val downloadPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val song = pendingCloudDownload
        pendingCloudDownload = null
        if (song != null && uri != null) onCloudDownload(song, uri)
    }
    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val accent = LocalArtworkAccent.current

    var activeLetter by remember { mutableStateOf<String?>(null) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubberTouchYPx by remember { mutableFloatStateOf(0f) }
    var scrubberHeightPx by remember { mutableFloatStateOf(1f) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var jumpJob by remember { mutableStateOf<Job?>(null) }


    val effectiveOnlyDownloaded = isCloudMode && onlyDownloaded
    val effectiveSortKey = if (sortKey == "bpm") "title" else sortKey
    val hasActiveFilter = onlyFavorites || onlyHiRes || effectiveOnlyDownloaded
    val hasCustomOrder = effectiveSortKey != "title" || descending

    val favoriteIds = remember(favoriteSongs) { favoriteSongs.map { it.id }.toSet() }
    val filteredSongs = if (youtubeState != null) {
        if (showDiscovery) discoveredSongs else youtubeState.results
    } else remember(songs, searchQuery, favoriteIds, onlyFavorites, onlyHiRes, effectiveOnlyDownloaded, downloadedCloudIds, effectiveSortKey, descending) {
        val hasQuery = searchQuery.isNotBlank()
        val query = searchQuery.trim()
        val matching = if (!onlyFavorites && !onlyHiRes && !effectiveOnlyDownloaded && !hasQuery) {
            songs
        } else {
            songs.filter { song ->
                (!onlyFavorites || song.id in favoriteIds) &&
                (!onlyHiRes || song.isHiRes) &&
                (!effectiveOnlyDownloaded || song.id in downloadedCloudIds) &&
                (!hasQuery || song.title.contains(query, ignoreCase = true) || song.artist.contains(query, ignoreCase = true) || song.album.contains(query, ignoreCase = true))
            }
        }

            val comparator = when (effectiveSortKey) {
                "artist" -> {
                    val keys = matching.associateWith { stripDiacritics(it.artist) }
                    compareBy<Song> { keys[it] ?: "" }.thenBy { stripDiacritics(it.title) }.thenBy { it.id }
                }
                "album" -> {
                    val keys = matching.associateWith { stripDiacritics(it.album) }
                    compareBy<Song> { keys[it] ?: "" }.thenBy { stripDiacritics(it.title) }.thenBy { it.id }
                }
                "duration" -> {
                    compareBy<Song> { it.durationMs }.thenBy { stripDiacritics(it.title) }.thenBy { it.id }
                }
                "recent" -> {
                    compareByDescending<Song> { it.dateAdded }.thenBy { stripDiacritics(it.title) }.thenBy { it.id }
                }

                else -> {
                    val keys = matching.associateWith { stripDiacritics(it.title) }
                    compareBy<Song> { keys[it] ?: "" }.thenBy { it.id }
                }
            }
            if (descending) matching.sortedWith(comparator.reversed()) else matching.sortedWith(comparator)
    }
    if (isYouTube) {
        LaunchedEffect(youtubeState?.searching, youtubeState?.showingResults, youtubeState?.resultTitle) {
            if (revealSongId == null && (youtubeState?.searching == true || youtubeState?.showingResults == false)) {
                listState.scrollToItem(0)
            }
        }
    } else {
        LaunchedEffect(searchQuery, effectiveSortKey, descending, onlyFavorites, onlyHiRes, effectiveOnlyDownloaded) {
            if (revealSongId == null) listState.scrollToItem(0)
        }
    }
    val headerRows = if (youtubeState == null) 0 else {
        (if (searchFocused) youtubeState.suggestions.size else 0) +
            (if (youtubeState.activePlaylist != null) 1 else 0) +
            (if (youtubeState.correctedQuery.isNotBlank()) 1 else 0) +
            (if (playbackError != null || youtubeState.searchError != null) 1 else 0) +
            (if (youtubeState.searching) 1 else 0)
    }
    val waitingForSongs = isCloudRefreshing || youtubeState?.searching == true || (showDiscovery && discoveryLoading)
    LaunchedEffect(revealSongId, filteredSongs, headerRows, waitingForSongs) {
        if (revealSongId == null || waitingForSongs) return@LaunchedEffect
        val songIndex = filteredSongs.indexOfFirst { it.id == revealSongId }
        if (songIndex >= 0) {
            val index = headerRows + songIndex
            val layout = listState.layoutInfo
            val alreadyVisible = layout.visibleItemsInfo.any {
                it.index == index && it.offset >= layout.viewportStartOffset &&
                    it.offset + it.size <= layout.viewportEndOffset - layout.afterContentPadding
            }
            if (!alreadyVisible) listState.scrollToItem(index)
        }
        onRevealSongHandled()
    }

    // Alphabet index list
    val alphabet = remember { listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#") }

    fun findIndexForLetter(letter: String): Int {
        if (filteredSongs.isEmpty()) return -1
        if (letter == "#") {
            val numIdx = filteredSongs.indexOfFirst { stripDiacritics(indexLabel(it)).firstOrNull()?.isLetter() != true }
            return if (numIdx >= 0) numIdx else 0
        }
        val targetChar = letter.first().uppercaseChar()
        val exact = filteredSongs.indexOfFirst {
            stripDiacritics(indexLabel(it)).firstOrNull()?.uppercaseChar() == targetChar
        }
        if (exact >= 0) return exact
        val nextIdx = filteredSongs.indexOfFirst {
            stripDiacritics(indexLabel(it)).firstOrNull()?.let { first -> first.isLetter() && first.uppercaseChar() > targetChar } == true
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Canciones",
                        color = TextWhite,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            youtubeState?.searching == true -> "Cargando resultados…"
                            isYouTube && youtubeState?.showingResults == true -> "${filteredSongs.size} resultados"
                            isYouTube -> "Búsqueda de YouTube"
                            hasActiveFilter || searchQuery.isNotBlank() -> "${filteredSongs.size} de ${songs.size} canciones"
                            else -> "${filteredSongs.size} canciones"
                        },
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isYouTube) {
                        IconButton(
                            onClick = { listPanel = SongListPanel.SORT },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Ordenar canciones",
                                tint = if (hasCustomOrder) accent else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = { listPanel = SongListPanel.OPTIONS },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FilterAlt,
                                contentDescription = "Filtros y opciones de canciones",
                                tint = if (hasActiveFilter) accent else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onAudioSettings,
                        modifier = Modifier.size(48.dp)
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
            sourceSelector()
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
                    if (isYouTube) {
                        IconButton(onClick = { submitYouTubeSearch(searchQuery) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Search, "Buscar en YouTube", tint = accent, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { if (youtube != null) youtube.changeQuery(it) else localQuery = it },
                        textStyle = TextStyle(color = TextWhite, fontSize = 13.sp,
                            lineHeight = 18.sp, fontWeight = FontWeight.Normal),
                        cursorBrush = SolidColor(accent), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                            if (isYouTube) submitYouTubeSearch(searchQuery) else keyboard?.hide()
                        }),
                        modifier = Modifier.weight(1f).focusRequester(searchFocus).onFocusChanged {
                            if (it.isFocused && !searchFocused) youtube?.changeQuery(searchQuery)
                            searchFocused = it.isFocused
                        },
                        decorationBox = { field ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) Text(
                                    if (isYouTube) "Canción, artista o enlace de YouTube" else "Buscar canciones, artistas o álbumes",
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
                                .clickable { if (youtube != null) youtube.clearSearch() else localQuery = "" }
                        )
                    }
                }
            }

            if (showDiscovery) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(YouTubeDiscovery.entries) { category ->
                        val active = discoveryCategory == category
                        FilterChip(
                            selected = active,
                            onClick = {
                                discoveryCategory = category
                                keyboard?.hide()
                                focusManager.clearFocus()
                                scope.launch { listState.scrollToItem(0) }
                            },
                            label = { Text(category.label) },
                            shape = RoundedCornerShape(24.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = .18f),
                                selectedLabelColor = accent,
                                labelColor = TextMuted
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            val refreshModifier = if (isCloudMode) {
                Modifier.pullToRefresh(
                    isRefreshing = isCloudRefreshing,
                    state = pullRefreshState,
                    onRefresh = onCloudRefresh
                )
            } else Modifier
            Box(Modifier.fillMaxSize().then(refreshModifier)) {
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 185.dp)
                    ) {
                        if (youtube != null && youtubeState != null) {
                            youTubeSearchItems(youtubeState, searchFocused, playbackError, onRetryPlayback, youtube, ::submitYouTubeSearch)
                            if (showDiscovery && discoveredSongs.isEmpty()) {
                                item("youtube_discovery_status") {
                                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        when {
                                            discoveryLoading -> CircularProgressIndicator(Modifier.size(24.dp), color = accent)
                                            discoveryFailed -> {
                                                Text("No se pudieron cargar las sugerencias.", color = TextMuted)
                                                TextButton(onClick = { discoveryAttempt++ }) { Text("Reintentar", color = accent) }
                                            }
                                            else -> Text("Esta categoría aún no tiene sugerencias.", color = TextMuted)
                                        }
                                    }
                                }
                            }
                        } else if (filteredSongs.isEmpty() && !isCloudRefreshing) item {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No hay canciones con estas opciones", color = TextMuted)
                                TextButton(onClick = { localQuery = ""; onlyFavorites = false; onlyHiRes = false; onlyDownloaded = false; saveOptions() }) { Text("Mostrar todas", color = accent) }
                            }
                        }
                        itemsIndexed(
                            items = filteredSongs,
                            key = { index, song -> if (youtubeState?.activePlaylist != null) "$index:${song.id}" else song.id },
                            contentType = { _, _ -> "song_row" }
                        ) { _, song ->
                            SongListItem(
                                song = song,
                                isPlayingThisSong = song.id == currentPlayingSongId,
                                isPlaybackActive = isPlaying,
                                onClick = { onSongSelected(song, filteredSongs) },
                                onOptionsClick = { selectedSongForOptions = song }
                            )
                        }
                        if (youtube != null && youtubeState != null && youtubeState.hasMoreResults) {
                            item(key = "youtube_more") {
                                Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    youtubeState.moreError?.let { Text(it, color = TextMuted) }
                                    TextButton(enabled = !youtubeState.loadingMore, onClick = youtube::searchMore) {
                                        Text(
                                            when {
                                                youtubeState.loadingMore -> "Cargando más…"
                                                youtubeState.moreError != null -> "Reintentar"
                                                else -> "Cargar más resultados"
                                            },
                                            color = accent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Fast alphabet jump scrubber
                    if (!isYouTube && searchQuery.isEmpty() && effectiveSortKey != "duration" && effectiveSortKey != "recent" && effectiveSortKey != "bpm" && !descending && filteredSongs.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp, top = 8.dp, bottom = 190.dp)
                                .fillMaxHeight()
                                .onGloballyPositioned { coordinates ->
                                    scrubberHeightPx = coordinates.size.height.toFloat()
                                }
                                .pointerInput(effectiveSortKey, alphabet, filteredSongs) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        hideJob?.cancel()
                                        isScrubbing = true
                                        activeLetter = null

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
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    } catch (_: Exception) {}
                                                    val targetIdx = findIndexForLetter(letter)
                                                    if (targetIdx >= 0) {
                                                        jumpJob?.cancel()
                                                        jumpJob = scope.launch { listState.scrollToItem(targetIdx) }
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
                                    .width(32.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                alphabet.forEach { letter ->
                                    val isSelected = isScrubbing && activeLetter == letter
                                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                if (isCloudMode) {
                    PullToRefreshDefaults.Indicator(
                        state = pullRefreshState,
                        isRefreshing = isCloudRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = SurfaceCard,
                        color = accent
                    )
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

        listPanel?.let { panel ->
            ModalBottomSheet(
                onDismissRequest = { listPanel = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = lerp(BackgroundDark, accent, .08f)
            ) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
                        start = 12.dp, end = 12.dp, bottom = 16.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (panel == SongListPanel.QUALITY) {
                            IconButton(onClick = { listPanel = SongListPanel.OPTIONS }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a filtros y opciones", tint = TextWhite)
                            }
                        }
                        Text(
                            panel.title,
                            color = TextWhite,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    when (panel) {
                        SongListPanel.SORT -> {
                            songSortOptions.forEach { (key, label) ->
                                SongListChoice(label, effectiveSortKey == key, accent) {
                                    sortKey = key
                                    saveOptions()
                                    listPanel = null
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = .1f))
                            SongListChoice("Orden descendente", descending, accent) {
                                descending = !descending
                                saveOptions()
                            }
                        }

                        SongListPanel.OPTIONS -> {
                            SongListChoice("Solo favoritos", onlyFavorites, accent) {
                                onlyFavorites = !onlyFavorites
                                saveOptions()
                            }
                            SongListChoice("Solo Hi-Res", onlyHiRes, accent) {
                                onlyHiRes = !onlyHiRes
                                saveOptions()
                            }
                            if (isCloudMode) {
                                SongListChoice("Solo descargadas", onlyDownloaded, accent) {
                                    onlyDownloaded = !onlyDownloaded
                                    saveOptions()
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = .1f))
                            if (isCloudMode) {
                                DropdownMenuItem(
                                    text = {
                                        Column(Modifier.padding(vertical = 6.dp)) {
                                            Text(SongListPanel.QUALITY.title, color = TextWhite)
                                            Text(streamQuality.label, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = { listPanel = SongListPanel.QUALITY },
                                    trailingIcon = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextMuted) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Crear playlist", color = accent) },
                                onClick = {
                                    listPanel = null
                                    createPlaylist = true
                                },
                                trailingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, tint = accent) }
                            )
                        }

                        SongListPanel.QUALITY -> {
                            CloudStreamQuality.entries.forEach { quality ->
                                SongListChoice(quality.label, streamQuality == quality, accent) {
                                    if (quality != streamQuality) onStreamQualitySelected(quality)
                                    listPanel = SongListPanel.OPTIONS
                                }
                            }
                            Text(
                                "Se aplica a la próxima canción.\n64 kbps ≈ 0,5 MB/min · 96 kbps ≈ 0,7 MB/min",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if(createPlaylist) PlaylistPickerSheet(
            songs = emptyList(),
            isCloudMode = isCloudMode,
            cloudPlaylists = cloudPlaylists,
            onCreateCloudPlaylist = onCreateCloudPlaylist,
            onAddToCloudPlaylist = onAddToCloudPlaylist,
        ) { createPlaylist = false }

        // Song Options ModalBottomSheet (All actions copied from reference)
        if (selectedSongForOptions != null) {
            val song = selectedSongForOptions!!
            val isFav = favoriteSongs.any { it.id == song.id }

            SongOptionsMenuModal(
                song = song,
                isFavorite = isFav,
                onDownload = if (song.isOnline) ({ onYouTubeDownload(song) }) else cloudDownloadName(song)?.let { filename ->
                    {
                        if (android.os.Build.VERSION.SDK_INT >= 29) onCloudDownload(song, null)
                        else {
                            pendingCloudDownload = song
                            downloadPicker.launch(filename)
                        }
                    }
                },
                onStartRadio = if (isCloudMode) onStartRadio else null,
                cloudPlaylists = cloudPlaylists,
                onCreateCloudPlaylist = onCreateCloudPlaylist,
                onAddToCloudPlaylist = onAddToCloudPlaylist,
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

private fun LazyListScope.youTubeSearchItems(
    state: OnlineState,
    focused: Boolean,
    playbackError: String?,
    onRetryPlayback: () -> Unit,
    youtube: OnlineCoordinator,
    onSubmit: (String) -> Unit
) {
    if (focused) {
        items(state.suggestions, key = { "suggestion:$it" }) { suggestion ->
            DropdownMenuItem(
                text = { Text(suggestion, color = TextWhite) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = LocalArtworkAccent.current) },
                onClick = { onSubmit(suggestion) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
    state.activePlaylist?.let { playlist ->
        item(key = "youtube_playlist_title") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = youtube::clearSearch) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a la búsqueda", tint = LocalArtworkAccent.current)
                }
                Text(playlist.title, color = TextWhite, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (state.correctedQuery.isNotBlank()) {
        item(key = "youtube_corrected_query") {
            Text("Resultados para «${state.correctedQuery}»", color = TextMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
    if (playbackError != null || state.searchError != null) {
        item(key = "youtube_search_error") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (state.searchError != null) {
                    Text(state.searchError, color = TextMuted)
                    TextButton(onClick = youtube::retrySearch) { Text("Reintentar búsqueda", color = LocalArtworkAccent.current) }
                }
                if (playbackError != null) {
                    Text(playbackError, color = TextMuted)
                    TextButton(onClick = onRetryPlayback) { Text("Reintentar reproducción", color = LocalArtworkAccent.current) }
                }
            }
        }
    }
    when {
        state.searching -> item(key = "youtube_searching") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = LocalArtworkAccent.current)
            }
        }
        state.showingResults && state.results.isEmpty() && state.searchError == null && !state.hasMoreResults ->
            item(key = "youtube_no_results") {
                Text(
                    if (state.activePlaylist != null) "Esta playlist está vacía" else "No se encontraron canciones en YouTube",
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
    }
}

@Composable
private fun SongListChoice(label: String, checked: Boolean, accent: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = if (checked) accent else TextWhite) },
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (checked) accent.copy(alpha = .10f) else Color.Transparent)
            .semantics { selected = checked },
        onClick = onClick,
        trailingIcon = { if (checked) Icon(Icons.Default.Check, null, tint = accent) }
    )
}
