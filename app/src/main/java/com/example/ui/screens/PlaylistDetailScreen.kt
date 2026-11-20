package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.PlaybackEngineState
import com.example.data.model.PlaylistEntity
import com.example.data.model.TrackEntity
import com.example.ui.components.BlurredCoverBackdrop
import com.example.ui.components.LikedSongsArtworkBox
import com.example.ui.components.RealTrackArtwork
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.AccentOcean
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.PulseGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.rememberCoverColorPalette

@Composable
fun PlaylistDetailScreen(
    title: String,
    subtitle: String,
    coverUrl: String?,
    localArtPath: String?,
    authHeaders: Map<String, String>,
    isLikedCollection: Boolean,
    isLocalCollection: Boolean,
    customPlaylist: PlaylistEntity?,
    tracks: List<TrackEntity>,
    currentTrack: TrackEntity?,
    engineState: PlaybackEngineState,
    onBack: () -> Unit,
    onPlayAll: (shuffle: Boolean) -> Unit,
    onPlayTrack: (TrackEntity) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onOpenTrackCard: (TrackEntity) -> Unit,
    onRemoveFromPlaylist: ((TrackEntity) -> Unit)?,
    onDeletePlaylist: (() -> Unit)?,
    onImportAudioClick: () -> Unit,
    bottomInsetPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val effectiveCoverUrl = coverUrl ?: tracks.firstNotNullOfOrNull { it.coverUrl }
    val effectiveLocalArt = localArtPath ?: tracks.firstNotNullOfOrNull { it.localArtPath }

    val palette = rememberCoverColorPalette(
        coverUrl = effectiveCoverUrl,
        localArtPath = effectiveLocalArt,
        authHeaders = authHeaders
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Blurred dynamic background harmonized with the album/playlist cover art
        BlurredCoverBackdrop(
            coverUrl = effectiveCoverUrl,
            localArtPath = effectiveLocalArt,
            palette = palette,
            authHeaders = authHeaders,
            blurRadius = 48.dp,
            imageAlpha = 0.28f,
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = bottomInsetPadding.calculateBottomPadding() + 24.dp
            )
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("playlist_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = TextPrimary
                        )
                    }
                    Row {
                        if (isLocalCollection) {
                            IconButton(onClick = onImportAudioClick) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOpen,
                                    contentDescription = stringResource(R.string.action_import_audio),
                                    tint = PulseGreen
                                )
                            }
                        }
                        if (customPlaylist != null && onDeletePlaylist != null) {
                            IconButton(
                                onClick = onDeletePlaylist,
                                modifier = Modifier.testTag("delete_playlist_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Удалить плейлист",
                                    tint = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        isLikedCollection -> LikedSongsArtworkBox(
                            modifier = Modifier
                                .size(200.dp)
                                .shadow(20.dp, RoundedCornerShape(20.dp)),
                            cornerRadius = 20.dp,
                            iconSize = 68.dp
                        )
                        isLocalCollection -> Box(
                            modifier = Modifier
                                .size(200.dp)
                                .shadow(20.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(AccentOcean, PulseGreen))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AudioFile,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(68.dp)
                            )
                        }
                        else -> RealTrackArtwork(
                            coverUrl = effectiveCoverUrl,
                            localArtPath = effectiveLocalArt,
                            authHeaders = authHeaders,
                            contentDescription = title,
                            cornerRadius = 20.dp,
                            placeholderIconSize = 60.dp,
                            modifier = Modifier
                                .size(200.dp)
                                .shadow(
                                    elevation = 22.dp,
                                    shape = RoundedCornerShape(20.dp),
                                    ambientColor = palette.accent,
                                    spotColor = palette.accent
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$subtitle • ${tracks.size} треков",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = { onPlayAll(true) },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("playlist_shuffle_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = stringResource(R.string.cd_shuffle),
                                    tint = PulseGreen
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            onClick = { onPlayAll(false) },
                            shape = CircleShape,
                            color = PulseGreen,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("playlist_play_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.cd_play),
                                    tint = ObsidianDeep,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (tracks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Список пока пуст",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isLocalCollection) {
                                "Импортируйте аудиофайлы из памяти смартфона."
                            } else {
                                "Добавьте треки из вашей медиатеки или Hub."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(tracks, key = { "detail_${it.id}" }) { track ->
                    TrackRowItem(
                        track = track,
                        authHeaders = authHeaders,
                        isCurrent = currentTrack?.id == track.id,
                        isPlaying = engineState.isPlaying,
                        visualizerLevels = engineState.visualizerLevels,
                        activeTint = palette.accent,
                        onPlayClick = { onPlayTrack(track) },
                        onFavoriteClick = { onToggleFavorite(track) },
                        onOpenCardClick = { onOpenTrackCard(track) },
                        trailingExtra = if (onRemoveFromPlaylist != null) {
                            {
                                IconButton(onClick = { onRemoveFromPlaylist(track) }) {
                                    Icon(
                                        imageVector = Icons.Filled.RemoveCircleOutline,
                                        contentDescription = "Убрать",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}
