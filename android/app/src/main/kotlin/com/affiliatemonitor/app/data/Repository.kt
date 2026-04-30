package com.affiliatemonitor.app.data

import android.content.Context
import kotlinx.coroutines.flow.first

/**
 * Thin wrapper that re-reads the user-configured backend URL before every call
 * so changing the URL in Settings doesn't require a restart.
 */
class Repository(private val context: Context) {

    private suspend fun service(): ApiService {
        val base = Prefs.backendUrl(context).first()
        return ApiClient.service(base)
    }

    suspend fun health() = service().health()
    suspend fun listSources() = service().listSources()
    suspend fun addSource(url: String) = service().createSource(SourceCreate(url = url))
    suspend fun updateSource(id: Int, update: SourceUpdate) = service().updateSource(id, update)
    suspend fun deleteSource(id: Int) = service().deleteSource(id)
    suspend fun scanSource(id: Int) = service().scanSource(id)
    suspend fun scanAll() = service().scanAll()
    suspend fun dashboard() = service().dashboard()
    suspend fun queue() = service().queue()
    suspend fun posted() = service().posted()
    suspend fun getPost(id: Int) = service().getPost(id)
    suspend fun markPosted(id: Int) = service().markPosted(id)
    suspend fun rejectPost(id: Int) = service().rejectPost(id)
    suspend fun logs(category: String? = null, level: String? = null) = service().logs(category, level)
    suspend fun getSettings() = service().getSettings()
    suspend fun patchSettings(update: SettingsUpdate) = service().patchSettings(update)
}
