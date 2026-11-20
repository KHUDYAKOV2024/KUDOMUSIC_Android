package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.TrackSourceType
import com.example.ui.components.AddStreamDialog
import com.example.ui.components.AddToPlaylistSheet
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.EqualizerBottomSheet
import com.example.ui.components.HubSettingsBottomSheet
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.QueueBottomSheet
import com.example.ui.components.SleepTimerBottomSheet
import com.example.ui.components.TrackCardBottomSheet
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HubAuthScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.PulseGreen
import com.example.ui.theme.SoundPulseTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.DetailTarget
import com.example.ui.viewmodel.MusicPlayerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            SoundPulseTheme {
                KudoMusicApp()
            }
        }
    }
}

@Composable
fun KudoMusicApp(
    viewModel: MusicPlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistTrackMap by viewModel.playlistTrackMap.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val authHeaders = remember(uiState.hubSession) { uiState.hubSession.buildAuthHeaders() }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioFiles(uris)
        }
    }

    val launchAudioImport = {
        audioPickerLauncher.launch(arrayOf("audio/*"))
    }

    val messageToShow = engineState.errorMessage ?: uiState.statusMessage
    LaunchedEffect(messageToShow) {
        if (messageToShow != null) {
            snackbarHostState.showSnackbar(messageToShow)
            viewModel.consumeStatusMessage()
        }
    }

    if (!uiState.hubSession.isAuthorized) {
        HubAuthScreen(
            initialHubUrl = uiState.hubSession.hubUrl,
            initialUsername = uiState.hubSession.username,
            isConnecting = uiState.isHubConnecting,
            errorMessage = uiState.hubAuthError,
            canContinueOffline = uiState.canContinueOffline,
            onConnectClick = viewModel::connectToHub,
            onContinueOfflineClick = viewModel::continueHubOffline
        )
        return
    }

    val navItems = listOf(
        Triple(AppTab.HOME, R.string.nav_home, Pair(Icons.Filled.Home, Icons.Outlined.Home)),
        Triple(AppTab.SEARCH, R.string.nav_search, Pair(Icons.Filled.Search, Icons.Outlined.Search)),
        Triple(AppTab.LIBRARY, R.string.nav_library, Pair(Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic))
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.statusBars,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (useRail) Color.Transparent else ObsidianDeep.copy(alpha = 0.94f))
                ) {
                    val activeTrack = playbackState.currentTrack
                    if (activeTrack != null) {
                        MiniPlayerBar(
                            track = activeTrack,
                            authHeaders = authHeaders,
                            engineState = engineState,
                            onOpenNowPlaying = viewModel::openNowPlaying,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onToggleFavorite = { viewModel.toggleFavorite(activeTrack) },
                            onSkipNext = viewModel::skipNext,
                            onSkipPrevious = viewModel::skipPrevious,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .widthIn(max = 720.dp)
                                .then(
                                    if (useRail) Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                                    else Modifier
                                )
                        )
                    }

                    if (!useRail) {
                        NavigationBar(
                            containerColor = ObsidianDeep.copy(alpha = 0.96f),
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            navItems.forEach { (tab, labelRes, icons) ->
                                val selected = uiState.selectedTab == tab && uiState.activeDetail == null
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) icons.first else icons.second,
                                            contentDescription = stringResource(labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(labelRes)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PulseGreen,
                                        selectedTextColor = TextPrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = PulseGreen.copy(alpha = 0.16f)
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${tab.name}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = CharcoalSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(0.15f))
                        navItems.forEach { (tab, labelRes, icons) ->
                            val selected = uiState.selectedTab == tab && uiState.activeDetail == null
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = stringResource(labelRes)
                                    )
                                },
                                label = { Text(stringResource(labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = PulseGreen,
                                    selectedTextColor = TextPrimary,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = PulseGreen.copy(alpha = 0.18f)
                                ),
                                modifier = Modifier.testTag("rail_tab_${tab.name}")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                val contentBottomPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val detailTarget = uiState.activeDetail
                    if (detailTarget != null) {
                        when (detailTarget) {
                            DetailTarget.LikedSongs -> {
                                PlaylistDetailScreen(
                                    title = stringResource(R.string.liked_songs_title),
                                    subtitle = stringResource(R.string.liked_songs_subtitle),
                                    coverUrl = null,
                                    localArtPath = null,
                                    authHeaders = authHeaders,
                                    isLikedCollection = true,
                                    isLocalCollection = false,
                                    customPlaylist = null,
                                    tracks = favoriteTracks,
                                    currentTrack = playbackState.currentTrack,
                                    engineState = engineState,
                                    onBack = viewModel::closeDetail,
                                    onPlayAll = { shuffle ->
                                        viewModel.playPlaylist(favoriteTracks, "Любимые треки", shuffle)
                                    },
                                    onPlayTrack = { track ->
                                        viewModel.playTrack(track, favoriteTracks, "Любимые треки")
                                    },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onOpenTrackCard = { viewModel.setTrackForCardSheet(it) },
                                    onRemoveFromPlaylist = null,
                                    onDeletePlaylist = null,
                                    onImportAudioClick = launchAudioImport,
                                    bottomInsetPadding = contentBottomPadding,
                                    modifier = Modifier.widthIn(max = 840.dp)
                                )
                            }

                            DetailTarget.LocalAudio -> {
                                val localTracks = allTracks.filter {
                                    it.parsedSourceType == TrackSourceType.LOCAL_URI
                                }
                                PlaylistDetailScreen(
                                    title = stringResource(R.string.local_files_title),
                                    subtitle = "Файлы из памяти смартфона",
                                    coverUrl = null,
                                    localArtPath = null,
                                    authHeaders = authHeaders,
                                    isLikedCollection = false,
                                    isLocalCollection = true,
                                    customPlaylist = null,
                                    tracks = localTracks,
                                    currentTrack = playbackState.currentTrack,
                                    engineState = engineState,
                                    onBack = viewModel::closeDetail,
                                    onPlayAll = { shuffle ->
                                        viewModel.playPlaylist(localTracks, "Аудио с устройства", shuffle)
                                    },
                                    onPlayTrack = { track ->
                                        viewModel.playTrack(track, localTracks, "Аудио с устройства")
                                    },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onOpenTrackCard = { viewModel.setTrackForCardSheet(it) },
                                    onRemoveFromPlaylist = { viewModel.deleteTrack(it) },
                                    onDeletePlaylist = null,
                                    onImportAudioClick = launchAudioImport,
                                    bottomInsetPadding = contentBottomPadding,
                                    modifier = Modifier.widthIn(max = 840.dp)
                                )
                            }

                            is DetailTarget.Album -> {
                                val album = albums.firstOrNull { it.key == detailTarget.albumKey }
                                if (album != null) {
                                    val sub = buildString {
                                        append(album.artist)
                                        if (album.year > 0) {
                                            append(" • ")
                                            append(album.year)
                                        }
                                    }
                                    PlaylistDetailScreen(
                                        title = album.title,
                                        subtitle = sub,
                                        coverUrl = album.coverUrl,
                                        localArtPath = album.localArtPath,
                                        authHeaders = authHeaders,
                                        isLikedCollection = false,
                                        isLocalCollection = false,
                                        customPlaylist = null,
                                        tracks = album.tracks,
                                        currentTrack = playbackState.currentTrack,
                                        engineState = engineState,
                                        onBack = viewModel::closeDetail,
                                        onPlayAll = { shuffle ->
                                            viewModel.playPlaylist(album.tracks, album.title, shuffle)
                                        },
                                        onPlayTrack = { track ->
                                            viewModel.playTrack(track, album.tracks, album.title)
                                        },
                                        onToggleFavorite = viewModel::toggleFavorite,
                                        onOpenTrackCard = { viewModel.setTrackForCardSheet(it) },
                                        onRemoveFromPlaylist = null,
                                        onDeletePlaylist = null,
                                        onImportAudioClick = launchAudioImport,
                                        bottomInsetPadding = contentBottomPadding,
                                        modifier = Modifier.widthIn(max = 840.dp)
                                    )
                                } else {
                                    LaunchedEffect(Unit) { viewModel.closeDetail() }
                                }
                            }

                            is DetailTarget.CustomPlaylist -> {
                                val playlist = playlists.firstOrNull { it.id == detailTarget.playlistId }
                                if (playlist != null) {
                                    val tracks = playlistTrackMap[playlist.id].orEmpty()
                                    PlaylistDetailScreen(
                                        title = playlist.name,
                                        subtitle = playlist.description,
                                        coverUrl = playlist.coverUrl,
                                        localArtPath = null,
                                        authHeaders = authHeaders,
                                        isLikedCollection = false,
                                        isLocalCollection = false,
                                        customPlaylist = playlist,
                                        tracks = tracks,
                                        currentTrack = playbackState.currentTrack,
                                        engineState = engineState,
                                        onBack = viewModel::closeDetail,
                                        onPlayAll = { shuffle ->
                                            viewModel.playPlaylist(tracks, playlist.name, shuffle)
                                        },
                                        onPlayTrack = { track ->
                                            viewModel.playTrack(track, tracks, playlist.name)
                                        },
                                        onToggleFavorite = viewModel::toggleFavorite,
                                        onOpenTrackCard = { viewModel.setTrackForCardSheet(it) },
                                        onRemoveFromPlaylist = { track ->
                                            viewModel.removeTrackFromPlaylist(playlist.id, track)
                                        },
                                        onDeletePlaylist = { viewModel.deletePlaylist(playlist) },
                                        onImportAudioClick = launchAudioImport,
                                        bottomInsetPadding = contentBottomPadding,
                                        modifier = Modifier.widthIn(max = 840.dp)
                                    )
                                } else {
                                    LaunchedEffect(Unit) { viewModel.closeDetail() }
                                }
                            }
                        }
                    } else {
                        when (uiState.selectedTab) {
                            AppTab.HOME -> HomeScreen(
                                hubSession = uiState.hubSession,
                                authHeaders = authHeaders,
                                allTracks = allTracks,
                                albums = albums,
                                favoriteTracks = favoriteTracks,
                                recentlyPlayed = recentlyPlayed,
                                playlists = playlists,
                                selectedFilter = uiState.homeFilter,
                                currentTrack = playbackState.currentTrack,
                                engineState = engineState,
                                onSelectFilter = viewModel::selectHomeFilter,
                                onOpenDetail = viewModel::openDetail,
                                onPlayTrack = viewModel::playTrack,
                                onPlayAlbum = { album ->
                                    viewModel.playPlaylist(album.tracks, album.title, shuffle = false)
                                },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onOpenTrackCard = { viewModel.setTrackForCardSheet(it) },
                                onImportAudioClick = launchAudioImport,
                                onAddStreamClick = { viewModel.setShowAddStreamDialog(true) },
                                onSyncHubClick = viewModel::syncCurrentHub,
                                onOpenHubSettings = { viewModel.setShowHubSettingsSheet(true) },
                                bottomInsetPadding = contentBottomPadding,
                                modifier = Modifier.widthIn(max = 840.dp)
                            )

                            AppTab.SEARCH -> SearchScreen(
                                query = uiState.searchQuery,
                                allTracks = allTracks,
                                albums = albums,
                                authHeaders = authHeaders,
                                currentTrack = playbackState.currentTrack,
                                engineState = engineState,
                                onQueryChange = viewModel::updateSearchQuery,
                                onOpenAlbum = { key -> viewModel.openDetail(DetailTarget.Album(key)) },
                                onPlayTrack = viewModel::playTrack,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onOpenTrackCard = { viewModel.setTrackForCardSheet(it) },
                                bottomInsetPadding = contentBottomPadding,
                                modifier = Modifier.widthIn(max = 840.dp)
                            )

                            AppTab.LIBRARY -> LibraryScreen(
                                allTracks = allTracks,
                                albums = albums,
                                favoriteTracks = favoriteTracks,
                                playlists = playlists,
                                playlistTrackMap = playlistTrackMap,
                                authHeaders = authHeaders,
                                selectedFilter = uiState.libraryFilter,
                                currentTrack = playbackState.currentTrack,
                                engineState = engineState,
                                onSelectFilter = viewModel::selectLibraryFilter,
                                onOpenDetail = viewModel::openDetail,
                                onPlayTrack = viewModel::playTrack,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onOpenTrackCard = { viewModel.setTrackForCardSheet(it) },
                                onCreatePlaylistClick = { viewModel.setShowCreatePlaylistDialog(true) },
                                onImportAudioClick = launchAudioImport,
                                onAddStreamClick = { viewModel.setShowAddStreamDialog(true) },
                                bottomInsetPadding = contentBottomPadding,
                                modifier = Modifier.widthIn(max = 840.dp)
                            )
                        }
                    }
                }
            }
        }

        // Full-screen gesture-powered Now Playing overlay
        val currentTrack = playbackState.currentTrack
        AnimatedVisibility(
            visible = uiState.isNowPlayingOpen && currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            if (currentTrack != null) {
                NowPlayingScreen(
                    track = currentTrack,
                    authHeaders = authHeaders,
                    contextLabel = playbackState.contextLabel,
                    engineState = engineState,
                    isShuffleEnabled = playbackState.isShuffleEnabled,
                    repeatMode = playbackState.repeatMode,
                    eqPreset = playbackState.eqPreset,
                    sleepTimerRemainingSec = uiState.sleepTimerRemainingSec,
                    onClose = viewModel::closeNowPlaying,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSkipNext = viewModel::skipNext,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSeekTo = viewModel::seekTo,
                    onSeekBy = viewModel::seekBy,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                    onToggleFavorite = { viewModel.toggleFavorite(currentTrack) },
                    onOpenEq = { viewModel.setShowEqSheet(true) },
                    onOpenSleepTimer = { viewModel.setShowSleepTimerSheet(true) },
                    onOpenAddToPlaylist = { viewModel.setTrackForPlaylistSheet(currentTrack) },
                    onOpenQueue = { viewModel.setShowQueueSheet(true) }
                )
            }
        }

        // Song Card Sheet (cover-color-harmonized)
        uiState.trackForCardSheet?.let { cardTrack ->
            val matchingAlbum = albums.firstOrNull { alb ->
                alb.tracks.any { it.id == cardTrack.id }
            }
            TrackCardBottomSheet(
                track = cardTrack,
                authHeaders = authHeaders,
                onPlayNow = { viewModel.playTrack(cardTrack, allTracks, cardTrack.album.ifBlank { "Медиатека" }) },
                onToggleFavorite = { viewModel.toggleFavorite(cardTrack) },
                onOpenAlbum = matchingAlbum?.let { alb ->
                    { viewModel.openDetail(DetailTarget.Album(alb.key)) }
                },
                onAddToPlaylist = { viewModel.setTrackForPlaylistSheet(cardTrack) },
                onDeleteTrack = { viewModel.deleteTrack(cardTrack) },
                onDismiss = { viewModel.setTrackForCardSheet(null) }
            )
        }

        if (uiState.showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { viewModel.setShowCreatePlaylistDialog(false) },
                onCreate = viewModel::createPlaylist
            )
        }

        if (uiState.showAddStreamDialog) {
            AddStreamDialog(
                onDismiss = { viewModel.setShowAddStreamDialog(false) },
                onAdd = viewModel::addStreamUrl
            )
        }

        uiState.trackForPlaylistSheet?.let { trackToAdd ->
            AddToPlaylistSheet(
                track = trackToAdd,
                playlists = playlists,
                playlistTrackMap = playlistTrackMap,
                authHeaders = authHeaders,
                onDismiss = { viewModel.setTrackForPlaylistSheet(null) },
                onSelectPlaylist = { playlist ->
                    viewModel.addTrackToPlaylist(playlist, trackToAdd)
                },
                onCreateNewPlaylist = { viewModel.setShowCreatePlaylistDialog(true) }
            )
        }

        if (uiState.showEqSheet) {
            EqualizerBottomSheet(
                activePreset = playbackState.eqPreset,
                onSelectPreset = viewModel::selectEqPreset,
                onDismiss = { viewModel.setShowEqSheet(false) }
            )
        }

        if (uiState.showSleepTimerSheet) {
            SleepTimerBottomSheet(
                remainingSeconds = uiState.sleepTimerRemainingSec,
                onSelectMinutes = viewModel::setSleepTimer,
                onDismiss = { viewModel.setShowSleepTimerSheet(false) }
            )
        }

        if (uiState.showQueueSheet) {
            QueueBottomSheet(
                queue = playbackState.queue,
                currentIndex = playbackState.currentQueueIndex,
                authHeaders = authHeaders,
                onSelectTrack = { track ->
                    viewModel.playTrack(track, playbackState.queue, playbackState.contextLabel)
                },
                onDismiss = { viewModel.setShowQueueSheet(false) }
            )
        }

        if (uiState.showHubSettingsSheet) {
            HubSettingsBottomSheet(
                session = uiState.hubSession,
                isSyncing = uiState.isHubConnecting,
                onSyncClick = viewModel::syncCurrentHub,
                onSwitchHubClick = viewModel::disconnectHub,
                onDismiss = { viewModel.setShowHubSettingsSheet(false) }
            )
        }
    }
}
