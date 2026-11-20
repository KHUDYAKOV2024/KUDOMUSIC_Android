package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import com.example.data.model.EqPreset
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

data class PlaybackEngineState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val visualizerLevels: List<Float> = listOf(0.22f, 0.35f, 0.28f, 0.4f, 0.22f),
    val errorMessage: String? = null
)

class AudioPlaybackEngine(
    private val context: Context,
    private val onTrackCompleted: () -> Unit,
    private val onPlaybackStateChanged: () -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackEngineState())
    val state: StateFlow<PlaybackEngineState> = _state.asStateFlow()

    private var currentTrack: TrackEntity? = null
    private var currentHeaders: Map<String, String> = emptyMap()
    private var eqPreset: EqPreset = EqPreset.FLAT

    private var mediaPlayer: MediaPlayer? = null
    private var mediaEqualizer: Equalizer? = null
    private var mediaBassBoost: BassBoost? = null
    private var progressJob: Job? = null

    fun playTrack(track: TrackEntity, authHeaders: Map<String, String> = emptyMap()) {
        stopInternal()
        currentTrack = track
        currentHeaders = authHeaders
        val isRemote = track.parsedSourceType == TrackSourceType.HUB_STREAM

        _state.update {
            it.copy(
                isPlaying = false,
                isBuffering = isRemote,
                positionMs = 0L,
                durationMs = track.durationMs,
                errorMessage = null
            )
        }
        onPlaybackStateChanged()

        try {
            val uri = Uri.parse(track.uriString)
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                if (isRemote && authHeaders.isNotEmpty()) {
                    setDataSource(context, uri, authHeaders)
                } else if (isRemote) {
                    setDataSource(track.uriString)
                } else {
                    setDataSource(context, uri)
                }

                setOnPreparedListener { player ->
                    val realDuration = player.duration.toLong().coerceAtLeast(track.durationMs)
                    attachEffects(player.audioSessionId)
                    player.start()
                    _state.update {
                        it.copy(
                            isPlaying = true,
                            isBuffering = false,
                            durationMs = realDuration,
                            positionMs = 0L,
                            errorMessage = null
                        )
                    }
                    onPlaybackStateChanged()
                    startProgressPolling()
                }
                setOnCompletionListener {
                    _state.update { it.copy(isPlaying = false) }
                    onPlaybackStateChanged()
                    onTrackCompleted()
                }
                setOnErrorListener { _, _, _ ->
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            errorMessage = if (isRemote) {
                                "Не удалось воспроизвести трек с Hub. Проверьте соединение."
                            } else {
                                "Аудиофайл на устройстве недоступен."
                            }
                        )
                    }
                    onPlaybackStateChanged()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (_: Exception) {
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "Не удалось открыть источник аудио"
                )
            }
            onPlaybackStateChanged()
        }
    }

    fun togglePlayPause() {
        val track = currentTrack ?: return
        val mp = mediaPlayer ?: run {
            playTrack(track, currentHeaders)
            return
        }
        if (_state.value.isBuffering) return
        try {
            if (mp.isPlaying) {
                mp.pause()
                _state.update { it.copy(isPlaying = false) }
            } else {
                mp.start()
                _state.update { it.copy(isPlaying = true) }
            }
            onPlaybackStateChanged()
        } catch (_: IllegalStateException) {
            playTrack(track, currentHeaders)
        }
    }

    fun pause() {
        if (!_state.value.isPlaying) return
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: IllegalStateException) {
        }
        _state.update { it.copy(isPlaying = false) }
        onPlaybackStateChanged()
    }

    fun seekTo(targetMs: Long) {
        val maxDur = _state.value.durationMs.coerceAtLeast(1L)
        val clamped = targetMs.coerceIn(0L, maxDur)
        try {
            val mp = mediaPlayer
            if (mp != null && !_state.value.isBuffering && mp.duration > 0) {
                mp.seekTo(clamped.toInt())
                _state.update { it.copy(positionMs = clamped) }
            }
        } catch (_: IllegalStateException) {
        }
    }

    fun seekBy(deltaMs: Long) {
        val target = (_state.value.positionMs + deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun setEqPreset(preset: EqPreset) {
        eqPreset = preset
        applyEffects()
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun stop() {
        stopInternal()
        currentTrack = null
        _state.update {
            it.copy(isPlaying = false, isBuffering = false, positionMs = 0L, durationMs = 0L)
        }
        onPlaybackStateChanged()
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var phase = 0.0
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null && _state.value.isPlaying) {
                    try {
                        val pos = mp.currentPosition.toLong().coerceAtLeast(0L)
                        val dur = mp.duration.toLong().coerceAtLeast(0L)
                        phase += 0.38
                        val levels = List(5) { idx ->
                            val raw = 0.28 + 0.52 * abs(sin(phase + idx * 0.85))
                            raw.toFloat().coerceIn(0.16f, 1.0f)
                        }
                        _state.update {
                            it.copy(
                                positionMs = pos,
                                durationMs = if (dur > 0L) dur else it.durationMs,
                                visualizerLevels = levels
                            )
                        }
                    } catch (_: IllegalStateException) {
                    }
                }
                delay(180L)
            }
        }
    }

    private fun attachEffects(audioSessionId: Int) {
        releaseEffects()
        try {
            mediaEqualizer = Equalizer(0, audioSessionId).apply { enabled = true }
            mediaBassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
            applyEffects()
        } catch (_: Exception) {
        }
    }

    private fun applyEffects() {
        try {
            val eq = mediaEqualizer
            if (eq != null) {
                val bands = eq.numberOfBands.toInt()
                val minLevel = eq.bandLevelRange[0].toInt()
                val maxLevel = eq.bandLevelRange[1].toInt()
                for (band in 0 until bands) {
                    val fraction = if (bands > 1) band.toFloat() / (bands - 1) else 0.5f
                    val gainFactor = when (eqPreset) {
                        EqPreset.FLAT -> 0f
                        EqPreset.BASS_BOOST -> (1f - fraction) * 0.72f - 0.1f
                        EqPreset.VOCAL -> if (fraction in 0.3f..0.75f) 0.55f else -0.12f
                        EqPreset.NIGHT -> if (fraction > 0.6f) -0.45f else 0.08f
                    }
                    val level = (gainFactor * (maxLevel - minLevel) / 2f).toInt()
                        .coerceIn(minLevel, maxLevel)
                    eq.setBandLevel(band.toShort(), level.toShort())
                }
            }
            val boost = mediaBassBoost
            if (boost != null && boost.strengthSupported) {
                val strength: Short = when (eqPreset) {
                    EqPreset.BASS_BOOST -> 780
                    EqPreset.NIGHT -> 240
                    else -> 0
                }
                boost.setStrength(strength)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopInternal() {
        progressJob?.cancel()
        progressJob = null
        releaseEffects()
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    private fun releaseEffects() {
        try {
            mediaEqualizer?.release()
        } catch (_: Exception) {
        }
        try {
            mediaBassBoost?.release()
        } catch (_: Exception) {
        }
        mediaEqualizer = null
        mediaBassBoost = null
    }
}
