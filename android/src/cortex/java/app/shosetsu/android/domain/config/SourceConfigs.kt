package app.shosetsu.android.domain.config

import app.shosetsu.android.domain.model.Source
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ApiSourceConfig(
    val endpointPath: String,
    val queryParam: String = "search",
    val extraParams: Map<String, String> = emptyMap(),
    val headerApiKeyName: String? = null,
    val apiKey: String? = null,
    val limitParam: String = "per-page"
)

@Serializable
data class ScrapeSourceConfig(
    val searchUrlTemplate: String,
    val resultItemSelector: String,
    val titleSelector: String,
    val linkSelector: String,
    val pdfLinkSelector: String? = null,
    val nextPageSelector: String? = null,
    val maxPages: Int = 1,
    val enablePdfResolution: Boolean = false,
    val allowedPdfDomains: List<String> = emptyList()
)

object SourceConfigCodec {
    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parseApi(source: Source): ApiSourceConfig? = source.configJson?.let { runCatching { json.decodeFromString<ApiSourceConfig>(it) }.getOrNull() }

    fun parseScrape(source: Source): ScrapeSourceConfig? = source.configJson?.let { runCatching { json.decodeFromString<ScrapeSourceConfig>(it) }.getOrNull() }
}
