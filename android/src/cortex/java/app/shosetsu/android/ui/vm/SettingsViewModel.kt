package app.shosetsu.android.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shosetsu.android.data.repo.CatalogFetchStatus
import app.shosetsu.android.data.repo.DebugEventsRepository
import app.shosetsu.android.data.repo.PresetCatalog
import app.shosetsu.android.data.repo.SourcesRepository
import app.shosetsu.android.data.store.CortexDataStore
import app.shosetsu.android.domain.model.DebugEvent
import app.shosetsu.android.domain.model.DebugLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val dataStore: CortexDataStore,
    private val debugEventsRepository: DebugEventsRepository,
    private val sourcesRepository: SourcesRepository
) : ViewModel() {
    val events: StateFlow<List<DebugEvent>> = debugEventsRepository.events
    val persistLogs: StateFlow<Boolean> = debugEventsRepository.persistLogs
    val catalogStatus = sourcesRepository.catalogStatusFlow.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), CatalogFetchStatus())

    private val _previewCatalog = MutableStateFlow<PresetCatalog?>(null)
    val previewCatalog: StateFlow<PresetCatalog?> = _previewCatalog.asStateFlow()

    fun setPersistLogs(enabled: Boolean) = debugEventsRepository.setPersistLogs(enabled)
    fun clearLogs() = debugEventsRepository.clear()

    fun resetSettings() = viewModelScope.launch {
        dataStore.resetSettings()
        debugEventsRepository.log(DebugLevel.Info, "settings", "Settings reset to defaults")
    }

    fun fetchCatalog(url: String, allowHttpInDevMode: Boolean, pinnedDomain: String?) = viewModelScope.launch {
        val result = sourcesRepository.fetchCatalog(url, allowHttpInDevMode, pinnedDomain)
        _previewCatalog.value = result.getOrNull()
        result.exceptionOrNull()?.let { debugEventsRepository.log(DebugLevel.Error, "catalog", "Catalog fetch failed", details = it.message) }
    }

    fun importCatalog(selectedPresetIds: Set<String>, overrideEnabled: Boolean) = viewModelScope.launch {
        val catalog = _previewCatalog.value ?: return@launch
        val result = sourcesRepository.importCatalogPresets(catalog, selectedPresetIds, overrideEnabled)
        debugEventsRepository.log(DebugLevel.Info, "catalog", "Imported Preset Catalog", details = "added=${result.added},replaced=${result.replaced}")
    }

    suspend fun exportSourcesAsCatalogJson(): String = sourcesRepository.exportSourcesAsCatalogJson()
}
