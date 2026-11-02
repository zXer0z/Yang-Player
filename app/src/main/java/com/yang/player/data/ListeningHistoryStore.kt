package com.yang.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Private on-device history. No accounts, network calls or audio analysis. */
class ListeningHistoryStore private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("listening_history_v1", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val profile = state.asStateFlow()

    private fun load(): ListeningProfile {
        val tracks = mutableMapOf<String, TrackListeningStats>()
        runCatching {
            val json = JSONObject(preferences.getString("tracks", "{}") ?: "{}")
            json.keys().forEach { id ->
                val item = json.optJSONObject(id) ?: return@forEach
                tracks[id] = TrackListeningStats(
                    item.optInt("plays").coerceAtLeast(0),
                    item.optLong("listened").coerceAtLeast(0),
                    item.optLong("last").coerceAtLeast(0),
                    item.optLong("position").coerceAtLeast(0)
                )
            }
        }
        return ListeningProfile(tracks, preferences.getStringSet("favorites", emptySet()).orEmpty().toSet())
    }

    @Synchronized
    fun record(songId: String, listenedMs: Long, positionMs: Long, qualifiedPlay: Boolean) {
        val profile = state.value
        val previous = profile.tracks[songId] ?: TrackListeningStats()
        val updated = previous.copy(
            playCount = previous.playCount + if (qualifiedPlay) 1 else 0,
            listenedMs = previous.listenedMs + listenedMs.coerceAtLeast(0),
            lastPlayedAt = if (listenedMs > 0) System.currentTimeMillis() else previous.lastPlayedAt,
            resumePositionMs = positionMs.coerceAtLeast(0)
        )
        if (previous == updated) return
        val tracks = (profile.tracks + (songId to updated)).entries
            .sortedByDescending { it.value.lastPlayedAt }.take(1500).associate { it.toPair() }
        val json = JSONObject()
        tracks.forEach { (id, stats) ->
            json.put(id, JSONObject().put("plays", stats.playCount).put("listened", stats.listenedMs)
                .put("last", stats.lastPlayedAt).put("position", stats.resumePositionMs))
        }
        preferences.edit().putString("tracks", json.toString()).apply()
        state.value = profile.copy(tracks = tracks)
    }

    @Synchronized
    fun toggleFavorite(songId: String) {
        val profile = state.value
        val ids = profile.favoriteIds.toMutableSet()
        if (!ids.remove(songId)) ids.add(songId)
        preferences.edit().putStringSet("favorites", ids).apply()
        state.value = profile.copy(favoriteIds = ids)
    }

    companion object {
        @Volatile private var instance: ListeningHistoryStore? = null
        fun get(context: Context): ListeningHistoryStore = instance ?: synchronized(this) {
            instance ?: ListeningHistoryStore(context.applicationContext).also { instance = it }
        }
    }
}
