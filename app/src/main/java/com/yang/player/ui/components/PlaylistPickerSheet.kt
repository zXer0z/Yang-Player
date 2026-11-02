package com.yang.player.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.player.data.PlaylistStore
import com.yang.player.model.Song
import com.yang.player.ui.theme.BackgroundDark
import com.yang.player.ui.theme.LocalArtworkAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(songs: List<Song>, onDismiss: () -> Unit) {
    val context=LocalContext.current;val store=remember { PlaylistStore.get(context) };val lists by store.playlists.collectAsState()
    val accent=LocalArtworkAccent.current;var name by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=lerp(BackgroundDark,accent,.08f)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=22.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(if(songs.isEmpty()) "Crear playlist" else "Añadir a playlist",color=Color.White,fontSize=22.sp)
            OutlinedTextField(value=name,onValueChange={name=it.take(80)},singleLine=true,label={Text("Nombre de la nueva playlist")},modifier=Modifier.fillMaxWidth(),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=accent,focusedLabelColor=accent,cursorColor=accent,focusedTextColor=Color.White,unfocusedTextColor=Color.White))
            Button(onClick={store.create(name,songs);Toast.makeText(context,"Playlist creada",Toast.LENGTH_SHORT).show();onDismiss()},enabled=name.trim().isNotEmpty(),modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=BackgroundDark)) { Text(if(songs.isEmpty()) "Crear" else "Crear y añadir canción") }
            if(songs.isNotEmpty()) {
                if(lists.isNotEmpty())Text("Tus playlists",color=Color.White.copy(alpha=.6f))
                lists.forEach { list -> Column(Modifier.fillMaxWidth().clickable { store.add(list.id,songs);Toast.makeText(context,"Guardado en "+list.name,Toast.LENGTH_SHORT).show();onDismiss() }.padding(vertical=12.dp)) { Text(list.name,color=Color.White,fontSize=17.sp);Text("${list.songs.size} canciones",color=Color.White.copy(alpha=.5f),fontSize=12.sp) } }
            }
        }
    }
}
