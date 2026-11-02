package com.yang.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.SurfaceCard
import com.yang.player.ui.theme.TextMuted
import com.yang.player.ui.theme.TextWhite

@Composable
fun SongListItem(
    song: Song,
    isPlayingThisSong: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 3.dp),
    onOptionsClick: () -> Unit = {},
    highlightCurrentSong: Boolean = true,
    isPlaybackActive: Boolean = false
) {
    val context = LocalContext.current
    val accent = LocalArtworkAccent.current
    val isHighlighted = isPlayingThisSong && highlightCurrentSong

    val imageRequest = remember(song.artworkUri) {
        ImageRequest.Builder(context)
            .data(song.artworkUri)
            .size(150, 150)
            .precision(Precision.INEXACT)
            .crossfade(false) // Zero alpha animation overhead during fling
            .build()
    }

    // The current track is marked locally, without a competing row container.
    val rowModifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with fallback icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(20.dp)
            )

            AsyncImage(
                model = imageRequest,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isHighlighted) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.52f)))
                NowPlayingIndicator(
                    isPlaying = isPlaybackActive,
                    color = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist/Album (compact and sleek without bulky badges or duration)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isHighlighted) accent else TextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} · ${song.album}",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Options 3-dots button with generous thumb touch target
        IconButton(
            onClick = onOptionsClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Opciones",
                tint = TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
