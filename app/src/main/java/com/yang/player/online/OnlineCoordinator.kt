package com.yang.player.online

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.yang.player.model.AudioFormat
import com.yang.player.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

data class AccountInfo(val name: String = "Usuario", val handle: String = "", val avatar: String? = null, val connected: Boolean = false)
data class OnlinePlaylist(val id: String, val title: String, val subtitle: String, val image: String?)
data class OnlineShelf(val title: String, val songs: List<Song>, val playlists: List<OnlinePlaylist>)
data class OnlineState(
    val query: String = "", val suggestions: List<String> = emptyList(), val results: List<Song> = emptyList(),
    val shelves: List<OnlineShelf> = emptyList(), val playlists: List<OnlinePlaylist> = emptyList(),
    val searching: Boolean = false, val loadingHome: Boolean = false, val error: String? = null,
    val correctedQuery: String = "", val resultTitle: String = "", val showingResults: Boolean = false,
    val cacheAvailable: Boolean = false
)

/** Adapts the account implementation from MP3_player to the Compose/Media3 app. */
class OnlineCoordinator(context: Context, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private val profilePreferences = context.getSharedPreferences("youtube_profile", Context.MODE_PRIVATE)
    private val historyPreferences = context.getSharedPreferences("online_searches", Context.MODE_PRIVATE)
    private val memo = CatalogMemo(context)
    private val _knownTracks = MutableStateFlow(runCatching { JSONArray(historyPreferences.getString("saved_tracks", "[]")).songs() }.getOrDefault(emptyList()))
    val knownTracks = _knownTracks.asStateFlow()
    private val _account = MutableStateFlow(cachedAccount())
    val account = _account.asStateFlow()
    private val _accountBusy = MutableStateFlow(false)
    val accountBusy = _accountBusy.asStateFlow()
    private val _state = MutableStateFlow(OnlineState())
    val state = _state.asStateFlow()
    private var homeJob: Job? = null
    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var accountJob: Job? = null
    private var searchGeneration = 0L

    fun rememberSong(song: Song) {
        if (!song.isOnline) return
        val songs = (listOf(song) + _knownTracks.value).distinctBy { it.id }.take(300)
        _knownTracks.value = songs
        historyPreferences.edit().putString("saved_tracks", songsJson(songs).toString()).apply()
    }

    init { if (YouTubeAuth.hasSavedSession(context)) refreshAccount() }

    private fun cachedAccount(): AccountInfo = runCatching {
        if (!YouTubeAuth.isLoggedIn(context)) return AccountInfo()
        if (profilePreferences.getString("scope", "") != OnlineHomeCache.scope(context)) return AccountInfo(connected = true)
        val json = JSONObject(profilePreferences.getString("profile", "{}") ?: "{}")
        AccountInfo(json.optString("name").ifBlank { "Usuario" }, json.optString("handle"), json.optString("photo").takeIf { it.startsWith("https://") }, true)
    }.getOrDefault(AccountInfo())

    fun login(activity: Activity) { YouTubeAuth.startLogin(activity) { refreshAccount() } }

    fun logout() {
        accountJob?.cancel(); homeJob?.cancel(); searchJob?.cancel(); suggestionJob?.cancel()
        _accountBusy.value = false
        _account.value = AccountInfo()
        _state.value = OnlineState()
        YouTubeAuth.logout(context) { loadHome(force = true) }
    }

    fun refreshAccount() {
        accountJob?.cancel()
        accountJob = scope.launch {
            _accountBusy.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    if (!YouTubeAuth.verifySaved(context)) throw IllegalStateException("Vuelve a conectar tu cuenta de YouTube")
                    val accountScope = OnlineHomeCache.scope(context)
                    val json = YouTubeMusicClient.accountProfile(context)
                    if (accountScope != OnlineHomeCache.scope(context)) throw CancellationException()
                    profilePreferences.edit().putString("scope", accountScope).putString("profile", json.toString()).apply()
                    YouTubeAuth.setAccountName(context, json.getString("name"))
                    AccountInfo(json.getString("name"), json.optString("handle"), json.optString("photo").takeIf { it.startsWith("https://") }, true)
                }
                _account.value = result
                _state.update { it.copy(error = null) }
                loadHome(force = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (!YouTubeAuth.isLoggedIn(context)) { _account.value = AccountInfo(); _state.value = OnlineState(error = error.message) }
                else _state.update { it.copy(error = error.message ?: "No se pudo actualizar el perfil") }
            } finally { _accountBusy.value = false }
        }
    }

    fun loadHome(force: Boolean = false) {
        if (homeJob?.isActive == true && !force) return
        homeJob?.cancel()
        val accountScope = OnlineHomeCache.scope(context)
        homeJob = scope.launch {
            _state.update { it.copy(loadingHome = true) }
            try {
                val cached = withContext(Dispatchers.IO) { OnlineHomeCache.read(context, accountScope) }
                if (accountScope != OnlineHomeCache.scope(context)) return@launch
                if (cached != null) {
                    publishHome(cached)
                    if (!force && System.currentTimeMillis() - cached.created < 15 * 60_000) return@launch
                }
                val fresh = withContext(Dispatchers.IO) {
                    val home = YouTubeMusicClient.fetchHome(context)
                    val playlists = if (YouTubeAuth.isLoggedIn(context)) YouTubeMusicClient.fetchUserPlaylists(context) else emptyList()
                    OnlineHomeCache.Snapshot(accountScope, System.currentTimeMillis(), playlists, home).also {
                        if (accountScope == OnlineHomeCache.scope(context)) { OnlineHomeCache.remember(it); OnlineHomeCache.write(context, it) }
                    }
                }
                if (accountScope == OnlineHomeCache.scope(context)) publishHome(fresh)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (_account.value.connected && !YouTubeAuth.isLoggedIn(context)) {
                    _account.value = AccountInfo()
                    _state.value = OnlineState(error = "Vuelve a conectar tu cuenta de YouTube")
                } else _state.update { it.copy(error = error.message ?: "No se pudo cargar YouTube") }
            }
            finally { _state.update { it.copy(loadingHome = false) } }
        }
    }

    private fun publishHome(snapshot: OnlineHomeCache.Snapshot) {
        _state.update { it.copy(
            shelves = snapshot.sections.map { shelf -> OnlineShelf(shelf.title, shelf.songs.map(::song), shelf.playlists.map(::playlist)) },
            playlists = snapshot.playlists.map(::playlist), cacheAvailable = true, error = null
        ) }
    }

    fun changeQuery(value: String) {
        _state.update { it.copy(query = value.take(200), error = null) }
        suggestionJob?.cancel()
        val query = value.trim().take(200)
        val accountScope = OnlineHomeCache.scope(context)
        _state.update { it.copy(suggestions = recentQueries(accountScope).filter { recent -> recent.startsWith(query, true) }.take(6)) }
        if (query.length < 2 || query.contains("://")) return
        suggestionJob = scope.launch {
            delay(280)
            try {
                val values = withContext(Dispatchers.IO) {
                    memo.read("suggest:$query", 24 * 60 * 60_000L)?.optJSONArray("values")?.strings()
                        ?: OnlineAudio.suggestions(query).also { memo.write("suggest:$query", JSONObject().put("values", JSONArray(it))) }
                }
                if (_state.value.query.trim() == query && accountScope == OnlineHomeCache.scope(context)) {
                    _state.update { it.copy(suggestions = (recentQueries(accountScope).filter { recent -> recent.startsWith(query, true) } + values).distinct().take(8)) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Recent searches remain available offline. */ }
        }
    }

    fun search(value: String = _state.value.query) {
        val query = value.trim().take(200)
        if (query.isBlank()) return
        suggestionJob?.cancel(); searchJob?.cancel()
        val generation = ++searchGeneration
        val accountScope = OnlineHomeCache.scope(context)
        _state.update { it.copy(query = query, suggestions = emptyList(), searching = true, showingResults = true, resultTitle = query, correctedQuery = "", error = null) }
        rememberQuery(accountScope, query)
        searchJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val key = "search:$query"
                    memo.read(key, 30 * 60_000)?.let { return@withContext it }
                    val page = OnlineAudio.searchPage(query)
                    JSONObject().put("corrected", page.correctedQuery).put("songs", songsJson(page.songs.map(::song))).also { memo.write(key, it) }
                }
                if (accountScope != OnlineHomeCache.scope(context)) return@launch
                _state.update { it.copy(results = result.getJSONArray("songs").songs(), correctedQuery = result.optString("corrected")) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(error = error.message ?: "No se pudo buscar en YouTube") } }
            finally { if (generation == searchGeneration) _state.update { it.copy(searching = false) } }
        }
    }

    fun openPlaylist(value: OnlinePlaylist) {
        searchJob?.cancel(); suggestionJob?.cancel()
        val generation = ++searchGeneration
        val accountScope = OnlineHomeCache.scope(context)
        _state.update { it.copy(showingResults = true, results = emptyList(), searching = true, suggestions = emptyList(), resultTitle = value.title, correctedQuery = "", error = null) }
        searchJob = scope.launch {
            try {
                val songs = withContext(Dispatchers.IO) {
                    val key = "playlist:$accountScope:${value.id}"
                    memo.read(key, 15 * 60_000)?.optJSONArray("songs")?.songs()
                        ?: YouTubeMusicClient.fetchPlaylistTracks(context, value.id).map(::song).also {
                            memo.write(key, JSONObject().put("songs", songsJson(it)))
                        }
                }
                if (accountScope == OnlineHomeCache.scope(context)) _state.update { it.copy(results = songs) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(error = error.message ?: "No se pudo cargar la lista") } }
            finally { if (generation == searchGeneration) _state.update { it.copy(searching = false) } }
        }
    }

    fun showHome() { searchJob?.cancel(); suggestionJob?.cancel(); _state.update { it.copy(query = "", showingResults = false, suggestions = emptyList(), searching = false, error = null) }; loadHome() }
    fun dismissSuggestions() { suggestionJob?.cancel(); _state.update { it.copy(suggestions = emptyList()) } }
    private fun recentQueries(key: String): List<String> = runCatching { JSONArray(historyPreferences.getString(key, "[]")).strings() }.getOrDefault(emptyList())
    private fun rememberQuery(key: String, value: String) { historyPreferences.edit().putString(key, JSONArray((listOf(value) + recentQueries(key)).distinct().take(20)).toString()).apply() }
    private fun playlist(value: YouTubeMusicClient.PlaylistItem) = OnlinePlaylist(value.id, value.title, value.subtitle, value.thumbnailUrl)
    private fun song(value: OnlineTrack) = Song("yt:${value.uri.lastPathSegment}", value.title, value.artist, value.album, value.duration,
        value.uri.toString(), OnlineAudio.imageUrl(value.uri).takeIf { it.startsWith("https://") }, format = AudioFormat.M4A, sampleRateHz = 0, bitDepth = 0)
    private fun JSONArray.strings() = (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
    private fun songsJson(songs: List<Song>) = JSONArray().apply { songs.forEach {
        put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist).put("duration", it.durationMs).put("uri", it.mediaUri).put("art", it.artworkUri.orEmpty()))
    } }
    private fun JSONArray.songs() = (0 until length()).mapNotNull { index ->
        runCatching { val row = getJSONObject(index); val uri = row.getString("uri"); require(uri.startsWith("yangyoutube://"));
            Song(row.getString("id"), row.getString("title"), row.getString("artist"), "YouTube", row.optLong("duration"), uri,
                row.optString("art").takeIf { it.startsWith("https://") }, format = AudioFormat.M4A, sampleRateHz = 0, bitDepth = 0)
        }.getOrNull()
    }
}

private class CatalogMemo(context: Context) {
    private val directory = context.cacheDir.resolve("online_catalog_v1").apply { mkdirs() }
    private fun file(key: String): File = directory.resolve(MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 255) } + ".json")
    fun read(key: String, ttl: Long): JSONObject? = runCatching {
        val file = file(key)
        if (!file.isFile || file.length() > 3 * 1024 * 1024 || System.currentTimeMillis() - file.lastModified() > ttl) null else JSONObject(file.readText())
    }.getOrNull()
    @Synchronized fun write(key: String, value: JSONObject) {
        runCatching {
            val json = value.toString(); if (json.length > 1_500_000) return
            val target = file(key); val temporary = directory.resolve(target.name + ".tmp")
            temporary.writeText(json); if (!temporary.renameTo(target)) temporary.delete()
            val files = directory.listFiles().orEmpty().filter { it.extension == "json" }.sortedByDescending { it.lastModified() }
            var total = 0L
            files.forEachIndexed { index, cached -> total += cached.length(); if (index >= 80 || total > 12 * 1024 * 1024) cached.delete() }
        }
    }
}
