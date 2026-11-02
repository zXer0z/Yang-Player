package com.yang.player.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.yang.player.model.AudioFormat
import com.yang.player.model.Song
import org.json.JSONArray
import org.json.JSONObject

internal data class SavedPlayback(val songs: List<Song>, val index: Int, val positionMs: Long, val repeat: Int, val shuffle: Boolean)

/** The service owns the checkpoint. Restoring it never starts playback. */
@UnstableApi
internal class PlaybackSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("last_playback_v1", Context.MODE_PRIVATE)

    fun load(): SavedPlayback? = runCatching {
        val array = JSONArray(prefs.getString("queue", "[]"))
        val songs = (0 until array.length()).map { i ->
            val j = array.getJSONObject(i)
            Song(j.getString("id"), j.getString("title"), j.optString("artist"), j.optString("album"), j.optLong("duration"),
                j.getString("uri"), j.optString("art").takeIf(String::isNotBlank), j.optString("path"),
                runCatching { AudioFormat.valueOf(j.optString("format")) }.getOrDefault(AudioFormat.UNKNOWN),
                j.optInt("rate", 44100), j.optInt("bits", 16), j.optBoolean("hires"), albumArtist = j.optString("albumArtist"))
        }
        if (songs.isEmpty()) null else SavedPlayback(songs, prefs.getInt("index", 0).coerceIn(songs.indices),
            prefs.getLong("position", 0).coerceAtLeast(0), prefs.getInt("repeat", Player.REPEAT_MODE_OFF), prefs.getBoolean("shuffle", false))
    }.getOrNull()

    fun removeSongFile(deleted: Song) {
        val saved = load() ?: return
        val rows = JSONArray(prefs.getString("queue", "[]"))
        val kept = saved.songs.indices.filterNot { com.yang.player.data.sameSongFile(saved.songs[it], deleted) }
        if (kept.size == saved.songs.size) return
        if (kept.isEmpty()) { prefs.edit().clear().apply(); return }
        val queue = JSONArray()
        kept.forEach { queue.put(rows.getJSONObject(it)) }
        val currentKept = saved.index in kept
        val index = if (currentKept) kept.indexOf(saved.index) else kept.count { it < saved.index }.coerceAtMost(kept.lastIndex)
        prefs.edit().putString("queue", queue.toString()).putInt("index", index)
            .putLong("position", if (currentKept) saved.positionMs else 0L).apply()
    }

    fun saveQueue(player: Player) {
        // An empty queue must clear the checkpoint, including after asynchronous duplicate removals.
        // Playlist replacement writes its new queue on the following timeline event.
        if (player.mediaItemCount == 0) { prefs.edit().clear().apply(); return }
        val array = JSONArray()
        for (i in 0 until player.mediaItemCount) {
            val song = song(player.getMediaItemAt(i), duration = if(i == player.currentMediaItemIndex) player.duration else 0) ?: return
            array.put(JSONObject().put("id", song.id).put("title", song.title).put("artist", song.artist).put("album", song.album)
                .put("duration", song.durationMs).put("uri", song.mediaUri).put("art", song.artworkUri.orEmpty()).put("path", song.dataPath)
                .put("format", song.format.name).put("rate", song.sampleRateHz).put("bits", song.bitDepth).put("hires", song.isHiRes).put("albumArtist", song.albumArtist))
        }
        val edit = prefs.edit()
        val json = array.toString()
        if (prefs.getString("queue", null) != json) edit.putString("queue", json)
        edit.putInt("index", player.currentMediaItemIndex.coerceAtLeast(0)).putLong("position", player.currentPosition.coerceAtLeast(0))
            .putInt("repeat", player.repeatMode).putBoolean("shuffle", player.shuffleModeEnabled).apply()
    }

    fun savePosition(player: Player) {
        if (player.mediaItemCount == 0) return
        prefs.edit().putInt("index", player.currentMediaItemIndex.coerceAtLeast(0)).putLong("position", player.currentPosition.coerceAtLeast(0))
            .putInt("repeat", player.repeatMode).putBoolean("shuffle", player.shuffleModeEnabled).apply()
    }

    companion object {
        fun item(song: Song): MediaItem {
            val uri = Uri.parse(song.mediaUri)
            val extras = Bundle().apply {
                putString("sourceId", song.id); putString("sourceTitle", song.title); putString("sourceCodec", song.format.displayName)
                putInt("sourceRate", song.sampleRateHz); putInt("sourceBits", song.bitDepth)
                putString("yangUri", song.mediaUri); putString("yangPath", song.dataPath); putString("yangFormat", song.format.name)
                putLong("yangDuration", song.durationMs); putBoolean("yangHiRes", song.isHiRes)
            }
            return MediaItem.Builder().setMediaId(song.id).setUri(uri)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).setAlbumArtist(song.albumArtist)
                    .setArtworkUri(song.artworkUri?.let(Uri::parse)).setExtras(extras).build()).build()
        }

        fun song(item: MediaItem, fallback: Song? = null, duration: Long = 0): Song? {
            val meta = item.mediaMetadata
            val extras = meta.extras
            val uri = extras?.getString("yangUri") ?: item.requestMetadata.mediaUri?.toString() ?: item.localConfiguration?.uri?.toString() ?: fallback?.mediaUri ?: return null
            if (uri.isBlank()) return null
            val format = runCatching { AudioFormat.valueOf(extras?.getString("yangFormat") ?: extras?.getString("sourceCodec").orEmpty()) }
                .getOrDefault(fallback?.format ?: AudioFormat.fromPathOrMime(uri, null))
            return Song(id = item.mediaId.ifBlank { fallback?.id ?: uri }, title = meta.title?.toString() ?: fallback?.title ?: "Sin título",
                artist = meta.artist?.toString() ?: fallback?.artist.orEmpty(), album = meta.albumTitle?.toString() ?: fallback?.album.orEmpty(),
                durationMs = duration.takeIf { it > 0 } ?: extras?.getLong("yangDuration")?.takeIf { it > 0 } ?: fallback?.durationMs ?: 0,
                mediaUri = uri, artworkUri = meta.artworkUri?.toString() ?: fallback?.artworkUri, dataPath = extras?.getString("yangPath") ?: fallback?.dataPath.orEmpty(),
                format = format, sampleRateHz = extras?.getInt("sourceRate", fallback?.sampleRateHz ?: 44100) ?: fallback?.sampleRateHz ?: 44100,
                bitDepth = extras?.getInt("sourceBits", fallback?.bitDepth ?: 16) ?: fallback?.bitDepth ?: 16,
                isHiRes = extras?.getBoolean("yangHiRes", fallback?.isHiRes ?: false) ?: fallback?.isHiRes ?: false,
                albumArtist = meta.albumArtist?.toString() ?: fallback?.albumArtist.orEmpty())
        }
    }
}
