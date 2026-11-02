package com.yang.player.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.yang.player.player.SleepMode
import com.yang.player.player.SleepTimerStore
import com.yang.player.ui.theme.LocalArtworkAccent

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackModesRow(shuffle: Boolean, repeatMode: Int, onShuffle: () -> Unit, onRepeat: () -> Unit) {
    val timer by SleepTimerStore.state.collectAsState()
    val context = LocalContext.current
    val accent = LocalArtworkAccent.current
    var showTimer by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text("Reproducción", color = Color.White.copy(alpha = .4f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        TextButton(onClick = onShuffle, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shuffle, null, tint = accent); Spacer(Modifier.width(18.dp))
                Text("Aleatorio", color = Color.White, modifier = Modifier.weight(1f))
                Text(if (shuffle) "Activado" else "Desactivado", color = accent, fontSize = 12.sp)
            }
        }
        TextButton(onClick = onRepeat, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null, tint = accent)
                Spacer(Modifier.width(18.dp)); Text("Repetición", color = Color.White, modifier = Modifier.weight(1f))
                Text(when(repeatMode) { Player.REPEAT_MODE_ONE -> "Esta canción"; Player.REPEAT_MODE_ALL -> "Toda la cola"; else -> "Desactivada" }, color = accent, fontSize = 12.sp)
            }
        }
        TextButton(onClick = { showTimer = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = accent); Spacer(Modifier.width(18.dp))
                Text("Temporizador", color = Color.White, modifier = Modifier.weight(1f))
                Text(when(timer.mode) { SleepMode.OFF -> "Desactivado"; SleepMode.END_OF_TRACK -> "Al finalizar"; else -> "${(timer.remainingMs + 59_999) / 60_000} min" }, color = accent, fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = .07f), modifier = Modifier.padding(vertical = 6.dp))
    }
    if (showTimer) ModalBottomSheet(onDismissRequest = { showTimer = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Temporizador de apagado", style = MaterialTheme.typography.titleLarge)
            Text("La reproducción se pausará automáticamente.", style = MaterialTheme.typography.bodyMedium)
            if (timer.mode != SleepMode.OFF) Text(if (timer.mode == SleepMode.END_OF_TRACK) "Activo: al terminar ${timer.track}" else "Activo: quedan ${(timer.remainingMs + 59_999) / 60_000} min", color = accent)
            listOf(15, 30, 45, 60, 90).chunked(3).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.forEach { minutes -> OutlinedButton(onClick = {
                        if (SleepTimerStore.setMinutes(minutes)) showTimer = false
                        else Toast.makeText(context, "Elige primero una canción", Toast.LENGTH_SHORT).show()
                    }) { Text("$minutes min") } }
                }
            }
            Button(onClick = {
                if (SleepTimerStore.atEndOfTrack()) showTimer = false
                else Toast.makeText(context, "Elige primero una canción", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("Al terminar esta canción") }
            if (timer.mode != SleepMode.OFF) TextButton(onClick = { SleepTimerStore.cancel(); showTimer = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar temporizador") }
            TextButton(onClick = { showTimer = false }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
        }
    }
}
