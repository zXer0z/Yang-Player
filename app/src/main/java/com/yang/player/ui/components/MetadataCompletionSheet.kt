package com.yang.player.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.yang.player.data.CompletionKind
import com.yang.player.data.MetadataMatch
import com.yang.player.data.OnlineMetadata
import com.yang.player.lyrics.LyricsRepository
import com.yang.player.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val revision by repository.changes.collectAsState()
    val current = remember(song, revision) { repository.apply(song) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var choosing by remember(song.mediaUri) { mutableStateOf(initialKind == CompletionKind.METADATA) }
    var success by remember(song.mediaUri) { mutableStateOf(false) }
    var kind by remember(song.mediaUri) { mutableStateOf(initialKind) }
    var query by remember(song.mediaUri) { mutableStateOf(listOf(current.title, current.artist.takeUnless(OnlineMetadata::missing).orEmpty()).filter(String::isNotBlank).joinToString(" ")) }
    var matches by remember(song.mediaUri) { mutableStateOf<List<MetadataMatch>>(emptyList()) }
    var selected by remember(song.mediaUri) { mutableStateOf<MetadataMatch?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var checked by remember(selected) { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    var availability by remember(song.mediaUri, kind, revision) { mutableStateOf<String?>("Comprobando datos existentes…") }

    LaunchedEffect(song.mediaUri, kind, revision) {
        availability = "Comprobando datos existentes…"
        availability = try {
            when (kind) {
                CompletionKind.METADATA -> if (listOf(current.title, current.artist, current.album).none(OnlineMetadata::missing)) "Título, artista y álbum ya están completos." else null
                CompletionKind.ARTWORK -> if (repository.hasArtwork(current)) "Esta canción ya tiene carátula. Se conservará." else null
                CompletionKind.LYRICS -> if (LyricsRepository(context).load(current) != null) "Esta canción ya tiene letras. Se conservarán." else null
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { "No se pudieron comprobar los datos del archivo. Revisa el acceso a la canción." }
    }

    fun search() {
        if (busy || availability != null || query.trim().length < 2) return
        keyboard?.hide()
        busy = true
        selected = null
        matches = emptyList()
        message = null
        searched = true
        scope.launch {
            try {
                matches = repository.search(query, kind, current)
                if (matches.isEmpty()) message = "Sin coincidencias. Prueba otro título o artista."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: java.io.IOException) { message = "No se pudo conectar. Revisa Internet y vuelve a intentar." }
            catch (error: Exception) { message = error.message ?: "No se pudo completar la búsqueda." }
            finally { busy = false }
        }
    }

    val match = selected
    val mismatch = match != null && (current.durationMs <= 0 || match.durationMs <= 0 || abs(current.durationMs - match.durationMs) > 3000)
    val label = when (kind) { CompletionKind.METADATA -> "Título, artista y álbum"; CompletionKind.ARTWORK -> "Carátula"; CompletionKind.LYRICS -> "Letras" }
    fun chooseAgain() {
        choosing = true; success = false; selected = null; matches = emptyList(); searched = false; message = null
    }
    fun save() {
        val candidate = selected ?: return
        if (busy || availability != null || (mismatch && !checked)) return
        busy = true; saving = true; message = null
        scope.launch {
            try {
                message = repository.save(current, candidate, kind)
                selected = null; matches = emptyList(); searched = false; success = true
                onSaved()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "No se pudo guardar. Inténtalo de nuevo." }
            finally { busy = false; saving = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).imePadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (success) "Guardado" else "Completar canción", style = MaterialTheme.typography.titleLarge)
                    Text(current.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(enabled = !saving, onClick = onDismiss) { Text("Cerrar") }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    success -> {
                        Text(message ?: "Los cambios se guardaron en Yang.", style = MaterialTheme.typography.titleMedium)
                        Text("Ya puedes verlos en tu biblioteca.")
                    }
                    choosing -> {
                        Text("1 · ¿Qué quieres completar?", style = MaterialTheme.typography.titleMedium)
                        CompletionKind.entries.forEach { option ->
                            Card(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                kind = option; choosing = false; selected = null; matches = emptyList(); message = null; searched = false
                            }) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(when (option) { CompletionKind.METADATA -> "Título, artista y álbum"; CompletionKind.ARTWORK -> "Carátula"; CompletionKind.LYRICS -> "Letras" },
                                        fontWeight = FontWeight.SemiBold)
                                    Text(when (option) { CompletionKind.METADATA -> "Rellenar los campos que están vacíos"; CompletionKind.ARTWORK -> "Buscar una portada para esta canción"; CompletionKind.LYRICS -> "Buscar letras sincronizadas o de texto" },
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Text("Los cambios se guardan solo en Yang. Se conserva lo que ya tienes.", style = MaterialTheme.typography.bodySmall)
                    }
                    match == null -> {
                        TextButton(enabled = !busy, onClick = { chooseAgain() }) { Text("‹ Cambiar lo que quiero completar") }
                        Text("2 · Buscar $label", style = MaterialTheme.typography.titleMedium)
                        Text(if (kind == CompletionKind.LYRICS) "Fuente: LRCLIB" else "Fuente: catálogo de Apple", style = MaterialTheme.typography.labelMedium)
                        if (availability != null) {
                            Text(availability!!, color = MaterialTheme.colorScheme.primary)
                        } else {
                            OutlinedTextField(value = query, onValueChange = {
                                query = it; matches = emptyList(); searched = false; message = null
                            }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                label = { Text("Canción y artista") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { search() }))
                            if (searched && matches.isNotEmpty()) Text("Toca la versión que coincide con tu canción.")
                            matches.forEach { result ->
                                Card(Modifier.fillMaxWidth().clickable(enabled = !busy) { selected = result; message = null }) {
                                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (kind == CompletionKind.ARTWORK) AsyncImage(result.artwork, "Carátula del resultado",
                                            Modifier.size(64.dp), contentScale = ContentScale.Crop)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(result.title, fontWeight = FontWeight.SemiBold)
                                            Text(result.artist)
                                            Text("${result.album} · ${durationLabel(result.durationMs)}", style = MaterialTheme.typography.bodySmall)
                                            if (kind == CompletionKind.LYRICS) Text(if (result.synced) "Sincronizada" else "Solo texto", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        TextButton(enabled = !busy, onClick = { selected = null; message = null }) { Text("‹ Elegir otra versión") }
                        Text("3 · Revisar y guardar", style = MaterialTheme.typography.titleMedium)
                        Text(match.title, fontWeight = FontWeight.SemiBold)
                        Text("${match.artist} · ${match.album}")
                        when (kind) {
                            CompletionKind.METADATA -> listOf(Triple("Título", current.title, match.title), Triple("Artista", current.artist, match.artist),
                                Triple("Álbum", current.album, match.album)).forEach { (field, old, value) ->
                                Text(if (OnlineMetadata.missing(old)) "$field: $value" else "$field: $old (se conserva)")
                            }
                            CompletionKind.ARTWORK -> AsyncImage(match.artwork, "Carátula que se guardará",
                                Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
                            CompletionKind.LYRICS -> {
                                Text(if (match.synced) "Letra sincronizada" else "Texto sin sincronización")
                                Card { Text(match.lyrics.take(1200), Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) }
                                if (match.lyrics.length > 1200) Text("Vista previa · se guardará la letra completa", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text("Tu canción: ${durationLabel(current.durationMs)} · esta versión: ${durationLabel(match.durationMs)}", style = MaterialTheme.typography.bodySmall)
                        if (mismatch) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, onCheckedChange = { checked = it }, enabled = !busy)
                            Text("La duración no coincide o falta. Confirmo que es la versión correcta.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(enabled = !saving, onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(match.sourceUrl))) } }) {
                            Text(if (kind == CompletionKind.LYRICS) "Ver fuente en LRCLIB" else "Ver en Apple Music")
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!success) message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            if (!choosing) {
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).navigationBarsPadding()) {
                    when {
                        success -> Button(onClick = { chooseAgain() }, modifier = Modifier.fillMaxWidth()) { Text("Completar otro dato") }
                        match != null -> Button(onClick = { save() }, enabled = !busy && availability == null && (!mismatch || checked), modifier = Modifier.fillMaxWidth()) {
                            Text(if (saving) "Guardando…" else when(kind) { CompletionKind.ARTWORK -> "Guardar carátula"; CompletionKind.LYRICS -> "Guardar letra"; CompletionKind.METADATA -> "Guardar datos faltantes" })
                        }
                        availability == null -> Button(onClick = { search() }, enabled = !busy && query.trim().length >= 2, modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "Buscando…" else if (searched) "Volver a buscar" else "Buscar coincidencias")
                        }
                        else -> TextButton(onClick = { chooseAgain() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Completar otra cosa") }
                    }
                }
            }
        }
    }
}

private fun durationLabel(ms: Long): String = if (ms <= 0) "sin duración" else "${ms / 60000}:${((ms / 1000) % 60).toString().padStart(2, '0')}"
