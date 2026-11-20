package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.PlaybackEngineState
import com.example.data.model.AlbumSummary
import com.example.data.model.PlaylistEntity
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import com.example.ui.components.LikedSongsArtworkBox
import com.example.ui.components.RealTrackArtwork
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.AccentOcean
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.ElevatedCardHighlight
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.PulseGreen
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DetailTarget
import com.example.ui.viewmodel.LibraryFilter

@Composable
fun LibraryScreen(
    allTracks: List<TrackEntity>,
    albums: List<AlbumSummary>,
    favoriteTracks: List<TrackEntity>,
    playlists: List<PlaylistEntity>,
    playlistTrackMap: Map<Long, List<TrackEntity>>,
    authHeaders: Map<String, String>,
    selectedFilter: LibraryFilter,
    currentTrack: TrackEntity?,
    engineState: PlaybackEngineState,
    onSelectFilter: (LibraryFilter) -> Unit,
    onOpenDetail: (DetailTarget) -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>, String) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onOpenTrackCard: (TrackEntity) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onImportAudioClick: () -> Unit,
    onAddStreamClick: () -> Unit,
    bottomInsetPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val localCount = remember(allTracks) {
        allTracks.count { it.parsedSourceType == TrackSourceType.LOCAL_URI }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = bottomInsetPadding.calculateBottomPadding() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onImportAudioClick,
                        modifier = Modifier.testTag("library_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = stringResource(R.string.action_import_audio),
                            tint = PulseGreen
                        )
                    }
                    IconButton(
                        onClick = onAddStreamClick,
                        modifier = Modifier.testTag("library_add_stream_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddLink,
                            contentDescription = stringResource(R.string.action_add_stream),
                            tint = TextPrimary
                        )
                    }
                    Surface(
                        onClick = onCreatePlaylistClick,
                        shape = CircleShape,
                        color = ElevatedCardHighlight,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("library_create_playlist_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.action_create_playlist),
                                tint = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    LibraryFilter.ALBUMS to "Альбомы",
                    LibraryFilter.PLAYLISTS to "Плейлисты",
                    LibraryFilter.TRACKS to "Все треки"
                )
                items(filters) { (filter, label) ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onSelectFilter(filter) },
                        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PulseGreen,
                            selectedLabelColor = ObsidianDeep,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = TextPrimary
                        ),
                        border = null
                    )
                }
            }
        }

        // Pinned Liked & Device Audio rows
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenDetail(DetailTarget.LikedSongs) }
                        .padding(8.dp)
                        .testTag("library_liked_songs_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LikedSongsArtworkBox(
                        modifier = Modifier.size(56.dp),
                        cornerRadius = 12.dp,
                        iconSize = 24.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.liked_songs_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${favoriteTracks.size} треков",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenDetail(DetailTarget.LocalAudio) }
                        .padding(8.dp)
                        .testTag("library_local_audio_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(AccentOcean, PulseGreen))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AudioFile,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.local_files_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "С телефона • $localCount треков",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        when (selectedFilter) {
            LibraryFilter.ALBUMS -> {
                if (albums.isEmpty()) {
                    item {
                        EmptyLibraryBanner(
                            title = "Альбомов пока нет",
                            subtitle = "Подключите Hub или импортируйте аудиофайлы с устройства — альбомы соберутся автоматически."
                        )
                    }
                } else {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            albums.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    pair.forEach { album ->
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(ElevatedCard)
                                                .border(0.75.dp, SubtleBorder, RoundedCornerShape(16.dp))
                                                .clickable { onOpenDetail(DetailTarget.Album(album.key)) }
                                                .padding(10.dp)
                                        ) {
                                            RealTrackArtwork(
                                                coverUrl = album.coverUrl,
                                                localArtPath = album.localArtPath,
                                                authHeaders = authHeaders,
                                                contentDescription = album.title,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(140.dp),
                                                cornerRadius = 12.dp,
                                                placeholderIconSize = 36.dp
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = album.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${album.artist} • ${album.tracks.size} тр.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            LibraryFilter.PLAYLISTS -> {
                if (playlists.isEmpty()) {
                    item {
                        EmptyLibraryBanner(
                            title = "Плейлистов пока нет",
                            subtitle = "Нажмите «+» вверху, чтобы создать свой первый плейлист."
                        )
                    }
                } else {
                    items(playlists, key = { "lib_pl_${it.id}" }) { playlist ->
                        val plTracks = playlistTrackMap[playlist.id].orEmpty()
                        val firstTrack = plTracks.firstOrNull()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenDetail(DetailTarget.CustomPlaylist(playlist.id)) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RealTrackArtwork(
                                coverUrl = playlist.coverUrl ?: firstTrack?.coverUrl,
                                localArtPath = firstTrack?.localArtPath,
                                authHeaders = authHeaders,
                                contentDescription = playlist.name,
                                modifier = Modifier.size(58.dp),
                                cornerRadius = 12.dp,
                                placeholderIconSize = 24.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Плейлист • ${plTracks.size} треков",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            LibraryFilter.TRACKS -> {
                if (allTracks.isEmpty()) {
                    item {
                        EmptyLibraryBanner(
                            title = "Треков пока нет",
                            subtitle = "Обновите Hub или импортируйте музыку с телефона."
                        )
                    }
                } else {
                    items(allTracks, key = { "lib_tr_${it.id}" }) { track ->
                        TrackRowItem(
                            track = track,
                            authHeaders = authHeaders,
                            isCurrent = currentTrack?.id == track.id,
                            isPlaying = engineState.isPlaying,
                            visualizerLevels = engineState.visualizerLevels,
                            onPlayClick = { onPlayTrack(track, allTracks, "Моя медиатека") },
                            onFavoriteClick = { onToggleFavorite(track) },
                            onOpenCardClick = { onOpenTrackCard(track) },
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryBanner(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ElevatedCard,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(0.75.dp, SubtleBorder, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
