package app.shosetsu.android.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shosetsu.android.data.connector.ConnectorRegistry
import app.shosetsu.android.data.repo.DebugEventsRepository
import app.shosetsu.android.data.repo.SourceImportPreview
import app.shosetsu.android.data.repo.SourcesRepository
import app.shosetsu.android.domain.model.DebugLevel
import app.shosetsu.android.domain.model.Source
import app.shosetsu.android.domain.model.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SourcesViewModel(
    private val sourcesRepository: SourcesRepository,
    private val connectorRegistry: ConnectorRegistry,
    private val debugEventsRepository: DebugEventsRepository
) : ViewModel() {
    val sources = sourcesRepository.sourcesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pendingImport = MutableStateFlow<SourceImportPreview?>(null)
    val pendingImport: StateFlow<SourceImportPreview?> = _pendingImport.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    fun addSource(name: String, baseUrl: String, type: SourceType, notes: String = "", configJson: String?) = viewModelScope.launch {
        sourcesRepository.addSource(name, baseUrl, type, notes, configJson)
    }

    fun updateSource(source: Source) = viewModelScope.launch {
        sourcesRepository.updateSource(source)
    }

    fun toggleSource(id: String, enabled: Boolean) = viewModelScope.launch {
        sourcesRepository.toggleSource(id, enabled)
    }

    fun exportSourcesJson(onDone: (String) -> Unit) = viewModelScope.launch {
        onDone(sourcesRepository.exportSourcesJson())
    }

    fun previewImport(json: String) = viewModelScope.launch {
        runCatching { sourcesRepository.previewImport(json) }
            .onSuccess { _pendingImport.value = it }
            .onFailure { _testResult.value = "Import failed: ${it.message}" }
    }

    fun confirmImport() = viewModelScope.launch {
        pendingImport.value?.let { sourcesRepository.applyImport(it) }
        _pendingImport.value = null
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    fun testSource(tempSource: Source) = viewModelScope.launch {
        runCatching {
            val results = connectorRegistry.forSource(tempSource).search(tempSource, "test", limit = 1)
            val title = results.firstOrNull()?.title ?: "(no result title)"
            "Test passed. Results: ${results.size}, sample: $title"
        }.onSuccess {
            _testResult.value = it
            debugEventsRepository.log(DebugLevel.Info, "source_test", "Source test passed", tempSource.id, tempSource.name, it)
        }.onFailure {
            val msg = "Test failed: ${it.message}"
            _testResult.value = msg
            debugEventsRepository.log(DebugLevel.Error, "source_test", "Source test failed", tempSource.id, tempSource.name, it.message)
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    fun resetDefaults() = viewModelScope.launch {
        sourcesRepository.resetToDefaults()
    }
}
