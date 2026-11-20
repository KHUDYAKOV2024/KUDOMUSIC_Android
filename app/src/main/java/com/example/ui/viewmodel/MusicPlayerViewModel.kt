package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.PlaybackCoordinator
import com.example.audio.PlaybackEngineState
import com.example.audio.SharedPlaybackState
import com.example.data.hub.HubConfigStore
import com.example.data.hub.HubConnectResult
import com.example.data.hub.HubConnectionService
import com.example.data.hub.HubSession
import com.example.data.local.AppDatabase
import com.example.data.local.MusicRepository
import com.example.data.model.AlbumSummary
import com.example.data.model.EqPreset
import com.example.data.model.PlaylistEntity
import com.example.data.model.TrackEntity
import com.example.data.model.buildAlbumSummaries
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab {
    HOME,
    SEARCH,
    LIBRARY
}

enum class HomeFilter {
    ALL,
    HUB,
    LOCAL
}

enum class LibraryFilter {
    ALBUMS,
    PLAYLISTS,
    TRACKS
}

sealed interface DetailTarget {
    data object LikedSongs : DetailTarget
    data object LocalAudio : DetailTarget
    data class Album(val albumKey: String) : DetailTarget
    data class CustomPlaylist(val playlistId: Long) : DetailTarget
}

data class PlayerUiState(
    val hubSession: HubSession = HubSession(),
    val isHubConnecting: Boolean = false,
    val hubAuthError: String? = null,
    val canContinueOffline: Boolean = false,
    val showHubSettingsSheet: Boolean = false,
    val selectedTab: AppTab = AppTab.HOME,
    val homeFilter: HomeFilter = HomeFilter.ALL,
    val libraryFilter: LibraryFilter = LibraryFilter.ALBUMS,
    val searchQuery: String = "",
    val activeDetail: DetailTarget? = null,
    val isNowPlayingOpen: Boolean = false,
    val sleepTimerRemainingSec: Int? = null,
    val statusMessage: String? = null,
    val trackForCardSheet: TrackEntity? = null,
    val trackForPlaylistSheet: TrackEntity? = null,
    val showCreatePlaylistDialog: Boolean = false,
    val showAddStreamDialog: Boolean = false,
    val showEqSheet: Boolean = false,
    val showSleepTimerSheet: Boolean = false,
    val showQueueSheet: Boolean = false
)

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val hubConfigStore = HubConfigStore(application.applicationContext)
    private val coordinator = PlaybackCoordinator.getInstance(application.applicationContext)

    private val repository = MusicRepository(
        context = application.applicationContext,
        dao = AppDatabase.getInstance(application.applicationContext).musicDao()
    )

    val allTracks: StateFlow<List<TrackEntity>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<AlbumSummary>> = repository.allTracks
        .map { buildAlbumSummaries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTracks: StateFlow<List<TrackEntity>> = repository.favoriteTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<TrackEntity>> = repository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistTrackMap: StateFlow<Map<Long, List<TrackEntity>>> = combine(
        repository.allTracks,
        repository.allPlaylistRefs
    ) { tracks, refs ->
        val trackById = tracks.associateBy { it.id }
        refs.groupBy { it.playlistId }
            .mapValues { (_, items) ->
                items.sortedBy { it.addedAt }.mapNotNull { trackById[it.trackId] }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val playbackState: StateFlow<SharedPlaybackState> = coordinator.playbackState
    val engineState: StateFlow<PlaybackEngineState> = coordinator.engineState

    private val _uiState = MutableStateFlow(
        PlayerUiState(hubSession = hubConfigStore.loadSession())
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var sleepTimerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.allTracks.collect { tracks ->
                coordinator.syncTracksFromDb(tracks)
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(selectedTab = tab, activeDetail = null) }
    }

    fun selectHomeFilter(filter: HomeFilter) {
        _uiState.update { it.copy(homeFilter = filter) }
    }

    fun selectLibraryFilter(filter: LibraryFilter) {
        _uiState.update { it.copy(libraryFilter = filter) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openDetail(target: DetailTarget) {
        _uiState.update { it.copy(activeDetail = target) }
    }

    fun closeDetail() {
        _uiState.update { it.copy(activeDetail = null) }
    }

    fun openNowPlaying() {
        if (playbackState.value.currentTrack != null) {
            _uiState.update { it.copy(isNowPlayingOpen = true) }
        }
    }

    fun closeNowPlaying() {
        _uiState.update { it.copy(isNowPlayingOpen = false) }
    }

    fun playTrack(
        track: TrackEntity,
        contextTracks: List<TrackEntity> = allTracks.value,
        contextLabel: String = "Медиатека"
    ) {
        coordinator.playTrack(track, contextTracks, contextLabel)
    }

    fun playPlaylist(
        tracks: List<TrackEntity>,
        contextLabel: String,
        shuffle: Boolean = false
    ) {
        if (!coordinator.playPlaylist(tracks, contextLabel, shuffle)) {
            showStatus("В этом списке пока нет треков")
        }
    }

    fun togglePlayPause() {
        coordinator.togglePlayPause(allTracks.value)
    }

    fun seekTo(ms: Long) {
        coordinator.seekTo(ms)
    }

    fun seekBy(deltaMs: Long) {
        coordinator.seekBy(deltaMs)
    }

    fun skipNext() {
        coordinator.skipNext()
    }

    fun skipPrevious() {
        coordinator.skipPrevious()
    }

    fun toggleShuffle() {
        coordinator.toggleShuffle()
    }

    fun cycleRepeatMode() {
        coordinator.cycleRepeatMode()
    }

    fun toggleFavorite(track: TrackEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(track.id, track.isFavorite)
            showStatus(
                if (!track.isFavorite) "Добавлено в «Любимые треки»"
                else "Удалено из «Любимых треков»"
            )
        }
    }

    fun importAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = repository.importAudioUris(uris)
            if (imported.isNotEmpty()) {
                showStatus("Добавлено с устройства: ${imported.size}")
                playTrack(imported.first(), imported + allTracks.value, "Аудио с устройства")
            }
        }
    }

    fun addStreamUrl(title: String, artist: String, url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            showStatus("Укажите ссылку http:// или https://")
            return
        }
        viewModelScope.launch {
            val created = repository.addStreamTrack(title, artist, trimmed)
            _uiState.update { it.copy(showAddStreamDialog = false) }
            showStatus("Поток «${created.title}» добавлен")
            playTrack(created, listOf(created) + allTracks.value, "Онлайн-поток")
        }
    }

    fun createPlaylist(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name, description)
            _uiState.update { it.copy(showCreatePlaylistDialog = false) }
            showStatus("Плейлист «${name.trim()}» создан")
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist.id)
            _uiState.update { it.copy(activeDetail = null) }
            showStatus("Плейлист «${playlist.name}» удалён")
        }
    }

    fun addTrackToPlaylist(playlist: PlaylistEntity, track: TrackEntity) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlist.id, track.id)
            _uiState.update { it.copy(trackForPlaylistSheet = null) }
            showStatus("Добавлено в «${playlist.name}»")
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, track: TrackEntity) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, track.id)
            showStatus("Трек убран из плейлиста")
        }
    }

    fun deleteTrack(track: TrackEntity) {
        viewModelScope.launch {
            if (playbackState.value.currentTrack?.id == track.id) {
                coordinator.stopAndClear()
                _uiState.update { it.copy(isNowPlayingOpen = false) }
            }
            repository.deleteTrack(track.id)
            showStatus("Трек «${track.title}» удалён")
        }
    }

    fun selectEqPreset(preset: EqPreset) {
        coordinator.setEqPreset(preset)
        _uiState.update { it.copy(showEqSheet = false) }
        showStatus("Эквалайзер: ${preset.label}")
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _uiState.update { it.copy(sleepTimerRemainingSec = null, showSleepTimerSheet = false) }
            showStatus("Таймер сна отключён")
            return
        }
        val totalSeconds = minutes * 60
        _uiState.update { it.copy(sleepTimerRemainingSec = totalSeconds, showSleepTimerSheet = false) }
        showStatus("Таймер сна: $minutes мин.")
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                _uiState.update { it.copy(sleepTimerRemainingSec = remaining) }
            }
            coordinator.pause()
            _uiState.update { it.copy(sleepTimerRemainingSec = null) }
            showStatus("Воспроизведение остановлено по таймеру сна")
        }
    }

    fun connectToHub(rawUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isHubConnecting = true,
                    hubAuthError = null,
                    canContinueOffline = false
                )
            }
            when (val result = HubConnectionService.connectAndSync(rawUrl, username, password)) {
                is HubConnectResult.Connected -> {
                    val importedCount = repository.replaceHubTracks(result.importedTracks)
                    val session = HubSession(
                        hubUrl = result.normalizedUrl,
                        username = username.trim(),
                        password = password.trim(),
                        accessToken = result.accessToken,
                        protocol = result.protocol,
                        isAuthorized = true,
                        isConnectedOnline = true
                    )
                    hubConfigStore.saveSession(session)
                    val msg = if (importedCount > 0) {
                        "Подключено (${result.protocol.displayName}) • треков: $importedCount"
                    } else {
                        "Подключено к Hub: ${session.displayHost}"
                    }
                    _uiState.update {
                        it.copy(
                            hubSession = session,
                            isHubConnecting = false,
                            hubAuthError = null,
                            canContinueOffline = false,
                            showHubSettingsSheet = false,
                            statusMessage = msg
                        )
                    }
                }

                is HubConnectResult.AuthFailed -> {
                    _uiState.update {
                        it.copy(
                            isHubConnecting = false,
                            hubAuthError = result.message,
                            canContinueOffline = false
                        )
                    }
                }

                is HubConnectResult.Unreachable -> {
                    _uiState.update {
                        it.copy(
                            isHubConnecting = false,
                            hubAuthError = result.message,
                            canContinueOffline = true
                        )
                    }
                }

                is HubConnectResult.InvalidUrl -> {
                    _uiState.update {
                        it.copy(
                            isHubConnecting = false,
                            hubAuthError = result.message,
                            canContinueOffline = false
                        )
                    }
                }
            }
        }
    }

    fun continueHubOffline(rawUrl: String, username: String, password: String) {
        val normalized = HubConnectionService.normalizeHubUrl(rawUrl)
        if (normalized == null) {
            _uiState.update { it.copy(hubAuthError = "Введите корректный адрес Hub") }
            return
        }
        val session = HubSession(
            hubUrl = normalized,
            username = username.trim(),
            password = password.trim(),
            isAuthorized = true,
            isConnectedOnline = false
        )
        hubConfigStore.saveSession(session)
        _uiState.update {
            it.copy(
                hubSession = session,
                isHubConnecting = false,
                hubAuthError = null,
                canContinueOffline = false,
                statusMessage = "Hub сохранён: ${session.displayHost}"
            )
        }
    }

    fun syncCurrentHub() {
        val session = _uiState.value.hubSession
        if (session.hubUrl.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isHubConnecting = true) }
            when (val result = HubConnectionService.connectAndSync(
                session.hubUrl,
                session.username,
                session.password
            )) {
                is HubConnectResult.Connected -> {
                    val count = repository.replaceHubTracks(result.importedTracks)
                    val updated = session.copy(
                        accessToken = result.accessToken,
                        protocol = result.protocol,
                        isConnectedOnline = true
                    )
                    hubConfigStore.saveSession(updated)
                    _uiState.update {
                        it.copy(
                            hubSession = updated,
                            isHubConnecting = false,
                            showHubSettingsSheet = false,
                            statusMessage = if (count > 0) {
                                "Синхронизировано с Hub • треков: $count"
                            } else {
                                "Связь с ${updated.displayHost} активна"
                            }
                        )
                    }
                }

                is HubConnectResult.AuthFailed -> {
                    _uiState.update {
                        it.copy(isHubConnecting = false, statusMessage = result.message)
                    }
                }

                is HubConnectResult.Unreachable -> {
                    val updated = session.copy(isConnectedOnline = false)
                    hubConfigStore.saveSession(updated)
                    _uiState.update {
                        it.copy(
                            hubSession = updated,
                            isHubConnecting = false,
                            statusMessage = result.message
                        )
                    }
                }

                is HubConnectResult.InvalidUrl -> {
                    _uiState.update {
                        it.copy(isHubConnecting = false, statusMessage = result.message)
                    }
                }
            }
        }
    }

    fun disconnectHub() {
        coordinator.stopAndClear()
        hubConfigStore.clearAuthorization()
        val current = _uiState.value.hubSession
        viewModelScope.launch {
            repository.clearHubTracks()
        }
        _uiState.update {
            it.copy(
                hubSession = current.copy(isAuthorized = false, isConnectedOnline = false, accessToken = ""),
                showHubSettingsSheet = false,
                isNowPlayingOpen = false,
                activeDetail = null,
                hubAuthError = null,
                canContinueOffline = false
            )
        }
    }

    fun setShowHubSettingsSheet(show: Boolean) {
        _uiState.update { it.copy(showHubSettingsSheet = show) }
    }

    fun setShowCreatePlaylistDialog(show: Boolean) {
        _uiState.update { it.copy(showCreatePlaylistDialog = show) }
    }

    fun setShowAddStreamDialog(show: Boolean) {
        _uiState.update { it.copy(showAddStreamDialog = show) }
    }

    fun setTrackForCardSheet(track: TrackEntity?) {
        _uiState.update { it.copy(trackForCardSheet = track) }
    }

    fun setTrackForPlaylistSheet(track: TrackEntity?) {
        _uiState.update { it.copy(trackForPlaylistSheet = track) }
    }

    fun setShowEqSheet(show: Boolean) {
        _uiState.update { it.copy(showEqSheet = show) }
    }

    fun setShowSleepTimerSheet(show: Boolean) {
        _uiState.update { it.copy(showSleepTimerSheet = show) }
    }

    fun setShowQueueSheet(show: Boolean) {
        _uiState.update { it.copy(showQueueSheet = show) }
    }

    fun consumeStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
        coordinator.engine.clearError()
    }

    private fun showStatus(msg: String) {
        _uiState.update { it.copy(statusMessage = msg) }
    }

    override fun onCleared() {
        super.onCleared()
        sleepTimerJob?.cancel()
    }
}
