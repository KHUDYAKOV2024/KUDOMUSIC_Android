package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.PlaybackEngineState
import com.example.data.model.EqPreset
import com.example.data.model.RepeatMode
import com.example.data.model.TrackEntity
import com.example.data.model.formatDuration
import com.example.ui.components.BlurredCoverBackdrop
import com.example.ui.components.LiveEqualizerBars
import com.example.ui.components.RealTrackArtwork
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.PulseGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.rememberCoverColorPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(
    track: TrackEntity,
    authHeaders: Map<String, String>,
    contextLabel: String,
    engineState: PlaybackEngineState,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    eqPreset: EqPreset,
    sleepTimerRemainingSec: Int?,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val palette = rememberCoverColorPalette(
        coverUrl = track.coverUrl,
        localArtPath = track.localArtPath,
        authHeaders = authHeaders
    )
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Dismiss swipe-down animation state
    val dismissOffsetY = remember { Animatable(0f) }
    // Horizontal artwork swipe-to-skip state
    val artSwipeX = remember { Animatable(0f) }
    // Gesture feedback overlay ("+10с", "-10с", "♥")
    var gestureHudLabel by remember { mutableStateOf<String?>(null) }

    val triggerHud: (String) -> Unit = { label ->
        scope.launch {
            gestureHudLabel = label
            delay(650L)
            if (gestureHudLabel == label) gestureHudLabel = null
        }
    }

    val artworkScale by animateFloatAsState(
        targetValue = if (engineState.isPlaying) 1.0f else 0.93f,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 260f),
        label = "artwork_play_scale"
    )

    val duration = engineState.durationMs.coerceAtLeast(track.durationMs).coerceAtLeast(1L)
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val sliderValue = if (isDraggingSlider) {
        dragFraction
    } else {
        (engineState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }
    val displayedPositionMs = if (isDraggingSlider) {
        (dragFraction * duration).toLong()
    } else {
        engineState.positionMs
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dismissOffsetY.value
                alpha = (1f - (dismissOffsetY.value / 1100f)).coerceIn(0.45f, 1f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f || dismissOffsetY.value > 0f) {
                            scope.launch {
                                dismissOffsetY.snapTo((dismissOffsetY.value + dragAmount).coerceAtLeast(0f))
                            }
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (dismissOffsetY.value > 180f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClose()
                                dismissOffsetY.snapTo(0f)
                            } else {
                                dismissOffsetY.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 420f))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dismissOffsetY.animateTo(0f, spring()) }
                    }
                )
            }
    ) {
        // Blurred real cover art + harmonious dynamic light color gradient
        BlurredCoverBackdrop(
            coverUrl = track.coverUrl,
            localArtPath = track.localArtPath,
            palette = palette,
            authHeaders = authHeaders,
            blurRadius = 64.dp,
            imageAlpha = 0.34f,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Subtle drag handle pill
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.32f))
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("now_playing_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.now_playing_context),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = contextLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onOpenAddToPlaylist,
                    modifier = Modifier.testTag("now_playing_add_playlist_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlaylistAdd,
                        contentDescription = stringResource(R.string.add_to_playlist_title),
                        tint = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Interactive gesture-powered album cover art
            // Swipe left/right = skip track, Double-tap left/right = -10s/+10s, Double-tap center = favorite
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(1f)
                    .offset { IntOffset(artSwipeX.value.roundToInt(), 0) }
                    .scale(artworkScale)
                    .pointerInput(track.id) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                scope.launch {
                                    artSwipeX.snapTo((artSwipeX.value + dragAmount * 0.65f).coerceIn(-160f, 160f))
                                }
                            },
                            onDragEnd = {
                                val dx = artSwipeX.value
                                scope.launch {
                                    if (dx < -85f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSkipNext()
                                    } else if (dx > 85f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSkipPrevious()
                                    }
                                    artSwipeX.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 380f))
                                }
                            },
                            onDragCancel = {
                                scope.launch { artSwipeX.animateTo(0f, spring()) }
                            }
                        )
                    }
                    .pointerInput(track.id) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val third = size.width / 3f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                when {
                                    offset.x < third -> {
                                        onSeekBy(-10_000L)
                                        triggerHud("-10 сек")
                                    }
                                    offset.x > third * 2f -> {
                                        onSeekBy(10_000L)
                                        triggerHud("+10 сек")
                                    }
                                    else -> {
                                        onToggleFavorite()
                                        triggerHud(if (track.isFavorite) "Убрано" else "♥")
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = track,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.95f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.95f)) },
                    label = "now_playing_artwork_anim"
                ) { displayedTrack ->
                    RealTrackArtwork(
                        coverUrl = displayedTrack.coverUrl,
                        localArtPath = displayedTrack.localArtPath,
                        authHeaders = authHeaders,
                        contentDescription = displayedTrack.title,
                        cornerRadius = 22.dp,
                        placeholderIconSize = 72.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(
                                elevation = 26.dp,
                                shape = RoundedCornerShape(22.dp),
                                ambientColor = palette.accent,
                                spotColor = palette.accent
                            )
                    )
                }

                // Floating glass HUD feedback on double-tap gestures
                androidx.compose.animation.AnimatedVisibility(
                    visible = gestureHudLabel != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.85f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.62f)
                    ) {
                        Text(
                            text = gestureHudLabel.orEmpty(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = PulseGreen,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Track title, artist, equalizer bars & favorite toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        LiveEqualizerBars(
                            levels = engineState.visualizerLevels,
                            isPlaying = engineState.isPlaying,
                            barColor = PulseGreen,
                            maxHeight = 16.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(track.artist)
                            if (track.album.isNotBlank()) {
                                append(" • ")
                                append(track.album)
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleFavorite()
                    },
                    modifier = Modifier.testTag("now_playing_favorite_button")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (track.isFavorite) PulseGreen else TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive scrubber slider
            Slider(
                value = sliderValue,
                onValueChange = { value ->
                    isDraggingSlider = true
                    dragFraction = value
                },
                onValueChangeFinished = {
                    val targetMs = (dragFraction * duration).toLong()
                    isDraggingSlider = false
                    onSeekTo(targetMs)
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("now_playing_seek_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(displayedPositionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = if (engineState.durationMs > 0L || track.durationMs > 0L) {
                        formatDuration(duration)
                    } else {
                        "LIVE"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Playback deck
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleShuffle()
                    },
                    modifier = Modifier.testTag("now_playing_shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.cd_shuffle),
                        tint = if (isShuffleEnabled) PulseGreen else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSkipPrevious()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("now_playing_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_previous),
                        tint = TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTogglePlayPause()
                    },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .size(74.dp)
                        .testTag("now_playing_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (engineState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                color = ObsidianDeep,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (engineState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (engineState.isPlaying) R.string.cd_pause else R.string.cd_play
                                ),
                                tint = ObsidianDeep,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSkipNext()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("now_playing_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.cd_next),
                        tint = TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCycleRepeat()
                    },
                    modifier = Modifier.testTag("now_playing_repeat_button")
                ) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = stringResource(R.string.cd_repeat),
                        tint = if (repeatMode != RepeatMode.OFF) PulseGreen else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom utility bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onOpenEq,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier.testTag("now_playing_eq_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = if (eqPreset != EqPreset.FLAT) PulseGreen else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = eqPreset.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (eqPreset != EqPreset.FLAT) PulseGreen else TextPrimary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenSleepTimer,
                        modifier = Modifier.testTag("now_playing_timer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = stringResource(R.string.sleep_timer_title),
                            tint = if (sleepTimerRemainingSec != null) PulseGreen else TextMuted
                        )
                    }
                    IconButton(
                        onClick = onOpenQueue,
                        modifier = Modifier.testTag("now_playing_queue_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QueueMusic,
                            contentDescription = stringResource(R.string.queue_title),
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
