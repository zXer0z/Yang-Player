package com.yang.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.yang.player.model.Song
import com.yang.player.ui.theme.HiResCyan
import com.yang.player.ui.theme.SurfaceCard
import com.yang.player.ui.theme.TextMuted
import com.yang.player.ui.theme.TextWhite

@Composable
fun HeroContinueListeningCarousel(
    featuredSongs: List<Song>,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onPlayToggle: (Song) -> Unit
) {
    if (featuredSongs.isEmpty()) return

    // 100% unified carousel design: Every card uses the pure uncropped carátula format!
    // No mismatched solid cards leaving empty black gaps.
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(featuredSongs, key = { it.id }) { song ->
            val isCurrent = song.id == currentPlayingSongId
            val isPlayingThis = isCurrent && isPlaying

            CleanSongHeroItem(
                song = song,
                isPlayingThis = isPlayingThis,
                onCardClick = { onSongClick(song) },
                onPlayClick = { onPlayToggle(song) }
            )
        }
    }
}

@Composable
private fun CleanSongHeroItem(
    song: Song,
    isPlayingThis: Boolean,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val context = LocalContext.current

    // Request full 600x600 HD resolution for crystal-clear album covers
    val imageRequest = remember(song.artworkUri) {
        ImageRequest.Builder(context)
            .data(song.artworkUri)
            .size(600, 600)
            .precision(Precision.EXACT)
            .crossfade(true)
            .build()
    }

    Column(
        modifier = Modifier
            .width(210.dp)
            .clickable { onCardClick() }
    ) {
        // 1. Full uncropped square album artwork in HD (NO forced background box, 1:1 true aspect ratio!)
        Box(
            modifier = Modifier
                .size(210.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(18.dp),
                    spotColor = Color(0x66000000)
                )
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(48.dp)
            )

            AsyncImage(
                model = imageRequest,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Info Row: Title, Artist & Format badge on left; circular play button on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Circular Play Button (White with black icon)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onPlayClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlayingThis) "Pausa" else "Reproducir",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
