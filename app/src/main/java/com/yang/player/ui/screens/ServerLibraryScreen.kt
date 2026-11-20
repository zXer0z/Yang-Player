package com.yang.player.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.yang.player.data.ServerLibrary
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.TextMuted
import com.yang.player.ui.theme.TextWhite
import com.yang.player.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
@Composable
internal fun ServerLibraryScreen(viewModel: PlayerViewModel, bottomPadding: Dp, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext
    val accent = LocalArtworkAccent.current
    val scope = rememberCoroutineScope()
    val cloudMode by viewModel.cloudMode.collectAsState()
    val cloudLoading by viewModel.cloudLoading.collectAsState()
    var profile by remember { mutableStateOf(ServerLibrary.saved(app)) }
    var address by remember { mutableStateOf(profile?.base.orEmpty()) }
    var user by remember { mutableStateOf(profile?.user.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val canReusePassword = profile?.base == address.trim().trimEnd('/') && profile?.user == user.trim()
    val fieldShape = RoundedCornerShape(14.dp)
    val actionShape = RoundedCornerShape(16.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextWhite,
        unfocusedTextColor = TextWhite,
        cursorColor = accent,
        focusedLabelColor = accent,
        unfocusedLabelColor = TextMuted,
        focusedBorderColor = accent.copy(alpha = .8f),
        unfocusedBorderColor = Color.White.copy(alpha = .13f),
        focusedContainerColor = Color.White.copy(alpha = .04f),
        unfocusedContainerColor = Color.White.copy(alpha = .04f)
    )

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPadding + 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Conexión a servidor", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Usa tu propio servidor con Navidrome.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = .045f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                address, { address = it }, Modifier.fillMaxWidth(),
                label = { Text("URL del servidor") },
                singleLine = true, enabled = !busy, shape = fieldShape, colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                user, { user = it }, Modifier.fillMaxWidth(),
                label = { Text("Usuario") },
                singleLine = true, enabled = !busy, shape = fieldShape, colors = fieldColors
            )
            OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth(),
                label = { Text(if (canReusePassword) "Contraseña guardada" else "Contraseña") },
                singleLine = true, enabled = !busy, shape = fieldShape, colors = fieldColors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = actionShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF111318),
                    disabledContainerColor = Color.White.copy(alpha = .08f),
                    disabledContentColor = TextMuted
                ),
                enabled = !busy && address.isNotBlank() && user.isNotBlank() && (password.isNotBlank() || canReusePassword),
                onClick = {
                    scope.launch {
                        busy = true
                        status = null
                        try {
                            val login = ServerLibrary.login(address, user,
                                password.ifBlank { if (canReusePassword) profile?.password.orEmpty() else "" })
                            withContext(Dispatchers.IO) { ServerLibrary.connect(app, login) }
                            val sameCatalog = profile?.id == login.id
                            profile = login
                            password = ""
                            status = "Conexión guardada."
                            viewModel.reloadCloudLibrary(keepCatalog = sameCatalog)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            status = failure.message ?: "No se pudo conectar a Navidrome."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text(if (busy) "Conectando…" else "Guardar conexión", fontWeight = FontWeight.SemiBold)
            }
            if (profile != null && cloudMode) {
                OutlinedButton(
                    enabled = !busy && !cloudLoading,
                    onClick = viewModel::reloadCloudLibrary,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = actionShape,
                    border = BorderStroke(1.dp, accent.copy(alpha = .25f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent, disabledContentColor = TextMuted)
                ) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(if (cloudLoading) "Actualizando…" else "Actualizar catálogo")
                }
            }
            status?.let { Text(it, color = TextMuted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
