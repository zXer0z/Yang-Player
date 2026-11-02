package com.yang.player.data

import android.content.Context
import com.yang.player.model.Song
import com.yang.player.model.AudioFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LocalPlaylist(val id: String, val name: String, val songs: List<Song>)

/** Local lists retain song snapshots, including online URIs; never alter audio files. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaylistStore private constructor(context: Context) {
    private val completion = OnlineMetadata.get(context)
    private val prefs=context.getSharedPreferences("local_playlists",Context.MODE_PRIVATE)
    private val state=MutableStateFlow(read())
    val playlists=state.asStateFlow()
    private fun read(): List<LocalPlaylist> = runCatching {
        val a=JSONArray(prefs.getString("lists","[]"));(0 until a.length()).mapNotNull { i -> runCatching {
            val j=a.getJSONObject(i);val tracks=j.getJSONArray("songs")
            LocalPlaylist(j.getString("id"),j.getString("name"),(0 until tracks.length()).mapNotNull { k -> runCatching {
                val t=tracks.getJSONObject(k);Song(id=t.getString("id"),title=t.getString("title"),artist=t.optString("artist"),album=t.optString("album"),durationMs=t.optLong("duration"),mediaUri=t.getString("uri"),artworkUri=t.optString("art").takeIf { it.isNotBlank() },dataPath=t.optString("path"),format=runCatching { AudioFormat.valueOf(t.optString("format")) }.getOrDefault(AudioFormat.UNKNOWN),sampleRateHz=t.optInt("rate",44100),bitDepth=t.optInt("bits",16),isHiRes=t.optBoolean("hires"))
            }.getOrNull() }.distinctBy { it.id })
        }.getOrNull() }
    }.getOrDefault(emptyList()).map { it.copy(songs = it.songs.map(completion::apply)) }
    @Synchronized fun refreshMetadata() { state.value = read() }
    private fun save(lists: List<LocalPlaylist>) {
        val a=JSONArray();lists.forEach { list -> val tracks=JSONArray();list.songs.forEach { t -> tracks.put(JSONObject().put("id",t.id).put("title",t.title).put("artist",t.artist).put("album",t.album).put("duration",t.durationMs).put("uri",t.mediaUri).put("art",t.artworkUri.orEmpty()).put("path",t.dataPath).put("format",t.format.name).put("rate",t.sampleRateHz).put("bits",t.bitDepth).put("hires",t.isHiRes)) };a.put(JSONObject().put("id",list.id).put("name",list.name).put("songs",tracks)) }
        prefs.edit().putString("lists",a.toString()).apply();state.value=lists
    }
    @Synchronized fun removeSongFile(song: Song) {
        save(state.value.map { list -> list.copy(songs = list.songs.filterNot { sameSongFile(it, song) }) })
    }
    @Synchronized fun create(name: String, songs: List<Song> = emptyList()): LocalPlaylist {
        val title=name.trim().take(80);require(title.isNotEmpty())
        val list=LocalPlaylist(UUID.randomUUID().toString(),title,songs.distinctBy { it.id });save(state.value+list);return list
    }
    @Synchronized fun add(id: String,songs: List<Song>) { save(state.value.map { if(it.id==id) it.copy(songs=(it.songs+songs).distinctBy { song -> song.id }) else it }) }
    companion object {
        @Volatile private var instance: PlaylistStore?=null
        fun get(context: Context)=instance?:synchronized(this){instance?:PlaylistStore(context.applicationContext).also { instance=it }}
    }
}
