package app.shosetsu.android.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shosetsu.android.data.repo.DownloadsRepository
import app.shosetsu.android.domain.model.DownloadItem
import app.shosetsu.android.domain.model.SearchResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(private val downloadsRepository: DownloadsRepository) : ViewModel() {
    val downloads = downloadsRepository.downloadsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun download(result: SearchResult, sourceName: String, onDone: (Result<DownloadItem>) -> Unit) = viewModelScope.launch {
        onDone(downloadsRepository.downloadPdf(result, sourceName))
    }
}
