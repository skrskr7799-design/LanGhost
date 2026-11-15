package com.langhost.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

data class AppSettingsData(
    val watchModeEnabled: Boolean = false,
    val notifyNewDevices: Boolean = true,
    val scanTimeoutMs: Int = 150,
    val autoScanOnOpen: Boolean = false
)

class AppSettings(private val context: Context) {

    private val KEY_WATCH_MODE = booleanPreferencesKey("watch_mode")
    private val KEY_NOTIFY_NEW = booleanPreferencesKey("notify_new_devices")
    private val KEY_SCAN_TIMEOUT = intPreferencesKey("scan_timeout_ms")
    private val KEY_AUTO_SCAN = booleanPreferencesKey("auto_scan_on_open")

    val settingsFlow: Flow<AppSettingsData> = context.settingsDataStore.data.map { prefs ->
        AppSettingsData(
            watchModeEnabled = prefs[KEY_WATCH_MODE] ?: false,
            notifyNewDevices = prefs[KEY_NOTIFY_NEW] ?: true,
            scanTimeoutMs = (prefs[KEY_SCAN_TIMEOUT] ?: 150).coerceIn(50, 500),
            autoScanOnOpen = prefs[KEY_AUTO_SCAN] ?: false
        )
    }

    suspend fun getOnce(): AppSettingsData = try {
        settingsFlow.first()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AppLog.warning(AppLog.Component.APP_SETTINGS, AppLog.Event.SETTINGS_READ_FALLBACK)
        AppSettingsData()
    }

    suspend fun setWatchMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_WATCH_MODE] = enabled }
    }

    suspend fun setNotifyNewDevices(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_NOTIFY_NEW] = enabled }
    }

    suspend fun setScanTimeout(ms: Int) {
        context.settingsDataStore.edit { it[KEY_SCAN_TIMEOUT] = ms.coerceIn(50, 500) }
    }

    suspend fun setAutoScanOnOpen(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_SCAN] = enabled }
    }
}
