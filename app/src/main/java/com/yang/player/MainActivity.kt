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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import com.yang.player.data.AppearancePreferences
import com.yang.player.data.ThemeMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.yang.player.ui.components.BottomNavBar
import com.yang.player.ui.components.ArtworkAtmosphere
import com.yang.player.ui.components.PlayerDock
import com.yang.player.ui.components.YouTubeDownloadSheet
import com.yang.player.ui.components.AppUpdatePrompt
import com.yang.player.ui.components.rememberYouTubeVideo
import com.yang.player.ui.components.FloatingMiniPlayer
import com.yang.player.ui.components.NavigationTab
import com.yang.player.ui.components.LibrarySourceSelector
import com.yang.player.model.LibrarySource
import com.yang.player.ui.screens.FullPlayerModal
import com.yang.player.ui.screens.DownloadsScreen
import com.yang.player.ui.screens.HiFiAudioScreen
import com.yang.player.ui.screens.HomeScreen
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
        com.yang.player.audio.AudioEngineStore.get(this).refreshRoute()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
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
    var updateRequest by remember { mutableIntStateOf(0) }
    val screenStateHolder = rememberSaveableStateHolder()
    val librarySource by viewModel.librarySource.collectAsState()
    val isYouTube = librarySource == LibrarySource.YOUTUBE
    val sourceSelector: @Composable () -> Unit = {
        LibrarySourceSelector(librarySource, viewModel::selectLibrarySource)
    }
    val cloudMode by viewModel.cloudMode.collectAsState()
    val cloudLoading by viewModel.cloudLoading.collectAsState()
    val cloudStatus by viewModel.cloudStatus.collectAsState()
    val downloadedCloudIds by viewModel.downloadedCloudIds.collectAsState()
    val cloudStreamQuality by viewModel.cloudStreamQuality.collectAsState()
    val cloudPlaylists by viewModel.cloudPlaylists.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val featuredSongs by viewModel.featuredSongs.collectAsState()
    val suggestedSongs by viewModel.suggestedSongs.collectAsState()
    val recommendedAlbums by viewModel.recommendedAlbums.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val account by viewModel.online.account.collectAsState()
    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsState()
    val youtubeDownloadSong by viewModel.youtubeDownloadSong.collectAsState()
    val youtubeVideoEnabled by viewModel.youtubeVideoEnabled.collectAsState()
    val currentVideoSong = playerState.currentSong?.takeIf { youtubeVideoEnabled && it.isOnline }
    var videoOpened by remember(currentVideoSong?.mediaUri, playerState.currentIndex) { mutableStateOf(false) }
    if (isFullPlayerVisible && currentVideoSong != null) {
        SideEffect { videoOpened = true }
    }
    val video = if (currentVideoSong != null && (isFullPlayerVisible || videoOpened)) {
        rememberYouTubeVideo(
            currentVideoSong, playerState.currentIndex, isFullPlayerVisible,
            viewModel::videoClock, viewModel::holdAudioForVideo
        )
    } else null
    val libraryDestination by viewModel.libraryDestination.collectAsState()
    val globalDetail = libraryDestination?.key?.isNotBlank() == true
    val detailTracks = remember(libraryDestination, collections) { when(libraryDestination?.page) {
        "albums" -> collections.songsByAlbum(libraryDestination?.key.orEmpty())
        "artists" -> collections.songsByArtist(libraryDestination?.key.orEmpty())
        else -> emptyList()
    } }
    val globalCover = if(globalDetail) detailTracks.firstOrNull { it.artworkUri != null } ?: detailTracks.firstOrNull() else null
    val isArtistDetail = globalDetail && libraryDestination?.page == "artists"
    val artistPhoto = com.yang.player.data.rememberArtistPhoto(if (isArtistDetail) libraryDestination?.title else null)
    val effectiveDetailArtwork = if (isArtistDetail && !artistPhoto.isNullOrBlank()) artistPhoto else globalCover?.artworkUri
    BackHandler(globalDetail && !isFullPlayerVisible) { viewModel.consumeLibraryDestination() }
    // Keep the artwork palette warm while browsing, so opening the sheet never
    // starts with a different fallback color or repeats artwork extraction.
    val playingPalette = rememberArtworkPalette(playerState.currentSong)
    val libraryPalette = rememberArtworkPalette(effectiveDetailArtwork, globalCover)
    val artworkPalette = if ((effectiveDetailArtwork != null || globalCover != null) && !isFullPlayerVisible) libraryPalette else playingPalette
    val artworkAccent = artworkPalette.accentColor // The backdrop animates in drawing; avoid recomposing the whole UI every frame.
    val backdropLayer = rememberGraphicsLayer()
    var backdropOrigin by remember { mutableStateOf(Offset.Zero) }
    val hasNativeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var compactDock by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var dockHeight by remember { mutableStateOf(172.dp) }
    var requestSearchFocus by remember { mutableStateOf(false) }
    var revealSongId by remember { mutableStateOf<String?>(null) }
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

    val context = LocalContext.current
    val appearancePrefs = remember { AppearancePreferences.get(context) }
    val appearanceConfig by appearancePrefs.config.collectAsState()

    val effectiveAccent = when (appearanceConfig.themeMode) {
        ThemeMode.DYNAMIC -> artworkAccent
        ThemeMode.OLED_BLACK -> Color(0xFFE2E8F0)
        ThemeMode.CUSTOM_ACCENT -> runCatching { Color(android.graphics.Color.parseColor(appearanceConfig.staticAccentHex)) }.getOrDefault(Color(0xFF007AFF))
    }

    val effectiveBackground = when (appearanceConfig.themeMode) {
        ThemeMode.OLED_BLACK -> Color.Black
        else -> BackgroundDark
    }

    val showAtmosphere = appearanceConfig.themeMode == ThemeMode.DYNAMIC

    CompositionLocalProvider(LocalArtworkAccent provides effectiveAccent) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(effectiveBackground)
    ) {
        // Main Screen Area based on selected bottom tab
        Box(modifier = Modifier.fillMaxSize()
            .background(effectiveBackground)
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
            if (showAtmosphere) {
                ArtworkAtmosphere(artworkPalette)
            }
            // Insets protect content, while the atmosphere still fills system bars.
            Box(Modifier.fillMaxSize().then(if(globalDetail) Modifier else Modifier.statusBarsPadding()).nestedScroll(dockScrollConnection)) {
            if(globalDetail) {
                CollectionDetailScreen(libraryDestination?.title.orEmpty(), libraryDestination?.page == "artists", detailTracks, favoriteSongs.map { it.id }.toSet(), playerState.currentSong?.id, playerState.isPlaying,
                    dockHeight + 12.dp, onBack=viewModel::consumeLibraryDestination, onPlay=viewModel::playSong,
                    onPlayNext=viewModel::playNext, onAddToQueue=viewModel::addToQueue, onFavorite=viewModel::toggleFavorite, onAddCollection=viewModel::addSongsToQueue,
                    onStartRadio = if (cloudMode) viewModel::startCloudRadio else null,
                    cloudPlaylists = cloudPlaylists,
                    onCreateCloudPlaylist = viewModel::createCloudPlaylist,
                    onAddToCloudPlaylist = viewModel::addToCloudPlaylist)
            } else { screenStateHolder.SaveableStateProvider(
                if (selectedTab == NavigationTab.INICIO || selectedTab == NavigationTab.CANCIONES) "$selectedTab-$librarySource" else selectedTab.name
            ) {
            Column(Modifier.fillMaxSize()) {
                if (librarySource == LibrarySource.NAVIDROME && (selectedTab == NavigationTab.INICIO || selectedTab == NavigationTab.CANCIONES)) {
                    when {
                        cloudLoading && selectedTab == NavigationTab.INICIO ->
                            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                        cloudStatus != null -> androidx.compose.material3.TextButton(onClick = viewModel::reloadCloudLibrary) {
                            androidx.compose.material3.Text(cloudStatus.orEmpty() + " · Reintentar")
                        }
                    }
                }
            when (selectedTab) {
                NavigationTab.INICIO -> {
                    val playlists by viewModel.homePlaylists.collectAsState()
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
                        artists = collections.artists,
                        allPlaylists = playlists,
                        currentPlayingSongId = playerState.currentSong?.id,
                        isPlaying = playerState.isPlaying,
                        onSongSelected = if (isYouTube) viewModel::playOnlineSong else viewModel::playSong,
                        onAlbumSelected = viewModel::openAlbum,
                        onArtistSelected = viewModel::openArtist,
                        onPlayToggle = { song -> viewModel.toggleFeaturedSong(song) },
                        onProfileClick = { viewModel.selectTab(NavigationTab.TU) },
                        onViewAllFavorites = viewModel::openFavorites,
                        isCloudMode = cloudMode,
                        sourceSelector = sourceSelector,
                        youtube = viewModel.online.takeIf { isYouTube },
                        onConnectYouTube = viewModel::openYouTubeAccount,
                        onOpenYouTubePlaylist = { playlist ->
                            viewModel.online.openPlaylist(playlist)
                            viewModel.selectTab(NavigationTab.CANCIONES)
                        }
                    )
                }
                NavigationTab.CANCIONES -> {
                    val shownFavorites by (if (isYouTube) viewModel.youtubeFavoriteSongs else viewModel.favoriteSongs).collectAsState()
                    SongsLibraryScreen(
                        onAudioSettings = { viewModel.selectTab(NavigationTab.HI_FI) },
                        requestSearchFocus = requestSearchFocus,
                        onSearchFocusHandled = { requestSearchFocus = false },
                        revealSongId = revealSongId,
                        onRevealSongHandled = { revealSongId = null },
                        isPlaying = playerState.isPlaying,
                        songs = allSongs,
                        favoriteSongs = shownFavorites,
                        currentPlayingSongId = playerState.currentSong?.id,
                        onSongSelected = if (isYouTube) viewModel::playOnlineSong else viewModel::playSong,
                        onPlayNext = { song -> viewModel.playNext(song) },
                        onAddToQueue = { song -> viewModel.addToQueue(song) },
                        onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                        onViewAlbum = viewModel::openAlbumByName,
                        onViewArtist = viewModel::openArtist,
                        cloudDownloadName = viewModel::cloudDownloadName,
                        onCloudDownload = viewModel::downloadCloudSong,
                        onYouTubeDownload = viewModel::openYouTubeDownload,
                        isCloudMode = librarySource == LibrarySource.NAVIDROME,
                        isCloudRefreshing = librarySource == LibrarySource.NAVIDROME && cloudLoading,
                        onCloudRefresh = viewModel::reloadCloudLibrary,
                        downloadedCloudIds = downloadedCloudIds,
                        streamQuality = cloudStreamQuality,
                        onStreamQualitySelected = viewModel::setCloudStreamQuality,
                        cloudPlaylists = cloudPlaylists,
                        onCreateCloudPlaylist = viewModel::createCloudPlaylist,
                        onAddToCloudPlaylist = viewModel::addToCloudPlaylist,
                        onStartRadio = if (cloudMode) viewModel::startCloudRadio else null,
                        sourceSelector = sourceSelector,
                        youtube = viewModel.online.takeIf { isYouTube },
                        playbackError = playerState.playbackError.takeIf { isYouTube && playerState.currentSong?.isOnline == true },
                        onRetryPlayback = viewModel::retryPlayback
                    )
                }
                NavigationTab.HI_FI -> {
                    HiFiAudioScreen(playerState = playerState, bottomContentPadding = dockHeight + 12.dp)
                }
                NavigationTab.DESCARGADAS -> {
                    if (isYouTube) DownloadsScreen(
                        dockHeight + 12.dp,
                        onPlayAudio = viewModel::openAudio,
                        sourceSelector = sourceSelector
                    )
                }
                NavigationTab.TU -> {
                    YouScreen(viewModel, dockHeight + 12.dp,
                        onSongs = { viewModel.selectTab(NavigationTab.CANCIONES) },
                        onRescan = onRescan,
                        onCheckUpdates = { updateRequest++ })
                }
            }
            } } }
            }
        }

        PlayerDock(
            compact = compactDock,
            selectedTab = selectedTab,
            currentSong = playerState.currentSong,
            isPlaying = playerState.isPlaying,
            showDownloads = isYouTube,
            backdropLayer = if (hasNativeBlur) backdropLayer else null,
            backdropOrigin = backdropOrigin,
            onTabSelected = { compactDock = false; viewModel.consumeLibraryDestination(); viewModel.selectTab(it) },
            onHome = { compactDock = false },
            onSearch = {
                requestSearchFocus = true
                revealSongId = null
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
        // High frequency progress flows are collected ONLY inside the modal's progress component
        if (isFullPlayerVisible) {
            FullPlayerModal(
                palette = playingPalette,
                song = playerState.currentSong,
                queue = playerState.queue,
                currentIndex = playerState.currentIndex,
                nextSong = nextSong,
                previousSong = previousSong,
                isPlaying = playerState.isPlaying,
                playbackProgress = viewModel.playbackProgress,
                playbackPositionMs = viewModel.playbackPositionMs,
                durationMs = playerState.durationMs,
                video = video,
                loadRelatedVideos = viewModel.online::relatedVideos,
                onPlaySuggestedVideo = viewModel::playSuggestedVideo,
                onYouTubeDownload = viewModel::openYouTubeDownload,
                isFavorite = favoriteSongs.any { it.id == playerState.currentSong?.id },
                isShuffle = playerState.isShuffle,
                repeatMode = playerState.repeatMode,
                onShuffle = viewModel::toggleShuffle,
                onRepeat = viewModel::cycleRepeat,
                onDismiss = {
                    revealSongId = playerState.currentSong?.id
                    viewModel.hideFullPlayer()
                },
                onPlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.next() },
                onPrevious = { viewModel.previous() },
                onSeek = { pos -> viewModel.seekTo(pos) },
                onQueueSelect = { index -> viewModel.selectQueueItem(index) },
                onQueueMove = { from, to -> viewModel.moveQueueItem(from, to) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                onPlayNext = { song -> viewModel.playNext(song) },
                onAddToQueue = { song -> viewModel.addToQueue(song) },
                onStartRadio = if (cloudMode) viewModel::startCloudRadio else null,
                cloudPlaylists = cloudPlaylists,
                onCreateCloudPlaylist = viewModel::createCloudPlaylist,
                onAddToCloudPlaylist = viewModel::addToCloudPlaylist
            )
        }
        youtubeDownloadSong?.let { download ->
            YouTubeDownloadSheet(
                song = download,
                palette = if (playerState.currentSong?.id == download.id) playingPalette else rememberArtworkPalette(download),
                themeMode = appearanceConfig.themeMode,
                onDismiss = viewModel::closeYouTubeDownload
            )
        }
        AppUpdatePrompt(updateRequest)
    }
    }
}
