package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import com.example.data.model.formatDuration
import com.example.ui.theme.CoverColorPalette
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.ElevatedCardHighlight
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PulseGreen
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@Composable
fun RealTrackArtwork(
    coverUrl: String?,
    localArtPath: String?,
    authHeaders: Map<String, String> = emptyMap(),
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    placeholderIconSize: Dp = 24.dp
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(cornerRadius)
    val localFile = localArtPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
    val modelData: Any? = localFile ?: coverUrl?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(ElevatedCardHighlight, ElevatedCard)
                )
            )
            .border(0.75.dp, Color.White.copy(alpha = 0.08f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (modelData != null) {
            val request = ImageRequest.Builder(context)
                .data(modelData)
                .crossfade(280)
                .apply {
                    if (modelData is String) {
                        authHeaders.forEach { (k, v) -> addHeader(k, v) }
                    }
                }
                .build()

            SubcomposeAsyncImage(
                model = request,
                contentDescription = contentDescription ?: stringResource(R.string.cd_artwork),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    MinimalArtworkPlaceholder(iconSize = placeholderIconSize)
                },
                error = {
                    MinimalArtworkPlaceholder(iconSize = placeholderIconSize)
                }
            )
        } else {
            MinimalArtworkPlaceholder(iconSize = placeholderIconSize)
        }
    }
}

@Composable
private fun MinimalArtworkPlaceholder(iconSize: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF222926),
                        Color(0xFF141816)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Ambient blurred backdrop tinted lightly with the cover's extracted color palette.
 */
@Composable
fun BlurredCoverBackdrop(
    coverUrl: String?,
    localArtPath: String?,
    palette: CoverColorPalette,
    authHeaders: Map<String, String> = emptyMap(),
    blurRadius: Dp = 54.dp,
    imageAlpha: Float = 0.30f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localFile = localArtPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
    val modelData: Any? = localFile ?: coverUrl?.takeIf { it.isNotBlank() }

    Box(modifier = modifier.background(ObsidianBg)) {
        if (modelData != null) {
            val request = ImageRequest.Builder(context)
                .data(modelData)
                .crossfade(450)
                .apply {
                    if (modelData is String) {
                        authHeaders.forEach { (k, v) -> addHeader(k, v) }
                    }
                }
                .build()

            SubcomposeAsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius)
                    .alpha(imageAlpha)
            )
        }

        // Light harmonious color gradient veil derived from the cover artwork
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.softTop,
                            palette.softMid,
                            ObsidianBg.copy(alpha = 0.92f),
                            ObsidianBg
                        )
                    )
                )
        )
    }
}

@Composable
fun LiveEqualizerBars(
    levels: List<Float>,
    isPlaying: Boolean,
    barColor: Color = PulseGreen,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 18.dp
) {
    val safeLevels = if (levels.size >= 3) levels.take(4) else listOf(0.3f, 0.6f, 0.45f, 0.7f)
    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        safeLevels.forEachIndexed { index, level ->
            val target = if (isPlaying) level.coerceIn(0.18f, 1.0f) else 0.22f + (index % 2) * 0.1f
            val animated by animateFloatAsState(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 320f),
                label = "eq_bar_$index"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(maxHeight * animated)
                    .clip(CircleShape)
                    .background(barColor)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRowItem(
    track: TrackEntity,
    authHeaders: Map<String, String>,
    isCurrent: Boolean,
    isPlaying: Boolean,
    visualizerLevels: List<Float>,
    activeTint: Color = PulseGreen,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onOpenCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingExtra: (@Composable () -> Unit)? = null
) {
    val sourceLabel = when (track.parsedSourceType) {
        TrackSourceType.LOCAL_URI -> stringResource(R.string.source_device)
        TrackSourceType.HUB_STREAM -> "Hub"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isCurrent) activeTint.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onPlayClick,
                onLongClick = onOpenCardClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("track_row_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp)) {
            RealTrackArtwork(
                coverUrl = track.coverUrl,
                localArtPath = track.localArtPath,
                authHeaders = authHeaders,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 10.dp,
                placeholderIconSize = 22.dp
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.50f)),
                    contentAlignment = Alignment.Center
                ) {
                    LiveEqualizerBars(
                        levels = visualizerLevels,
                        isPlaying = isPlaying,
                        barColor = PulseGreen,
                        maxHeight = 18.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) PulseGreen else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (track.parsedSourceType == TrackSourceType.HUB_STREAM) PulseGreen else TextSecondary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = buildString {
                        append(track.artist)
                        if (track.durationMs > 0L) {
                            append(" • ")
                            append(formatDuration(track.durationMs))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.testTag("track_fav_${track.id}")
        ) {
            Icon(
                imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(R.string.cd_favorite),
                tint = if (track.isFavorite) PulseGreen else TextMuted,
                modifier = Modifier.size(21.dp)
            )
        }

        if (trailingExtra != null) {
            trailingExtra()
        } else {
            IconButton(
                onClick = onOpenCardClick,
                modifier = Modifier.testTag("track_card_menu_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.cd_more),
                    tint = TextSecondary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
fun LikedSongsArtworkBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    iconSize: Dp = 26.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF4F46E5), Color(0xFF1DB954))
                )
            )
            .border(0.75.dp, SubtleBorder, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = stringResource(R.string.liked_songs_title),
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}
