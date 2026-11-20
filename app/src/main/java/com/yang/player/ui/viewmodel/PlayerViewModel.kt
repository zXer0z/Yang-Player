package com.yang.player.ui.viewmodel

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.yang.player.data.AudioCoverFetcher
import com.yang.player.data.AudioScanner
import com.yang.player.data.CloudPlaylistSummary
import com.yang.player.data.CloudStreamQuality
import com.yang.player.data.DocumentAudioLibrary
import android.net.Uri
import com.yang.player.data.HomeRecommendations
import com.yang.player.data.LibraryTrack
import com.yang.player.data.ServerLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import com.yang.player.data.PlaylistStore
import com.yang.player.data.SmartCategory
import com.yang.player.data.SmartPlaylistGenerator
import com.yang.player.data.UnifiedPlaylist
import com.yang.player.data.ListeningHistoryStore
import com.yang.player.model.Album
import com.yang.player.model.CategoryFilter
import com.yang.player.model.LibrarySource
import com.yang.player.model.PlayerState
import com.yang.player.model.Song
import com.yang.player.player.MusicPlayerController
import com.yang.player.online.OnlineCoordinator
import com.yang.player.ui.components.NavigationTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryDestination(val page: String, val key: String = "", val title: String = "")

@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val _libraryDestination = MutableStateFlow<LibraryDestination?>(null)
    val libraryDestination = _libraryDestination.asStateFlow()
    fun consumeLibraryDestination() { _libraryDestination.value = null }
    private fun openLibrary(destination: LibraryDestination) {
        _libraryDestination.value = destination
        if(destination.key.isBlank()) selectTab(NavigationTab.TU)
    }
    fun openPlaylists() = openLibrary(LibraryDestination("playlists"))
    fun openYouTubeAccount() = openLibrary(LibraryDestination("account"))
    fun openAlbum(album: Album) = openLibrary(LibraryDestination("albums", album.id, album.name))
    fun openFavorites() = openLibrary(LibraryDestination("favorites"))
    fun openArtist(name: String) {
        val primary = collections.value.primaryArtist(name)
        openLibrary(LibraryDestination("artists", collections.value.artistKey(name), primary))
    }
    fun openAlbumByName(name: String) { albums.value.firstOrNull { it.name == name }?.let(::openAlbum) }

    private val playerController = MusicPlayerController(application)
    private val completion = com.yang.player.data.OnlineMetadata.get(application)
    private val audioScanner = AudioScanner(application)
    private val documentLibrary = DocumentAudioLibrary(application)
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()
    private val _libraryMessage = MutableStateFlow<String?>(null)
    val libraryMessage = _libraryMessage.asStateFlow()
    private val _musicFolders = MutableStateFlow(documentLibrary.folders().map(documentLibrary::folderLabel))
    val musicFolders = _musicFolders.asStateFlow()
    private val history = ListeningHistoryStore.get(application)
    val online = OnlineCoordinator(application, viewModelScope)
    private var scanJob: Job? = null

    val playerState: StateFlow<PlayerState> = playerController.playerState
    val playbackPositionMs: StateFlow<Long> = playerController.playbackPositionMs
    val playbackProgress: StateFlow<Float> = playerController.playbackProgress

    private val _selectedCategory = MutableStateFlow(CategoryFilter.TODO)
    val selectedCategory: StateFlow<CategoryFilter> = _selectedCategory.asStateFlow()

    private val _selectedTab = MutableStateFlow(NavigationTab.INICIO)
    val selectedTab: StateFlow<NavigationTab> = _selectedTab.asStateFlow()

    private val _isFullPlayerVisible = MutableStateFlow(false)
    val isFullPlayerVisible: StateFlow<Boolean> = _isFullPlayerVisible.asStateFlow()

    private val _featuredSongs = MutableStateFlow<List<Song>>(emptyList())
    val featuredSongs: StateFlow<List<Song>> = _featuredSongs.asStateFlow()

    private val libraryPreferences = application.getSharedPreferences("library_source", android.content.Context.MODE_PRIVATE)
    private val _cloudMode = MutableStateFlow(libraryPreferences.getBoolean(ServerLibrary.modePreferenceKey, false))
    val cloudMode = _cloudMode.asStateFlow()
    private val _librarySource = MutableStateFlow(
        when {
            libraryPreferences.getBoolean("browse_youtube", false) -> LibrarySource.YOUTUBE
            _cloudMode.value -> LibrarySource.NAVIDROME
            else -> LibrarySource.DEVICE
        }
    )
    val librarySource = _librarySource.asStateFlow()
    private val _cloudStatus = MutableStateFlow<String?>(null)
    val cloudStatus = _cloudStatus.asStateFlow()
    private val _cloudLoading = MutableStateFlow(false)
    val cloudLoading = _cloudLoading.asStateFlow()
    private var cloudJob: Job? = null
    private var recommendationJob: Job? = null
    private var localCatalog = emptyList<Song>()
    private var cloudTracks = emptyMap<String, LibraryTrack>()
    private var cloudByNavidromeId = emptyMap<String, Song>()
    private var cloudServerAlbumOrder = emptyList<String>()
    private val _cloudStarredIds = MutableStateFlow<Set<String>>(emptySet())
    private val _cloudPlaylists = MutableStateFlow<List<CloudPlaylistSummary>>(emptyList())
    val cloudPlaylists: StateFlow<List<CloudPlaylistSummary>> = _cloudPlaylists.asStateFlow()
    private val _cloudStreamQuality = MutableStateFlow(ServerLibrary.streamQuality(application))
    val cloudStreamQuality: StateFlow<CloudStreamQuality> = _cloudStreamQuality.asStateFlow()
    private val cloudDownloadPrefs = application.getSharedPreferences("cloud_downloads", android.content.Context.MODE_PRIVATE)
    private val _downloadedCloudTick = MutableStateFlow(0)

    fun normalizedKey(value: String): String {
        val folded = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKD)
        val sb = StringBuilder(folded.length)
        var lastSpace = true
        for (ch in folded) {
            if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
            val low = ch.lowercaseChar()
            if (low in 'a'..'z' || low in '0'..'9') {
                sb.append(low)
                lastSpace = false
            } else if (!lastSpace) {
                sb.append(' ')
                lastSpace = true
            }
        }
        return sb.toString().trim()
    }

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()
    val downloadedCloudIds: StateFlow<Set<String>> = combine(_allSongs, cloudMode, _downloadedCloudTick) { songs, cloud, _ ->
        if (!cloud) emptySet()
        else {
            val savedIds = cloudDownloadPrefs.getStringSet("ids", emptySet()).orEmpty()
            val localKeys = localCatalog.map { "${normalizedKey(it.artist)}|${normalizedKey(it.title)}" }.toSet()
            songs.filter { s ->
                s.id in savedIds || "${normalizedKey(s.artist)}|${normalizedKey(s.title)}" in localKeys
            }.mapTo(mutableSetOf()) { it.id }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val favoriteSongs: StateFlow<List<Song>> = combine(_allSongs, online.knownTracks, history.profile, cloudMode, _cloudStarredIds) { songs, known, profile, cloud, starred ->
        (if (cloud) songs else songs + known).distinctBy { it.id }.filter {
            it.id in profile.favoriteIds || (cloud && it.id in starred)
        }.sortedWith(compareByDescending<Song> { profile.tracks[it.id]?.lastPlayedAt ?: 0L }.thenBy { it.title })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val youtubeFavoriteSongs: StateFlow<List<Song>> = combine(online.knownTracks, history.profile) { tracks, profile ->
        tracks.filter { it.id in profile.favoriteIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _suggestedSongs = MutableStateFlow<List<Song>>(emptyList())
    val suggestedSongs = _suggestedSongs.asStateFlow()
    private val _recommendedAlbums = MutableStateFlow<List<Album>>(emptyList())
    val recommendedAlbums = _recommendedAlbums.asStateFlow()

    private val savedPlaylists = flow { emitAll(PlaylistStore.get(application).playlists) }
    val homePlaylists: StateFlow<List<UnifiedPlaylist>> = combine(allSongs, savedPlaylists, cloudMode, _cloudPlaylists) { songs, saved, cloud, serverPls ->
        val libraryMap = songs.associateBy { it.id }
        val userPlaylists = if (cloud) {
            val byNavId = songs.mapNotNull { s -> ServerLibrary.trackId(s.mediaUri)?.let { it to s } }.toMap()
            serverPls.mapNotNull { pl ->
                val resolved = pl.songIds.mapNotNull { byNavId[it] }.distinctBy { it.id }
                val covers = resolved.mapNotNull { it.artworkUri }.distinct().take(4)
                UnifiedPlaylist(
                    id = "cloud_pl_${pl.id}",
                    name = pl.name,
                    subtitle = "${resolved.size} ${if (resolved.size == 1) "canción" else "canciones"}",
                    description = "Navidrome",
                    songs = resolved,
                    category = SmartCategory.USER,
                    isSmart = false,
                    gradientColors = listOf(Color(0xFF1F2833), Color(0xFF12151C)),
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    coverArtworks = covers
                )
            }
        } else {
            saved.mapNotNull { lp ->
                val updatedSongs = lp.songs.map { libraryMap[it.id] ?: it }
                val covers = updatedSongs.mapNotNull { it.artworkUri }.distinct().take(4)
                UnifiedPlaylist(
                    id = "user_${lp.id}",
                    name = lp.name,
                    subtitle = "${updatedSongs.size} ${if (updatedSongs.size == 1) "canción" else "canciones"}",
                    description = "Lista personalizada",
                    songs = updatedSongs,
                    category = SmartCategory.USER,
                    isSmart = false,
                    gradientColors = listOf(Color(0xFF232730), Color(0xFF14161C)),
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    coverArtworks = covers
                )
            }
        }
        val recent = if (cloud && songs.isNotEmpty()) listOf(
            UnifiedPlaylist(
                id = "cloud_recent",
                name = "Recién añadidas",
                subtitle = "Servidor Navidrome",
                description = "Últimas incorporaciones del servidor",
                songs = songs.sortedByDescending { it.dateAdded }.take(30),
                category = SmartCategory.ALL,
                isSmart = true,
                gradientColors = listOf(Color(0xFF264047), Color(0xFF14161C)),
                icon = Icons.AutoMirrored.Rounded.QueueMusic
            )
        ) else emptyList()
        val smart = SmartPlaylistGenerator.generateSmartPlaylists(songs)
        recent + userPlaylists + smart
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Dynamically derive albums from real scanned songs
    val collections = _allSongs.map { com.yang.player.data.LibraryGrouping(it) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.yang.player.data.LibraryGrouping(emptyList()))
    val albums: StateFlow<List<Album>> = collections.map { it.albums }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            completion.changes.collect {
                localCatalog = localCatalog.map(completion::apply)
                if (!_cloudMode.value) _allSongs.value = localCatalog
                com.yang.player.data.PlaylistStore.get(application).refreshMetadata()
                playerController.refreshCompletedMetadata()
                refreshHomeRecommendations()
            }
        }
        if (_cloudMode.value) reloadCloudLibrary() else scanLocalDeviceAudio()
        playerController.onQueueEnded = {
            handleQueueEnded()
        }
        viewModelScope.launch {
            playerController.playbackPositionMs.collect { pos ->
                checkSmartDjPrefetch(pos)
            }
        }
        viewModelScope.launch {
            playerState.map { it.currentSong?.id }.distinctUntilChanged().collect {
                // Refresh off-screen; do not reshuffle a carousel under the user's finger.
                if (_selectedTab.value != NavigationTab.INICIO) refreshHomeRecommendations()
            }
        }
    }

    private var lastPrefetchedSongId: String? = null

    private fun checkSmartDjPrefetch(pos: Long) {
        val state = playerState.value
        val cur = state.currentSong ?: return
        if (!state.isPlaying) return
        val dur = state.durationMs
        if (dur <= 0L || dur - pos > 12_000L) return

        if (state.currentIndex < state.queue.size - 1) return
        if (lastPrefetchedSongId == cur.id) return

        val appCfg = com.yang.player.data.AppearancePreferences.get(getApplication()).config.value
        if (!appCfg.roonRadioEnabled) return

        val songs = _allSongs.value
        if (songs.isEmpty()) return

        val queueIds = state.queue.map { it.id }.toSet()
        val plan = com.yang.player.data.SmartDjSelector.planNextTrack(
            currentSong = cur,
            library = songs,
            recentSongIds = queueIds,
            isSmartMode = true,
            defaultFadeDuration = 3.5f,
            respectGapless = true
        )
        val nextTrack = plan?.nextSong ?: songs.filter { it.id !in queueIds }.shuffled().firstOrNull()
        if (nextTrack != null) {
            lastPrefetchedSongId = cur.id
            playerController.addToQueue(nextTrack)
        }
    }

    fun selectCategory(category: CategoryFilter) {
        _selectedCategory.value = category
    }

    fun selectTab(tab: NavigationTab) {
        _selectedTab.value = tab
    }

    fun playSong(song: Song, queue: List<Song> = _allSongs.value) {
        online.rememberSong(song)
        playerController.playSong(song, if (queue.any { it.id == song.id }) queue else listOf(song) + queue)
    }

    fun playOnlineSong(song: Song, queue: List<Song>) {
        if (playerState.value.currentSong?.id == song.id) togglePlayPause() else playSong(song, queue)
    }

    fun retryPlayback() {
        val state = playerState.value
        state.currentSong?.let { playerController.playSong(it, state.queue, playbackPositionMs.value, state.currentIndex) }
    }

    fun playAlbum(album: Album) {
        val albumSongs = collections.value.songsByAlbum(album.id)
        if (albumSongs.isNotEmpty()) {
            playerController.playSong(albumSongs.first(), albumSongs)
        }
    }

    fun togglePlayPause(song: Song? = null) {
        val current = playerState.value.currentSong
        if (song != null && song.id != current?.id) {
            playSong(song)
        } else if (current != null) {
            playerController.togglePlayPause()
        } else if (_allSongs.value.isNotEmpty()) {
            playSong(_allSongs.value.first(), _allSongs.value)
        }
    }

    fun toggleShuffle() = playerController.toggleShuffle()
    fun cycleRepeat() = playerController.cycleRepeat()
    fun next() = playerController.skipToNext()
    fun previous() = playerController.skipToPrevious()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun playNext(song: Song) = playerController.playNext(song)
    fun addToQueue(song: Song) = playerController.addToQueue(song)
    fun addSongsToQueue(songs: List<Song>) = playerController.addSongsToQueue(songs)
    fun replaceUpcomingQueue(newUpcoming: List<Song>) = playerController.replaceUpcomingQueue(newUpcoming)
    fun selectQueueItem(index: Int) = playerController.selectQueueItem(index)
    fun moveQueueItem(from: Int, to: Int) = playerController.moveQueueItem(from, to)

    fun toggleFavorite(song: Song) {
        val wasFav = song.id in history.profile.value.favoriteIds || (_cloudMode.value && song.id in _cloudStarredIds.value)
        online.rememberSong(song)
        history.toggleFavorite(song.id)
        if (_cloudMode.value && song.mediaUri.startsWith(ServerLibrary.SCHEME)) {
            val targetFav = !wasFav
            _cloudStarredIds.value = if (targetFav) _cloudStarredIds.value + song.id else _cloudStarredIds.value - song.id
            val trackId = ServerLibrary.trackId(song.mediaUri)
            if (trackId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        val profile = ServerLibrary.saved(getApplication()) ?: return@runCatching
                        ServerLibrary.setStarred(profile, trackId, targetFav)
                    }
                }
            }
        }
        refreshHomeRecommendations()
    }

    fun selectLibrarySource(source: LibrarySource) {
        if (_librarySource.value == source) return
        if (source == LibrarySource.YOUTUBE) {
            consumeLibraryDestination()
            _librarySource.value = source
            libraryPreferences.edit().putBoolean("browse_youtube", true).apply()
        } else {
            setCloudMode(source == LibrarySource.NAVIDROME)
        }
    }

    fun setCloudMode(enabled: Boolean) {
        _librarySource.value = if (enabled) LibrarySource.NAVIDROME else LibrarySource.DEVICE
        libraryPreferences.edit().putBoolean("browse_youtube", false).apply()
        if (_cloudMode.value == enabled) return
        consumeLibraryDestination()
        _cloudMode.value = enabled
        libraryPreferences.edit().putBoolean(ServerLibrary.modePreferenceKey, enabled).apply()
        if (enabled) {
            _allSongs.value = emptyList()
            _featuredSongs.value = emptyList()
            _suggestedSongs.value = emptyList()
            _recommendedAlbums.value = emptyList()
            recommendationJob?.cancel()
            reloadCloudLibrary()
        } else {
            cloudJob?.cancel()
            _cloudLoading.value = false
            _cloudStatus.value = null
            clearCloudCatalog()
            _allSongs.value = localCatalog
            refreshHomeRecommendations()
            scanLocalDeviceAudio()
        }
    }
    private fun clearCloudCatalog() {
        cloudTracks = emptyMap()
        cloudByNavidromeId = emptyMap()
        _cloudStarredIds.value = emptySet()
        _cloudPlaylists.value = emptyList()
        cloudServerAlbumOrder = emptyList()
    }

    fun reloadCloudLibrary() {
        if (!_cloudLoading.value) reloadCloudLibrary(keepCatalog = true)
    }

    internal fun reloadCloudLibrary(keepCatalog: Boolean) {
        if (!_cloudMode.value) return
        cloudJob?.cancel()
        if (!keepCatalog) {
            clearCloudCatalog()
            _allSongs.value = emptyList()
            refreshHomeRecommendations()
        }
        _cloudLoading.value = true
        _cloudStatus.value = null
        val app = getApplication<Application>()
        cloudJob = viewModelScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) { ServerLibrary.saved(app) }
                    ?: error("Configura la conexión a tu servidor en Ajustes.")
                val tracks = withContext(Dispatchers.IO) {
                    val catalog = linkedMapOf<String, LibraryTrack>()
                    var offset: Int? = 0
                    while (offset != null) {
                        ensureActive()
                        val page = ServerLibrary.songs(profile, "", offset)
                        for (track in page.tracks) catalog[track.song.id] = track
                        offset = page.nextOffset
                    }
                    catalog
                }
                cloudTracks = tracks
                cloudByNavidromeId = tracks.values.associate { it.navidromeId to it.song }
                _allSongs.value = tracks.values.map(LibraryTrack::song)
                refreshHomeRecommendations()

                _cloudLoading.value = false
                // Tie enrichment to this refresh so a newer catalog cancels it.
                val songsByServerId = cloudByNavidromeId
                launch(Dispatchers.IO) {
                    val starredDef = async { runCatching { ServerLibrary.starredTrackIds(profile) }.getOrDefault(emptySet()) }
                    val plsDef = async { runCatching { ServerLibrary.playlists(profile) }.getOrDefault(emptyList()) }
                    val albumsDef = async {
                        runCatching {
                            (ServerLibrary.albumKeys(profile, "recent", 10) + ServerLibrary.albumKeys(profile, "newest", 10)).distinct()
                        }.getOrDefault(emptyList())
                    }
                    val starredIds = starredDef.await().mapNotNull { songsByServerId[it]?.id }.toSet()
                    val serverPls = plsDef.await()
                    val albumOrder = albumsDef.await()
                    withContext(Dispatchers.Main) {
                        if (_cloudMode.value) {
                            _cloudStarredIds.value = starredIds
                            _cloudPlaylists.value = serverPls
                            cloudServerAlbumOrder = albumOrder
                            refreshHomeRecommendations()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _cloudStatus.value = failure.message ?: "No se pudo actualizar la biblioteca del servidor."
            } finally {
                if (isActive) {
                    _cloudLoading.value = false
                }
            }
        }
    }

    fun cloudDownloadName(song: Song): String? = cloudTracks[song.id]?.filename

    fun setCloudStreamQuality(quality: CloudStreamQuality) {
        val app = getApplication<Application>()
        ServerLibrary.setStreamQuality(app, quality)
        _cloudStreamQuality.value = quality
        android.widget.Toast.makeText(app, "${quality.label} · desde la próxima canción", android.widget.Toast.LENGTH_SHORT).show()
    }



    fun startCloudRadio(seed: Song) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val similar = withContext(Dispatchers.IO) {
                        val profile = ServerLibrary.saved(app) ?: return@withContext emptyList()
                        val trackId = ServerLibrary.trackId(seed.mediaUri) ?: return@withContext emptyList()
                        runCatching {
                            ServerLibrary.similarTrackIds(profile, trackId, 24).mapNotNull { cloudByNavidromeId[it] }
                        }.getOrDefault(emptyList()).filter { it.id != seed.id }
                }
                if (similar.isEmpty()) {
                    android.widget.Toast.makeText(app, "Sin pistas similares disponibles.", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (playerState.value.currentSong?.id == seed.id) {
                    playerController.replaceUpcomingQueue(similar)
                } else {
                    playSong(seed, listOf(seed) + similar)
                }
                android.widget.Toast.makeText(app, "Radio similar (${similar.size} pistas)", android.widget.Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                android.widget.Toast.makeText(app, "No se pudo iniciar la radio.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createCloudPlaylist(name: String, songs: List<Song>) {
        val clean = name.trim().take(80)
        if (clean.isEmpty()) return
        val app = getApplication<Application>()
        val trackIds = songs.mapNotNull { ServerLibrary.trackId(it.mediaUri) }
        viewModelScope.launch {
            try {
                val created = withContext(Dispatchers.IO) {
                    val profile = ServerLibrary.saved(app) ?: error("Configura Navidrome en Ajustes.")
                    ServerLibrary.createPlaylist(profile, clean, trackIds)
                }
                _cloudPlaylists.value = listOf(created) + _cloudPlaylists.value
                android.widget.Toast.makeText(app, "Playlist creada en el servidor", android.widget.Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.widget.Toast.makeText(app, failure.message ?: "No se pudo crear la playlist.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addToCloudPlaylist(playlistId: String, songs: List<Song>) {
        val app = getApplication<Application>()
        val trackIds = songs.mapNotNull { ServerLibrary.trackId(it.mediaUri) }
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val profile = ServerLibrary.saved(app) ?: error("Configura Navidrome en Ajustes.")
                    ServerLibrary.addToPlaylist(profile, playlistId, trackIds)
                }
                _cloudPlaylists.value = _cloudPlaylists.value.map { pl ->
                    if (pl.id == playlistId) pl.copy(songIds = (pl.songIds + trackIds).distinct()) else pl
                }
                android.widget.Toast.makeText(app, "Añadido a la playlist del servidor", android.widget.Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.widget.Toast.makeText(app, failure.message ?: "No se pudo actualizar la playlist.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }













    fun downloadCloudSong(song: Song, destination: Uri?) {
        val track = cloudTracks[song.id] ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val profile = ServerLibrary.saved(app) ?: error("Configura Navidrome en Ajustes.")
                    ServerLibrary.save(app, profile, track, destination)
                }
                val nextIds = cloudDownloadPrefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet().apply { add(song.id) }
                cloudDownloadPrefs.edit().putStringSet("ids", nextIds).apply()
                _downloadedCloudTick.value++
                android.widget.Toast.makeText(app, "Canción guardada", android.widget.Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.widget.Toast.makeText(app, failure.message ?: "No se pudo descargar la canción.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun refreshHomeRecommendations() {
        playerController.flushListeningHistory()
        val songs = _allSongs.value
        val profile = history.profile.value
        val currentId = playerState.value.currentSong?.id
        val serverOrder = if (_cloudMode.value) cloudServerAlbumOrder else emptyList()
        recommendationJob?.cancel()
        recommendationJob = viewModelScope.launch {
            val selection = withContext(Dispatchers.Default) { HomeRecommendations.select(songs, profile, currentId) }
            _featuredSongs.value = selection.featured
            _suggestedSongs.value = selection.suggestions
            _recommendedAlbums.value = if (serverOrder.isEmpty()) selection.albums else {
                val rank = serverOrder.withIndex().associate { it.value to it.index }
                selection.albums.sortedBy { rank["${it.name.trim().lowercase()}|${it.artist.trim().lowercase()}"] ?: Int.MAX_VALUE }
            }
        }
    }

    fun playFeaturedSong(song: Song, queue: List<Song>) {
        if (song.id == playerState.value.currentSong?.id) {
            showFullPlayer()
        } else {
            val position = HomeRecommendations.resumePosition(song, history.profile.value)
            playerController.playSong(song, queue, position)
        }
    }

    fun toggleFeaturedSong(song: Song) {
        if (song.id == playerState.value.currentSong?.id) togglePlayPause()
        else playFeaturedSong(song, _featuredSongs.value)
    }

    fun showFullPlayer() {
        _isFullPlayerVisible.value = true
    }

    fun hideFullPlayer() {
        _isFullPlayerVisible.value = false
        refreshHomeRecommendations()
    }

    fun scanLocalDeviceAudio() {
        if (_cloudMode.value || scanJob?.isActive == true) return
        _isScanning.value = true
        _libraryMessage.value = null
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
            val indexed = audioScanner.scanLocalAudio()
            val (documents, unreadable) = documentLibrary.scan(indexed)
            val localSongs = (indexed + documents).distinctBy { it.mediaUri }.map(completion::apply).sortedBy { it.title.lowercase() }
            withContext(Dispatchers.Main) {
                localCatalog = localSongs
                if (!_cloudMode.value) {
                    _allSongs.value = localSongs
                    refreshHomeRecommendations()
                }
            }
            _libraryMessage.value = "${localSongs.size} canciones en tu biblioteca" + if (unreadable > 0) ". No se pudieron leer $unreadable archivos o carpetas; revisa sus permisos." else ""

            if (localSongs.isNotEmpty()) {
                // Pre-warm high-resolution art cache (600x600 HD) in background
                viewModelScope.launch(Dispatchers.IO) { AudioCoverFetcher.preWarmCache(getApplication<Application>(), localSongs) }
            }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _libraryMessage.value = "No se pudo actualizar la biblioteca. Revisa los permisos e inténtalo de nuevo." }
            finally { _isScanning.value = false }
        }
    }

    fun removeDeletedSong(song: Song) {
        scanJob?.cancel()
        scanJob = null
        val removed = (localCatalog + song).filter { com.yang.player.data.sameSongFile(it, song) }.distinctBy { it.id }
        playerController.removeDeletedSong(song)
        localCatalog = localCatalog.filterNot { com.yang.player.data.sameSongFile(it, song) }
        if (!_cloudMode.value) _allSongs.value = localCatalog
        com.yang.player.data.PlaylistStore.get(getApplication()).removeSongFile(song)
        removed.forEach { if (it.id in history.profile.value.favoriteIds) history.toggleFavorite(it.id) }
        refreshHomeRecommendations()
        scanLocalDeviceAudio()
    }

    fun addMusicFolder(uri: Uri) {
        runCatching { documentLibrary.addFolder(uri) }.onSuccess {
            _musicFolders.value = documentLibrary.folders().map(documentLibrary::folderLabel)
            scanLocalDeviceAudio()
        }.onFailure { _libraryMessage.value = "No se pudo conservar el acceso a la carpeta. Vuelve a seleccionarla." }
    }

    fun openAudio(uri: Uri) {
        viewModelScope.launch {
            try {
                val song = kotlinx.coroutines.withContext(Dispatchers.IO) { completion.apply(documentLibrary.open(uri)) }
                playSong(song, listOf(song))
                showFullPlayer()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _libraryMessage.value = "No se pudo abrir este audio. Comprueba el archivo y sus permisos." }
        }
    }

    private fun handleQueueEnded() {
        val config = com.yang.player.data.AppearancePreferences.get(getApplication()).config.value
        if (!config.roonRadioEnabled) return

        val current = playerState.value.currentSong ?: return
        val currentQueue = playerState.value.queue
        val queueIds = currentQueue.map { it.id }.toSet()
        val songs = _allSongs.value
        if (songs.isEmpty()) return

        val plan = com.yang.player.data.SmartDjSelector.planNextTrack(
            currentSong = current,
            library = songs,
            recentSongIds = queueIds,
            isSmartMode = true,
            defaultFadeDuration = 3.5f,
            respectGapless = true
        )

        val nextTrack = plan?.nextSong
            ?: songs.filter { it.id !in queueIds }.shuffled().firstOrNull()

        if (nextTrack != null) {
            viewModelScope.launch(Dispatchers.Main) {
                playSong(nextTrack, currentQueue + nextTrack)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
