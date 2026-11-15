package com.langhost.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.pingDataStore: DataStore<Preferences> by preferencesDataStore(name = "ping_history")

data class PingSample(
    val timestamp: Long,
    val latencyMs: Long?
)

data class PingStats(
    val samples: List<PingSample>,
    val avgMs: Double,
    val minMs: Long,
    val maxMs: Long,
    val lossPercent: Float,
    val uptimePercent: Float
)

class PingHistoryStore(private val context: Context) {

    private fun key(ip: String) = stringPreferencesKey("ping_${ip.replace('.', '_')}")

    fun historyFlow(ip: String): Flow<List<PingSample>> = context.pingDataStore.data.map { prefs ->
        parse(prefs[key(ip)])
    }

    suspend fun addSample(ip: String, latencyMs: Long?) {
        context.pingDataStore.edit { prefs ->
            val current = parse(prefs[key(ip)]).toMutableList()
            current.add(PingSample(System.currentTimeMillis(), latencyMs))
            val trimmed = if (current.size > 100) current.takeLast(100) else current
            val arr = JSONArray()
            trimmed.forEach { s ->
                arr.put(JSONObject().apply {
                    put("t", s.timestamp)
                    if (s.latencyMs != null) put("ms", s.latencyMs) else put("ms", JSONObject.NULL)
                })
            }
            prefs[key(ip)] = arr.toString()
        }
    }

    suspend fun getStats(ip: String): PingStats {
        val samples = try {
            historyFlow(ip).first()
        } catch (_: Exception) {
            emptyList()
        }
        return computeStats(samples)
    }

    fun computeStats(samples: List<PingSample>): PingStats {
        if (samples.isEmpty()) {
            return PingStats(emptyList(), 0.0, 0, 0, 0f, 0f)
        }
        val ok = samples.mapNotNull { it.latencyMs }
        val loss = samples.count { it.latencyMs == null }
        val lossPct = loss * 100f / samples.size
        return PingStats(
            samples = samples,
            avgMs = if (ok.isNotEmpty()) ok.average() else 0.0,
            minMs = ok.minOrNull() ?: 0,
            maxMs = ok.maxOrNull() ?: 0,
            lossPercent = lossPct,
            uptimePercent = 100f - lossPct
        )
    }

    private fun parse(raw: String?): List<PingSample> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val ms = if (o.isNull("ms")) null else o.optLong("ms")
                PingSample(o.optLong("t"), ms)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
