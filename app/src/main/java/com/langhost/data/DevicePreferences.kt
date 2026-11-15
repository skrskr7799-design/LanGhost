package com.langhost.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
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

    /** Key by network-scoped stable device identity. */
    private fun key(stableId: String) = stringPreferencesKey("dev_${stableId.replace(':', '_').replace('@', '_')}")
    private val KEY_KNOWN_IDS = stringPreferencesKey("known_stable_ids_json")
    private val LEGACY_KEY_KNOWN_IDS = stringSetPreferencesKey("known_stable_ids")
    private val MAX_KNOWN_IDS = 512

    fun getCustomization(stableId: String): Flow<DeviceCustomization> {
        return context.deviceDataStore.data.map { prefs ->
            val raw = prefs[key(stableId)] ?: return@map DeviceCustomization()
            parseCustomization(raw)
        }
    }

    suspend fun getCustomizationOnce(
        stableId: String,
        legacyIds: List<String> = emptyList()
    ): DeviceCustomization {
        return try {
            val preferences = context.deviceDataStore.data.first()
            (listOf(stableId) + legacyIds).firstNotNullOfOrNull { id ->
                preferences[key(id)]?.let(::parseCustomization)
            } ?: DeviceCustomization()
        } catch (cancelled: CancellationException) {
            throw cancelled
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
            val ordered = parseKnownIds(prefs[KEY_KNOWN_IDS])
            val legacy = prefs[LEGACY_KEY_KNOWN_IDS].orEmpty().sorted()
            val retained = retainKnownIds(ordered + legacy, ids, MAX_KNOWN_IDS)
            prefs[KEY_KNOWN_IDS] = JSONArray(retained).toString()
            prefs.remove(LEGACY_KEY_KNOWN_IDS)
        }
    }

    suspend fun getKnownIds(): Set<String> {
        return try {
            context.deviceDataStore.data.map { prefs ->
                val ordered = parseKnownIds(prefs[KEY_KNOWN_IDS])
                if (ordered.isNotEmpty()) ordered.toSet()
                else prefs[LEGACY_KEY_KNOWN_IDS] ?: emptySet()
            }.first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun parseKnownIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

internal fun retainKnownIds(existing: List<String>, incoming: List<String>, maxSize: Int = 512): List<String> {
    require(maxSize > 0)
    val ordered = linkedSetOf<String>()
    (existing + incoming).forEach { id ->
        if (id.isNotBlank()) {
            ordered.remove(id)
            ordered.add(id)
        }
    }
    return ordered.toList().takeLast(maxSize)
}
