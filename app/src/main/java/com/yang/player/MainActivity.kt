package com.yang.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import com.yang.player.ui.screens.CollectionDetailScreen
import com.yang.player.data.HomeRecommendations
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.yang.player.ui.components.BottomNavBar
import com.yang.player.ui.components.ArtworkAtmosphere
import com.yang.player.ui.components.PlayerDock
import com.yang.player.ui.components.FloatingMiniPlayer
import com.yang.player.ui.components.NavigationTab
import com.yang.player.ui.screens.FullPlayerModal
import com.yang.player.ui.screens.HiFiAudioScreen
import com.yang.player.ui.screens.HomeScreen
import com.yang.player.ui.screens.OnlineScreen
import com.yang.player.ui.screens.AccountScreen
import com.yang.player.ui.screens.YouScreen
import com.yang.player.ui.screens.SongsLibraryScreen
import com.yang.player.ui.theme.BackgroundDark
import com.yang.player.ui.theme.YangPlayerTheme
import com.yang.player.ui.theme.rememberArtworkPalette
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlin.math.abs

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = hasAudioPermission()
        if (audioGranted) {
            viewModel.scanLocalDeviceAudio()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()

        setContent {
            YangPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    YangPlayerRootApp(viewModel = viewModel, onRescan = {
                        if (hasAudioPermission()) viewModel.scanLocalDeviceAudio() else checkPermissions()
                    })
                }
            }
        }
    }

    private fun hasAudioPermission(): Boolean = ContextCompat.checkSelfPermission(this,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            viewModel.scanLocalDeviceAudio()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHomeRecommendations()
    }
}

@UnstableApi
@Composable
fun YangPlayerRootApp(viewModel: PlayerViewModel, onRescan: () -> Unit = { viewModel.scanLocalDeviceAudio() }) {
    com.yang.player.ui.components.SongDeletionHost(onDeleted = viewModel::removeDeletedSong) {
        YangPlayerContent(viewModel, onRescan)
    }
}

@UnstableApi
@Composable
private fun YangPlayerContent(viewModel: PlayerViewModel, onRescan: () -> Unit) {
    val screenStateHolder = rememberSaveableStateHolder()
    val playerState by viewModel.playerState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val featuredSongs by viewModel.featuredSongs.collectAsState()
    val suggestedSongs by viewModel.suggestedSongs.collectAsState()
    val recommendedAlbums by viewModel.recommendedAlbums.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val account by viewModel.online.account.collectAsState()
    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsState()
    val libraryDestination by viewModel.libraryDestination.collectAsState()
    val globalDetail = libraryDestination?.key?.isNotBlank() == true
    val detailTracks = remember(libraryDestination, allSongs) { when(libraryDestination?.page) {
        "albums" -> allSongs.filter { HomeRecommendations.albumKey(it) == libraryDestination?.key }
        "artists" -> allSongs.filter { it.artist.trim().lowercase(java.util.Locale.ROOT) == libraryDestination?.key }
        else -> emptyList()
    } }
    val globalCover = if(globalDetail) detailTracks.firstOrNull { it.artworkUri != null } ?: detailTracks.firstOrNull() else null
    BackHandler(globalDetail && !isFullPlayerVisible) { viewModel.consumeLibraryDestination() }
    // Keep the artwork palette warm while browsing, so opening the sheet never
    // starts with a different fallback color or repeats artwork extraction.
    var libraryArtwork by remember { mutableStateOf<com.yang.player.model.Song?>(null) }
    val playingPalette = rememberArtworkPalette(playerState.currentSong)
    val libraryPalette = rememberArtworkPalette(globalCover ?: libraryArtwork)
    val artworkPalette = if ((globalCover != null || selectedTab == NavigationTab.TU && libraryArtwork != null) && !isFullPlayerVisible) libraryPalette else playingPalette
    val artworkAccent = artworkPalette.accentColor // The backdrop animates in drawing; avoid recomposing the whole UI every frame.
    val backdropLayer = rememberGraphicsLayer()
    var backdropOrigin by remember { mutableStateOf(Offset.Zero) }
    val hasNativeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var compactDock by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var dockHeight by remember { mutableStateOf(172.dp) }
    var requestSearchFocus by remember { mutableStateOf(false) }
    val scrollEvents = remember {
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
    val dockScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (abs(consumed.y) > 0.5f) scrollEvents.tryEmit(Unit)
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(scrollEvents) {
        scrollEvents.collectLatest {
            compactDock = true
            // Includes fling events: expansion waits until scrolling has settled.
            delay(1800)
            compactDock = false
        }
    }
    LaunchedEffect(selectedTab, isFullPlayerVisible) { compactDock = false }

    CompositionLocalProvider(LocalArtworkAccent provides artworkAccent) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Main Screen Area based on selected bottom tab
        Box(modifier = Modifier.fillMaxSize()
            .onGloballyPositioned { backdropOrigin = it.positionInRoot() }
            .drawWithContent {
                if (hasNativeBlur) {
                    backdropLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(backdropLayer)
                } else {
                    drawContent()
                }
            }
        ) {
            ArtworkAtmosphere(artworkPalette)
            // Insets protect content, while the atmosphere still fills system bars.
            Box(Modifier.fillMaxSize().then(if(globalDetail || selectedTab == NavigationTab.TU && libraryArtwork != null) Modifier else Modifier.statusBarsPadding()).nestedScroll(dockScrollConnection)) {
            if(globalDetail) {
                CollectionDetailScreen(libraryDestination?.title.orEmpty(), libraryDestination?.page == "artists", detailTracks, favoriteSongs.map { it.id }.toSet(), playerState.currentSong?.id, playerState.isPlaying,
                    dockHeight + 12.dp, onBack=viewModel::consumeLibraryDestination, onPlay=viewModel::playSong,
                    onPlayNext=viewModel::playNext, onAddToQueue=viewModel::addToQueue, onFavorite=viewModel::toggleFavorite, onAddCollection=viewModel::addSongsToQueue)
            } else { screenStateHolder.SaveableStateProvider(selectedTab.name) {
            when (selectedTab) {
                NavigationTab.INICIO -> {
                    HomeScreen(
                        onPlaylists = viewModel::openPlaylists,
                        suggestedSongs = suggestedSongs,
                        recommendedAlbums = recommendedAlbums,
                        onFeaturedSongSelected = { song, queue -> viewModel.playFeaturedSong(song, queue) },
                        bottomContentPadding = dockHeight + 12.dp,
                        userName = account.name,
                        profileImageUrl = account.avatar,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { viewModel.selectCategory(it) },
                        featuredSongs = featuredSongs,
                        favoriteSongs = favoriteSongs,
                        albums = albums,
                        allSongs = allSongs,
                        currentPlayingSongId = playerState.currentSong?.id,
                        isPlaying = playerState.isPlaying,
                        onSongSelected = { song, queue -> viewModel.playSong(song, queue) },
                        onAlbumSelected = viewModel::openAlbum,
                        onPlayToggle = { song -> viewModel.toggleFeaturedSong(song) },
                        onProfileClick = { viewModel.selectTab(NavigationTab.TU) },
                        onViewAllFavorites = viewModel::openFavorites
                    )
                }
                NavigationTab.CANCIONES -> {
                    SongsLibraryScreen(
                        onAudioSettings = { viewModel.selectTab(NavigationTab.HI_FI) },
                        requestSearchFocus = requestSearchFocus,
                        onSearchFocusHandled = { requestSearchFocus = false },
                        isPlaying = playerState.isPlaying,
                        songs = allSongs,
                        favoriteSongs = favoriteSongs,
                        currentPlayingSongId = playerState.currentSong?.id,
                        onSongSelected = { song, queue -> viewModel.playSong(song, queue) },
                        onPlayNext = { song -> viewModel.playNext(song) },
                        onAddToQueue = { song -> viewModel.addToQueue(song) },
                        onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                        onViewAlbum = viewModel::openAlbumByName,
                        onViewArtist = viewModel::openArtist
                    )
                }
                NavigationTab.HI_FI -> {
                    HiFiAudioScreen(playerState = playerState, bottomContentPadding = dockHeight + 12.dp)
                }
                NavigationTab.ONLINE -> {
                    OnlineScreen(
                        online = viewModel.online,
                        currentSongId = playerState.currentSong?.id,
                        isPlaying = playerState.isPlaying,
                        favoriteIds = favoriteSongs.map { it.id }.toSet(),
                        onPlay = { song, queue -> viewModel.playOnlineSong(song, queue) },
                        onPlayNext = viewModel::playNext,
                        onAddToQueue = viewModel::addToQueue,
                        onFavorite = viewModel::toggleFavorite,
                        onAccount = { viewModel.selectTab(NavigationTab.TU) },
                        bottomContentPadding = dockHeight + 12.dp,
                        playbackError = playerState.playbackError.takeIf { playerState.currentSong?.isOnline == true },
                        onRetryPlayback = viewModel::retryPlayback
                    )
                }
                NavigationTab.TU -> {
                    YouScreen(viewModel, dockHeight + 12.dp,
                        onSongs = { viewModel.selectTab(NavigationTab.CANCIONES) }, onRescan = onRescan,
                        onArtworkChange = { libraryArtwork = it })
                }
            }
            } }
            }
        }

        PlayerDock(
            compact = compactDock,
            selectedTab = selectedTab,
            currentSong = playerState.currentSong,
            isPlaying = playerState.isPlaying,
            backdropLayer = if (hasNativeBlur) backdropLayer else null,
            backdropOrigin = backdropOrigin,
            onTabSelected = { compactDock = false; viewModel.consumeLibraryDestination(); viewModel.selectTab(it) },
            onHome = { compactDock = false },
            onSearch = {
                requestSearchFocus = true
                viewModel.consumeLibraryDestination()
                viewModel.selectTab(NavigationTab.CANCIONES)
            },
            onOpenPlayer = { viewModel.showFullPlayer() },
            onPlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.next() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { size -> dockHeight = with(density) { size.height.toDp() } }
                .navigationBarsPadding()
        )

        val nextSong = playerState.queue.getOrNull(playerState.nextIndex)
        val previousSong = playerState.queue.getOrNull(playerState.previousIndex)

        // Full Screen Player Modal (Expands on MiniPlayer click)
        // High frequency progress flows are ONLY collected while modal is open
        if (isFullPlayerVisible) {
            val progress by viewModel.playbackProgress.collectAsState()
            val playbackPositionMs by viewModel.playbackPositionMs.collectAsState()

            FullPlayerModal(
                palette = playingPalette,
                song = playerState.currentSong,
                queue = playerState.queue,
                currentIndex = playerState.currentIndex,
                nextSong = nextSong,
                previousSong = previousSong,
                isPlaying = playerState.isPlaying,
                progress = progress,
                currentPositionMs = playbackPositionMs,
                durationMs = playerState.durationMs,
                isFavorite = favoriteSongs.any { it.id == playerState.currentSong?.id },
                isShuffle = playerState.isShuffle,
                repeatMode = playerState.repeatMode,
                onShuffle = viewModel::toggleShuffle,
                onRepeat = viewModel::cycleRepeat,
                onDismiss = { viewModel.hideFullPlayer() },
                onPlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.next() },
                onPrevious = { viewModel.previous() },
                onSeek = { pos -> viewModel.seekTo(pos) },
                onQueueSelect = { index -> viewModel.selectQueueItem(index) },
                onQueueMove = { from, to -> viewModel.moveQueueItem(from, to) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                onPlayNext = { song -> viewModel.playNext(song) },
                onAddToQueue = { song -> viewModel.addToQueue(song) }
            )
        }
    }
    }
}
