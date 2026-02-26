package app.shosetsu.android.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shosetsu.android.data.repo.ContentTypeFilter
import app.shosetsu.android.data.repo.SearchOptions
import app.shosetsu.android.data.repo.SearchRepository
import app.shosetsu.android.data.repo.SearchSortMode
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val sourceErrors: List<String> = emptyList(),
    val onlyWithPdf: Boolean = false,
    val sourceFilterIds: Set<String> = emptySet(),
    val sortMode: SearchSortMode = SearchSortMode.Relevance,
    val contentTypeFilter: ContentTypeFilter = ContentTypeFilter.All,
    val selectedResult: SearchResult? = null,
    val isOnline: Boolean = true,
    val fromCache: Boolean = false
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val sourcesProvider: () -> List<Source>,
    isOnlineFlow: Flow<Boolean>
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var activeSearchJob: Job? = null

    init {
        viewModelScope.launch {
            isOnlineFlow.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
        viewModelScope.launch {
            combine(_uiState.debounce(450), _uiState) { a, _ -> a }
                .collect { state ->
                    searchInternal(state.query, state.onlyWithPdf, state.sourceFilterIds, state.sortMode, state.contentTypeFilter)
                }
        }
    }

    private fun searchInternal(query: String, onlyWithPdf: Boolean, sourceFilters: Set<String>, sortMode: SearchSortMode, contentTypeFilter: ContentTypeFilter) {
        activeSearchJob?.cancel()
        activeSearchJob = viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.update { it.copy(results = emptyList(), sourceErrors = emptyList(), isLoading = false) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            val response = searchRepository.search(
                query,
                sourcesProvider().filter { it.enabled },
                SearchOptions(onlyWithPdf = onlyWithPdf, sourceFilterIds = sourceFilters, sortMode = sortMode, contentTypeFilter = contentTypeFilter),
                offlineMode = !_uiState.value.isOnline
            )
            _uiState.update { it.copy(isLoading = false, results = response.results, sourceErrors = response.sourceErrors, fromCache = response.fromCache) }
        }
    }

    fun onQueryChanged(query: String) { _uiState.update { it.copy(query = query) } }
    fun setOnlyWithPdf(enabled: Boolean) { _uiState.update { it.copy(onlyWithPdf = enabled) } }
    fun setContentTypeFilter(filter: ContentTypeFilter) { _uiState.update { it.copy(contentTypeFilter = filter) } }
    fun toggleSourceFilter(sourceId: String) { _uiState.update { it.copy(sourceFilterIds = it.sourceFilterIds.toMutableSet().apply { if (contains(sourceId)) remove(sourceId) else add(sourceId) }) } }
    fun setSortMode(sortMode: SearchSortMode) { _uiState.update { it.copy(sortMode = sortMode) } }
    fun setSelectedResult(result: SearchResult?) { _uiState.update { it.copy(selectedResult = result) } }

    fun searchNow() {
        val s = _uiState.value
        searchInternal(s.query, s.onlyWithPdf, s.sourceFilterIds, s.sortMode, s.contentTypeFilter)
    }

    fun cancelInFlightSearch() {
        activeSearchJob?.cancel()
        _uiState.update { it.copy(isLoading = false) }
    }

    override fun onCleared() {
        cancelInFlightSearch()
        super.onCleared()
    }
}
