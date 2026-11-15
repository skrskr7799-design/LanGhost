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

private val Context.wolDataStore: DataStore<Preferences> by preferencesDataStore(name = "wol_profiles")

data class WolProfile(
    val id: String,
    val name: String,
    val mac: String,
    val broadcastIp: String = "255.255.255.255",
    val port: Int = 9
)

class WolProfiles(private val context: Context) {

    private val KEY = stringPreferencesKey("profiles_json")

    val profilesFlow: Flow<List<WolProfile>> = context.wolDataStore.data.map { prefs ->
        val raw = prefs[KEY] ?: return@map emptyList()
        try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                WolProfile(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    mac = o.optString("mac"),
                    broadcastIp = o.optString("broadcastIp", "255.255.255.255"),
                    port = o.optInt("port", 9)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveAll(profiles: List<WolProfile>) {
        context.wolDataStore.edit { prefs ->
            val arr = JSONArray()
            profiles.forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("mac", p.mac)
                    put("broadcastIp", p.broadcastIp)
                    put("port", p.port)
                })
            }
            prefs[KEY] = arr.toString()
        }
    }

    suspend fun add(profile: WolProfile) {
        context.wolDataStore.edit { prefs ->
            val current = try {
                val raw = prefs[KEY] ?: "[]"
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i) }.toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }
            current.add(JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("mac", profile.mac)
                put("broadcastIp", profile.broadcastIp)
                put("port", profile.port)
            })
            val arr = JSONArray()
            current.forEach { arr.put(it) }
            prefs[KEY] = arr.toString()
        }
    }

    suspend fun remove(id: String) {
        context.wolDataStore.edit { prefs ->
            val raw = prefs[KEY] ?: return@edit
            try {
                val arr = JSONArray(raw)
                val out = JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("id") != id) out.put(o)
                }
                prefs[KEY] = out.toString()
            } catch (_: Exception) {
            }
        }
    }
}
