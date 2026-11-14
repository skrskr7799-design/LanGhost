package com.langhost.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_prefs")

data class DeviceCustomization(
    val nickname: String = "",
    val notes: String = "",
    val isFavorite: Boolean = false,
    val isWatched: Boolean = false
)

class DevicePreferences(private val context: Context) {

    private fun key(ip: String) = stringPreferencesKey("device_$ip")
    private val KEY_KNOWN_IPS = stringSetPreferencesKey("known_ips")

    fun getCustomization(ip: String): Flow<DeviceCustomization> {
        return context.deviceDataStore.data.map { prefs ->
            val raw = prefs[key(ip)] ?: return@map DeviceCustomization()
            try {
                val json = JSONObject(raw)
                DeviceCustomization(
                    nickname = json.optString("nickname", ""),
                    notes = json.optString("notes", ""),
                    isFavorite = json.optBoolean("isFavorite", false),
                    isWatched = json.optBoolean("isWatched", false)
                )
            } catch (e: Exception) {
                DeviceCustomization()
            }
        }
    }

    suspend fun saveCustomization(ip: String, customization: DeviceCustomization) {
        context.deviceDataStore.edit { prefs ->
            val json = JSONObject().apply {
                put("nickname", customization.nickname)
                put("notes", customization.notes)
                put("isFavorite", customization.isFavorite)
                put("isWatched", customization.isWatched)
            }
            prefs[key(ip)] = json.toString()
        }
    }

    suspend fun saveKnownIps(ips: List<String>) {
        context.deviceDataStore.edit { prefs ->
            val existing = prefs[KEY_KNOWN_IPS] ?: emptySet()
            prefs[KEY_KNOWN_IPS] = existing + ips.toSet()
        }
    }

    suspend fun getKnownIps(): Set<String> {
        return try {
            context.deviceDataStore.data.map { it[KEY_KNOWN_IPS] ?: emptySet() }.first()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
