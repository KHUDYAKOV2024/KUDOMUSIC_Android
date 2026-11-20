package com.example.data.hub

import android.content.Context
import android.net.Uri
import android.util.Base64

enum class HubProtocol(val displayName: String) {
    SUBSONIC("Subsonic / Navidrome"),
    JELLYFIN("Jellyfin / Emby"),
    REST_HUB("KUDOMUSIC Hub API"),
    DIRECT_FEED("HTTP / M3U Поток")
}

data class HubSession(
    val hubUrl: String = "",
    val username: String = "",
    val password: String = "",
    val accessToken: String = "",
    val protocol: HubProtocol = HubProtocol.REST_HUB,
    val isAuthorized: Boolean = false,
    val isConnectedOnline: Boolean = false
) {
    val displayHost: String
        get() {
            if (hubUrl.isBlank()) return "Hub"
            val parsed = runCatching { Uri.parse(hubUrl) }.getOrNull()
            return parsed?.host?.takeIf { it.isNotBlank() } ?: hubUrl
        }

    fun buildAuthHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        when {
            accessToken.isNotBlank() && protocol == HubProtocol.JELLYFIN -> {
                headers["X-Emby-Token"] = accessToken
                headers["Authorization"] = "MediaBrowser Token=\"$accessToken\""
            }
            accessToken.isNotBlank() -> {
                headers["Authorization"] = "Bearer $accessToken"
            }
            username.isNotBlank() && password.isNotBlank() -> {
                val encoded = Base64.encodeToString(
                    "$username:$password".toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                headers["Authorization"] = "Basic $encoded"
            }
        }
        return headers
    }
}

class HubConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSession(): HubSession {
        val url = prefs.getString(KEY_HUB_URL, "").orEmpty()
        val username = prefs.getString(KEY_USERNAME, "").orEmpty()
        val password = prefs.getString(KEY_PASSWORD, "").orEmpty()
        val token = prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty()
        val protocolName = prefs.getString(KEY_PROTOCOL, HubProtocol.REST_HUB.name).orEmpty()
        val protocol = HubProtocol.entries.firstOrNull { it.name == protocolName } ?: HubProtocol.REST_HUB
        val authorized = prefs.getBoolean(KEY_IS_AUTHORIZED, false) && url.isNotBlank()
        val online = prefs.getBoolean(KEY_IS_ONLINE, false)
        return HubSession(
            hubUrl = url,
            username = username,
            password = password,
            accessToken = token,
            protocol = protocol,
            isAuthorized = authorized,
            isConnectedOnline = online
        )
    }

    fun saveSession(session: HubSession) {
        prefs.edit()
            .putString(KEY_HUB_URL, session.hubUrl)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_PASSWORD, session.password)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_PROTOCOL, session.protocol.name)
            .putBoolean(KEY_IS_AUTHORIZED, session.isAuthorized)
            .putBoolean(KEY_IS_ONLINE, session.isConnectedOnline)
            .apply()
    }

    fun clearAuthorization() {
        prefs.edit()
            .putBoolean(KEY_IS_AUTHORIZED, false)
            .putBoolean(KEY_IS_ONLINE, false)
            .remove(KEY_ACCESS_TOKEN)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "kudomusic_hub_prefs_v2"
        private const val KEY_HUB_URL = "hub_url"
        private const val KEY_USERNAME = "hub_username"
        private const val KEY_PASSWORD = "hub_password"
        private const val KEY_ACCESS_TOKEN = "hub_access_token"
        private const val KEY_PROTOCOL = "hub_protocol"
        private const val KEY_IS_AUTHORIZED = "hub_is_authorized"
        private const val KEY_IS_ONLINE = "hub_is_online"
    }
}
