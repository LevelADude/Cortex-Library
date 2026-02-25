package app.shosetsu.android.data.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cortex_library")

class CortexDataStore(private val context: Context) {
    private val sourcesKey = stringPreferencesKey("sources")
    private val downloadsKey = stringPreferencesKey("downloads")

    fun sourcesJson(): Flow<String?> = context.dataStore.data.map { it[sourcesKey] }
    fun downloadsJson(): Flow<String?> = context.dataStore.data.map { it[downloadsKey] }

    suspend fun saveSourcesJson(value: String) = context.dataStore.edit { it[sourcesKey] = value }
    suspend fun saveDownloadsJson(value: String) = context.dataStore.edit { it[downloadsKey] = value }

    suspend fun edit(block: suspend (Preferences.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
