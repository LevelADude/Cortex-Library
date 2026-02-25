package app.shosetsu.android.data.repo

import app.shosetsu.android.data.store.CortexDataStore
import app.shosetsu.android.domain.config.ApiSourceConfig
import app.shosetsu.android.domain.config.ScrapeSourceConfig
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.Source
import app.shosetsu.android.domain.model.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import java.util.UUID

class SourcesRepository(private val dataStore: CortexDataStore) {
    private val json = SourceConfigCodec.json

    private val presets = listOf(
        Source(
            id = "preset_open_alex",
            name = "OpenAlex",
            baseUrl = "https://api.openalex.org",
            type = SourceType.Api,
            configJson = json.encodeToString(ApiSourceConfig(endpointPath = "/works", queryParam = "search")),
            notes = "Built-in OpenAlex Works API preset"
        ),
        Source(
            id = "preset_arxiv",
            name = "arXiv",
            baseUrl = "https://export.arxiv.org",
            type = SourceType.Api,
            configJson = json.encodeToString(
                ApiSourceConfig(
                    endpointPath = "/api/query",
                    queryParam = "search_query",
                    limitParam = "max_results"
                )
            ),
            notes = "Built-in arXiv Atom API preset"
        ),
        Source(
            id = "preset_open_library",
            name = "Open Library",
            baseUrl = "https://openlibrary.org",
            type = SourceType.Api,
            configJson = json.encodeToString(
                ApiSourceConfig(
                    endpointPath = "/search.json",
                    queryParam = "q",
                    limitParam = "limit"
                )
            ),
            notes = "Built-in Open Library preset (TODO: evaluate DOAB selectors/API stability)"
        ),
        Source(
            id = "preset_demo_scrape",
            name = "Demo Scrape Source",
            baseUrl = "asset://demo_scrape_search.html",
            type = SourceType.GenericWeb,
            configJson = json.encodeToString(
                ScrapeSourceConfig(
                    searchUrlTemplate = "asset://demo_scrape_search.html?q={query}",
                    resultItemSelector = ".result-item",
                    titleSelector = ".title",
                    linkSelector = ".title",
                    pdfLinkSelector = ".pdf-link",
                    maxPages = 1
                )
            ),
            notes = "Local asset-based source used to validate scraping pipeline"
        )
    )

    val sourcesFlow: Flow<List<Source>> = dataStore.sourcesJson().map { raw ->
        raw?.let { runCatching { json.decodeFromString<List<Source>>(it) }.getOrNull() } ?: presets
    }

    suspend fun addSource(name: String, baseUrl: String, type: SourceType, notes: String = "", configJson: String?) {
        val updated = sourcesFlow.first() + Source(
            id = UUID.randomUUID().toString(),
            name = name,
            baseUrl = baseUrl,
            type = type,
            notes = notes,
            configJson = configJson
        )
        dataStore.saveSourcesJson(json.encodeToString(updated))
    }

    suspend fun toggleSource(id: String, enabled: Boolean) {
        val updated = sourcesFlow.first().map { if (it.id == id) it.copy(enabled = enabled) else it }
        dataStore.saveSourcesJson(json.encodeToString(updated))
    }
}
