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
    val name: String,
    val bssid: String = "",
    val ssid: String = "",
    val notes: String = ""
)

class NetworkProfiles(private val context: Context) {

    private val keyProfiles = stringPreferencesKey("profiles")
    private val keyActive = stringPreferencesKey("active_id")

    val profilesFlow: Flow<List<NetworkProfile>> = context.profilesDataStore.data.map { prefs ->
        parse(prefs[keyProfiles])
    }

    val activeIdFlow: Flow<String?> = context.profilesDataStore.data.map { it[keyActive] }

    suspend fun add(profile: NetworkProfile) {
        require(profile.id.isNotBlank() && profile.id.length <= MAX_PROFILE_ID_LENGTH)
        require(profile.name.isNotBlank())
        context.profilesDataStore.edit { prefs ->
            val retained = retainNetworkProfiles(
                existing = parse(prefs[keyProfiles]),
                incoming = listOf(profile)
            )
            prefs[keyProfiles] = toJson(retained)
        }
    }

    suspend fun remove(id: String) {
        context.profilesDataStore.edit { prefs ->
            val list = parse(prefs[keyProfiles]).filter { it.id != id }
            prefs[keyProfiles] = toJson(list)
            if (prefs[keyActive] == id) prefs.remove(keyActive)
        }
    }

    suspend fun setActive(id: String?) {
        context.profilesDataStore.edit { prefs ->
            val exists = id != null && parse(prefs[keyProfiles]).any { it.id == id }
            if (exists) prefs[keyActive] = id!! else prefs.remove(keyActive)
        }
    }

    private fun parse(raw: String?): List<NetworkProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val parsed = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val name = o.optString("name")
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                NetworkProfile(
                    id = id,
                    name = name,
                    bssid = o.optString("bssid"),
                    ssid = o.optString("ssid"),
                    notes = o.optString("notes")
                )
            }
            retainNetworkProfiles(emptyList(), parsed)
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

    private companion object {
        const val MAX_PROFILE_ID_LENGTH = 128
    }
}

internal fun retainNetworkProfiles(
    existing: List<NetworkProfile>,
    incoming: List<NetworkProfile>,
    maxProfiles: Int = 32
): List<NetworkProfile> {
    require(maxProfiles > 0)
    val ordered = linkedMapOf<String, NetworkProfile>()
    (existing + incoming).forEach { profile ->
        val id = profile.id.trim()
        val name = profile.name.trim()
        if (id.isNotEmpty() && name.isNotEmpty()) {
            ordered.remove(id)
            ordered[id] = profile.copy(
                id = id,
                name = name.take(80),
                bssid = profile.bssid.take(64),
                ssid = profile.ssid.take(256),
                notes = profile.notes.take(2048)
            )
        }
    }
    return ordered.values.toList().takeLast(maxProfiles)
}
