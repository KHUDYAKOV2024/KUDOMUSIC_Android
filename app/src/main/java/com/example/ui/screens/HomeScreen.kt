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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.PlaybackEngineState
import com.example.data.hub.HubSession
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
import com.example.ui.theme.rememberCoverColorPalette
import com.example.ui.viewmodel.DetailTarget
import com.example.ui.viewmodel.HomeFilter
import java.util.Calendar

@Composable
fun HomeScreen(
    hubSession: HubSession,
    authHeaders: Map<String, String>,
    allTracks: List<TrackEntity>,
    albums: List<AlbumSummary>,
    favoriteTracks: List<TrackEntity>,
    recentlyPlayed: List<TrackEntity>,
    playlists: List<PlaylistEntity>,
    selectedFilter: HomeFilter,
    currentTrack: TrackEntity?,
    engineState: PlaybackEngineState,
    onSelectFilter: (HomeFilter) -> Unit,
    onOpenDetail: (DetailTarget) -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>, String) -> Unit,
    onPlayAlbum: (AlbumSummary) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onOpenTrackCard: (TrackEntity) -> Unit,
    onImportAudioClick: () -> Unit,
    onAddStreamClick: () -> Unit,
    onSyncHubClick: () -> Unit,
    onOpenHubSettings: () -> Unit,
    bottomInsetPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val greetingRes = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            in 18..22 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    }

    val palette = rememberCoverColorPalette(
        coverUrl = currentTrack?.coverUrl ?: albums.firstOrNull()?.coverUrl,
        localArtPath = currentTrack?.localArtPath ?: albums.firstOrNull()?.localArtPath,
        authHeaders = authHeaders
    )

    val filteredTracks = remember(allTracks, selectedFilter) {
        when (selectedFilter) {
            HomeFilter.ALL -> allTracks
            HomeFilter.HUB -> allTracks.filter { it.parsedSourceType == TrackSourceType.HUB_STREAM }
            HomeFilter.LOCAL -> allTracks.filter { it.parsedSourceType == TrackSourceType.LOCAL_URI }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Light ambient atmosphere matching active cover art
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.softTop.copy(alpha = 0.42f),
                            palette.softMid.copy(alpha = 0.14f),
                            ObsidianBg.copy(alpha = 0f)
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 14.dp,
                bottom = bottomInsetPadding.calculateBottomPadding() + 22.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Brand wordmark + Live Hub connection pill
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium.copy(
                            letterSpacing = 1.6.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = PulseGreen
                    )
                    Surface(
                        onClick = onOpenHubSettings,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .height(36.dp)
                            .border(0.75.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                            .testTag("home_hub_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (hubSession.isConnectedOnline) PulseGreen else Color(0xFFE09F3E))
                            )
                            Icon(
                                imageVector = Icons.Outlined.Hub,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = hubSession.displayHost,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Greeting + Quick actions
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(greetingRes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = onImportAudioClick,
                            shape = CircleShape,
                            color = PulseGreen,
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("home_import_audio_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = ObsidianDeep,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.action_import_audio),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ObsidianDeep
                                )
                            }
                        }
                        Surface(
                            onClick = onAddStreamClick,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("home_add_stream_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.AddLink,
                                    contentDescription = stringResource(R.string.action_add_stream),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Filter chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = listOf(
                        HomeFilter.ALL to "Все",
                        HomeFilter.HUB to "Из Hub",
                        HomeFilter.LOCAL to "С телефона"
                    )
                    items(chips) { (filter, label) ->
                        val selected = selectedFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectFilter(filter) },
                            label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PulseGreen,
                                selectedLabelColor = ObsidianDeep,
                                containerColor = Color.White.copy(alpha = 0.08f),
                                labelColor = TextPrimary
                            ),
                            border = null,
                            modifier = Modifier.testTag("home_filter_${filter.name}")
                        )
                    }
                }
            }

            // Quick-pick 2-column cards (Liked + Local + up to 4 real albums)
            item {
                val quickAlbums = albums.take(4)
                val quickCards = buildList {
                    add(QuickItem("liked", stringResource(R.string.liked_songs_title), isLiked = true))
                    add(QuickItem("local", stringResource(R.string.local_files_title), isLocal = true))
                    quickAlbums.forEach { album ->
                        add(
                            QuickItem(
                                id = "alb_${album.key}",
                                title = album.title,
                                coverUrl = album.coverUrl,
                                localArtPath = album.localArtPath,
                                albumKey = album.key
                            )
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickCards.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { card ->
                                QuickPickGlassCard(
                                    item = card,
                                    authHeaders = authHeaders,
                                    onClick = {
                                        when {
                                            card.isLiked -> onOpenDetail(DetailTarget.LikedSongs)
                                            card.isLocal -> onOpenDetail(DetailTarget.LocalAudio)
                                            card.albumKey != null -> onOpenDetail(DetailTarget.Album(card.albumKey))
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Real Albums horizontal carousel
            if (albums.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Альбомы",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(albums, key = { it.key }) { album ->
                                AlbumCarouselCard(
                                    album = album,
                                    authHeaders = authHeaders,
                                    onOpen = { onOpenDetail(DetailTarget.Album(album.key)) },
                                    onQuickPlay = { onPlayAlbum(album) }
                                )
                            }
                        }
                    }
                }
            }

            // Recently played strip
            if (recentlyPlayed.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.section_recent),
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentlyPlayed.take(8), key = { "recent_${it.id}" }) { track ->
                                RecentTrackTile(
                                    track = track,
                                    authHeaders = authHeaders,
                                    isCurrent = currentTrack?.id == track.id,
                                    onClick = { onPlayTrack(track, recentlyPlayed, "Недавно прослушано") }
                                )
                            }
                        }
                    }
                }
            }

            // Main tracks section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when (selectedFilter) {
                            HomeFilter.ALL -> stringResource(R.string.section_all_tracks)
                            HomeFilter.HUB -> "Медиатека Hub"
                            HomeFilter.LOCAL -> "Музыка с телефона"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    if (filteredTracks.isNotEmpty()) {
                        Text(
                            text = "${filteredTracks.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (filteredTracks.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ElevatedCard.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .border(1.dp, SubtleBorder, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Hub,
                                contentDescription = null,
                                tint = PulseGreen,
                                modifier = Modifier.size(38.dp)
                            )
                            Text(
                                text = if (selectedFilter == HomeFilter.LOCAL) {
                                    "На устройстве пока нет импортированных треков"
                                } else {
                                    "Медиатека пуста"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Синхронизируйте треки с подключённого Hub (${hubSession.displayHost}) или выберите аудиофайлы из памяти смартфона.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onSyncHubClick,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PulseGreen,
                                        contentColor = ObsidianDeep
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Обновить Hub")
                                }
                                OutlinedButton(
                                    onClick = onImportAudioClick,
                                    shape = CircleShape,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder)
                                ) {
                                    Text("С телефона", color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredTracks, key = { it.id }) { track ->
                    TrackRowItem(
                        track = track,
                        authHeaders = authHeaders,
                        isCurrent = currentTrack?.id == track.id,
                        isPlaying = engineState.isPlaying,
                        visualizerLevels = engineState.visualizerLevels,
                        activeTint = palette.accent,
                        onPlayClick = { onPlayTrack(track, filteredTracks, "Главная") },
                        onFavoriteClick = { onToggleFavorite(track) },
                        onOpenCardClick = { onOpenTrackCard(track) },
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}

private data class QuickItem(
    val id: String,
    val title: String,
    val isLiked: Boolean = false,
    val isLocal: Boolean = false,
    val coverUrl: String? = null,
    val localArtPath: String? = null,
    val albumKey: String? = null
)

@Composable
private fun QuickPickGlassCard(
    item: QuickItem,
    authHeaders: Map<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.White.copy(alpha = 0.07f),
        modifier = modifier
            .height(58.dp)
            .border(0.75.dp, Color.White.copy(alpha = 0.09f), shape)
            .testTag("quick_pick_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                item.isLiked -> LikedSongsArtworkBox(
                    modifier = Modifier.size(58.dp),
                    cornerRadius = 0.dp,
                    iconSize = 23.dp
                )
                item.isLocal -> Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(Brush.linearGradient(listOf(AccentOcean, PulseGreen))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AudioFile,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }
                else -> RealTrackArtwork(
                    coverUrl = item.coverUrl,
                    localArtPath = item.localArtPath,
                    authHeaders = authHeaders,
                    contentDescription = item.title,
                    modifier = Modifier.size(58.dp),
                    cornerRadius = 0.dp,
                    placeholderIconSize = 22.dp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun AlbumCarouselCard(
    album: AlbumSummary,
    authHeaders: Map<String, String>,
    onOpen: () -> Unit,
    onQuickPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .testTag("album_card_${album.key}")
    ) {
        Box(modifier = Modifier.size(156.dp)) {
            RealTrackArtwork(
                coverUrl = album.coverUrl,
                localArtPath = album.localArtPath,
                authHeaders = authHeaders,
                contentDescription = album.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 16.dp,
                placeholderIconSize = 40.dp
            )
            Surface(
                onClick = onQuickPlay,
                shape = CircleShape,
                color = PulseGreen,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play),
                        tint = ObsidianDeep,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
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

@Composable
private fun RecentTrackTile(
    track: TrackEntity,
    authHeaders: Map<String, String>,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(118.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        RealTrackArtwork(
            coverUrl = track.coverUrl,
            localArtPath = track.localArtPath,
            authHeaders = authHeaders,
            contentDescription = track.title,
            modifier = Modifier.size(118.dp),
            cornerRadius = 12.dp,
            placeholderIconSize = 30.dp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (isCurrent) PulseGreen else TextPrimary,
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
}
