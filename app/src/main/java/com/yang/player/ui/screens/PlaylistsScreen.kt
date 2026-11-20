package com.yang.player.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yang.player.data.PlaylistStore
import com.yang.player.data.SmartCategory
import com.yang.player.data.SmartPlaylistGenerator
import com.yang.player.data.UnifiedPlaylist
import com.yang.player.model.Song
import com.yang.player.ui.components.PlaylistPickerSheet
import com.yang.player.ui.components.SongListItem
import com.yang.player.ui.components.SongOptionsMenuModal
import com.yang.player.ui.theme.*

@Composable
fun PlaylistsScreen(
    songs: List<Song>,
    favoriteIds: Set<String>,
    currentId: String?,
    playing: Boolean,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onNext: (Song) -> Unit,
    onQueue: (Song) -> Unit,
    onFavorite: (Song) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val accent = LocalArtworkAccent.current
    val store = remember { PlaylistStore.get(context) }
    val userPlaylistsRaw by store.playlists.collectAsState()

    // Map song instances from current library for real-time metadata & cover updates
    val libraryMap = remember(songs) { songs.associateBy { it.id } }

    // Unified User Playlists
    val userPlaylists = remember(userPlaylistsRaw, libraryMap) {
        userPlaylistsRaw.map { lp ->
            val updatedSongs = lp.songs.map { libraryMap[it.id] ?: it }
            val covers = updatedSongs.mapNotNull { it.artworkUri }.distinct().take(4)
            UnifiedPlaylist(
                id = "user_${lp.id}",
                name = lp.name,
                subtitle = "${updatedSongs.size} ${if (updatedSongs.size == 1) "canción" else "canciones"}",
                description = "Lista personalizada de tu biblioteca musical.",
                songs = updatedSongs,
                category = SmartCategory.USER,
                isSmart = false,
                gradientColors = listOf(Color(0xFF232730), Color(0xFF14161C)),
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                coverArtworks = covers
            )
        }
    }

    // Auto-Generated Smart Playlists (Emociones, Ritmo, Géneros)
    val smartPlaylists = remember(songs) {
        SmartPlaylistGenerator.generateSmartPlaylists(songs)
    }

    // Navigation and screen state
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf(SmartCategory.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    // Dialogs & Sheets
    var showCreateSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<UnifiedPlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<UnifiedPlaylist?>(null) }
    var songOptionsTarget by remember { mutableStateOf<Song?>(null) }

    // Currently viewed playlist (if in detail view)
    val currentDetailPlaylist = remember(selectedPlaylistId, userPlaylists, smartPlaylists) {
        if (selectedPlaylistId == null) null
        else (userPlaylists + smartPlaylists).firstOrNull { it.id == selectedPlaylistId }
    }

    fun handleBack() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (selectedPlaylistId != null) {
            selectedPlaylistId = null
        } else if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            onBack()
        }
    }

    BackHandler { handleBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentDetailPlaylist,
            transitionSpec = {
                if (targetState != null) {
                    (fadeIn(animationSpec = tween(220)) + slideInHorizontally { it / 4 })
                        .togetherWith(fadeOut(animationSpec = tween(180)))
                } else {
                    fadeIn(animationSpec = tween(220))
                        .togetherWith(fadeOut(animationSpec = tween(180)) + slideOutHorizontally { it / 4 })
                }
            },
            label = "PlaylistScreenTransition"
        ) { detailPlaylist ->
            if (detailPlaylist != null) {
                // ==========================================
                // VISTA DE DETALLE DE LA PLAYLIST
                // ==========================================
                PlaylistDetailScreen(
                    playlist = detailPlaylist,
                    allLibrarySongs = songs,
                    favoriteIds = favoriteIds,
                    currentId = currentId,
                    playing = playing,
                    bottomPadding = bottomPadding,
                    accent = accent,
                    onBack = { handleBack() },
                    onPlay = onPlay,
                    onOptions = { songOptionsTarget = it },
                    onRename = { renameTarget = detailPlaylist },
                    onDelete = { deleteTarget = detailPlaylist },
                    onRemoveSongFromPlaylist = { song ->
                        val rawId = detailPlaylist.id.removePrefix("user_")
                        store.removeSong(rawId, song.id)
                        Toast.makeText(context, "Canción eliminada de la lista", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                // ==========================================
                // VISTA PRINCIPAL (GRID + CATEGORÍAS)
                // ==========================================
                val filteredPlaylists = remember(selectedCategory, searchQuery, userPlaylists, smartPlaylists) {
                    val base = when (selectedCategory) {
                        SmartCategory.ALL -> userPlaylists + smartPlaylists
                        SmartCategory.USER -> userPlaylists
                        SmartCategory.MOOD -> smartPlaylists.filter { it.category == SmartCategory.MOOD }
                        SmartCategory.RHYTHM -> smartPlaylists.filter { it.category == SmartCategory.RHYTHM }
                        SmartCategory.GENRE -> smartPlaylists.filter { it.category == SmartCategory.GENRE }
                    }
                    if (searchQuery.isBlank()) {
                        base
                    } else {
                        val query = searchQuery.trim().lowercase()
                        base.filter { p ->
                            p.name.lowercase().contains(query) ||
                                p.songs.any { s -> s.title.lowercase().contains(query) || s.artist.lowercase().contains(query) }
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 16.dp,
                        bottom = bottomPadding + 20.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Header con Título, Botón Crear y Botón Buscar
                    item(span = { GridItemSpan(2) }) {
                        PlaylistsTopHeader(
                            accent = accent,
                            isSearching = isSearchActive,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onToggleSearch = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            },
                            onBack = { handleBack() },
                            onCreate = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showCreateSheet = true
                            }
                        )
                    }

                    // 2. Chips de Categorías (Todas, Mis Playlists, Emociones, Ritmo, Géneros)
                    item(span = { GridItemSpan(2) }) {
                        CategoryPillsRow(
                            selectedCategory = selectedCategory,
                            accent = accent,
                            userCount = userPlaylists.size,
                            onSelect = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedCategory = it
                            }
                        )
                    }

                    // 3. Hero Card destacado (solo cuando está en pestaña "Todas" y no hay búsqueda)
                    if (selectedCategory == SmartCategory.ALL && searchQuery.isBlank() && smartPlaylists.isNotEmpty()) {
                        val heroPlaylist = smartPlaylists.firstOrNull { it.id == "smart_mood_euphoria" }
                            ?: smartPlaylists.first()
                        item(span = { GridItemSpan(2) }) {
                            PlaylistsHeroCard(
                                playlist = heroPlaylist,
                                accent = accent,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedPlaylistId = heroPlaylist.id
                                },
                                onPlay = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (heroPlaylist.songs.isNotEmpty()) {
                                        onPlay(heroPlaylist.songs.first(), heroPlaylist.songs)
                                    }
                                }
                            )
                        }
                    }

                    // 4. Si la categoría seleccionada no tiene elementos
                    if (filteredPlaylists.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            EmptyPlaylistsNotice(
                                category = selectedCategory,
                                accent = accent,
                                onCreate = { showCreateSheet = true }
                            )
                        }
                    }

                    // 5. Grid de Tarjetas de Playlist
                    items(filteredPlaylists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            accent = accent,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedPlaylistId = playlist.id
                            },
                            onQuickPlay = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (playlist.songs.isNotEmpty()) {
                                    onPlay(playlist.songs.first(), playlist.songs)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal para Crear Playlist nueva
    if (showCreateSheet) {
        PlaylistPickerSheet(emptyList()) {
            showCreateSheet = false
        }
    }

    // Diálogo para Renombrar Playlist
    renameTarget?.let { target ->
        RenamePlaylistDialog(
            currentName = target.name,
            accent = accent,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                val rawId = target.id.removePrefix("user_")
                store.rename(rawId, newName)
                renameTarget = null
                Toast.makeText(context, "Nombre actualizado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Diálogo para Eliminar Playlist
    deleteTarget?.let { target ->
        DeletePlaylistDialog(
            playlistName = target.name,
            accent = accent,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                val rawId = target.id.removePrefix("user_")
                store.delete(rawId)
                deleteTarget = null
                selectedPlaylistId = null
                Toast.makeText(context, "Playlist eliminada", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Menú de opciones de tres puntos para canciones
    songOptionsTarget?.let { song ->
        SongOptionsMenuModal(
            song = song,
            isFavorite = song.id in favoriteIds,
            onDismiss = { songOptionsTarget = null },
            onPlayNext = onNext,
            onAddToQueue = onQueue,
            onToggleFavorite = onFavorite
        )
    }
}

// =========================================================================
// HEADER & ACCIONES SUPERIORES
// =========================================================================

@Composable
private fun PlaylistsTopHeader(
    accent: Color,
    isSearching: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onBack: () -> Unit,
    onCreate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver",
                    tint = accent
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Playlists",
                    color = TextWhite,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }

            // Botón Buscar
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                Icon(
                    imageVector = if (isSearching) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = "Buscar",
                    tint = if (isSearching) accent else TextWhite.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Botón Nueva Playlist (Pill elegante sin bordes dobles)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f))
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Nueva",
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Campo de búsqueda animado
        AnimatedVisibility(
            visible = isSearching,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Buscar listas o temas...", color = TextDim, fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = accent
                ),
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            )
        }
    }
}

// =========================================================================
// FILTRO POR CHIPS / PILLS
// =========================================================================

@Composable
private fun CategoryPillsRow(
    selectedCategory: SmartCategory,
    accent: Color,
    userCount: Int,
    onSelect: (SmartCategory) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmartCategory.values().forEach { category ->
            val isSelected = category == selectedCategory
            val label = when (category) {
                SmartCategory.ALL -> "Todas"
                SmartCategory.USER -> "Mis listas"
                SmartCategory.MOOD -> "Emociones"
                SmartCategory.RHYTHM -> "Ritmo"
                SmartCategory.GENRE -> "Géneros"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isSelected) accent.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.05f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) accent else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { onSelect(category) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = label,
                    color = if (isSelected) accent else TextWhite.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// =========================================================================
// HERO HIGHLIGHT BANNER
// =========================================================================

@Composable
private fun PlaylistsHeroCard(
    playlist: UnifiedPlaylist,
    accent: Color,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        playlist.gradientColors.first().copy(alpha = 0.35f),
                        Color(0xFF151821)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(playlist.gradientColors.first().copy(alpha = 0.5f), Color.Transparent)),
                RoundedCornerShape(22.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Portada Mosaico en Miniatura
            PlaylistMosaicCover(
                covers = playlist.coverArtworks,
                gradientColors = playlist.gradientColors,
                icon = playlist.icon,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "DESTACADA",
                            color = accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${playlist.songs.size} canciones",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = playlist.name,
                    color = TextWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = playlist.description,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Botón de Reproducción Inmediata
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .shadow(8.dp, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Reproducir lista",
                    tint = Color(0xFF0F1117),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

// =========================================================================
// TARJETA DE PLAYLIST EN EL GRID
// =========================================================================

@Composable
private fun PlaylistCard(
    playlist: UnifiedPlaylist,
    accent: Color,
    onClick: () -> Unit,
    onQuickPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        // Portada Cuadrada con Mosaico y Botón Flotante
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            PlaylistMosaicCover(
                covers = playlist.coverArtworks,
                gradientColors = playlist.gradientColors,
                icon = playlist.icon,
                modifier = Modifier.fillMaxSize()
            )

            // Botón Flotante de Play en la esquina inferior derecha
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable { onQuickPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Nombre de la playlist
        Text(
            text = playlist.name,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Subtítulo con cantidad de canciones
        Text(
            text = playlist.subtitle,
            color = TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =========================================================================
// MOSAICO DE PORTADAS (2x2 O PORTADA ÚNICA O GRADIENTE VIBRANTE)
// =========================================================================

@Composable
fun PlaylistMosaicCover(
    covers: List<String>,
    gradientColors: List<Color>,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        when {
            // Mosaico 2x2 cuando hay al menos 4 portadas distintas
            covers.size >= 4 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = covers[0],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = covers[1],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = covers[2],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = covers[3],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            // Una sola portada cuando hay entre 1 y 3 portadas
            covers.isNotEmpty() -> {
                AsyncImage(
                    model = covers.first(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            // Icono con gradiente vibrante cuando no hay portadas
            else -> {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

// =========================================================================
// PANTALLA DE DETALLE DE LA PLAYLIST
// =========================================================================

@Composable
private fun PlaylistDetailScreen(
    playlist: UnifiedPlaylist,
    allLibrarySongs: List<Song>,
    favoriteIds: Set<String>,
    currentId: String?,
    playing: Boolean,
    bottomPadding: Dp,
    accent: Color,
    onBack: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onOptions: (Song) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRemoveSongFromPlaylist: (Song) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showOverflowMenu by remember { mutableStateOf(false) }
    var trackSearchQuery by remember { mutableStateOf("") }
    var showTrackSearch by remember { mutableStateOf(false) }

    val displayedTracks = remember(playlist.songs, trackSearchQuery) {
        if (trackSearchQuery.isBlank()) playlist.songs
        else {
            val q = trackSearchQuery.trim().lowercase()
            playlist.songs.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 14.dp,
            bottom = bottomPadding + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Barra Superior con Volver, Título y Menú de Opciones
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver a Playlists",
                        tint = accent
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = playlist.name,
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Botón Buscar en la playlist
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showTrackSearch = !showTrackSearch
                        if (!showTrackSearch) trackSearchQuery = ""
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Icon(
                        imageVector = if (showTrackSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = "Buscar tema",
                        tint = if (showTrackSearch) accent else TextWhite.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Menú para playlists de usuario (Renombrar, Eliminar)
                if (!playlist.isSmart) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "Opciones",
                                tint = TextWhite.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier.background(SurfaceElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Renombrar playlist", color = TextWhite) },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = accent) },
                                onClick = {
                                    showOverflowMenu = false
                                    onRename()
                                }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            DropdownMenuItem(
                                text = { Text("Eliminar playlist", color = Color(0xFFFF5252)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF5252)) },
                                onClick = {
                                    showOverflowMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }

        // 2. Buscador interno de canciones
        if (showTrackSearch) {
            item {
                OutlinedTextField(
                    value = trackSearchQuery,
                    onValueChange = { trackSearchQuery = it },
                    placeholder = { Text("Filtrar pistas de esta lista...", color = TextDim, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, null, tint = accent, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }

        // 3. Hero Header de la Playlist (Portada Grande + Metadata + Botones de Acción)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                playlist.gradientColors.first().copy(alpha = 0.35f),
                                SurfaceCard
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(playlist.gradientColors.first().copy(alpha = 0.4f), Color.White.copy(alpha = 0.05f))
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Portada Grande con Mosaico
                PlaylistMosaicCover(
                    covers = playlist.coverArtworks,
                    gradientColors = playlist.gradientColors,
                    icon = playlist.icon,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .shadow(16.dp, RoundedCornerShape(22.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Título
                Text(
                    text = playlist.name,
                    color = TextWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Descripción
                Text(
                    text = playlist.description,
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Contador de pistas y duración
                Text(
                    text = "${playlist.songs.size} canciones · ${playlist.totalDurationFormatted}",
                    color = accent.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Botones Principales: Reproducir y Aleatorio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (playlist.songs.isNotEmpty()) {
                                onPlay(playlist.songs.first(), playlist.songs)
                            }
                        },
                        enabled = playlist.songs.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color(0xFF0F1117)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Reproducir", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val shuffled = playlist.songs.shuffled()
                            if (shuffled.isNotEmpty()) {
                                onPlay(shuffled.first(), shuffled)
                            }
                        },
                        enabled = playlist.songs.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.2f)))
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            contentColor = TextWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Aleatorio", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 4. Encabezado de la lista de canciones
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Canciones",
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Si la lista está vacía
        if (displayedTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (trackSearchQuery.isNotBlank()) "No hay canciones que coincidan con la búsqueda."
                        else "Esta playlist aún no tiene canciones.\nPuedes añadir temas tocando los tres puntos de cualquier canción.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 5. Lista de Canciones
        items(displayedTracks, key = { it.id }) { song ->
            SongListItem(
                song = song,
                isPlayingThisSong = song.id == currentId,
                onClick = { onPlay(song, playlist.songs) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                onOptionsClick = { onOptions(song) },
                isPlaybackActive = playing
            )
        }
    }
}

// =========================================================================
// AVISO DE PLAYLIST VACÍA O SIN ELEMENTOS
// =========================================================================

@Composable
private fun EmptyPlaylistsNotice(
    category: SmartCategory,
    accent: Color,
    onCreate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = when (category) {
                SmartCategory.USER -> Icons.AutoMirrored.Rounded.QueueMusic
                SmartCategory.MOOD -> Icons.Rounded.WbSunny
                SmartCategory.RHYTHM -> Icons.Rounded.FitnessCenter
                SmartCategory.GENRE -> Icons.Rounded.Album
                else -> Icons.Rounded.MusicNote
            },
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(44.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = when (category) {
                SmartCategory.USER -> "No tienes playlists creadas aún"
                SmartCategory.MOOD -> "Sin listas de emociones disponibles"
                SmartCategory.RHYTHM -> "Sin listas de ritmo disponibles"
                SmartCategory.GENRE -> "Sin listas de género disponibles"
                else -> "No se encontraron playlists"
            },
            color = TextWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when (category) {
                SmartCategory.USER -> "Crea tu primera lista personalizada y añade canciones desde el menú de opciones."
                else -> "Añade más música a tu biblioteca para que Yang Player genere colecciones de este estilo."
            },
            color = TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp
        )

        if (category == SmartCategory.USER || category == SmartCategory.ALL) {
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onCreate,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF0F1117)
                )
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Crear Playlist", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =========================================================================
// DIÁLOGOS (RENOMBRAR / ELIMINAR)
// =========================================================================

@Composable
private fun RenamePlaylistDialog(
    currentName: String,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(22.dp),
        title = { Text(text = "Renombrar Playlist", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(text = "Elige un nuevo nombre para esta lista:", color = TextMuted, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.trim().isNotEmpty()) onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF0F1117))
            ) {
                Text(text = "Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar", color = TextMuted)
            }
        }
    )
}

@Composable
private fun DeletePlaylistDialog(
    playlistName: String,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(22.dp),
        title = { Text(text = "Eliminar Playlist", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = "¿Estás seguro de que deseas eliminar la playlist \"$playlistName\"?\n\nLas canciones permanecerán en tu biblioteca, solo se eliminará esta lista.",
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White)
            ) {
                Text(text = "Eliminar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar", color = TextMuted)
            }
        }
    )
}
