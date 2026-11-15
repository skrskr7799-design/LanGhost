package com.langhost.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.profilesDataStore: DataStore<Preferences> by preferencesDataStore(name = "network_profiles")

data class NetworkProfile(
    val id: String,
    val name: String, // Home, Work, Cafe
    val bssid: String = "",
    val ssid: String = "",
    val notes: String = ""
)

class NetworkProfiles(private val context: Context) {

    private val KEY = stringPreferencesKey("profiles")
    private val KEY_ACTIVE = stringPreferencesKey("active_id")

    val profilesFlow: Flow<List<NetworkProfile>> = context.profilesDataStore.data.map { prefs ->
        parse(prefs[KEY])
    }

    val activeIdFlow: Flow<String?> = context.profilesDataStore.data.map { it[KEY_ACTIVE] }

    suspend fun add(profile: NetworkProfile) {
        context.profilesDataStore.edit { prefs ->
            val list = parse(prefs[KEY]).toMutableList()
            list.removeAll { it.id == profile.id }
            list.add(profile)
            prefs[KEY] = toJson(list)
        }
    }

    suspend fun remove(id: String) {
        context.profilesDataStore.edit { prefs ->
            val list = parse(prefs[KEY]).filter { it.id != id }
            prefs[KEY] = toJson(list)
            if (prefs[KEY_ACTIVE] == id) prefs.remove(KEY_ACTIVE)
        }
    }

    suspend fun setActive(id: String?) {
        context.profilesDataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_ACTIVE) else prefs[KEY_ACTIVE] = id
        }
    }

    private fun parse(raw: String?): List<NetworkProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                NetworkProfile(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    bssid = o.optString("bssid"),
                    ssid = o.optString("ssid"),
                    notes = o.optString("notes")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun toJson(list: List<NetworkProfile>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("bssid", p.bssid)
                put("ssid", p.ssid)
                put("notes", p.notes)
            })
        }
        return arr.toString()
    }
}
