package com.yang.player.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.yang.player.data.AudioCoverFetcher
import com.yang.player.data.AudioScanner
import com.yang.player.data.DocumentAudioLibrary
import android.net.Uri
import com.yang.player.data.HomeRecommendations
import com.yang.player.data.ListeningHistoryStore
import com.yang.player.model.Album
import com.yang.player.model.CategoryFilter
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
import kotlinx.coroutines.flow.map
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
    fun openAlbum(album: Album) = openLibrary(LibraryDestination("albums", album.id, album.name))
    fun openFavorites() = openLibrary(LibraryDestination("favorites"))
    fun openArtist(name: String) = openLibrary(LibraryDestination("artists", name.trim().lowercase(java.util.Locale.ROOT), name))
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

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()
    val favoriteSongs: StateFlow<List<Song>> = combine(_allSongs, online.knownTracks, history.profile) { songs, known, profile ->
        (songs + known).distinctBy { it.id }.filter { it.id in profile.favoriteIds }
            .sortedWith(compareByDescending<Song> { profile.tracks[it.id]?.lastPlayedAt ?: 0L }.thenBy { it.title })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _suggestedSongs = MutableStateFlow<List<Song>>(emptyList())
    val suggestedSongs = _suggestedSongs.asStateFlow()
    private val _recommendedAlbums = MutableStateFlow<List<Album>>(emptyList())
    val recommendedAlbums = _recommendedAlbums.asStateFlow()

    // Dynamically derive albums from real scanned songs
    val albums: StateFlow<List<Album>> = _allSongs.map { songs ->
        HomeRecommendations.buildAlbums(songs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            completion.changes.collect {
                _allSongs.value = _allSongs.value.map(completion::apply)
                com.yang.player.data.PlaylistStore.get(application).refreshMetadata()
                playerController.refreshCompletedMetadata()
                refreshHomeRecommendations()
            }
        }
        // Automatically scan local audio on startup
        scanLocalDeviceAudio()
        viewModelScope.launch {
            playerState.map { it.currentSong?.id }.distinctUntilChanged().collect {
                // Refresh off-screen; do not reshuffle a carousel under the user's finger.
                if (_selectedTab.value != NavigationTab.INICIO) refreshHomeRecommendations()
            }
        }
    }

    fun selectCategory(category: CategoryFilter) {
        _selectedCategory.value = category
    }

    fun selectTab(tab: NavigationTab) {
        if (tab == NavigationTab.INICIO) refreshHomeRecommendations()
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
        val albumSongs = _allSongs.value.filter { HomeRecommendations.albumKey(it) == album.id }
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
    fun selectQueueItem(index: Int) = playerController.selectQueueItem(index)
    fun moveQueueItem(from: Int, to: Int) = playerController.moveQueueItem(from, to)

    fun toggleFavorite(song: Song) {
        online.rememberSong(song)
        history.toggleFavorite(song.id)
        refreshHomeRecommendations()
    }

    fun refreshHomeRecommendations() {
        playerController.flushListeningHistory()
        val selection = HomeRecommendations.select(_allSongs.value, history.profile.value, playerState.value.currentSong?.id)
        _featuredSongs.value = selection.featured
        _suggestedSongs.value = selection.suggestions
        _recommendedAlbums.value = selection.albums
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
        if (scanJob?.isActive == true) return
        _isScanning.value = true
        _libraryMessage.value = null
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
            val indexed = audioScanner.scanLocalAudio()
            val (documents, unreadable) = documentLibrary.scan(indexed)
            val localSongs = (indexed + documents).distinctBy { it.mediaUri }.map(completion::apply).sortedBy { it.title.lowercase() }
                _allSongs.value = localSongs
                val selection = HomeRecommendations.select(localSongs, history.profile.value, playerState.value.currentSong?.id)
                _featuredSongs.value = selection.featured
                _suggestedSongs.value = selection.suggestions
                _recommendedAlbums.value = selection.albums
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
        val removed = (_allSongs.value + song).filter { com.yang.player.data.sameSongFile(it, song) }.distinctBy { it.id }
        playerController.removeDeletedSong(song)
        _allSongs.value = _allSongs.value.filterNot { com.yang.player.data.sameSongFile(it, song) }
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

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
