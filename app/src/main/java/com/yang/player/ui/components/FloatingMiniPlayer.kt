package com.yang.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import com.yang.player.ui.theme.AccentBlue
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.MiniPlayerBorder
import com.yang.player.ui.theme.MiniPlayerGlass
import com.yang.player.ui.theme.SurfaceCard
import com.yang.player.ui.theme.TextMuted
import com.yang.player.ui.theme.TextWhite

@Composable
fun FloatingMiniPlayer(
    currentSong: Song?,
    isPlaying: Boolean,
    onMiniPlayerClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    backdropLayer: GraphicsLayer? = null,
    backdropOrigin: Offset = Offset.Zero,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    outerPadding: Boolean = true
) {
    val context = LocalContext.current
    val contentHorizontal by animateDpAsState(if (compact) 8.dp else 14.dp, tween(550), label = "miniHorizontal")
    val contentVertical by animateDpAsState(if (compact) 4.dp else 10.dp, tween(550), label = "miniVertical")
    val artworkSize by animateDpAsState(if (compact) 40.dp else 48.dp, tween(550), label = "miniArtwork")
    val artworkGap by animateDpAsState(if (compact) 8.dp else 14.dp, tween(550), label = "miniArtworkGap")
    val titleSize by animateFloatAsState(if (compact) 14f else 16f, tween(550), label = "miniTitleSize")
    val artistSize by animateFloatAsState(if (compact) 11f else 13f, tween(550), label = "miniArtistSize")
    val titleLineHeight by animateFloatAsState(if (compact) 17f else 24f, tween(550), label = "miniTitleLine")
    val artistLineHeight by animateFloatAsState(if (compact) 14f else 24f, tween(550), label = "miniArtistLine")
    val imageRequest = remember(currentSong?.artworkUri) {
        currentSong?.artworkUri?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(140, 140)
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build()
        }
    }

    // Mini-player matching reference design:
    // 24dp smooth corners, ambient artwork glow, bare vector play/skip buttons (no circle)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (outerPadding) 14.dp else 0.dp, vertical = if (outerPadding) 6.dp else 0.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color(0xAA000000),
                ambientColor = Color(0xAA000000)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(if (backdropLayer == null) MiniPlayerGlass else Color.Transparent)
            .border(1.dp, MiniPlayerBorder, RoundedCornerShape(24.dp))
            .clickable { onMiniPlayerClick() }
    ) {
        if (backdropLayer != null) {
            // Only this sampled background layer receives the native render
            // effect; cover art, typography and controls remain sharp.
            BackdropGlass(backdropLayer, backdropOrigin, Modifier.matchParentSize())
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = contentHorizontal, vertical = contentVertical),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork with ambient glow matching reference
                Box(
                    modifier = Modifier.size(artworkSize),
                    contentAlignment = Alignment.Center
                ) {
                    // Soft diffused ambient glow behind the artwork
                    if (imageRequest != null) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            modifier = Modifier
                                .size(50.dp)
                                .blur(14.dp)
                                .alpha(0.65f)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Sharp foreground cover art
                    Box(
                        modifier = Modifier
                            .size(artworkSize)
                            .clip(RoundedCornerShape(13.dp))
                            .background(SurfaceCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (currentSong != null) Color.White.copy(alpha = 0.2f) else LocalArtworkAccent.current.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )

                        if (imageRequest != null) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = currentSong?.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(artworkGap))

                // Title & Artist / Album (or Standby placeholder)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "Yang Player",
                        color = TextWhite,
                        fontSize = titleSize.sp,
                        lineHeight = titleLineHeight.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (currentSong != null) {
                            if (compact) currentSong.artist else "${currentSong.artist} · ${currentSong.album}"
                        } else {
                            "Listo para reproducir música"
                        },
                        color = TextMuted,
                        fontSize = artistSize.sp,
                        lineHeight = artistLineHeight.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls matching reference: bare vector Play/Pause & SkipNext without circle container
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = TextWhite,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = !compact,
                        enter = fadeIn(tween(350)) + expandHorizontally(tween(550)),
                        exit = fadeOut(tween(180)) + shrinkHorizontally(tween(550))
                    ) {
                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente",
                            tint = TextWhite,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    }
                }
            }

        }
    }
}
