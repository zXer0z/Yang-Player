package com.yang.player.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yang.player.data.CompletionKind
import com.yang.player.data.MetadataMatch
import com.yang.player.data.OnlineMetadata
import com.yang.player.lyrics.LyricsDocument
import com.yang.player.lyrics.LyricsRepository
import com.yang.player.model.Song
import com.yang.player.ui.theme.BackgroundDark
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.SurfaceCard
import com.yang.player.ui.theme.SurfaceDark
import com.yang.player.ui.theme.TextDim
import com.yang.player.ui.theme.TextMuted
import com.yang.player.ui.theme.TextWhite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class SheetTab {
    TAGS,
    LYRICS
}

private enum class SheetView {
    MAIN,
    APPLE_MUSIC_SEARCH,
    LRCLIB_SEARCH,
    MANUAL_LYRICS_EDITOR
}

private enum class OnlineSearchMode {
    COVER_ONLY,
    ALL_METADATA
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataCompletionSheet(
    song: Song,
    onDismiss: () -> Unit,
    initialKind: CompletionKind = CompletionKind.METADATA,
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { OnlineMetadata.get(context) }
    val lyricsRepository = remember { LyricsRepository(context) }
    val revision by repository.changes.collectAsState()
    val current = remember(song, revision) { repository.apply(song) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val accent = LocalArtworkAccent.current

    var activeTab by remember(song.mediaUri, initialKind) {
        mutableStateOf(if (initialKind == CompletionKind.LYRICS) SheetTab.LYRICS else SheetTab.TAGS)
    }
    var currentView by remember(song.mediaUri, initialKind) {
        mutableStateOf(SheetView.MAIN)
    }
    var searchMode by remember { mutableStateOf(OnlineSearchMode.COVER_ONLY) }

    // Tag editor states
    var title by remember(current.title) { mutableStateOf(current.title) }
    var artist by remember(current.artist) { mutableStateOf(current.artist) }
    var album by remember(current.album) { mutableStateOf(current.album) }
    var pendingArtwork by remember(song.mediaUri) { mutableStateOf<String?>(null) }
    var removeArtworkPending by remember(song.mediaUri) { mutableStateOf(false) }

    // Online search states (Apple Music or LRCLIB)
    var searchQuery by remember(song.mediaUri) {
        mutableStateOf(listOf(current.title, current.artist.takeUnless(OnlineMetadata::missing).orEmpty()).filter(String::isNotBlank).joinToString(" "))
    }
    var searchResults by remember(song.mediaUri) { mutableStateOf<List<MetadataMatch>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchedOnce by remember { mutableStateOf(false) }

    // Lyrics states
    var currentLyricsDoc by remember { mutableStateOf<LyricsDocument?>(null) }
    var isLoadingLyrics by remember { mutableStateOf(true) }
    var selectedLyricsMatch by remember { mutableStateOf<MetadataMatch?>(null) }
    var manualLyricsText by remember { mutableStateOf("") }

    // General state
    var isSaving by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    // Reload lyrics on revision change
    LaunchedEffect(song.id, song.mediaUri, revision) {
        isLoadingLyrics = true
        try {
            currentLyricsDoc = lyricsRepository.load(song)
        } catch (_: Exception) {
            currentLyricsDoc = null
        } finally {
            isLoadingLyrics = false
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            pendingArtwork = uri.toString()
            removeArtworkPending = false
        }
    }

    val lyricsFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isSaving = true
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    lyricsRepository.importFile(song, uri)
                    feedbackMessage = "Archivo de letras importado."
                    currentLyricsDoc = lyricsRepository.load(song)
                    onSaved()
                } catch (e: Exception) {
                    feedbackMessage = e.message ?: "No se pudo leer el archivo de letras."
                } finally {
                    isSaving = false
                }
            }
        }
    }

    val previewArtwork = when {
        removeArtworkPending -> null
        pendingArtwork != null -> pendingArtwork
        else -> current.artworkUri
    }
    val hasCustomArtwork = !removeArtworkPending && (pendingArtwork != null || repository.hasCustomArtwork(current))

    fun executeOnlineSearch(kind: CompletionKind) {
        if (isSearching || searchQuery.trim().length < 2) return
        keyboard?.hide()
        isSearching = true
        searchResults = emptyList()
        searchError = null
        searchedOnce = true
        scope.launch {
            try {
                val results = repository.search(searchQuery.trim(), kind, current)
                searchResults = results
                if (results.isEmpty()) {
                    searchError = "No se encontraron coincidencias. Prueba con otros términos."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: java.io.IOException) {
                searchError = "Sin conexión a Internet. Comprueba tu red y reintenta."
            } catch (error: Exception) {
                searchError = error.message ?: "Error al buscar en línea."
            } finally {
                isSearching = false
            }
        }
    }

    val appearancePrefs = remember { com.yang.player.data.AppearancePreferences.get(context) }
    val appearanceConfig by appearancePrefs.config.collectAsState()
    val sheetContainerBg = when (appearanceConfig.themeMode) {
        com.yang.player.data.ThemeMode.OLED_BLACK -> Color(0xFF0A0A0A)
        else -> lerp(BackgroundDark, accent, 0.08f)
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetContainerBg,
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
                .heightIn(max = 730.dp)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.08f), Color.Transparent)))
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            when (currentView) {
                SheetView.MAIN -> {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Editor de canción",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Personaliza etiquetas, carátula y letras para Yang",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        IconButton(
                            enabled = !isSaving,
                            onClick = onDismiss
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = TextMuted)
                        }
                    }

                    // Tab Switcher Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceDark)
                            .padding(4.dp)
                    ) {
                        val isTagsSelected = activeTab == SheetTab.TAGS
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isTagsSelected) accent.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { activeTab = SheetTab.TAGS }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = if (isTagsSelected) accent else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Etiquetas y Carátula",
                                    color = if (isTagsSelected) TextWhite else TextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = if (isTagsSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        val isLyricsSelected = activeTab == SheetTab.LYRICS
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isLyricsSelected) accent.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { activeTab = SheetTab.LYRICS }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = if (isLyricsSelected) accent else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Letras",
                                    color = if (isLyricsSelected) TextWhite else TextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = if (isLyricsSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (activeTab == SheetTab.TAGS) {
                        // TAGS & COVER TAB
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Artwork Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Artwork Thumbnail
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(SurfaceDark)
                                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (previewArtwork != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(previewArtwork)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = "Carátula",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.25f),
                                                modifier = Modifier.size(44.dp)
                                            )
                                        }

                                        if (hasCustomArtwork) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .background(Color.Black.copy(alpha = 0.70f))
                                                    .padding(vertical = 3.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Personalizada",
                                                    color = accent,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    // Cover Actions
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                searchMode = OnlineSearchMode.COVER_ONLY
                                                searchQuery = listOf(title, artist).filter(String::isNotBlank).joinToString(" ")
                                                searchResults = emptyList()
                                                searchError = null
                                                searchedOnce = false
                                                currentView = SheetView.APPLE_MUSIC_SEARCH
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = Color.White.copy(alpha = 0.05f),
                                                contentColor = TextWhite
                                            ),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Buscar portada", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        OutlinedButton(
                                            onClick = { galleryPicker.launch("image/*") },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = Color.White.copy(alpha = 0.05f),
                                                contentColor = TextWhite
                                            ),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent)
                                            Spacer(Modifier.width(8.dp))
                                            Text("De la galería", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        if (hasCustomArtwork) {
                                            TextButton(
                                                onClick = {
                                                    pendingArtwork = null
                                                    removeArtworkPending = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFFFF5252))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Quitar portada", fontSize = 12.sp, color = Color(0xFFFF5252))
                                            }
                                        }
                                    }
                                }
                            }

                            // Apple Music Auto-fill Card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchMode = OnlineSearchMode.ALL_METADATA
                                        searchQuery = listOf(title, artist).filter(String::isNotBlank).joinToString(" ")
                                        searchResults = emptyList()
                                        searchError = null
                                        searchedOnce = false
                                        currentView = SheetView.APPLE_MUSIC_SEARCH
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(accent.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Autocompletar con Apple Music",
                                            color = TextWhite,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Rellena título, artista, álbum y portada en 1 toque",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = accent.copy(alpha = 0.8f),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            // Editable Fields
                            TagInputField(
                                value = title,
                                onValueChange = { title = it },
                                label = "Título",
                                placeholder = "Nombre de la pista",
                                leadingIcon = Icons.Rounded.MusicNote,
                                accent = accent
                            )

                            TagInputField(
                                value = artist,
                                onValueChange = { artist = it },
                                label = "Artista",
                                placeholder = "Nombre del artista o grupo",
                                leadingIcon = Icons.Rounded.Person,
                                accent = accent
                            )

                            TagInputField(
                                value = album,
                                onValueChange = { album = it },
                                label = "Álbum",
                                placeholder = "Nombre del álbum",
                                leadingIcon = Icons.Rounded.Album,
                                accent = accent
                            )

                            // Reset button
                            if (repository.hasCustomChanges(song)) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            isSaving = true
                                            try {
                                                feedbackMessage = repository.resetToOriginal(song)
                                                title = song.title
                                                artist = song.artist
                                                album = song.album
                                                pendingArtwork = null
                                                removeArtworkPending = false
                                            } catch (e: Exception) {
                                                feedbackMessage = e.message ?: "Error al restablecer."
                                            } finally {
                                                isSaving = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Restablecer a etiquetas originales del archivo", color = TextMuted, fontSize = 12.sp)
                                }
                            }

                            feedbackMessage?.let {
                                Text(
                                    text = it,
                                    color = accent,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }

                            Spacer(Modifier.height(6.dp))
                        }

                        // Bottom Save Button for Tags
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .navigationBarsPadding()
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isSaving = true
                                        try {
                                            repository.saveCustomTags(
                                                song = song,
                                                title = title,
                                                artist = artist,
                                                album = album,
                                                artworkUriOrUrl = pendingArtwork,
                                                removeArtwork = removeArtworkPending
                                            )
                                            onSaved()
                                            onDismiss()
                                        } catch (e: Exception) {
                                            feedbackMessage = e.message ?: "No se pudieron guardar los cambios."
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                enabled = !isSaving,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Guardando…", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Guardar cambios", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // LYRICS TAB
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Current Lyrics Status Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Estado de letras",
                                            color = TextWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )

                                        val statusLabel = when {
                                            isLoadingLyrics -> "Cargando…"
                                            currentLyricsDoc?.enhanced == true -> "Sincronizada (Palabra por palabra)"
                                            currentLyricsDoc?.synchronized == true -> "Sincronizada (LRC)"
                                            currentLyricsDoc != null -> "Texto plano"
                                            else -> "Sin letras"
                                        }

                                        val hasLyrics = currentLyricsDoc != null
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (hasLyrics) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f))
                                                .padding(horizontal = 9.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                color = if (hasLyrics) accent else TextMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    if (currentLyricsDoc != null) {
                                        val doc = currentLyricsDoc!!
                                        val previewSnippet = doc.lines.take(8).joinToString("\n") { it.text }.take(350)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(SurfaceDark)
                                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), RoundedCornerShape(12.dp))
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = previewSnippet + if (doc.lines.size > 8) "\n…" else "",
                                                color = TextWhite.copy(alpha = 0.85f),
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        val raw = lyricsRepository.loadRawText(song)
                                                            ?: doc.lines.joinToString("\n") { it.text }
                                                        manualLyricsText = raw
                                                        currentView = SheetView.MANUAL_LYRICS_EDITOR
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = Color.White.copy(alpha = 0.05f),
                                                    contentColor = TextWhite
                                                ),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = accent)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Editar texto", fontSize = 12.sp)
                                            }

                                            if (lyricsRepository.hasCustomLyrics(song)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            isSaving = true
                                                            try {
                                                                repository.removeLyrics(song)
                                                                currentLyricsDoc = lyricsRepository.load(song)
                                                                feedbackMessage = "Letra personalizada eliminada."
                                                                onSaved()
                                                            } catch (e: Exception) {
                                                                feedbackMessage = e.message ?: "Error al eliminar letra."
                                                            } finally {
                                                                isSaving = false
                                                            }
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = Color.White.copy(alpha = 0.05f),
                                                        contentColor = Color(0xFFFF5252)
                                                    ),
                                                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f))
                                                ) {
                                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFFFF5252))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Quitar", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Esta pista no tiene letras. Puedes buscarlas en Internet, importar un archivo o escribirlas tú mismo.",
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            // Option 1: Search Online in LRCLIB
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = listOf(title, artist).filter(String::isNotBlank).joinToString(" ")
                                        searchResults = emptyList()
                                        searchError = null
                                        searchedOnce = false
                                        currentView = SheetView.LRCLIB_SEARCH
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(accent.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Search, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Buscar letras en Internet (LRCLIB)",
                                            color = TextWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Letras sincronizadas y de texto completo gratuitas",
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Option 2: Import from file
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { lyricsFilePicker.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = TextWhite, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Importar archivo de letras",
                                            color = TextWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Selecciona un archivo .lrc, .txt, .ttml o .srt",
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Option 3: Manual text editor
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            val raw = lyricsRepository.loadRawText(song)
                                                ?: currentLyricsDoc?.lines?.joinToString("\n") { it.text } ?: ""
                                            manualLyricsText = raw
                                            currentView = SheetView.MANUAL_LYRICS_EDITOR
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.EditNote, contentDescription = null, tint = TextWhite, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Escribir o pegar letra a mano",
                                            color = TextWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Pega texto de Internet o edita líneas directamente",
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            feedbackMessage?.let {
                                Text(
                                    text = it,
                                    color = accent,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                SheetView.APPLE_MUSIC_SEARCH -> {
                    // Apple Music Search View
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentView = SheetView.MAIN }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextWhite)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (searchMode == OnlineSearchMode.COVER_ONLY) "Buscar carátula online" else "Autocompletar canción",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Catálogo oficial de Apple · 600×600 HD",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = TextMuted)
                        }
                    }

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Título y artista…", color = TextDim, fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = accent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = accent
                        ),
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Borrar", tint = TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { executeOnlineSearch(CompletionKind.ARTWORK) })
                    )

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = { executeOnlineSearch(CompletionKind.ARTWORK) },
                        enabled = !isSearching && searchQuery.trim().length >= 2,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Buscando en Apple Music…", fontSize = 13.sp)
                        } else {
                            Text(if (searchedOnce) "Volver a buscar" else "Buscar coincidencias", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Results
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        searchError?.let {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                            ) {
                                Text(
                                    text = it,
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        if (searchResults.isNotEmpty()) {
                            Text(
                                text = "Toca para aplicar a tu canción:",
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                        }

                        searchResults.forEach { result ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (searchMode == OnlineSearchMode.COVER_ONLY) {
                                            pendingArtwork = result.artwork
                                            removeArtworkPending = false
                                        } else {
                                            title = result.title
                                            artist = result.artist
                                            album = result.album
                                            if (result.artwork.isNotBlank()) {
                                                pendingArtwork = result.artwork
                                                removeArtworkPending = false
                                            }
                                        }
                                        currentView = SheetView.MAIN
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceDark),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (result.artwork.isNotBlank()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(result.artwork)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = result.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Rounded.MusicNote, null, tint = TextMuted, modifier = Modifier.size(24.dp))
                                        }
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.title,
                                            color = TextWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = result.artist,
                                            color = TextMuted,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${result.album} · ${durationLabel(result.durationMs)}",
                                            color = TextDim,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Seleccionar",
                                        tint = accent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }

                SheetView.LRCLIB_SEARCH -> {
                    // LRCLIB Search View
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentView = SheetView.MAIN }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextWhite)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Buscar letras online",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Fuente: LRCLIB (sincronizadas y texto plano)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = TextMuted)
                        }
                    }

                    if (selectedLyricsMatch == null) {
                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Canción y artista…", color = TextDim, fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceDark,
                                unfocusedContainerColor = SurfaceDark,
                                focusedBorderColor = accent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                cursorColor = accent
                            ),
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Borrar", tint = TextMuted, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { executeOnlineSearch(CompletionKind.LYRICS) })
                        )

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = { executeOnlineSearch(CompletionKind.LYRICS) },
                            enabled = !isSearching && searchQuery.trim().length >= 2,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Buscando en LRCLIB…", fontSize = 13.sp)
                            } else {
                                Text(if (searchedOnce) "Volver a buscar" else "Buscar letras", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Results List
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            searchError?.let {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                                ) {
                                    Text(
                                        text = it,
                                        color = TextMuted,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            searchResults.forEach { result ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedLyricsMatch = result },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = result.title,
                                                color = TextWhite,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (result.synced) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (result.synced) "Sincronizada (LRC)" else "Texto plano",
                                                    color = if (result.synced) accent else TextMuted,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        Text(
                                            text = result.artist,
                                            color = TextMuted,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${result.album} · ${durationLabel(result.durationMs)}",
                                            color = TextDim,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                        }
                    } else {
                        // Selected Lyrics Preview
                        val match = selectedLyricsMatch!!
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                onClick = { selectedLyricsMatch = null },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Elegir otra versión", color = accent, fontSize = 13.sp)
                            }

                            Text(
                                text = "${match.title} — ${match.artist}",
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = if (match.synced) "Letra sincronizada con marcas de tiempo" else "Letra de texto completo",
                                color = TextMuted,
                                fontSize = 12.sp
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Text(
                                    text = match.lyrics.take(1500),
                                    color = TextWhite.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }

                            Spacer(Modifier.height(8.dp))
                        }

                        // Save Lyrics Button
                        Button(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    try {
                                        repository.save(current, match, CompletionKind.LYRICS)
                                        currentLyricsDoc = lyricsRepository.load(song)
                                        selectedLyricsMatch = null
                                        currentView = SheetView.MAIN
                                        feedbackMessage = "Letra guardada en Yang."
                                        onSaved()
                                    } catch (e: Exception) {
                                        feedbackMessage = e.message ?: "No se pudo guardar la letra."
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .navigationBarsPadding()
                                .padding(vertical = 4.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Guardando letra…", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Guardar esta letra", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                SheetView.MANUAL_LYRICS_EDITOR -> {
                    // Manual Lyrics Text Editor View
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentView = SheetView.MAIN }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextWhite)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Editor manual de letra",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pega texto plano o formato sincronizado [00:12.34]",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = TextMuted)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = manualLyricsText,
                            onValueChange = { manualLyricsText = it },
                            placeholder = { Text("Escribe o pega aquí la letra de la canción…", color = TextDim, fontSize = 13.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 280.dp, max = 400.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceDark,
                                unfocusedContainerColor = SurfaceDark,
                                focusedBorderColor = accent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                cursorColor = accent
                            )
                        )

                        Spacer(Modifier.height(4.dp))
                    }

                    // Save Manual Lyrics Button
                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                try {
                                    repository.saveLyrics(song, manualLyricsText.trim())
                                    currentLyricsDoc = lyricsRepository.load(song)
                                    currentView = SheetView.MAIN
                                    feedbackMessage = "Letra guardada con éxito."
                                    onSaved()
                                } catch (e: Exception) {
                                    feedbackMessage = e.message ?: "No se pudo guardar la letra."
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving && manualLyricsText.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .navigationBarsPadding()
                            .padding(vertical = 4.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Guardando letra…", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Guardar letra", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    accent: Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextMuted, fontSize = 13.sp) },
        placeholder = { Text(placeholder, color = TextDim, fontSize = 14.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceDark,
            unfocusedContainerColor = SurfaceDark,
            focusedBorderColor = accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite,
            cursorColor = accent
        ),
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = accent.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Borrar",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    )
}

private fun durationLabel(ms: Long): String =
    if (ms <= 0) "sin duración" else "${ms / 60000}:${((ms / 1000) % 60).toString().padStart(2, '0')}"
