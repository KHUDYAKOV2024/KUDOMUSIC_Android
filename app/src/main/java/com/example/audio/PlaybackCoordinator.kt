package com.example.audio

import android.content.Context
import com.example.data.hub.HubConfigStore
import com.example.data.local.AppDatabase
import com.example.data.model.EqPreset
import com.example.data.model.RepeatMode
import com.example.data.model.TrackEntity
import com.example.widget.KudoMusicWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SharedPlaybackState(
    val currentTrack: TrackEntity? = null,
    val contextLabel: String = "KUDOMUSIC",
    val queue: List<TrackEntity> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val eqPreset: EqPreset = EqPreset.FLAT
)

class PlaybackCoordinator private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao = AppDatabase.getInstance(appContext).musicDao()
    private val hubStore = HubConfigStore(appContext)

    private val _playbackState = MutableStateFlow(SharedPlaybackState())
    val playbackState: StateFlow<SharedPlaybackState> = _playbackState.asStateFlow()

    val engine: AudioPlaybackEngine = AudioPlaybackEngine(
        context = appContext,
        onTrackCompleted = { handleTrackCompleted() },
        onPlaybackStateChanged = { notifyWidgets() }
    )

    val engineState: StateFlow<PlaybackEngineState> = engine.state

    fun playTrack(
        track: TrackEntity,
        contextTracks: List<TrackEntity>,
        contextLabel: String
    ) {
        val list = if (contextTracks.isEmpty()) listOf(track) else contextTracks
        val queue = if (_playbackState.value.isShuffleEnabled && list.size > 1) {
            listOf(track) + list.filter { it.id != track.id }.shuffled()
        } else {
            list
        }
        val idx = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = queue,
                currentQueueIndex = idx,
                contextLabel = contextLabel
            )
        }
        val headers = hubStore.loadSession().buildAuthHeaders()
        engine.playTrack(track, headers)
        recordPlayAsync(track.id)
        notifyWidgets()
    }

    fun playPlaylist(
        tracks: List<TrackEntity>,
        contextLabel: String,
        shuffle: Boolean
    ): Boolean {
        if (tracks.isEmpty()) return false
        val ordered = if (shuffle) tracks.shuffled() else tracks
        val first = ordered.first()
        _playbackState.update {
            it.copy(
                isShuffleEnabled = shuffle,
                currentTrack = first,
                queue = ordered,
                currentQueueIndex = 0,
                contextLabel = contextLabel
            )
        }
        val headers = hubStore.loadSession().buildAuthHeaders()
        engine.playTrack(first, headers)
        recordPlayAsync(first.id)
        notifyWidgets()
        return true
    }

    fun togglePlayPause(fallbackTracks: List<TrackEntity> = emptyList()) {
        val current = _playbackState.value.currentTrack
        if (current == null) {
            val first = fallbackTracks.firstOrNull() ?: return
            playTrack(first, fallbackTracks, "Медиатека")
        } else {
            engine.togglePlayPause()
        }
    }

    fun pause() {
        engine.pause()
    }

    fun stopAndClear() {
        engine.stop()
        _playbackState.update {
            it.copy(currentTrack = null, queue = emptyList(), currentQueueIndex = -1)
        }
        notifyWidgets()
    }

    fun seekTo(ms: Long) {
        engine.seekTo(ms)
    }

    fun seekBy(deltaMs: Long) {
        engine.seekBy(deltaMs)
    }

    fun skipNext() {
        val state = _playbackState.value
        val queue = state.queue
        if (queue.isEmpty()) return
        val nextIdx = if (state.currentQueueIndex + 1 < queue.size) state.currentQueueIndex + 1 else 0
        val nextTrack = queue[nextIdx]
        _playbackState.update { it.copy(currentTrack = nextTrack, currentQueueIndex = nextIdx) }
        engine.playTrack(nextTrack, hubStore.loadSession().buildAuthHeaders())
        recordPlayAsync(nextTrack.id)
        notifyWidgets()
    }

    fun skipPrevious() {
        if (engineState.value.positionMs > 3_500L) {
            engine.seekTo(0L)
            return
        }
        val state = _playbackState.value
        val queue = state.queue
        if (queue.isEmpty()) return
        val prevIdx = if (state.currentQueueIndex - 1 >= 0) state.currentQueueIndex - 1 else queue.lastIndex
        val prevTrack = queue[prevIdx]
        _playbackState.update { it.copy(currentTrack = prevTrack, currentQueueIndex = prevIdx) }
        engine.playTrack(prevTrack, hubStore.loadSession().buildAuthHeaders())
        recordPlayAsync(prevTrack.id)
        notifyWidgets()
    }

    fun toggleShuffle() {
        _playbackState.update { state ->
            val newShuffle = !state.isShuffleEnabled
            val current = state.currentTrack
            val updatedQueue = if (newShuffle && current != null && state.queue.size > 1) {
                listOf(current) + state.queue.filter { it.id != current.id }.shuffled()
            } else {
                state.queue
            }
            val newIdx = if (current != null) {
                updatedQueue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
            } else {
                state.currentQueueIndex
            }
            state.copy(
                isShuffleEnabled = newShuffle,
                queue = updatedQueue,
                currentQueueIndex = newIdx
            )
        }
    }

    fun cycleRepeatMode() {
        _playbackState.update { state ->
            val next = when (state.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            state.copy(repeatMode = next)
        }
    }

    fun setEqPreset(preset: EqPreset) {
        engine.setEqPreset(preset)
        _playbackState.update { it.copy(eqPreset = preset) }
    }

    fun syncTracksFromDb(tracks: List<TrackEntity>) {
        val currentId = _playbackState.value.currentTrack?.id ?: return
        val updated = tracks.firstOrNull { it.id == currentId }
        if (updated != null) {
            _playbackState.update { state ->
                val updatedQueue = state.queue.map { q -> tracks.firstOrNull { it.id == q.id } ?: q }
                state.copy(currentTrack = updated, queue = updatedQueue)
            }
        }
    }

    private fun handleTrackCompleted() {
        val state = _playbackState.value
        val current = state.currentTrack ?: return
        when (state.repeatMode) {
            RepeatMode.ONE -> engine.playTrack(current, hubStore.loadSession().buildAuthHeaders())
            RepeatMode.ALL -> skipNext()
            RepeatMode.OFF -> {
                if (state.currentQueueIndex + 1 < state.queue.size) {
                    skipNext()
                }
            }
        }
    }

    private fun recordPlayAsync(trackId: Long) {
        scope.launch {
            runCatching { dao.recordPlay(trackId, System.currentTimeMillis()) }
        }
    }

    private fun notifyWidgets() {
        KudoMusicWidgetProvider.updateAllWidgets(appContext)
    }

    companion object {
        @Volatile
        private var instance: PlaybackCoordinator? = null

        fun getInstance(context: Context): PlaybackCoordinator {
            return instance ?: synchronized(this) {
                instance ?: PlaybackCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }
}
