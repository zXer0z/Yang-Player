package com.yang.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yang.player.online.OnlineCoordinator
import com.yang.player.ui.theme.LocalArtworkAccent

@Composable
fun AccountScreen(online: OnlineCoordinator, bottomContentPadding: Dp) {
    val account by online.account.collectAsState()
    val busy by online.accountBusy.collectAsState()
    val state by online.state.collectAsState()
    val context = LocalContext.current
    val accent = LocalArtworkAccent.current
    var confirmLogout by remember { mutableStateOf(false) }

    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false },
        title = { Text("¿Desconectar YouTube Music?") },
        text = { Text("Se cerrará la sesión de ${account.name} en Yang. Podrás seguir escuchando tu biblioteca local.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; online.logout() }) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Conservar sesión") } })

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)
        .padding(top = 24.dp, bottom = bottomContentPadding + 16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cuenta", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Tu conexión con YouTube Music", color = Color.White.copy(alpha = .5f), fontSize = 14.sp)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = .18f), Color.White.copy(alpha = .035f))))
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    if (account.connected && account.avatar != null) AsyncImage(account.avatar, "Foto de perfil", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(if (account.connected) Icons.Rounded.Person else Icons.Rounded.MusicNote, null, Modifier.size(34.dp), tint = accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (account.connected) account.name else "Tu música, más cerca", color = Color.White, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold)
                    if (account.connected && account.handle.isNotBlank()) Text(account.handle, color = Color.White.copy(alpha = .6f), fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (account.connected) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(15.dp), tint = accent)
                        Text(if (account.connected) "Cuenta conectada" else "Sin conectar", color = if (account.connected) accent else Color.White.copy(alpha = .5f), fontSize = 12.sp)
                    }
                }
            }
            if (!account.connected) {
                Text("Lleva tus playlists y recomendaciones de YouTube Music a la pestaña Online.", color = Color.White.copy(alpha = .7f), fontSize = 15.sp, lineHeight = 22.sp)
                Button(enabled = !busy, onClick = { context.activity()?.let(online::login) },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF111318)),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (busy) "Conectando…" else "Conectar YouTube Music", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text("Tus playlists y recomendaciones personales te esperan en Online.", color = Color.White.copy(alpha = .65f), fontSize = 14.sp, lineHeight = 21.sp)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .04f)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AccountFeature(Icons.AutoMirrored.Rounded.QueueMusic, "Tus playlists", "Encuentra tus listas en la pestaña Online.", accent)
            HorizontalDivider(color = Color.White.copy(alpha = .07f))
            AccountFeature(Icons.Rounded.MusicNote, "Un inicio para ti", "Descubre las recomendaciones de tu cuenta.", accent)
        }
        if (account.connected) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gestionar conexión", color = Color.White.copy(alpha = .45f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                OutlinedButton(onClick = online::refreshAccount, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(19.dp)); Spacer(Modifier.width(9.dp)); Text(if (busy) "Actualizando…" else "Actualizar perfil")
                }
                TextButton(onClick = { confirmLogout = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp), tint = Color(0xFFFF9299))
                    Spacer(Modifier.width(9.dp)); Text("Cerrar sesión", color = Color(0xFFFF9299))
                }
            }
        }
        state.error?.let { error ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFF9299).copy(alpha = .08f)).padding(16.dp)) {
                Text(error, color = Color(0xFFFFB6BA), fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        Text("Tu biblioteca local funciona sin conectar una cuenta.", color = Color.White.copy(alpha = .4f), fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun AccountFeature(icon: ImageVector, title: String, description: String, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(23.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Color.White.copy(alpha = .5f), fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
