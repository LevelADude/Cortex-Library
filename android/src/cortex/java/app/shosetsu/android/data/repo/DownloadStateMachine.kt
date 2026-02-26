package app.shosetsu.android.data.repo

import app.shosetsu.android.domain.model.DownloadItem

object DownloadStateMachine {
    private const val maxRetries = 2

    fun shouldStartNew(existing: List<DownloadItem>, pdfUrl: String): Boolean {
        if (existing.any { it.pdfUrl == pdfUrl && (it.status == "queued" || it.status == "downloading" || it.status == "completed") }) return false
        return true
    }

    fun nextRetryAllowed(item: DownloadItem): Boolean = item.status == "failed" && item.attempts <= maxRetries
}
