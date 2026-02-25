package app.shosetsu.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val id: String,
    val sourceId: String,
    val title: String,
    val authors: String,
    val year: String,
    val snippet: String,
    val pdfUrl: String? = null,
    val landingUrl: String? = null,
    val fileSize: String? = null,
    val contentType: ContentType = ContentType.Unknown,
    val language: String? = null,
    val publisherOrVenue: String? = null,
    val identifiers: Map<String, String>? = null,
    val openAccess: Boolean? = null,
    val tags: List<String> = emptyList()
)
