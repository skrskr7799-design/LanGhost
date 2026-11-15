package com.langhost.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lockDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_lock")

class AppLockSettings(private val context: Context) {
    private val KEY_ENABLED = booleanPreferencesKey("biometric_lock")

    val biometricEnabled: Flow<Boolean> = context.lockDataStore.data.map { it[KEY_ENABLED] ?: false }

    suspend fun setBiometricEnabled(v: Boolean) {
        context.lockDataStore.edit { it[KEY_ENABLED] = v }
    }

}
