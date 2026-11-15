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
    val isWatched: Boolean = false,
    val firstSeen: Long = 0L
)

class DevicePreferences(private val context: Context) {

    /** Key by stableId (MAC@BSSID or MAC or ip:x) */
    private fun key(stableId: String) = stringPreferencesKey("dev_${stableId.replace(':', '_').replace('@', '_')}")
    private val KEY_KNOWN_IDS = stringSetPreferencesKey("known_stable_ids")

    fun getCustomization(stableId: String): Flow<DeviceCustomization> {
        return context.deviceDataStore.data.map { prefs ->
            val raw = prefs[key(stableId)] ?: return@map DeviceCustomization()
            parseCustomization(raw)
        }
    }

    suspend fun getCustomizationOnce(stableId: String): DeviceCustomization {
        return try {
            getCustomization(stableId).first()
        } catch (_: Exception) {
            DeviceCustomization()
        }
    }

    private fun parseCustomization(raw: String): DeviceCustomization {
        return try {
            val json = JSONObject(raw)
            DeviceCustomization(
                nickname = json.optString("nickname", ""),
                notes = json.optString("notes", ""),
                isFavorite = json.optBoolean("isFavorite", false),
                isWatched = json.optBoolean("isWatched", false),
                firstSeen = json.optLong("firstSeen", 0L)
            )
        } catch (_: Exception) {
            DeviceCustomization()
        }
    }

    suspend fun saveCustomization(stableId: String, customization: DeviceCustomization) {
        context.deviceDataStore.edit { prefs ->
            val json = JSONObject().apply {
                put("nickname", customization.nickname)
                put("notes", customization.notes)
                put("isFavorite", customization.isFavorite)
                put("isWatched", customization.isWatched)
                put("firstSeen", customization.firstSeen)
            }
            prefs[key(stableId)] = json.toString()
        }
    }

    suspend fun saveKnownIds(ids: List<String>) {
        context.deviceDataStore.edit { prefs ->
            val existing = prefs[KEY_KNOWN_IDS] ?: emptySet()
            prefs[KEY_KNOWN_IDS] = existing + ids.toSet()
        }
    }

    suspend fun getKnownIds(): Set<String> {
        return try {
            context.deviceDataStore.data.map { it[KEY_KNOWN_IDS] ?: emptySet() }.first()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
