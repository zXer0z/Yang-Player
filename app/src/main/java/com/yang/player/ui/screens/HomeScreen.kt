package com.yang.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.yang.player.model.Album
import com.yang.player.model.CategoryFilter
import com.yang.player.model.Song
import com.yang.player.ui.components.AlbumCard
import com.yang.player.ui.components.CategoryChipsRow
import com.yang.player.ui.components.HeroContinueListeningCarousel
import com.yang.player.ui.components.HomeHeader
import com.yang.player.ui.components.SongListItem
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.BackgroundDark
import com.yang.player.ui.theme.HiResCyan
import com.yang.player.ui.theme.SurfaceCard
import com.yang.player.ui.theme.SurfaceElevated
import com.yang.player.ui.theme.TextMuted
import com.yang.player.ui.theme.TextWhite
import java.time.LocalTime

@Composable
fun HomeScreen(
    userName: String = "Usuario",
    profileImageUrl: String?,
    selectedCategory: CategoryFilter,
    onCategorySelected: (CategoryFilter) -> Unit,
    featuredSongs: List<Song>,
    favoriteSongs: List<Song>,
    albums: List<Album>,
    allSongs: List<Song> = emptyList(),
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongSelected: (Song, List<Song>) -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onPlayToggle: (Song) -> Unit,
    onProfileClick: () -> Unit,
    onViewAllFavorites: () -> Unit,
    bottomContentPadding: Dp = 172.dp,
    suggestedSongs: List<Song> = emptyList(),
    recommendedAlbums: List<Album> = emptyList(),
    onFeaturedSongSelected: (Song, List<Song>) -> Unit = onSongSelected,
    onPlaylists: () -> Unit = {}
) {
    val hiResSongs = remember(allSongs) { allSongs.filter { it.isHiRes } }
    val middleQueue = favoriteSongs.ifEmpty { suggestedSongs }
    val displayedFavorites = remember(middleQueue) { middleQueue.take(4) }
    val homeAlbums = recommendedAlbums.ifEmpty { albums.take(10) }
    val greeting = when (LocalTime.now().hour) {
        in 6..11 -> "Buenos días,"
        in 12..18 -> "Buenas tardes,"
        else -> "Buenas noches,"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        // Follow the dock's measured, animated height rather than reserving the
        // expanded menu's space while the dock is compact.
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomContentPadding)
    ) {
        // 1. Header with greeting and YouTube profile avatar
        item {
            HomeHeader(
                userName = userName,
                greeting = greeting,
                profileImageUrl = profileImageUrl,
                onProfileClick = onProfileClick
            )
        }

        // 2. Filter chips ("Todo", "Álbumes", "Listas", "+ Hi-Res")
        item {
            CategoryChipsRow(
                selectedCategory = selectedCategory,
                onCategorySelected = onCategorySelected
            )
        }

        // 3. Dynamic content switching based on selected Category Filter
        when (selectedCategory) {
            CategoryFilter.TODO -> {
                // Featured Hero Carousel ("CONTINÚA ESCUCHANDO")
                item(contentType = "hero_carousel") {
                    HeroContinueListeningCarousel(
                        featuredSongs = featuredSongs,
                        currentPlayingSongId = currentPlayingSongId,
                        isPlaying = isPlaying,
                        onSongClick = { song -> onFeaturedSongSelected(song, featuredSongs) },
                        onPlayToggle = onPlayToggle
                    )
                }

                // Separate the featured artwork from the quieter library sections.
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                    SectionHeader(
                        title = if (favoriteSongs.isNotEmpty()) "Favoritos" else "Para descubrir",
                        actionText = "Ver todo",
                        onActionClick = onViewAllFavorites
                    )
                }

                // Curated Favorites List (Only top songs shown, fast-fling optimized)
                items(
                    items = displayedFavorites,
                    key = { it.id },
                    contentType = { "fav_song_row" }
                ) { song ->
                    SongListItem(
                        song = song,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        isPlayingThisSong = song.id == currentPlayingSongId,
                        highlightCurrentSong = false,
                        onClick = { onSongSelected(song, middleQueue) },
                        onOptionsClick = { /* Options menu */ }
                    )
                }

                // Section Title: "Álbumes para ti" matching Image 2 reference
                if (homeAlbums.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(36.dp))
                        SectionHeader(
                            title = "Álbumes",
                            actionText = "Explorar",
                            onActionClick = { onCategorySelected(CategoryFilter.ALBUMES) }
                        )
                    }

                    // Horizontal Álbumes Carousel with larger 160dp covers
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = homeAlbums.take(10),
                                key = { it.id },
                                contentType = { "album_card" }
                            ) { album ->
                                AlbumCard(
                                    album = album,
                                    onClick = { onAlbumSelected(album) }
                                )
                            }
                        }
                    }
                }
            }

            CategoryFilter.ALBUMES -> {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionHeader(
                        title = "Colección de Álbumes",
                        subtitle = "${albums.size} álbumes disponibles"
                    )
                }

                // 2-column grid of albums
                val albumPairs = albums.chunked(2)
                items(
                    items = albumPairs,
                    key = { pair -> pair.first().id }
                ) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AlbumGridItem(
                            album = pair[0],
                            modifier = Modifier.weight(1f),
                            onClick = { onAlbumSelected(pair[0]) }
                        )
                        if (pair.size > 1) {
                            AlbumGridItem(
                                album = pair[1],
                                modifier = Modifier.weight(1f),
                                onClick = { onAlbumSelected(pair[1]) }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            CategoryFilter.LISTAS -> {
                item { androidx.compose.material3.TextButton(onClick=onPlaylists) { Text("Tus playlists · Crear y organizar",color=LocalArtworkAccent.current) } }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionHeader(
                        title = "Listas de Reproducción",
                        subtitle = "Listas inteligentes de tu biblioteca"
                    )
                }

                item {
                    SmartPlaylistCard(
                        title = "Colección Hi-Res Masters",
                        subtitle = "${hiResSongs.size} pistas · Audio 24-bit/96kHz y DSD",
                        icon = Icons.Default.GraphicEq,
                        accentGradient = Brush.horizontalGradient(
                            listOf(Color(0xFF007AFF), Color(0xFF38B2AC))
                        ),
                        onClick = {
                            if (hiResSongs.isNotEmpty()) {
                                onSongSelected(hiResSongs.first(), hiResSongs)
                            }
                        }
                    )
                }

                item {
                    SmartPlaylistCard(
                        title = "Favoritos",
                        subtitle = "${favoriteSongs.size} canciones preferidas",
                        icon = Icons.Default.Favorite,
                        accentGradient = Brush.horizontalGradient(
                            listOf(Color(0xFFFF2D55), Color(0xFFAF52DE))
                        ),
                        onClick = {
                            if (favoriteSongs.isNotEmpty()) {
                                onSongSelected(favoriteSongs.first(), favoriteSongs)
                            }
                        }
                    )
                }

                item {
                    SmartPlaylistCard(
                        title = "Recientes y Novedades",
                        subtitle = "${allSongs.take(25).size} pistas agregadas recientemente",
                        icon = Icons.Default.History,
                        accentGradient = Brush.horizontalGradient(
                            listOf(Color(0xFF5856D6), Color(0xFF007AFF))
                        ),
                        onClick = {
                            if (allSongs.isNotEmpty()) {
                                onSongSelected(allSongs.first(), allSongs.take(25))
                            }
                        }
                    )
                }

                item {
                    SmartPlaylistCard(
                        title = "Biblioteca Completa",
                        subtitle = "${allSongs.size} canciones en total",
                        icon = Icons.Default.LibraryMusic,
                        accentGradient = Brush.horizontalGradient(
                            listOf(Color(0xFF1E2638), Color(0xFF2C3E50))
                        ),
                        onClick = {
                            if (allSongs.isNotEmpty()) {
                                onSongSelected(allSongs.first(), allSongs)
                            }
                        }
                    )
                }
            }

            CategoryFilter.HI_RES -> {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    HiResAudiophileBanner(count = hiResSongs.size)
                }

                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    SectionHeader(
                        title = "Pistas Hi-Res Lossless",
                        subtitle = "${hiResSongs.size} pistas en alta resolución"
                    )
                }

                items(
                    items = hiResSongs,
                    key = { it.id },
                    contentType = { "hires_song_row" }
                ) { song ->
                    SongListItem(
                        song = song,
                        isPlayingThisSong = song.id == currentPlayingSongId,
                        highlightCurrentSong = false,
                        onClick = { onSongSelected(song, hiResSongs) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onActionClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        if (actionText != null) {
            Text(
                text = actionText,
                color = LocalArtworkAccent.current,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onActionClick() }.padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(album.artworkUri) {
        ImageRequest.Builder(context)
            .data(album.artworkUri)
            .size(220, 220)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .build()
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            )

            AsyncImage(
                model = imageRequest,
                contentDescription = album.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.name,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "${album.artist} · ${album.songCount} pistas",
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmartPlaylistCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentGradient: Brush,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceElevated)
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun HiResAudiophileBanner(count: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0F1E2E),
                        Color(0xFF0A131C),
                        Color(0xFF07090C)
                    )
                )
            )
            .border(1.dp, Color(0x3338B2AC), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(HiResCyan.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "HI-RES AUDIO",
                        color = HiResCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "STUDIO MASTER DIRECT",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Reproducción Bit-Perfect a 32-bit",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Decodificación nativa sin pérdidas para FLAC, DSD64/128, ALAC y WAV hasta 192 kHz. $count pistas en tu dispositivo.",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}
