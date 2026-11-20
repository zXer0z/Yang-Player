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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.yang.player.data.ArtistCollection
import com.yang.player.data.UnifiedPlaylist
import com.yang.player.data.rememberArtistPhoto
import com.yang.player.model.Album
import com.yang.player.model.CategoryFilter
import com.yang.player.model.Song
import com.yang.player.ui.components.AlbumCard
import com.yang.player.ui.components.CategoryChipsRow
import com.yang.player.ui.components.HeroContinueListeningCarousel
import com.yang.player.ui.components.HomeHeader
import com.yang.player.ui.components.SongListItem
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.SurfaceCard
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
    artists: List<ArtistCollection>,
    allPlaylists: List<UnifiedPlaylist>,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongSelected: (Song, List<Song>) -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onArtistSelected: (String) -> Unit = {},
    onPlayToggle: (Song) -> Unit,
    onProfileClick: () -> Unit,
    onViewAllFavorites: () -> Unit,
    bottomContentPadding: Dp = 172.dp,
    suggestedSongs: List<Song> = emptyList(),
    recommendedAlbums: List<Album> = emptyList(),
    onFeaturedSongSelected: (Song, List<Song>) -> Unit = onSongSelected,
    onPlaylists: () -> Unit = {},
    isCloudMode: Boolean = false,
) {
    val middleQueue = favoriteSongs.ifEmpty { suggestedSongs }
    val displayedFavorites = remember(middleQueue) { middleQueue.take(4) }
    val homeAlbums = recommendedAlbums.ifEmpty { albums.take(10) }
    val greeting = when (LocalTime.now().hour) {
        in 6..11 -> "Buenos días,"
        in 12..18 -> "Buenas tardes,"
        else -> "Buenas noches,"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomContentPadding)
    ) {
        // 1. Header with greeting and profile avatar
        item {
            HomeHeader(
                userName = userName,
                greeting = greeting,
                profileImageUrl = profileImageUrl,
                onProfileClick = onProfileClick
            )
        }

        // 2. Filter chips ("Todo", "Álbumes", "Artistas", "Listas")
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

                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionHeader(
                        title = if (favoriteSongs.isNotEmpty()) "Favoritos" else "Para descubrir",
                        actionText = "Ver todo",
                        onActionClick = onViewAllFavorites
                    )
                }

                // Curated Favorites List
                items(
                    items = displayedFavorites,
                    key = { it.id },
                    contentType = { "fav_song_row" }
                ) { song ->
                    SongListItem(
                        song = song,
                        isPlayingThisSong = song.id == currentPlayingSongId,
                        highlightCurrentSong = false,
                        onClick = { onSongSelected(song, middleQueue) }
                    )
                }

                allPlaylists.firstOrNull { it.id == "cloud_recent" }?.let { recent ->
                    item(key = "cloud_recent_title") {
                        Spacer(modifier = Modifier.height(28.dp))
                        SectionHeader(
                            title = recent.name,
                            actionText = "Ver listas",
                            onActionClick = { onCategorySelected(CategoryFilter.LISTAS) }
                        )
                    }
                    items(recent.songs.take(5), key = { "cloud_recent_${it.id}" }) { song ->
                        SongListItem(
                            song = song,
                            isPlayingThisSong = song.id == currentPlayingSongId,
                            highlightCurrentSong = false,
                            onClick = { onSongSelected(song, recent.songs) }
                        )
                    }
                }



                // Recommended Albums Carousel
                if (homeAlbums.isNotEmpty()) {
                    item(contentType = "albums_section") {
                        Column {
                            Spacer(modifier = Modifier.height(28.dp))
                            SectionHeader(
                                title = "Álbumes recomendados",
                                actionText = "Ver todo",
                                onActionClick = { onCategorySelected(CategoryFilter.ALBUMES) }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(
                                    items = homeAlbums,
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
            }

            CategoryFilter.ALBUMES -> {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionHeader(
                        title = "Colección de Álbumes",
                        subtitle = "${albums.size} álbumes disponibles"
                    )
                }

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

            CategoryFilter.ARTISTAS -> {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionHeader(
                        title = "Artistas",
                        subtitle = "${artists.size} artistas en tu biblioteca"
                    )
                }

                val artistPairs = artists.chunked(2)
                items(
                    items = artistPairs,
                    key = { pair -> pair.first().id }
                ) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ArtistGridItem(
                            name = pair[0].name,
                            trackCount = pair[0].songs.size,
                            songs = pair[0].songs,
                            modifier = Modifier.weight(1f),
                            onClick = { onArtistSelected(pair[0].name) }
                        )
                        if (pair.size > 1) {
                            ArtistGridItem(
                                name = pair[1].name,
                                trackCount = pair[1].songs.size,
                                songs = pair[1].songs,
                                modifier = Modifier.weight(1f),
                                onClick = { onArtistSelected(pair[1].name) }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            CategoryFilter.LISTAS -> {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Playlists",
                                color = TextWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${allPlaylists.size} listas disponibles",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                        TextButton(onClick = onPlaylists) {
                            Text("Ver todas", color = LocalArtworkAccent.current, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                val playlistPairs = allPlaylists.chunked(2)
                items(
                    items = playlistPairs,
                    key = { pair -> pair.first().id }
                ) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HomePlaylistGridItem(
                            playlist = pair[0],
                            accent = LocalArtworkAccent.current,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (pair[0].songs.isNotEmpty()) {
                                    onSongSelected(pair[0].songs.first(), pair[0].songs)
                                } else {
                                    onPlaylists()
                                }
                            },
                            onQuickPlay = {
                                if (pair[0].songs.isNotEmpty()) {
                                    onSongSelected(pair[0].songs.first(), pair[0].songs)
                                }
                            }
                        )
                        if (pair.size > 1) {
                            HomePlaylistGridItem(
                                playlist = pair[1],
                                accent = LocalArtworkAccent.current,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (pair[1].songs.isNotEmpty()) {
                                        onSongSelected(pair[1].songs.first(), pair[1].songs)
                                    } else {
                                        onPlaylists()
                                    }
                                },
                                onQuickPlay = {
                                    if (pair[1].songs.isNotEmpty()) {
                                        onSongSelected(pair[1].songs.first(), pair[1].songs)
                                    }
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
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
private fun ArtistGridItem(
    name: String,
    trackCount: Int,
    songs: List<Song>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val artistPhoto = rememberArtistPhoto(name)
    val fallbackCover = remember(songs) { songs.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri }
    val effectiveUri = artistPhoto ?: fallbackCover

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(SurfaceCard)
                .border(1.5.dp, Color.White.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!effectiveUri.isNullOrBlank()) {
                AsyncImage(
                    model = effectiveUri,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = name,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "$trackCount canciones",
            color = TextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HomePlaylistGridItem(
    playlist: UnifiedPlaylist,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onQuickPlay: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
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

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.name,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = playlist.subtitle,
            color = TextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
