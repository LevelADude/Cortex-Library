package app.shosetsu.android.data.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cortex_library")

class CortexDataStore(private val context: Context) {
    private val sourcesKey = stringPreferencesKey("sources")
    private val downloadsKey = stringPreferencesKey("downloads")
    private val downloadDirKey = stringPreferencesKey("download_dir")
    private val pdfOnlyKey = booleanPreferencesKey("pdf_only")
    private val persistDebugLogsKey = booleanPreferencesKey("persist_debug_logs")
    private val debugLogsKey = stringPreferencesKey("debug_logs")
    private val searchCacheKey = stringPreferencesKey("search_cache")

    fun sourcesJson(): Flow<String?> = context.dataStore.data.map { it[sourcesKey] }
    fun downloadsJson(): Flow<String?> = context.dataStore.data.map { it[downloadsKey] }
    fun downloadDirectory(): Flow<String?> = context.dataStore.data.map { it[downloadDirKey] }
    fun pdfOnlyMode(): Flow<Boolean> = context.dataStore.data.map { it[pdfOnlyKey] ?: true }
    fun persistDebugLogs(): Flow<Boolean> = context.dataStore.data.map { it[persistDebugLogsKey] ?: false }
    fun debugLogsJson(): Flow<String?> = context.dataStore.data.map { it[debugLogsKey] }
    fun searchCacheJson(): Flow<String?> = context.dataStore.data.map { it[searchCacheKey] }

    suspend fun saveSourcesJson(value: String) = context.dataStore.edit { it[sourcesKey] = value }
    suspend fun saveDownloadsJson(value: String) = context.dataStore.edit { it[downloadsKey] = value }
    suspend fun saveDownloadDirectory(value: String) = context.dataStore.edit { it[downloadDirKey] = value }
    suspend fun savePdfOnlyMode(value: Boolean) = context.dataStore.edit { it[pdfOnlyKey] = value }
    suspend fun savePersistDebugLogs(value: Boolean) = context.dataStore.edit { it[persistDebugLogsKey] = value }
    suspend fun saveDebugLogsJson(value: String) = context.dataStore.edit { it[debugLogsKey] = value }
    suspend fun clearDebugLogs() = context.dataStore.edit { it.remove(debugLogsKey) }
    suspend fun saveSearchCacheJson(value: String) = context.dataStore.edit { it[searchCacheKey] = value }
    suspend fun clearSearchCache() = context.dataStore.edit { it.remove(searchCacheKey) }

    suspend fun resetSettings() {
        context.dataStore.edit {
            it.remove(downloadDirKey)
            it[pdfOnlyKey] = true
            it[persistDebugLogsKey] = false
            it.remove(debugLogsKey)
            it.remove(searchCacheKey)
        }
    }

    suspend fun edit(block: suspend (Preferences.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
