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

    suspend fun downloadPdf(result: SearchResult, sourceName: String): Result<DownloadItem> = runCatching {
        val pdfUrl = result.pdfUrl ?: throw IllegalArgumentException("No PDF available for this result")
        val safeTitle = result.title.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val fileName = if (safeTitle.endsWith(".pdf", ignoreCase = true)) safeTitle else "$safeTitle.pdf"
        val dir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
        val file = File(dir, fileName)

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
}
