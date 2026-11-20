package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.PlaybackEngineState
import com.example.data.model.TrackEntity
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.PulseGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.rememberCoverColorPalette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MiniPlayerBar(
    track: TrackEntity,
    authHeaders: Map<String, String>,
    engineState: PlaybackEngineState,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = rememberCoverColorPalette(
        coverUrl = track.coverUrl,
        localArtPath = track.localArtPath,
        authHeaders = authHeaders
    )
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }

    val progress = if (engineState.durationMs > 0L) {
        (engineState.positionMs.toFloat() / engineState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .offset { IntOffset(dragX.value.roundToInt(), 0) }
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            dragX.snapTo((dragX.value + dragAmount * 0.55f).coerceIn(-120f, 120f))
                        }
                    },
                    onDragEnd = {
                        val offset = dragX.value
                        scope.launch {
                            if (offset < -65f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkipNext()
                            } else if (offset > 65f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkipPrevious()
                            }
                            dragX.animateTo(0f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f))
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragX.animateTo(0f, animationSpec = spring())
                        }
                    }
                )
            }
            .pointerInput(track.id) {
                var verticalSum = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        verticalSum += dragAmount
                    },
                    onDragEnd = {
                        if (verticalSum < -42f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenNowPlaying()
                        }
                        verticalSum = 0f
                    },
                    onDragCancel = { verticalSum = 0f }
                )
            }
            .clickable(onClick = onOpenNowPlaying)
            .testTag("mini_player_bar"),
        color = ElevatedCard.copy(alpha = 0.92f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            palette.softTop.copy(alpha = 0.58f),
                            palette.surfaceTint.copy(alpha = 0.34f),
                            ElevatedCard.copy(alpha = 0.94f)
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RealTrackArtwork(
                        coverUrl = track.coverUrl,
                        localArtPath = track.localArtPath,
                        authHeaders = authHeaders,
                        contentDescription = track.title,
                        modifier = Modifier.size(46.dp),
                        cornerRadius = 10.dp,
                        placeholderIconSize = 20.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
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
                        modifier = Modifier.testTag("mini_player_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.cd_favorite),
                            tint = if (track.isFavorite) PulseGreen else TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTogglePlayPause()
                        },
                        modifier = Modifier.testTag("mini_player_play_pause_button")
                    ) {
                        if (engineState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = PulseGreen,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (engineState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (engineState.isPlaying) R.string.cd_pause else R.string.cd_play
                                ),
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = PulseGreen,
                    trackColor = Color.White.copy(alpha = 0.14f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}
