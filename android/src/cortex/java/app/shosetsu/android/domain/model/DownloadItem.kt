package app.shosetsu.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DownloadItem(
    val id: String,
    val title: String,
    val pdfUrl: String,
    val filePath: String,
    val status: String,
    val progress: Int,
    val addedAt: Long = System.currentTimeMillis(),
    val sourceName: String
)
