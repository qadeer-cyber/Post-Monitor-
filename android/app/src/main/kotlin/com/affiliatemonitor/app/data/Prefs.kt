package com.affiliatemonitor.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "apm_prefs")

object Prefs {
    private val BACKEND_URL = stringPreferencesKey("backend_url")
    private val ONBOARDED = booleanPreferencesKey("onboarded")

    const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8000/"
    const val DEFAULT_AMAZON_TAG = "laique248-20"

    fun backendUrl(context: Context): Flow<String> =
        context.dataStore.data.map { it[BACKEND_URL] ?: DEFAULT_BACKEND_URL }

    suspend fun setBackendUrl(context: Context, value: String) {
        context.dataStore.edit { it[BACKEND_URL] = value }
    }

    fun isOnboarded(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[ONBOARDED] ?: false }

    suspend fun setOnboarded(context: Context, value: Boolean) {
        context.dataStore.edit { it[ONBOARDED] = value }
    }
}
