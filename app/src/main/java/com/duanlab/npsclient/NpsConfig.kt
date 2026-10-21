package com.duanlab.npsclient

import android.content.SharedPreferences
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

object IntentExtraKey {
    const val NpsConfig = "NpsConfig"
}

@Parcelize
data class NpsConfig(
    val serverHost: String,
    val serverPort: Int,
    val vkey: String,
    val connectionType: String
) : Parcelable {
    val serverAddress: String
        get() {
            val host = serverHost.trim()
            val formattedHost = if (host.contains(':') && !host.startsWith("[")) {
                "[$host]"
            } else {
                host
            }
            return "$formattedHost:$serverPort"
        }

    override fun toString(): String =
        "NpsConfig(server=$serverAddress, type=$connectionType, vkey=***)"

    companion object {
        fun fromPreferences(preferences: SharedPreferences): NpsConfig? {
            val host = preferences.getString(PreferencesKey.SERVER_HOST, "")?.trim().orEmpty()
            val port = preferences.getInt(PreferencesKey.SERVER_PORT, 0)
            val vkey = preferences.getString(PreferencesKey.VKEY, "")?.trim().orEmpty()
            val type = preferences.getString(PreferencesKey.CONNECTION_TYPE, "tcp").orEmpty()
            if (host.isEmpty() || port !in 1..65535 || vkey.isEmpty() ||
                type !in NpcCommand.SUPPORTED_TYPES
            ) {
                return null
            }
            return NpsConfig(host, port, vkey, type)
        }

        fun save(preferences: SharedPreferences, config: NpsConfig) {
            preferences.edit()
                .putString(PreferencesKey.SERVER_HOST, config.serverHost)
                .putInt(PreferencesKey.SERVER_PORT, config.serverPort)
                .putString(PreferencesKey.VKEY, config.vkey)
                .putString(PreferencesKey.CONNECTION_TYPE, config.connectionType)
                .apply()
        }
    }
}
