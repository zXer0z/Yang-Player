package com.yang.player.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.yang.player.ui.theme.BackgroundDark
import com.yang.player.ui.theme.SurfaceCard
import com.yang.player.ui.theme.TextMuted
import com.yang.player.ui.theme.TextWhite

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsMenuModal(
    song: Song?,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onViewAlbum: (String) -> Unit = {},
    onViewArtist: (String) -> Unit = {},
    onDeleteSong: ((Song) -> Unit)? = null,
    playbackControls: (@Composable () -> Unit)? = null
) {
    if (song == null) return
    val deleteSong = onDeleteSong ?: LocalSongDeletion.current

    var completion by remember(song.mediaUri) { mutableStateOf(false) }
    if (completion) { MetadataCompletionSheet(song, onDismiss); return }
    var playlistPicker by remember(song.id) { mutableStateOf(false) }
    if(playlistPicker) { PlaylistPickerSheet(listOf(song),onDismiss);return }
    val context = LocalContext.current
    val accent = LocalArtworkAccent.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val imageRequest = remember(song.artworkUri) {
        ImageRequest.Builder(context)
            .data(song.artworkUri)
            .size(160, 160)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .build()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = lerp(BackgroundDark, accent, 0.08f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.08f), Color.Transparent)))
                .padding(bottom = 32.dp)
        ) {
            // Header: Clean thumbnail, title and artist — album
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(24.dp)
                    )

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${song.artist} — ${song.album}",
                        color = TextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = Color(0x1FFFFFFF),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Scrollable Options List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                playbackControls?.invoke()
                // 1. Reproducir a continuación
                OptionItem(
                    icon = Icons.Default.PlayArrow,
                    label = "Reproducir a continuación",
                    onClick = {
                        onPlayNext(song)
                        onDismiss()
                    }
                )

                // 2. Añadir al final de la cola
                OptionItem(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "Añadir al final de la cola",
                    onClick = {
                        onAddToQueue(song)
                        onDismiss()
                    }
                )

                // 3. Añadir a playlist...
                OptionItem(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    label = "Añadir a playlist...",
                    onClick = { playlistPicker = true }
                )

                // 4. Añadir a favoritos / Eliminar de favoritos
                OptionItem(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = if (isFavorite) "Eliminar de favoritos" else "Añadir a favoritos",
                    iconTint = accent,
                    onClick = {
                        onToggleFavorite(song)
                        onDismiss()
                    }
                )

                // 5. Ver álbum
                OptionItem(
                    icon = Icons.Default.Album,
                    label = "Ver álbum («${song.album}»)",
                    onClick = {
                        onViewAlbum(song.album)
                        onDismiss()
                    }
                )

                // 6. Ver artista
                OptionItem(
                    icon = Icons.Outlined.Person,
                    label = "Ver artista («${song.artist}»)",
                    onClick = {
                        onViewArtist(song.artist)
                        onDismiss()
                    }
                )

                if (!song.isOnline) OptionItem(
                    icon = Icons.Default.Album,
                    label = "Completar datos faltantes",
                    onClick = { completion = true }
                )

                // 7. Compartir canción
                OptionItem(
                    icon = Icons.Default.Share,
                    label = "Compartir canción",
                    onClick = {
                        shareAudioTrack(context, song)
                        onDismiss()
                    }
                )

                // 8. Eliminar permanentemente
                if (!song.isOnline) OptionItem(
                    icon = Icons.Default.DeleteOutline,
                    label = "Eliminar permanentemente",
                    iconTint = Color(0xFFFF7777),
                    onClick = {
                        deleteSong(song)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    label: String,
    iconTint: Color = LocalArtworkAccent.current,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = label,
            color = TextWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun shareAudioTrack(context: Context, song: Song) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(song.mediaUri))
            putExtra(Intent.EXTRA_TEXT, "Escuchando «${song.title}» de ${song.artist} en Yang Player")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir «${song.title}»"))
    } catch (_: Exception) {}
}

