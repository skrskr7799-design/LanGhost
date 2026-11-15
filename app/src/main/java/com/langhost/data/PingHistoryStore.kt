package com.langhost.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
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
    private companion object {
        const val MAX_SAMPLES_PER_IP = 100
        const val MAX_TRACKED_IPS = 64
        const val HISTORY_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }

    private fun key(ip: String) = stringPreferencesKey("ping_${ip.replace('.', '_')}")

    fun historyFlow(ip: String): Flow<List<PingSample>> = context.pingDataStore.data.map { prefs ->
        val cutoff = System.currentTimeMillis() - HISTORY_RETENTION_MS
        parse(prefs[key(ip)]).filter { it.timestamp >= cutoff }
    }

    suspend fun addSample(ip: String, latencyMs: Long?) {
        context.pingDataStore.edit { prefs ->
            val now = System.currentTimeMillis()
            val cutoff = now - HISTORY_RETENTION_MS
            val currentKey = key(ip)
            val current = parse(prefs[currentKey]).filter { it.timestamp >= cutoff }.toMutableList()
            current.add(PingSample(now, latencyMs))
            prefs[currentKey] = serialize(current.takeLast(MAX_SAMPLES_PER_IP))

            val histories = prefs.asMap().entries.mapNotNull { (preferenceKey, value) ->
                if (preferenceKey.name.startsWith("ping_") && value is String) {
                    preferenceKey.name to (parse(value).maxOfOrNull { it.timestamp } ?: Long.MIN_VALUE)
                } else {
                    null
                }
            }
            val retainedKeys = retainPingHistoryKeys(
                histories.toMap(),
                maxKeys = MAX_TRACKED_IPS,
                now = now,
                retentionMs = HISTORY_RETENTION_MS
            )
            histories.asSequence().map { it.first }
                .filterNot { it in retainedKeys }
                .forEach { prefs.remove(stringPreferencesKey(it)) }
        }
    }

    suspend fun getStats(ip: String): PingStats {
        val samples = try {
            historyFlow(ip).first()
        } catch (cancelled: CancellationException) {
            throw cancelled
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

    private fun serialize(samples: List<PingSample>): String {
        val array = JSONArray()
        samples.forEach { sample ->
            array.put(JSONObject().apply {
                put("t", sample.timestamp)
                if (sample.latencyMs != null) put("ms", sample.latencyMs) else put("ms", JSONObject.NULL)
            })
        }
        return array.toString()
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

internal fun retainPingHistoryKeys(
    historyTimestamps: Map<String, Long>,
    maxKeys: Int = 64,
    now: Long = System.currentTimeMillis(),
    retentionMs: Long = 30L * 24 * 60 * 60 * 1000
): Set<String> {
    require(maxKeys > 0)
    require(retentionMs > 0)
    val cutoff = now - retentionMs
    return historyTimestamps.entries
        .filter { it.value >= cutoff }
        .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
        .take(maxKeys)
        .map { it.key }
        .toSet()
}
