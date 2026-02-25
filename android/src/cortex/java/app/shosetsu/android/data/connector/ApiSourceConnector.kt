package app.shosetsu.android.data.connector

import app.shosetsu.android.data.network.CortexHttpClient
import app.shosetsu.android.domain.config.ApiSourceConfig
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.UUID

class ApiSourceConnector : SourceConnector {
    private val sourceLocks = mutableMapOf<String, Mutex>()
    private val lastCallBySource = mutableMapOf<String, Long>()

    override suspend fun search(source: Source, query: String, limit: Int): List<SearchResult> {
        val config = SourceConfigCodec.parseApi(source)
            ?: defaultApiConfigFor(source)
            ?: return emptyList()

        val lock = sourceLocks.getOrPut(source.id) { Mutex() }
        lock.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - (lastCallBySource[source.id] ?: 0L)
            val minIntervalMs = 300L // TODO: replace with per-source policy persisted in source configuration.
            if (elapsed in 0 until minIntervalMs) delay(minIntervalMs - elapsed)
            lastCallBySource[source.id] = System.currentTimeMillis()
        }

        val url = buildUrl(source.baseUrl, config, query, limit) ?: return emptyList()
        val requestBuilder = Request.Builder().url(url)
        if (!config.headerApiKeyName.isNullOrBlank() && !config.apiKey.isNullOrBlank()) {
            requestBuilder.header(config.headerApiKeyName, config.apiKey)
        }

        val body = withContext(Dispatchers.IO) {
            CortexHttpClient.instance.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
        }

        return if (source.id.contains("open_alex", ignoreCase = true) || source.name.contains("openalex", ignoreCase = true)) {
            parseOpenAlexResults(body, source.id)
        } else {
            emptyList()
        }
    }

    private fun buildUrl(baseUrl: String, config: ApiSourceConfig, query: String, limit: Int): String? {
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        val endpoint = config.endpointPath.trim().removePrefix("/")
        val builder = base.newBuilder()
        if (endpoint.isNotBlank()) builder.addPathSegments(endpoint)
        builder.addQueryParameter(config.queryParam, query)
        config.extraParams.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        builder.addQueryParameter("per-page", limit.toString())
        return builder.build().toString()
    }

    private fun defaultApiConfigFor(source: Source): ApiSourceConfig? {
        if (source.id.contains("open_alex", ignoreCase = true) || source.name.contains("openalex", ignoreCase = true)) {
            return ApiSourceConfig(endpointPath = "/works", queryParam = "search")
        }
        return null
    }

    companion object {
        fun parseOpenAlexResults(jsonBody: String, sourceId: String): List<SearchResult> {
            val root = runCatching { SourceConfigCodec.json.parseToJsonElement(jsonBody).jsonObject }.getOrNull() ?: return emptyList()
            val results = root["results"] as? JsonArray ?: return emptyList()
            return results.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val title = obj["display_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val year = obj["publication_year"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val authors = extractAuthors(obj["authorships"])
                val primaryLanding = obj["primary_location"]?.jsonObject?.get("landing_page_url")?.jsonPrimitive?.contentOrNull
                val openAlexId = obj["id"]?.jsonPrimitive?.contentOrNull
                val pdfFromPrimary = obj["primary_location"]?.jsonObject?.get("pdf_url")?.jsonPrimitive?.contentOrNull
                val bestOa = obj["best_oa_location"]?.jsonObject?.get("pdf_url")?.jsonPrimitive?.contentOrNull
                // TODO: OpenAlex may omit direct PDF URLs for some records. Keep landing URL fallback.
                SearchResult(
                    id = UUID.randomUUID().toString(),
                    sourceId = sourceId,
                    title = title,
                    authors = authors,
                    year = year,
                    snippet = obj["abstract_inverted_index"]?.let { "Abstract available" } ?: "No abstract snippet",
                    pdfUrl = pdfFromPrimary ?: bestOa,
                    landingUrl = primaryLanding ?: openAlexId
                )
            }
        }

        private fun extractAuthors(authorships: JsonElement?): String {
            val array = authorships as? JsonArray ?: return ""
            return array.mapNotNull { auth ->
                (auth as? JsonObject)
                    ?.get("author")
                    ?.jsonObject
                    ?.get("display_name")
                    ?.jsonPrimitive
                    ?.contentOrNull
            }.joinToString(", ")
        }
    }
}
