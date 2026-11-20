package com.yang.player.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.yang.player.BuildConfig
import com.yang.player.data.AppUpdates
import com.yang.player.data.YangRelease
import com.yang.player.ui.theme.BackgroundDark
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.TextMuted
import com.yang.player.ui.theme.TextWhite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun AppUpdatePrompt(manualRequest: Int) {
    val context = LocalContext.current
    val updates = remember(context) { AppUpdates(context.applicationContext) }
    val accent = LocalArtworkAccent.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<YangRelease?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val install = { file: File ->
        try {
            val uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".updates", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            release = null
        } catch (_: Exception) {
            message = "No se pudo abrir el instalador de Android."
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) {
            apk?.let(install)
        } else {
            message = "Android necesita que permitas instalar actualizaciones desde Yang."
        }
    }
    fun prepareInstallation(file: File) {
        if (context.packageManager.canRequestPackageInstalls()) {
            install(file)
        } else {
            try {
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            } catch (_: Exception) {
                message = "No se pudo abrir el permiso de instalación de Android."
            }
        }
    }

    LaunchedEffect(updates, manualRequest) {
        if (downloading) return@LaunchedEffect
        message = if (manualRequest > 0) "Buscando actualizaciones…" else null
        try {
            val found = updates.check(force = manualRequest > 0)
            release = found
            apk = null
            message = if (found == null && manualRequest > 0) "Ya tienes la última versión de Yang." else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message = if (manualRequest > 0) "No se pudo comprobar la actualización en GitHub." else null
        }
    }
    val offer = release
    if (offer != null || message != null) {
        val dismiss = {
            if (!downloading) {
                release = null
                message = null
            }
        }
        AlertDialog(
            onDismissRequest = dismiss,
            containerColor = lerp(BackgroundDark, accent, .13f),
            title = { Text(if (offer == null) "Actualizaciones" else "Nueva versión · ${offer.version}", color = TextWhite) },
            text = {
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (offer != null && offer.notes.isNotBlank()) Text(offer.notes, color = TextMuted)
                    val detail = if (downloading) "Descargando actualización… $progress%" else message
                    if (detail != null) Text(detail, color = TextWhite)
                    if (downloading) LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth(), color = accent)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !downloading,
                    onClick = {
                        if (offer == null) dismiss()
                        else if (apk?.isFile == true) apk?.let(::prepareInstallation)
                        else scope.launch {
                            message = null
                            downloading = true
                            progress = 0
                            try {
                                val file = updates.download(offer) { progress = it }
                                apk = file
                                prepareInstallation(file)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                message = error.message ?: "No se pudo descargar la actualización."
                            } finally {
                                downloading = false
                            }
                        }
                    }
                ) { Text(if (offer == null) "Cerrar" else if (apk != null) "Instalar" else "Actualizar", color = accent) }
            },
            dismissButton = if (offer == null) null else {
                { TextButton(onClick = dismiss, enabled = !downloading) { Text("Más tarde", color = TextMuted) } }
            }
        )
    }
}
