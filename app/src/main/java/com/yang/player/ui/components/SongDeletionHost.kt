package com.yang.player.ui.components

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.yang.player.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

val LocalSongDeletion = staticCompositionLocalOf<(Song) -> Unit> {
    { error("El borrado de canciones requiere SongDeletionHost") }
}

/** Lives above song menus, so closing a menu cannot cancel a deletion request. */
@Composable
fun SongDeletionHost(onDeleted: (Song) -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Song?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Restore the selected identity across the system permission activity / process recreation.
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingId by rememberSaveable { mutableStateOf("") }
    var pendingTitle by rememberSaveable { mutableStateOf("") }
    var pendingPath by rememberSaveable { mutableStateOf("") }
    var retryAfterPermission by rememberSaveable { mutableStateOf(false) }

    fun pendingSong() = pendingUri?.let { Song(pendingId, pendingTitle, "", "", 0, it, dataPath = pendingPath) }
    fun notify(message: String) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val song = pendingSong()
        pendingUri = null
        busy = false
        if (result.resultCode == Activity.RESULT_OK && song != null) scope.launch {
            busy = true
            try {
                if (retryAfterPermission) withContext(Dispatchers.IO) { deleteDirect(context, song) }
                currentOnDeleted(song)
                notify("Canción eliminada permanentemente del dispositivo.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notify("No se pudo eliminar el archivo. Revisa sus permisos y vuelve a intentarlo.") }
            finally { busy = false }
        } else notify("Eliminación cancelada. Se conservó la canción.")
    }

    fun requestPermission(song: Song, sender: android.content.IntentSender, retry: Boolean) {
        pendingUri = song.mediaUri; pendingId = song.id; pendingTitle = song.title; pendingPath = song.dataPath
        retryAfterPermission = retry
        try { permission.launch(IntentSenderRequest.Builder(sender).build()) }
        catch (error: Exception) { pendingUri = null; busy = false; throw error }
    }

    CompositionLocalProvider(LocalSongDeletion provides { song ->
        if (!busy && pendingUri == null && !song.isOnline) selected = song
    }) { content() }

    selected?.let { song ->
        AlertDialog(onDismissRequest = { if (!busy) selected = null },
            title = { Text("¿Eliminar esta canción del dispositivo?") },
            text = { Text("«${song.title}»\n${song.artist}\n\nSe borrará el archivo de audio permanentemente, sin enviarlo a la papelera. También se quitará de la cola y de tus playlists en Yang. Esta acción no se puede deshacer.") },
            dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text("Conservar canción") } },
            confirmButton = { TextButton(enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), onClick = {
                busy = true
                scope.launch {
                    try {
                        val originalUri = Uri.parse(song.mediaUri)
                        val uri = if (Build.VERSION.SDK_INT >= 30 && DocumentsContract.isDocumentUri(context, originalUri)) {
                            withContext(Dispatchers.IO) { runCatching { MediaStore.getMediaUri(context, originalUri) }.getOrNull() } ?: originalUri
                        } else originalUri
                        if (Build.VERSION.SDK_INT >= 30 && uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
                            val request = withContext(Dispatchers.IO) { MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)) }
                            requestPermission(song, request.intentSender, false)
                            selected = null
                        } else {
                            try {
                                withContext(Dispatchers.IO) { deleteDirect(context, song) }
                                selected = null
                                currentOnDeleted(song)
                                notify("Canción eliminada permanentemente del dispositivo.")
                                busy = false
                            } catch (error: SecurityException) {
                                if (Build.VERSION.SDK_INT >= 29 && error is RecoverableSecurityException) {
                                    requestPermission(song, error.userAction.actionIntent.intentSender, true)
                                    selected = null
                                } else throw error
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { busy = false; selected = null; notify("No se pudo eliminar el archivo. Revisa sus permisos y vuelve a intentarlo.") }
                }
            }) { Text(if (busy) "Eliminando…" else "Eliminar permanentemente") } })
    }
}

private fun deleteDirect(context: Context, song: Song) {
    require(!song.isOnline)
    val uri = Uri.parse(song.mediaUri)
    val deleted = when {
        DocumentsContract.isDocumentUri(context, uri) -> {
            context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                require(cursor.moveToFirst())
                require(cursor.getString(1) != DocumentsContract.Document.MIME_TYPE_DIR) { "Solo se pueden borrar archivos de audio." }
                require(cursor.getLong(0) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE.toLong() != 0L)
            } ?: error("No se pudo consultar el archivo")
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
        uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY -> context.contentResolver.delete(uri, null, null) > 0
        uri.scheme == "file" -> {
            val file = File(requireNotNull(uri.path))
            require(file.isFile)
            file.delete()
        }
        else -> error("Este origen no admite eliminación permanente")
    }
    check(deleted) { "El proveedor no confirmó la eliminación." }
}
