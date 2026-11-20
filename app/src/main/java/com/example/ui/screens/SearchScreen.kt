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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.PlaybackEngineState
import com.example.data.model.AlbumSummary
import com.example.data.model.TrackEntity
import com.example.ui.components.RealTrackArtwork
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.PulseGreen
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SearchScreen(
    query: String,
    allTracks: List<TrackEntity>,
    albums: List<AlbumSummary>,
    authHeaders: Map<String, String>,
    currentTrack: TrackEntity?,
    engineState: PlaybackEngineState,
    onQueryChange: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>, String) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onOpenTrackCard: (TrackEntity) -> Unit,
    bottomInsetPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val availableGenres = remember(allTracks) {
        allTracks.map { it.genre.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(12)
    }

    val filteredTracks = remember(query, allTracks) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else allTracks.filter { track ->
            track.title.lowercase().contains(q) ||
                track.artist.lowercase().contains(q) ||
                track.album.lowercase().contains(q) ||
                track.genre.lowercase().contains(q)
        }
    }

    val filteredAlbums = remember(query, albums) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else albums.filter { alb ->
            alb.title.lowercase().contains(q) || alb.artist.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = bottomInsetPadding.calculateBottomPadding() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.nav_search),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        color = Color(0xFF47524C)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = ObsidianDeep
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = ObsidianDeep
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF3F5F4),
                    unfocusedContainerColor = Color(0xFFF3F5F4),
                    focusedTextColor = ObsidianDeep,
                    unfocusedTextColor = ObsidianDeep,
                    focusedBorderColor = PulseGreen,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = ObsidianDeep
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("search_input_field")
            )
        }

        if (query.isBlank()) {
            if (availableGenres.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Жанры в вашей медиатеке",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableGenres) { genre ->
                                Surface(
                                    onClick = { onQueryChange(genre) },
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.border(0.75.dp, SubtleBorder, CircleShape)
                                ) {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (albums.isNotEmpty()) {
                item {
                    Text(
                        text = "Альбомы",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
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
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(ElevatedCard)
                                            .clickable { onOpenAlbum(album.key) }
                                            .padding(10.dp)
                                    ) {
                                        RealTrackArtwork(
                                            coverUrl = album.coverUrl,
                                            localArtPath = album.localArtPath,
                                            authHeaders = authHeaders,
                                            contentDescription = album.title,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(132.dp),
                                            cornerRadius = 10.dp,
                                            placeholderIconSize = 34.dp
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
                                            text = album.artist,
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
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = PulseGreen,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Поиск по вашей музыке",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Загрузите треки из Hub или импортируйте аудио с устройства, чтобы искать по названиям, альбомам и жанрам.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else if (filteredTracks.isEmpty() && filteredAlbums.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MusicOff,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = stringResource(R.string.search_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.search_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            if (filteredAlbums.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Альбомы",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredAlbums, key = { it.key }) { album ->
                                Column(
                                    modifier = Modifier
                                        .width(126.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenAlbum(album.key) }
                                ) {
                                    RealTrackArtwork(
                                        coverUrl = album.coverUrl,
                                        localArtPath = album.localArtPath,
                                        authHeaders = authHeaders,
                                        contentDescription = album.title,
                                        modifier = Modifier.size(126.dp),
                                        cornerRadius = 12.dp,
                                        placeholderIconSize = 30.dp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = album.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = album.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (filteredTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "Треки (${filteredTracks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                items(filteredTracks, key = { it.id }) { track ->
                    TrackRowItem(
                        track = track,
                        authHeaders = authHeaders,
                        isCurrent = currentTrack?.id == track.id,
                        isPlaying = engineState.isPlaying,
                        visualizerLevels = engineState.visualizerLevels,
                        onPlayClick = { onPlayTrack(track, filteredTracks, "Поиск: $query") },
                        onFavoriteClick = { onToggleFavorite(track) },
                        onOpenCardClick = { onOpenTrackCard(track) },
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}
