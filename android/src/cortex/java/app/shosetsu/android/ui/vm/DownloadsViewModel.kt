package app.shosetsu.android.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shosetsu.android.data.repo.DownloadsRepository
import app.shosetsu.android.data.repo.DebugEventsRepository
import app.shosetsu.android.data.resolver.ArxivResolver
import app.shosetsu.android.data.resolver.DirectUrlResolver
import app.shosetsu.android.data.resolver.HtmlLinkResolver
import app.shosetsu.android.data.resolver.OpenAlexResolver
import app.shosetsu.android.data.resolver.OpenStaxResolver
import app.shosetsu.android.data.resolver.PdfResolverChain
import app.shosetsu.android.data.resolver.PdfResolutionVerifier
import app.shosetsu.android.data.resolver.PmcResolver
import app.shosetsu.android.data.resolver.ResolverCache
import app.shosetsu.android.data.resolver.StandardEbooksResolver
import app.shosetsu.android.data.store.CortexDataStore
import app.shosetsu.android.domain.model.DownloadItem
import app.shosetsu.android.domain.model.DebugLevel
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
    private val sourcesProvider: () -> List<Source>,
    private val debugEventsRepository: DebugEventsRepository,
    dataStore: CortexDataStore
) : ViewModel() {
    private val verifier = PdfResolutionVerifier()
    private val resolverCache = ResolverCache(dataStore)
    private val resolver = PdfResolverChain(
        listOf(
            DirectUrlResolver(verifier) { sourceId -> sourcesProvider().firstOrNull { it.id == sourceId } },
            ArxivResolver(),
            PmcResolver(),
            OpenAlexResolver(),
            StandardEbooksResolver(),
            OpenStaxResolver(),
            HtmlLinkResolver({ sourceId -> sourcesProvider().firstOrNull { it.id == sourceId } }, verifier, resolverCache)
        ),
        debugEventsRepository
    )

    private val _resolvingIds = MutableStateFlow<Set<String>>(emptySet())
    val resolvingIds: StateFlow<Set<String>> = _resolvingIds.asStateFlow()

    val downloads = downloadsRepository.downloadsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val downloadDirectory = downloadsRepository.downloadDirectoryFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setDownloadDirectory(path: String) = viewModelScope.launch {
        downloadsRepository.setDownloadDirectory(path)
    }

    fun findDownloadedFilePath(result: SearchResult): String? {
        return downloads.value.firstOrNull { it.title == result.title && it.sourceName == resolveSourceName(result.sourceId) }?.filePath
    }

    private fun resolveSourceName(sourceId: String): String = sourcesProvider().firstOrNull { it.id == sourceId }?.name ?: "Unknown Source"

    fun download(result: SearchResult, sourceName: String, onDone: (Result<DownloadItem>, SearchResult) -> Unit) = viewModelScope.launch {
        debugEventsRepository.log(DebugLevel.Info, "download", "Download started", result.sourceId, sourceName, result.title)
        _resolvingIds.value = _resolvingIds.value + result.id
        val resolved = resolver.resolve(result)
        val response = if (resolved.pdfUrl != null) {
            downloadsRepository.downloadPdf(resolved, sourceName)
        } else {
            Result.failure(IllegalArgumentException("No PDF found"))
        }
        if (response.isSuccess) {
            debugEventsRepository.log(DebugLevel.Info, "download", "Download finished", result.sourceId, sourceName, resolved.pdfUrl)
        } else {
            debugEventsRepository.log(DebugLevel.Error, "download", "Download failed", result.sourceId, sourceName, response.exceptionOrNull()?.message)
        }
        _resolvingIds.value = _resolvingIds.value - result.id
        onDone(response, resolved)
    }

    fun retry(item: DownloadItem, onDone: (Result<DownloadItem>) -> Unit) = viewModelScope.launch {
        onDone(downloadsRepository.retryDownload(item.id, item.sourceName))
    }

    fun cancel(item: DownloadItem) = viewModelScope.launch {
        downloadsRepository.cancelDownload(item.id)
    }
}
