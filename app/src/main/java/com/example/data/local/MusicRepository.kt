package com.example.data.local

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.hub.HubRemoteTrack
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistTrackCrossRef
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MusicRepository(
    private val context: Context,
    private val dao: MusicDao
) {
    val allTracks: Flow<List<TrackEntity>> = dao.observeAllTracks()
    val favoriteTracks: Flow<List<TrackEntity>> = dao.observeFavoriteTracks()
    val recentlyPlayed: Flow<List<TrackEntity>> = dao.observeRecentlyPlayed()
    val allPlaylists: Flow<List<PlaylistEntity>> = dao.observeAllPlaylists()
    val allPlaylistRefs: Flow<List<PlaylistTrackCrossRef>> = dao.observeAllPlaylistRefs()

    suspend fun replaceHubTracks(remoteTracks: List<HubRemoteTrack>): Int = withContext(Dispatchers.IO) {
        if (remoteTracks.isEmpty()) return@withContext 0
        dao.clearHubTracks()
        val now = System.currentTimeMillis()
        val entities = remoteTracks
            .filter { it.streamUrl.isNotBlank() }
            .mapIndexed { idx, remote ->
                TrackEntity(
                    remoteId = remote.remoteId,
                    title = remote.title,
                    artist = remote.artist,
                    album = remote.album,
                    albumId = remote.albumId,
                    trackNumber = remote.trackNumber,
                    year = remote.year,
                    durationMs = remote.durationMs,
                    sourceType = TrackSourceType.HUB_STREAM.name,
                    uriString = remote.streamUrl,
                    coverUrl = remote.coverUrl,
                    localArtPath = null,
                    genre = remote.genre,
                    addedAt = now - idx
                )
            }
        if (entities.isNotEmpty()) {
            dao.insertTracks(entities)
        }
        entities.size
    }

    suspend fun clearHubTracks() = withContext(Dispatchers.IO) {
        dao.clearHubTracks()
    }

    suspend fun importAudioUris(uris: List<Uri>): List<TrackEntity> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<TrackEntity>()
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        val existingUris = dao.getExistingUris().toHashSet()

        uris.forEachIndexed { index, uri ->
            val uriStr = uri.toString()
            if (uriStr in existingUris) return@forEachIndexed
            existingUris.add(uriStr)

            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }

            var title = resolveDisplayName(uri)
            var artist = "Неизвестный исполнитель"
            var album = "Аудио с устройства"
            var genre = ""
            var durationMs = 0L
            var year = 0
            var trackNumber = 0
            var localArtPath: String? = null

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { title = it.trim() }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { artist = it.trim() }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { album = it.trim() }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { genre = it.trim() }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?.let { durationMs = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    ?.toIntOrNull()
                    ?.let { year = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore("/")
                    ?.toIntOrNull()
                    ?.let { trackNumber = it }

                val embeddedPicture = retriever.embeddedPicture
                if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                    val coverFile = File(coversDir, "art_${UUID.randomUUID()}.jpg")
                    coverFile.writeBytes(embeddedPicture)
                    localArtPath = coverFile.absolutePath
                }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }

            val entity = TrackEntity(
                title = title,
                artist = artist,
                album = album,
                albumId = "${album.lowercase()}|${artist.lowercase()}",
                trackNumber = trackNumber,
                year = year,
                durationMs = durationMs,
                sourceType = TrackSourceType.LOCAL_URI.name,
                uriString = uriStr,
                coverUrl = null,
                localArtPath = localArtPath,
                genre = genre,
                addedAt = System.currentTimeMillis() + index
            )
            val id = dao.insertTrack(entity)
            imported.add(entity.copy(id = id))
        }
        imported
    }

    suspend fun addStreamTrack(
        title: String,
        artist: String,
        streamUrl: String
    ): TrackEntity = withContext(Dispatchers.IO) {
        val cleanUrl = streamUrl.trim()
        val entity = TrackEntity(
            title = title.trim().ifBlank { "Аудиопоток" },
            artist = artist.trim().ifBlank { "Hub Поток" },
            album = "Онлайн-потоки",
            albumId = "custom_streams",
            durationMs = 0L,
            sourceType = TrackSourceType.HUB_STREAM.name,
            uriString = cleanUrl,
            coverUrl = null,
            localArtPath = null,
            genre = "Поток",
            addedAt = System.currentTimeMillis()
        )
        val id = dao.insertTrack(entity)
        entity.copy(id = id)
    }

    suspend fun toggleFavorite(trackId: Long, currentFavorite: Boolean) {
        dao.setFavorite(trackId, !currentFavorite)
    }

    suspend fun recordPlay(trackId: Long) {
        dao.recordPlay(trackId, System.currentTimeMillis())
    }

    suspend fun deleteTrack(trackId: Long) {
        dao.deleteTrack(trackId)
    }

    suspend fun createPlaylist(name: String, description: String, coverUrl: String? = null): Long {
        return dao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                description = description.trim().ifBlank { "Пользовательский плейлист" },
                coverUrl = coverUrl,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.clearPlaylistRefs(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        dao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        dao.removeTrackFromPlaylist(playlistId, trackId)
    }

    private fun resolveDisplayName(uri: Uri): String {
        var name: String? = null
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        name = cursor.getString(idx)
                    }
                }
            }
        } catch (_: Exception) {
        }
        val raw = name ?: uri.lastPathSegment ?: "Аудиотрек"
        return raw.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Аудиотрек" }
    }
}
