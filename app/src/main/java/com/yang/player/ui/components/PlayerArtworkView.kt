package com.yang.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.yang.player.R
import com.yang.player.data.ArtworkStyle
import com.yang.player.model.Song
import com.yang.player.ui.theme.ArtworkPalette

/**
 * Universal Artwork Presenter for Full Player and Carousels.
 * Supports 4 distinct centered visual styles for the player:
 * 1. MODERN_ROUNDED: 24.dp rounded corners with delicate glass edge
 * 2. VINYL_RECORD: Spinning centered vinyl disc with grooves, inertia deceleration, and center label
 * 3. CD_CLASSIC: Iridescent prismatic compact disc with transparent hub ring
 * 4. STUDIO_FLAT: Full edge-to-edge studio flat art
 */
@Composable
fun PlayerArtworkView(
    song: Song?,
    isPlaying: Boolean,
    style: ArtworkStyle,
    palette: ArtworkPalette,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val artworkRequest = remember(song?.id, song?.artworkUri, song?.mediaUri) {
        if (song != null) {
            ImageRequest.Builder(context)
                .data(song.artworkUri ?: song.mediaUri)
                .size(700, 700)
                .precision(Precision.INEXACT)
                .allowHardware(true)
                .crossfade(false)
                .memoryCacheKey("artwork_${song.id}_${song.artworkUri ?: song.mediaUri}")
                .diskCacheKey("artwork_${song.id}_${song.artworkUri ?: song.mediaUri}")
                .build()
        } else null
    }

    // Realistic turntable / CD inertia physics:
    var rotationAngle by remember(song?.id) { mutableFloatStateOf(0f) }
    var rotationVelocity by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                if (lastNanos != 0L) {
                    val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    val targetVelocity = if (isPlaying) 42f else 0f
                    val lerpFactor = if (isPlaying) 4.5f else 1.8f
                    rotationVelocity += (targetVelocity - rotationVelocity) * (lerpFactor * dt).coerceIn(0f, 1f)
                    if (rotationVelocity > 0.01f || isPlaying) {
                        rotationAngle = (rotationAngle + rotationVelocity * dt) % 360f
                    }
                }
                lastNanos = nowNanos
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            ArtworkStyle.MODERN_ROUNDED -> {
                ModernRoundedArtwork(
                    song = song,
                    artworkRequest = artworkRequest
                )
            }
            ArtworkStyle.VINYL_RECORD -> {
                VinylDiscView(
                    song = song,
                    artworkRequest = artworkRequest,
                    rotationAngle = rotationAngle,
                    accentColor = palette.accentColor,
                    modifier = Modifier.fillMaxSize(0.96f).aspectRatio(1f)
                )
            }
            ArtworkStyle.CD_CLASSIC -> {
                CdClassicView(
                    song = song,
                    artworkRequest = artworkRequest,
                    rotationAngle = rotationAngle,
                    modifier = Modifier.fillMaxSize(0.95f).aspectRatio(1f)
                )
            }
            ArtworkStyle.STUDIO_FLAT -> {
                StudioFlatArtwork(
                    song = song,
                    artworkRequest = artworkRequest
                )
            }
        }
    }
}

/** 1. Modern Rounded Style: 24.dp rounded corners with delicate glass stroke */
@Composable
private fun ModernRoundedArtwork(
    song: Song?,
    artworkRequest: ImageRequest?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (artworkRequest != null && song != null) {
            AsyncImage(
                model = artworkRequest,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(72.dp)
            )
        }
    }
}

/** 2. Spinning Vinyl Record with realistic micro-grooves and center label */
@Composable
fun VinylDiscView(
    song: Song?,
    artworkRequest: ImageRequest?,
    rotationAngle: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotationAngle }
            .clip(CircleShape)
            .background(Color(0xFF0D0E12)),
        contentAlignment = Alignment.Center
    ) {
        // Grooves and specular light reflections
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Outer vinyl edge rim
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = radius - 1.dp.toPx(),
                center = center,
                style = Stroke(1.2.dp.toPx())
            )

            // Concentric sound grooves
            for (i in 0..18) {
                val r = radius * (0.47f + i * 0.026f)
                drawCircle(
                    color = Color.Black.copy(alpha = 0.45f),
                    radius = r,
                    center = center,
                    style = Stroke(0.8.dp.toPx())
                )
                if (i % 3 == 0) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = r + 0.5f,
                        center = center,
                        style = Stroke(0.5.dp.toPx())
                    )
                }
            }

            // Lead-in and run-out dead wax groove
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = radius * 0.45f,
                center = center,
                style = Stroke(1.dp.toPx())
            )

            // Realistic vinyl specular sheen (dual opposing light cones)
            val sheenBrush = Brush.sweepGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.09f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.09f),
                    Color.Transparent
                ),
                center = center
            )
            drawCircle(
                brush = sheenBrush,
                radius = radius * 0.98f,
                center = center
            )
        }

        // Center album label (approx 44% of disc diameter)
        Box(
            modifier = Modifier
                .fillMaxSize(0.44f)
                .clip(CircleShape)
                .background(Color(0xFF16181F))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (artworkRequest != null && song != null) {
                AsyncImage(
                    model = artworkRequest,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp)
                )
            }

            // Center spindle hole and metallic hub
            Image(
                painter = painterResource(R.drawable.vinyl_hub),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.38f)
            )
        }
    }
}

/** 3. Classic Compact Disc (CD) with transparent hub ring and prismatic rainbow reflection */
@Composable
private fun CdClassicView(
    song: Song?,
    artworkRequest: ImageRequest?,
    rotationAngle: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotationAngle }
            .clip(CircleShape)
            .background(Color(0xFF1A1C23)),
        contentAlignment = Alignment.Center
    ) {
        // Disc face: Printed artwork layer
        if (artworkRequest != null && song != null) {
            AsyncImage(
                model = artworkRequest,
                contentDescription = song.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        // Prismatic holographic rainbow reflection & clear CD hub
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Prismatic rainbow sheen sweep
            val rainbowBrush = Brush.sweepGradient(
                listOf(
                    Color(0x3300FFFF), // cyan
                    Color(0x33FF00FF), // magenta
                    Color(0x33FFFF00), // yellow
                    Color(0x3300E676), // green
                    Color(0x332979FF), // blue
                    Color(0x33FF1744), // red
                    Color(0x3300FFFF)
                ),
                center = center
            )
            drawCircle(
                brush = rainbowBrush,
                radius = radius,
                center = center
            )

            // Glass/polycarbonate gloss across the disc surface
            val glossBrush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.12f)
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )
            drawCircle(
                brush = glossBrush,
                radius = radius,
                center = center
            )

            // Outer mirrored bevel
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = radius - 1.dp.toPx(),
                center = center,
                style = Stroke(1.5.dp.toPx())
            )

            // Transparent Polycarbonate Hub Ring (between 20% and 36% radius)
            drawCircle(
                color = Color.White.copy(alpha = 0.40f),
                radius = radius * 0.37f,
                center = center,
                style = Stroke(1.2.dp.toPx())
            )
            drawCircle(
                color = Color(0xCC0B0D13),
                radius = radius * 0.36f,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = radius * 0.23f,
                center = center,
                style = Stroke(0.8.dp.toPx())
            )

            // Center spindle hole
            drawCircle(
                color = Color(0xFF000000),
                radius = radius * 0.13f,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = radius * 0.13f,
                center = center,
                style = Stroke(1.dp.toPx())
            )
        }
    }
}

/** 4. Studio Flat Style: Edge-to-edge pure square with 4.dp subtle corners */
@Composable
private fun StudioFlatArtwork(
    song: Song?,
    artworkRequest: ImageRequest?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 0.75.dp,
                color = Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (artworkRequest != null && song != null) {
            AsyncImage(
                model = artworkRequest,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(72.dp)
            )
        }
    }
}
