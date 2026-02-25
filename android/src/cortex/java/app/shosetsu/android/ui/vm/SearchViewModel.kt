package app.shosetsu.android.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shosetsu.android.data.repo.SearchRepository
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val sourceErrors: List<String> = emptyList()
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val sourcesProvider: () -> List<Source>
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.debounce(500).collect { state ->
                val query = state.query
                if (query.isBlank()) {
                    _uiState.update { it.copy(results = emptyList(), sourceErrors = emptyList(), isLoading = false) }
                    return@collect
                }
                _uiState.update { it.copy(isLoading = true) }
                val response = searchRepository.search(query, sourcesProvider().filter { it.enabled })
                _uiState.update {
                    it.copy(isLoading = false, results = response.results, sourceErrors = response.sourceErrors)
                }
            }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun searchNow() {
        viewModelScope.launch {
            val current = _uiState.value.query
            _uiState.update { it.copy(isLoading = true) }
            val response = searchRepository.search(current, sourcesProvider().filter { it.enabled })
            _uiState.update { it.copy(isLoading = false, results = response.results, sourceErrors = response.sourceErrors) }
        }
    }
}
