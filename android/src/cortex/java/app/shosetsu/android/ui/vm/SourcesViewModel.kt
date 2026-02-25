package app.shosetsu.android.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shosetsu.android.data.repo.SourcesRepository
import app.shosetsu.android.domain.model.SourceType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SourcesViewModel(private val sourcesRepository: SourcesRepository) : ViewModel() {
    val sources = sourcesRepository.sourcesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addSource(name: String, baseUrl: String, type: SourceType, notes: String = "", configJson: String?) = viewModelScope.launch {
        sourcesRepository.addSource(name, baseUrl, type, notes, configJson)
    }

    fun toggleSource(id: String, enabled: Boolean) = viewModelScope.launch {
        sourcesRepository.toggleSource(id, enabled)
    }
}
