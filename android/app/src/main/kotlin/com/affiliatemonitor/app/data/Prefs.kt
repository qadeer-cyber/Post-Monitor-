package com.affiliatemonitor.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "apm_prefs")

object Prefs {
    private val BACKEND_URL = stringPreferencesKey("backend_url")

    fun backendUrl(context: Context): Flow<String> =
        context.dataStore.data.map { it[BACKEND_URL] ?: "http://10.0.2.2:8000/" }

    suspend fun setBackendUrl(context: Context, value: String) {
        context.dataStore.edit { it[BACKEND_URL] = value }
    }
}
