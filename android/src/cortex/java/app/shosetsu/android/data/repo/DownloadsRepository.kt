package app.shosetsu.android.data.repo

import android.content.Context
import app.shosetsu.android.data.network.CortexHttpClient
import app.shosetsu.android.data.store.CortexDataStore
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.DownloadItem
import app.shosetsu.android.domain.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownloadsRepository(
    private val context: Context,
    private val dataStore: CortexDataStore
) {
    private val json = SourceConfigCodec.json
    private val activeDownloads = ConcurrentHashMap<String, String>()

    val downloadsFlow: Flow<List<DownloadItem>> = dataStore.downloadsJson().map { raw ->
        raw?.let { runCatching { json.decodeFromString<List<DownloadItem>>(it) }.getOrNull() } ?: emptyList()
    }

    val downloadDirectoryFlow: Flow<String?> = dataStore.downloadDirectory()
    val pdfOnlyModeFlow: Flow<Boolean> = dataStore.pdfOnlyMode()

    suspend fun setDownloadDirectory(path: String) = dataStore.saveDownloadDirectory(path)

    suspend fun downloadPdf(result: SearchResult, sourceName: String): Result<DownloadItem> = runCatching {
        val pdfOnlyMode = pdfOnlyModeFlow.first()
        require(pdfOnlyMode) { "Only PDF downloads are currently supported" }

        val pdfUrl = result.pdfUrl ?: throw IllegalArgumentException("No PDF available for this result")
        if (!pdfUrl.contains(".pdf", ignoreCase = true) && !pdfUrl.contains("/pdf", ignoreCase = true)) {
            throw IllegalArgumentException("Unsupported format. PDF-only downloads are enabled.")
        }

        val currentDownloads = downloadsFlow.first()
        val existing = currentDownloads.firstOrNull { it.pdfUrl == pdfUrl && it.status == "completed" }
        if (existing != null) return@runCatching existing
        if (!DownloadStateMachine.shouldStartNew(currentDownloads, pdfUrl) || activeDownloads.contains(pdfUrl)) {
            throw IllegalStateException("Download already in progress for this PDF")
        }

        val itemId = UUID.randomUUID().toString()
        activeDownloads[pdfUrl] = itemId
        updateOrInsert(
            DownloadItem(
                id = itemId,
                title = result.title,
                pdfUrl = pdfUrl,
                filePath = "",
                status = "queued",
                progress = 0,
                sourceName = sourceName,
                attempts = 0
            )
        )

        try {
            val baseDir = downloadDirectoryFlow.first()?.takeIf { it.isNotBlank() }?.let(::File)
                ?: File(context.getExternalFilesDir(null), "downloads")
            baseDir.mkdirs()
            val file = nextAvailableFile(baseDir, buildSafeFileName(result, sourceName))

            var attempt = 0
            var lastError: Throwable? = null
            while (attempt < 3) {
                attempt++
                updateStatus(itemId, "downloading", (attempt - 1) * 30, attempt, file.absolutePath)
                val run = runCatching {
                    withContext(Dispatchers.IO) {
                        val req = Request.Builder().url(pdfUrl).build()
                        CortexHttpClient.instance.newCall(req).execute().use { response ->
                            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                            response.body?.byteStream()?.use { input ->
                                file.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }
                if (run.isSuccess) {
                    val completed = updateStatus(itemId, "completed", 100, attempt, file.absolutePath)
                    return@runCatching completed
                }
                lastError = run.exceptionOrNull()
                if (attempt < 3) delay(attempt * 500L)
            }
            updateStatus(itemId, "failed", 0, 3, file.absolutePath)
            throw IllegalStateException(lastError?.message ?: "Download failed")
        } finally {
            activeDownloads.remove(pdfUrl)
        }
    }

    suspend fun retryDownload(id: String, sourceName: String): Result<DownloadItem> {
        val item = downloadsFlow.first().firstOrNull { it.id == id } ?: return Result.failure(IllegalArgumentException("Download not found"))
        if (!DownloadStateMachine.nextRetryAllowed(item)) return Result.failure(IllegalStateException("Retry limit reached"))
        return downloadPdf(
            SearchResult(
                id = item.id,
                sourceId = "",
                title = item.title,
                authors = "",
                year = "",
                snippet = "",
                pdfUrl = item.pdfUrl
            ),
            sourceName = sourceName
        )
    }

    suspend fun cancelDownload(id: String) {
        val item = downloadsFlow.first().firstOrNull { it.id == id } ?: return
        activeDownloads.remove(item.pdfUrl)
        updateStatus(id, "failed", item.progress, item.attempts, item.filePath)
    }

    private suspend fun updateStatus(id: String, status: String, progress: Int, attempts: Int, filePath: String): DownloadItem {
        val current = downloadsFlow.first().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        val updated = if (index >= 0) {
            current[index].copy(status = status, progress = progress, attempts = attempts, filePath = filePath)
        } else {
            throw IllegalArgumentException("Download not found")
        }
        current[index] = updated
        dataStore.saveDownloadsJson(json.encodeToString(current))
        return updated
    }

    private suspend fun updateOrInsert(item: DownloadItem) {
        val current = downloadsFlow.first().toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index >= 0) current[index] = item else current.add(0, item)
        dataStore.saveDownloadsJson(json.encodeToString(current))
    }

    private fun buildSafeFileName(result: SearchResult, sourceName: String): String {
        val sourceSegment = sourceName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val titleSegment = result.title.replace("[^a-zA-Z0-9._-]".toRegex(), "_").trim('_')
        val yearSegment = result.year.takeIf { it.isNotBlank() } ?: "n.d"
        val base = "${titleSegment.ifBlank { "document" }}_${yearSegment}_$sourceSegment"
        return base.take(90) + ".pdf"
    }

    private fun nextAvailableFile(dir: File, preferredName: String): File {
        var file = File(dir, preferredName)
        var index = 1
        val stem = preferredName.removeSuffix(".pdf")
        while (file.exists()) {
            file = File(dir, "${stem}_$index.pdf")
            index += 1
        }
        return file
    }
}
