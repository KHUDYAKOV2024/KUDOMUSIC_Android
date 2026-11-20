package com.example.data.hub

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

data class HubRemoteTrack(
    val remoteId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String,
    val trackNumber: Int,
    val year: Int,
    val genre: String,
    val streamUrl: String,
    val coverUrl: String?,
    val durationMs: Long
)

sealed interface HubConnectResult {
    data class Connected(
        val normalizedUrl: String,
        val protocol: HubProtocol,
        val accessToken: String,
        val importedTracks: List<HubRemoteTrack>
    ) : HubConnectResult

    data class AuthFailed(val message: String) : HubConnectResult
    data class Unreachable(val normalizedUrl: String, val message: String) : HubConnectResult
    data class InvalidUrl(val message: String) : HubConnectResult
}

object HubConnectionService {

    fun normalizeHubUrl(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            val isLocal = trimmed.startsWith("192.168.") ||
                trimmed.startsWith("10.") ||
                trimmed.startsWith("172.") ||
                trimmed.startsWith("127.") ||
                trimmed.startsWith("localhost", ignoreCase = true)
            if (isLocal) "http://$trimmed" else "https://$trimmed"
        }

        val uri = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
        val host = uri.host?.trim().orEmpty()
        if (host.isEmpty() || host.contains(" ")) return null
        return withScheme.trimEnd('/')
    }

    suspend fun connectAndSync(
        rawUrl: String,
        username: String,
        password: String
    ): HubConnectResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeHubUrl(rawUrl)
            ?: return@withContext HubConnectResult.InvalidUrl(
                "Введите корректный адрес Hub (например, http://192.168.1.10:4533 или https://music.example.com)"
            )

        val cleanUser = username.trim()
        val cleanPass = password.trim()

        // 1. Try Subsonic / OpenSubsonic / Navidrome API
        if (cleanUser.isNotEmpty()) {
            val subsonicResult = trySubsonicProtocol(baseUrl, cleanUser, cleanPass)
            if (subsonicResult != null) return@withContext subsonicResult
        }

        // 2. Try Jellyfin / Emby API
        if (cleanUser.isNotEmpty()) {
            val jellyfinResult = tryJellyfinProtocol(baseUrl, cleanUser, cleanPass)
            if (jellyfinResult != null) return@withContext jellyfinResult
        }

        // 3. Try Custom REST / JSON Hub API (login endpoints + tracks/albums endpoints + direct URL)
        tryGenericRestOrFeed(baseUrl, cleanUser, cleanPass)
    }

    private fun trySubsonicProtocol(
        baseUrl: String,
        username: String,
        password: String
    ): HubConnectResult? {
        val authQuery = buildSubsonicAuthQuery(username, password)
        val pingResp = httpGet("$baseUrl/rest/ping.view?$authQuery") ?: return null
        if (pingResp.code !in 200..299 || !pingResp.body.contains("subsonic-response")) {
            return null
        }

        val root = runCatching { JSONObject(pingResp.body).optJSONObject("subsonic-response") }.getOrNull()
            ?: return null
        val status = root.optString("status")
        if (status != "ok") {
            val errObj = root.optJSONObject("error")
            val errMsg = errObj?.optString("message")?.ifBlank { null }
                ?: "Неверный логин или пароль для Subsonic/Navidrome Hub"
            return HubConnectResult.AuthFailed(errMsg)
        }

        val collectedTracks = mutableListOf<HubRemoteTrack>()
        val seenIds = hashSetOf<String>()

        // Fetch albums first so albums & covers are rich and well-structured
        val albumsResp = httpGet("$baseUrl/rest/getAlbumList2.view?type=newest&size=24&$authQuery")
        val albumArray = albumsResp?.body?.let { body ->
            runCatching {
                JSONObject(body)
                    .optJSONObject("subsonic-response")
                    ?.optJSONObject("albumList2")
                    ?.optJSONArray("album")
            }.getOrNull()
        }

        if (albumArray != null) {
            for (i in 0 until minOf(albumArray.length(), 18)) {
                val albumObj = albumArray.optJSONObject(i) ?: continue
                val albumId = albumObj.optString("id")
                if (albumId.isBlank()) continue
                val detailResp = httpGet("$baseUrl/rest/getAlbum.view?id=${urlEncode(albumId)}&$authQuery")
                val songs = detailResp?.body?.let { body ->
                    runCatching {
                        JSONObject(body)
                            .optJSONObject("subsonic-response")
                            ?.optJSONObject("album")
                            ?.optJSONArray("song")
                    }.getOrNull()
                } ?: continue

                for (s in 0 until songs.length()) {
                    val song = songs.optJSONObject(s) ?: continue
                    val track = parseSubsonicSong(baseUrl, authQuery, song) ?: continue
                    if (seenIds.add(track.remoteId)) {
                        collectedTracks.add(track)
                    }
                }
            }
        }

        // Also fetch random songs if album list returned nothing
        if (collectedTracks.isEmpty()) {
            val randomResp = httpGet("$baseUrl/rest/getRandomSongs.view?size=40&$authQuery")
            val songs = randomResp?.body?.let { body ->
                runCatching {
                    JSONObject(body)
                        .optJSONObject("subsonic-response")
                        ?.optJSONObject("randomSongs")
                        ?.optJSONArray("song")
                }.getOrNull()
            }
            if (songs != null) {
                for (s in 0 until songs.length()) {
                    val song = songs.optJSONObject(s) ?: continue
                    val track = parseSubsonicSong(baseUrl, authQuery, song) ?: continue
                    if (seenIds.add(track.remoteId)) {
                        collectedTracks.add(track)
                    }
                }
            }
        }

        return HubConnectResult.Connected(
            normalizedUrl = baseUrl,
            protocol = HubProtocol.SUBSONIC,
            accessToken = "",
            importedTracks = collectedTracks
        )
    }

    private fun parseSubsonicSong(
        baseUrl: String,
        authQuery: String,
        song: JSONObject
    ): HubRemoteTrack? {
        val id = song.optString("id")
        if (id.isBlank()) return null
        val coverArtId = song.optString("coverArt")
        val coverUrl = if (coverArtId.isNotBlank()) {
            "$baseUrl/rest/getCoverArt.view?id=${urlEncode(coverArtId)}&size=600&$authQuery"
        } else null
        val streamUrl = "$baseUrl/rest/stream.view?id=${urlEncode(id)}&$authQuery"
        return HubRemoteTrack(
            remoteId = id,
            title = song.optString("title").ifBlank { "Без названия" },
            artist = song.optString("artist").ifBlank { "Неизвестный исполнитель" },
            album = song.optString("album").ifBlank { "Синглы" },
            albumId = song.optString("albumId"),
            trackNumber = song.optInt("track", 0),
            year = song.optInt("year", 0),
            genre = song.optString("genre"),
            streamUrl = streamUrl,
            coverUrl = coverUrl,
            durationMs = song.optLong("duration", 0L) * 1000L
        )
    }

    private fun buildSubsonicAuthQuery(username: String, password: String): String {
        val salt = UUID.randomUUID().toString().replace("-", "").take(8)
        val token = md5("$password$salt")
        return "u=${urlEncode(username)}&t=$token&s=$salt&v=1.16.1&c=KUDOMUSIC&f=json"
    }

    private fun tryJellyfinProtocol(
        baseUrl: String,
        username: String,
        password: String
    ): HubConnectResult? {
        val authHeader = "MediaBrowser Client=\"KUDOMUSIC\", Device=\"Android\", DeviceId=\"kudomusic-android\", Version=\"1.0.0\""
        val payload = JSONObject().apply {
            put("Username", username)
            put("Pw", password)
        }.toString()

        val authResp = httpPostJson(
            urlStr = "$baseUrl/Users/AuthenticateByName",
            jsonBody = payload,
            headers = mapOf("X-Emby-Authorization" to authHeader)
        ) ?: return null

        if (authResp.code == HttpURLConnection.HTTP_UNAUTHORIZED || authResp.code == HttpURLConnection.HTTP_FORBIDDEN) {
            return HubConnectResult.AuthFailed("Неверный логин или пароль для Jellyfin/Emby Hub")
        }
        if (authResp.code !in 200..299) return null

        val root = runCatching { JSONObject(authResp.body) }.getOrNull() ?: return null
        val accessToken = root.optString("AccessToken")
        val userId = root.optJSONObject("User")?.optString("Id").orEmpty()
        if (accessToken.isBlank() || userId.isBlank()) return null

        val itemsResp = httpGet(
            urlStr = "$baseUrl/Users/${urlEncode(userId)}/Items?IncludeItemTypes=Audio&Recursive=true&Fields=Genres,ProductionYear&SortBy=DateCreated&SortOrder=Descending&Limit=100",
            headers = mapOf("X-Emby-Token" to accessToken)
        )

        val tracks = mutableListOf<HubRemoteTrack>()
        val items = itemsResp?.body?.let { body ->
            runCatching { JSONObject(body).optJSONArray("Items") }.getOrNull()
        }

        if (items != null) {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("Id")
                if (id.isBlank()) continue
                val albumId = item.optString("AlbumId").ifBlank { id }
                val artistsArr = item.optJSONArray("Artists")
                val artist = item.optString("AlbumArtist").ifBlank {
                    if (artistsArr != null && artistsArr.length() > 0) artistsArr.optString(0) else "Неизвестный исполнитель"
                }
                val genresArr = item.optJSONArray("Genres")
                val genre = if (genresArr != null && genresArr.length() > 0) genresArr.optString(0) else ""
                val runTimeTicks = item.optLong("RunTimeTicks", 0L)
                val durationMs = if (runTimeTicks > 0L) runTimeTicks / 10_000L else 0L
                val coverUrl = "$baseUrl/Items/${urlEncode(albumId)}/Images/Primary?maxHeight=600&api_key=${urlEncode(accessToken)}"
                val streamUrl = "$baseUrl/Audio/${urlEncode(id)}/stream?static=true&api_key=${urlEncode(accessToken)}"

                tracks.add(
                    HubRemoteTrack(
                        remoteId = id,
                        title = item.optString("Name").ifBlank { "Без названия" },
                        artist = artist,
                        album = item.optString("Album").ifBlank { "Синглы" },
                        albumId = albumId,
                        trackNumber = item.optInt("IndexNumber", 0),
                        year = item.optInt("ProductionYear", 0),
                        genre = genre,
                        streamUrl = streamUrl,
                        coverUrl = coverUrl,
                        durationMs = durationMs
                    )
                )
            }
        }

        return HubConnectResult.Connected(
            normalizedUrl = baseUrl,
            protocol = HubProtocol.JELLYFIN,
            accessToken = accessToken,
            importedTracks = tracks
        )
    }

    private fun tryGenericRestOrFeed(
        baseUrl: String,
        username: String,
        password: String
    ): HubConnectResult {
        var bearerToken = ""
        val authHeaders = mutableMapOf<String, String>()

        // Optional REST login attempt if username & password provided
        if (username.isNotBlank() && password.isNotBlank()) {
            val loginPayload = JSONObject().apply {
                put("username", username)
                put("login", username)
                put("email", username)
                put("password", password)
            }.toString()

            val loginCandidates = listOf("$baseUrl/api/auth/login", "$baseUrl/api/login", "$baseUrl/auth/login")
            for (endpoint in loginCandidates) {
                val resp = httpPostJson(endpoint, loginPayload) ?: continue
                if (resp.code == HttpURLConnection.HTTP_UNAUTHORIZED || resp.code == HttpURLConnection.HTTP_FORBIDDEN) {
                    return HubConnectResult.AuthFailed("Hub отклонил логин или пароль (HTTP ${resp.code})")
                }
                if (resp.code in 200..299) {
                    val obj = runCatching { JSONObject(resp.body) }.getOrNull()
                    val foundToken = obj?.optString("token")?.ifBlank { null }
                        ?: obj?.optString("accessToken")?.ifBlank { null }
                        ?: obj?.optString("access_token")?.ifBlank { null }
                        ?: obj?.optJSONObject("data")?.optString("token")?.ifBlank { null }
                    if (!foundToken.isNullOrBlank()) {
                        bearerToken = foundToken
                        break
                    }
                }
            }

            if (bearerToken.isNotBlank()) {
                authHeaders["Authorization"] = "Bearer $bearerToken"
            } else {
                val basic = Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                authHeaders["Authorization"] = "Basic $basic"
            }
        } else if (password.isNotBlank()) {
            bearerToken = password
            authHeaders["Authorization"] = "Bearer $password"
        }

        // First test the exact entered URL
        val rootResp = httpGet(baseUrl, authHeaders)
            ?: return HubConnectResult.Unreachable(
                normalizedUrl = baseUrl,
                message = "Не удалось установить связь с Hub ($baseUrl). Проверьте адрес, порт и подключение к сети."
            )

        if (rootResp.code == HttpURLConnection.HTTP_UNAUTHORIZED || rootResp.code == HttpURLConnection.HTTP_FORBIDDEN) {
            return HubConnectResult.AuthFailed("Hub требует авторизацию (HTTP ${rootResp.code}). Проверьте логин и пароль.")
        }

        if (rootResp.code !in 200..399) {
            return HubConnectResult.Unreachable(
                normalizedUrl = baseUrl,
                message = "Сервер Hub вернул код HTTP ${rootResp.code}"
            )
        }

        val contentType = rootResp.contentType.lowercase()
        if (contentType.startsWith("audio/")) {
            val hostTitle = Uri.parse(baseUrl).lastPathSegment
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() } ?: "Аудиопоток Hub"
            return HubConnectResult.Connected(
                normalizedUrl = baseUrl,
                protocol = HubProtocol.DIRECT_FEED,
                accessToken = bearerToken,
                importedTracks = listOf(
                    HubRemoteTrack(
                        remoteId = baseUrl,
                        title = hostTitle,
                        artist = Uri.parse(baseUrl).host ?: "Hub",
                        album = "Поток Hub",
                        albumId = "hub_stream",
                        trackNumber = 1,
                        year = 0,
                        genre = "Поток",
                        streamUrl = baseUrl,
                        coverUrl = null,
                        durationMs = 0L
                    )
                )
            )
        }

        var parsedTracks = parseBodyForTracks(rootResp.body, contentType, baseUrl)

        // If root URL was an HTML web interface, probe common JSON music endpoints on the Hub
        if (parsedTracks.isEmpty()) {
            val apiPaths = listOf(
                "$baseUrl/api/tracks",
                "$baseUrl/api/songs",
                "$baseUrl/api/music",
                "$baseUrl/api/library",
                "$baseUrl/api/albums",
                "$baseUrl/tracks.json",
                "$baseUrl/playlist.m3u"
            )
            for (endpoint in apiPaths) {
                val resp = httpGet(endpoint, authHeaders) ?: continue
                if (resp.code in 200..299) {
                    val found = parseBodyForTracks(resp.body, resp.contentType.lowercase(), baseUrl)
                    if (found.isNotEmpty()) {
                        parsedTracks = found
                        break
                    }
                }
            }
        }

        return HubConnectResult.Connected(
            normalizedUrl = baseUrl,
            protocol = if (parsedTracks.isNotEmpty()) HubProtocol.REST_HUB else HubProtocol.DIRECT_FEED,
            accessToken = bearerToken,
            importedTracks = parsedTracks
        )
    }

    private fun parseBodyForTracks(body: String, contentType: String, baseUrl: String): List<HubRemoteTrack> {
        val trimmed = body.trim()
        return when {
            contentType.contains("json") || trimmed.startsWith("{") || trimmed.startsWith("[") ->
                parseJsonTracks(trimmed, baseUrl)
            contentType.contains("mpegurl") || trimmed.contains("#EXTM3U") ->
                parseM3uTracks(trimmed, baseUrl)
            else -> emptyList()
        }
    }

    private fun parseJsonTracks(body: String, baseUrl: String): List<HubRemoteTrack> {
        val result = mutableListOf<HubRemoteTrack>()
        val array: JSONArray = when {
            body.startsWith("[") -> runCatching { JSONArray(body) }.getOrNull()
            body.startsWith("{") -> {
                val root = runCatching { JSONObject(body) }.getOrNull()
                root?.optJSONArray("tracks")
                    ?: root?.optJSONArray("songs")
                    ?: root?.optJSONArray("items")
                    ?: root?.optJSONArray("data")
                    ?: root?.optJSONArray("music")
                    ?: root?.optJSONArray("files")
            }
            else -> null
        } ?: return emptyList()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val rawUrl = obj.optString("streamUrl").ifBlank {
                obj.optString("stream_url").ifBlank {
                    obj.optString("audioUrl").ifBlank {
                        obj.optString("audio_url").ifBlank {
                            obj.optString("url").ifBlank { obj.optString("src") }
                        }
                    }
                }
            }
            if (rawUrl.isBlank()) continue
            val resolvedUrl = resolveRelativeUrl(baseUrl, rawUrl)
            val rawCover = obj.optString("coverUrl").ifBlank {
                obj.optString("cover_url").ifBlank {
                    obj.optString("cover").ifBlank {
                        obj.optString("artwork").ifBlank {
                            obj.optString("image").ifBlank { obj.optString("thumbnail") }
                        }
                    }
                }
            }
            val resolvedCover = rawCover.takeIf { it.isNotBlank() }?.let { resolveRelativeUrl(baseUrl, it) }

            val title = obj.optString("title").ifBlank {
                obj.optString("name").ifBlank { "Трек ${i + 1}" }
            }
            val artist = obj.optString("artist").ifBlank {
                obj.optString("author").ifBlank { Uri.parse(baseUrl).host ?: "Hub" }
            }
            val album = obj.optString("album").ifBlank { "Медиатека Hub" }
            val rawDuration = obj.optLong("durationMs", 0L).takeIf { it > 0L }
                ?: obj.optLong("duration", 0L).let { if (it in 1..36_000) it * 1000L else it }

            result.add(
                HubRemoteTrack(
                    remoteId = obj.optString("id").ifBlank { resolvedUrl },
                    title = title,
                    artist = artist,
                    album = album,
                    albumId = obj.optString("albumId").ifBlank { album.lowercase() },
                    trackNumber = obj.optInt("trackNumber", obj.optInt("track", i + 1)),
                    year = obj.optInt("year", 0),
                    genre = obj.optString("genre"),
                    streamUrl = resolvedUrl,
                    coverUrl = resolvedCover,
                    durationMs = rawDuration
                )
            )
        }
        return result
    }

    private fun parseM3uTracks(body: String, baseUrl: String): List<HubRemoteTrack> {
        val result = mutableListOf<HubRemoteTrack>()
        var pendingTitle: String? = null
        var pendingArtist: String? = null
        var pendingDurationMs = 0L

        body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val afterColon = line.substringAfter(":")
                val durSec = afterColon.substringBefore(",").trim().toLongOrNull() ?: 0L
                pendingDurationMs = if (durSec > 0L) durSec * 1000L else 0L
                val meta = afterColon.substringAfter(",", "").trim()
                if (meta.contains(" - ")) {
                    pendingArtist = meta.substringBefore(" - ").trim()
                    pendingTitle = meta.substringAfter(" - ").trim()
                } else if (meta.isNotEmpty()) {
                    pendingTitle = meta
                }
            } else if (!line.startsWith("#")) {
                val resolved = resolveRelativeUrl(baseUrl, line)
                val title = pendingTitle ?: Uri.parse(resolved).lastPathSegment
                    ?.substringBeforeLast('.')
                    ?.ifBlank { "Поток Hub" } ?: "Поток Hub"
                result.add(
                    HubRemoteTrack(
                        remoteId = resolved,
                        title = title,
                        artist = pendingArtist ?: (Uri.parse(baseUrl).host ?: "Hub"),
                        album = "Плейлист Hub",
                        albumId = "hub_m3u",
                        trackNumber = result.size + 1,
                        year = 0,
                        genre = "Поток",
                        streamUrl = resolved,
                        coverUrl = null,
                        durationMs = pendingDurationMs
                    )
                )
                pendingTitle = null
                pendingArtist = null
                pendingDurationMs = 0L
            }
        }
        return result
    }

    private data class HttpResponse(val code: Int, val contentType: String, val body: String)

    private fun httpGet(urlStr: String, headers: Map<String, String> = emptyMap()): HttpResponse? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7_500
                readTimeout = 7_500
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json, audio/*, application/x-mpegurl, text/plain, */*")
                setRequestProperty("User-Agent", "KUDOMUSIC-Android/2.0")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val body = stream?.let { readLimited(it) }.orEmpty()
            HttpResponse(code, conn.contentType.orEmpty(), body)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun httpPostJson(
        urlStr: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7_500
                readTimeout = 7_500
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "KUDOMUSIC-Android/2.0")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val body = stream?.let { readLimited(it) }.orEmpty()
            HttpResponse(code, conn.contentType.orEmpty(), body)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun readLimited(inputStream: java.io.InputStream): String {
        return runCatching {
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(4096)
                var total = 0
                while (total < 512_000) {
                    val read = reader.read(buf)
                    if (read <= 0) break
                    sb.append(buf, 0, read)
                    total += read
                }
                sb.toString()
            }
        }.getOrDefault("")
    }

    private fun resolveRelativeUrl(baseUrl: String, target: String): String {
        if (target.startsWith("http://", ignoreCase = true) ||
            target.startsWith("https://", ignoreCase = true)
        ) {
            return target
        }
        return runCatching {
            URI(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/").resolve(target).toString()
        }.getOrDefault(target)
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
