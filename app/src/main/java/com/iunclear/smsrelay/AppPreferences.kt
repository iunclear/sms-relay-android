package com.iunclear.smsrelay

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sms_relay_settings")

data class RelaySettings(
    val enabled: Boolean = false,
    val deviceName: String = android.os.Build.MODEL ?: "Android",
    val endpoint: String = ""
)

class AppPreferences(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val deviceName = stringPreferencesKey("device_name")
        val endpoint = stringPreferencesKey("endpoint")
    }

    val settings: Flow<RelaySettings> = context.dataStore.data.map { values ->
        RelaySettings(
            enabled = values[Keys.enabled] ?: false,
            deviceName = values[Keys.deviceName] ?: (android.os.Build.MODEL ?: "Android"),
            endpoint = values[Keys.endpoint] ?: ""
        )
    }

    suspend fun save(enabled: Boolean, deviceName: String, endpoint: String) {
        context.dataStore.edit { values ->
            values[Keys.enabled] = enabled
            values[Keys.deviceName] = deviceName.trim()
            values[Keys.endpoint] = endpoint.trim()
        }
    }
}
