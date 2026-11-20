package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TrackSourceType {
    HUB_STREAM,
    LOCAL_URI
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class EqPreset(val label: String, val subtitle: String) {
    FLAT("Обычный", "Сбалансированное чистое звучание"),
    BASS_BOOST("Глубокий бас", "Усиленный низ для электроники и хип-хопа"),
    VOCAL("Чистый вокал", "Подчёркнутая середина и детализация голоса"),
    NIGHT("Ночной режим", "Мягкое комфортное звучание на низкой громкости")
}

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val remoteId: String = "",
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String = "",
    val trackNumber: Int = 0,
    val year: Int = 0,
    val durationMs: Long,
    val sourceType: String = TrackSourceType.HUB_STREAM.name,
    val uriString: String = "",
    val coverUrl: String? = null,
    val localArtPath: String? = null,
    val genre: String = "",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val addedAt: Long = System.currentTimeMillis()
) {
    val parsedSourceType: TrackSourceType
        get() = TrackSourceType.entries.firstOrNull { it.name == sourceType } ?: TrackSourceType.HUB_STREAM

    val artworkKey: String?
        get() = localArtPath?.takeIf { it.isNotBlank() } ?: coverUrl?.takeIf { it.isNotBlank() }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val coverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

data class AlbumSummary(
    val key: String,
    val title: String,
    val artist: String,
    val year: Int,
    val coverUrl: String?,
    val localArtPath: String?,
    val tracks: List<TrackEntity>
)

fun buildAlbumSummaries(tracks: List<TrackEntity>): List<AlbumSummary> {
    return tracks
        .filter { it.album.isNotBlank() }
        .groupBy { track ->
            val idPart = track.albumId.ifBlank { "${track.album.lowercase()}|${track.artist.lowercase()}" }
            idPart
        }
        .map { (key, albumTracks) ->
            val sorted = albumTracks.sortedWith(
                compareBy<TrackEntity> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
            val first = sorted.first()
            AlbumSummary(
                key = key,
                title = first.album,
                artist = first.artist,
                year = sorted.maxOfOrNull { it.year } ?: 0,
                coverUrl = sorted.firstNotNullOfOrNull { it.coverUrl?.takeIf { u -> u.isNotBlank() } },
                localArtPath = sorted.firstNotNullOfOrNull { it.localArtPath?.takeIf { p -> p.isNotBlank() } },
                tracks = sorted
            )
        }
        .sortedByDescending { it.tracks.maxOfOrNull { t -> t.addedAt } ?: 0L }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
