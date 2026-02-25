package app.shosetsu.android.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shosetsu.android.data.repo.DownloadsRepository
import app.shosetsu.android.data.resolver.ArxivResolver
import app.shosetsu.android.data.resolver.DirectUrlResolver
import app.shosetsu.android.data.resolver.HtmlLinkResolver
import app.shosetsu.android.data.resolver.OpenAlexResolver
import app.shosetsu.android.data.resolver.PdfResolverChain
import app.shosetsu.android.domain.model.DownloadItem
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val downloadsRepository: DownloadsRepository,
    private val sourcesProvider: () -> List<Source>
) : ViewModel() {
    private val resolver = PdfResolverChain(
        listOf(
            DirectUrlResolver(),
            ArxivResolver(),
            OpenAlexResolver(),
            HtmlLinkResolver { sourceId -> sourcesProvider().firstOrNull { it.id == sourceId } }
        )
    )

    private val _resolvingIds = MutableStateFlow<Set<String>>(emptySet())
    val resolvingIds: StateFlow<Set<String>> = _resolvingIds.asStateFlow()

    val downloads = downloadsRepository.downloadsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun download(result: SearchResult, sourceName: String, onDone: (Result<DownloadItem>, SearchResult) -> Unit) = viewModelScope.launch {
        _resolvingIds.value = _resolvingIds.value + result.id
        val resolved = resolver.resolve(result)
        val response = if (resolved.pdfUrl != null) {
            downloadsRepository.downloadPdf(resolved, sourceName)
        } else {
            Result.failure(IllegalArgumentException("No PDF found"))
        }
        _resolvingIds.value = _resolvingIds.value - result.id
        onDone(response, resolved)
    }
}
