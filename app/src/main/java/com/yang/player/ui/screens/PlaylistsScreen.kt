package com.yang.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.player.data.PlaylistStore
import com.yang.player.model.Song
import com.yang.player.ui.components.PlaylistPickerSheet
import com.yang.player.ui.components.SongListItem
import com.yang.player.ui.components.SongOptionsMenuModal
import com.yang.player.ui.theme.LocalArtworkAccent

@Composable
fun PlaylistsScreen(songs: List<Song>, favoriteIds: Set<String>, currentId: String?, playing: Boolean, bottomPadding: Dp, onBack: () -> Unit, onPlay: (Song,List<Song>) -> Unit, onNext: (Song) -> Unit, onQueue: (Song) -> Unit, onFavorite: (Song) -> Unit) {
    val context=LocalContext.current;val store=remember { PlaylistStore.get(context) };val lists by store.playlists.collectAsState()
    var selected by rememberSaveable { mutableStateOf("") };var create by remember { mutableStateOf(false) };var options by remember { mutableStateOf<Song?>(null) }
    val list=lists.firstOrNull { it.id==selected };val accent=LocalArtworkAccent.current
    val tracks=remember(list,songs) { val library=songs.associateBy { it.id };list?.songs?.map { library[it.id]?:it }.orEmpty() }
    fun back(){if(selected.isNotEmpty())selected="" else onBack()}
    BackHandler { back() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(bottom=bottomPadding)) {
        item { Row(Modifier.fillMaxWidth().padding(vertical=14.dp)) { IconButton(onClick={back()}) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Volver",tint=accent) };Text(list?.name?:"Tus playlists",color=Color.White,fontSize=25.sp,modifier=Modifier.weight(1f).padding(top=6.dp));if(list==null)IconButton(onClick={create=true}) { Icon(Icons.Rounded.Add,"Crear playlist",tint=accent) } } }
        if(list==null) {
            if(lists.isEmpty())item { Text("Crea una playlist y añade canciones desde su menú de tres puntos.",color=Color.White.copy(alpha=.6f),modifier=Modifier.padding(vertical=20.dp)) }
            items(lists,key={it.id}) { p -> Column(Modifier.fillMaxWidth().clickable { selected=p.id }.padding(vertical=18.dp)) { Text(p.name,color=Color.White,fontSize=18.sp);Text("${p.songs.size} canciones",color=Color.White.copy(alpha=.5f),fontSize=13.sp) } }
        } else {
            item { Text("${tracks.size} canciones",color=Color.White.copy(alpha=.5f));if(tracks.isEmpty())Text("Añade canciones desde su menú de tres puntos.",color=Color.White.copy(alpha=.6f),modifier=Modifier.padding(top=20.dp));else Button(onClick={onPlay(tracks.first(),tracks)},modifier=Modifier.padding(vertical=16.dp),colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=Color(0xFF101216))) { Text("Reproducir") } }
            items(tracks,key={it.id}) { song -> SongListItem(song,song.id==currentId,onClick={onPlay(song,tracks)},onOptionsClick={options=song},isPlaybackActive=playing) }
        }
    }
    if(create)PlaylistPickerSheet(emptyList()) { create=false }
    options?.let { song -> SongOptionsMenuModal(song,song.id in favoriteIds,onDismiss={options=null},onPlayNext=onNext,onAddToQueue=onQueue,onToggleFavorite=onFavorite) }
}
