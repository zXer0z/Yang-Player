package com.yang.player.ui.components

import android.Manifest
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import com.yang.player.data.ThemeMode
import com.yang.player.model.Song
import com.yang.player.online.DownloadKind
import com.yang.player.online.YouTubeDownloadOption
import com.yang.player.online.YouTubeDownloadOptions
import com.yang.player.online.YouTubeDownloads
import com.yang.player.ui.theme.ArtworkPalette
import com.yang.player.ui.theme.BackgroundDark
import com.yang.player.ui.theme.LocalArtworkAccent
import kotlinx.coroutines.CancellationException

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YouTubeDownloadSheet(
    song: Song,
    palette: ArtworkPalette,
    themeMode: ThemeMode,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val downloads = remember(context) { YouTubeDownloads.get(context) }
    val status by downloads.status.collectAsState()
    val accent = LocalArtworkAccent.current
    var folder by remember { mutableStateOf(downloads.folder()) }
    var folderError by remember { mutableStateOf(false) }
    var hasNotifications by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasNotifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            folderError = runCatching { downloads.chooseFolder(uri) }.isFailure
            folder = downloads.folder()
        }
    }
    var kind by rememberSaveable(song.id) { mutableStateOf(DownloadKind.AUDIO) }
    var selectedId by rememberSaveable(song.id) { mutableStateOf<String?>(null) }
    var showQualities by remember { mutableStateOf(false) }
    var complete by rememberSaveable(song.id) { mutableStateOf(true) }
    var options by remember(song.id) { mutableStateOf(emptyList<YouTubeDownloadOption>()) }
    var loading by remember(song.id) { mutableStateOf(true) }
    var failed by remember(song.id) { mutableStateOf(false) }
    var attempt by remember(song.id) { mutableIntStateOf(0) }
    LaunchedEffect(song.mediaUri, attempt) {
        loading = true
        failed = false
        try {
            options = YouTubeDownloadOptions.load(song.mediaUri, refresh = attempt != 0)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    val choices = options.filter { it.kind == kind }
    val selected = choices.firstOrNull { it.id == selectedId } ?: choices.firstOrNull()
    val running = status?.running == true
    val buttonColors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = BackgroundDark)
    val sheetColor = when (themeMode) {
        ThemeMode.OLED_BLACK -> Color.Black
        ThemeMode.CUSTOM_ACCENT -> lerp(BackgroundDark, accent, .16f)
        ThemeMode.DYNAMIC -> lerp(BackgroundDark, palette.topColor, .50f)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetColor
    ) {
        Box(Modifier.fillMaxWidth()) {
            ArtworkBlurBackdrop(palette, themeMode, accent, Modifier.matchParentSize(), dim = .47f)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text("Descargar", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(song.title, color = Color.White.copy(alpha = .72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        listOf(DownloadKind.AUDIO to "Audio", DownloadKind.VIDEO to "Video").forEachIndexed { index, (type, label) ->
                            SegmentedButton(
                                selected = kind == type,
                                onClick = { kind = type; selectedId = null },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = accent.copy(alpha = .18f), activeContentColor = accent,
                                    inactiveContainerColor = Color.Transparent, inactiveContentColor = Color.White
                                )
                            ) { Text(label) }
                        }
                    }
                }
                item {
                    if (selected != null) {
                        Box {
                            OutlinedButton(
                                onClick = { showQualities = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                            ) {
                                Text("Calidad", Modifier.weight(1f), color = Color.White)
                                Text("${selected.label} · ${selected.extension.uppercase()}")
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = showQualities, onDismissRequest = { showQualities = false }, containerColor = sheetColor) {
                                choices.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text("${option.label} · ${option.extension.uppercase()}", color = if (option == selected) accent else Color.White) },
                                        onClick = {
                                            selectedId = option.id
                                            showQualities = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    when {
                        loading -> VideoFeedStatus("Buscando calidades…", accent, loading = true)
                        failed -> VideoFeedStatus("No se pudieron consultar las calidades.", accent, action = "Reintentar") { attempt++ }
                        choices.isEmpty() -> VideoFeedStatus("No hay calidades disponibles para esta opción.", accent)
                    }
                    if (kind == DownloadKind.AUDIO) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Completar datos y letras en Yang", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                Text("Catálogo de Apple y LRCLIB", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(complete, { complete = it }, colors = SwitchDefaults.colors(checkedTrackColor = accent))
                        }
                    }
                    val folderName = folder?.let {
                        runCatching { DocumentsContract.getTreeDocumentId(it).substringAfter(':') }.getOrNull()
                    }
                    TextButton(
                        onClick = { folderPicker.launch(folder) },
                        enabled = !running,
                        colors = ButtonDefaults.textButtonColors(contentColor = accent)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(folderName ?: "Elegir carpeta de descarga", Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (folderError) Text("Android no concedió acceso a esa carpeta.", color = Color.White)
                    if (Build.VERSION.SDK_INT >= 33 && !hasNotifications) {
                        TextButton(
                            onClick = { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            colors = ButtonDefaults.textButtonColors(contentColor = accent)
                        ) { Text("Activar notificaciones de progreso") }
                    }
                    status?.let {
                        if (it.running || it.title == song.title) {
                            VideoFeedStatus(it.stage, accent, loading = it.running && it.progress == null)
                            it.progress?.let { progress ->
                                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = accent)
                            }
                        }
                    }
                    if (running) {
                        OutlinedButton(downloads::cancel, Modifier.fillMaxWidth()) { Text("Cancelar descarga", color = accent) }
                    } else {
                        Button(
                            onClick = {
                                val destination = folder ?: return@Button
                                val option = selected ?: return@Button
                                downloads.start(song, option.id, destination, complete)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = folder != null && selected != null,
                            colors = buttonColors
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Text("Descargar", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
