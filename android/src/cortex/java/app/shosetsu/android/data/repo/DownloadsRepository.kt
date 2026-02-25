package app.shosetsu.android.data.repo

import android.content.Context
import app.shosetsu.android.data.network.CortexHttpClient
import app.shosetsu.android.data.store.CortexDataStore
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.DownloadItem
import app.shosetsu.android.domain.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.Request
import java.io.File
import java.util.UUID

class DownloadsRepository(
    private val context: Context,
    private val dataStore: CortexDataStore
) {
    private val json = SourceConfigCodec.json

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

        val baseDir = downloadDirectoryFlow.first()?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(context.getExternalFilesDir(null), "downloads")
        baseDir.mkdirs()
        val file = nextAvailableFile(baseDir, buildSafeFileName(result, sourceName))

        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(pdfUrl).build()
            CortexHttpClient.instance.newCall(req).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }

        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            title = result.title,
            pdfUrl = pdfUrl,
            filePath = file.absolutePath,
            status = "Completed",
            progress = 100,
            sourceName = sourceName
        )
        val updated = listOf(item) + downloadsFlow.first()
        dataStore.saveDownloadsJson(json.encodeToString(updated))
        item
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
