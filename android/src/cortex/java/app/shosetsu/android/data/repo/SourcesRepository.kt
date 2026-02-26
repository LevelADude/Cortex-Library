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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.Request
import java.time.Instant
import java.util.UUID

@Serializable
data class SourceImportPreview(
    val mergedSources: List<Source>,
    val replaced: Int,
    val added: Int,
    val backupPrevious: List<Source>
)

class SourcesRepository(private val dataStore: CortexDataStore) {
    private val json = SourceConfigCodec.json

    private val presets = listOf(
        Source(
            id = "preset_open_alex",
            stablePresetId = "openalex_api",
            name = "OpenAlex",
            baseUrl = "https://api.openalex.org",
            type = SourceType.Api,
            tags = listOf("papers", "oa"),
            contentTypesSupported = "papers",
            configJson = json.encodeToString(ApiSourceConfig(endpointPath = "/works", queryParam = "search")),
            notes = "Built-in OpenAlex Works API preset"
        ),
        Source(
            id = "preset_arxiv",
            stablePresetId = "arxiv_api",
            name = "arXiv",
            baseUrl = "https://export.arxiv.org",
            type = SourceType.Api,
            tags = listOf("papers", "preprints"),
            contentTypesSupported = "papers",
            configJson = json.encodeToString(ApiSourceConfig(endpointPath = "/api/query", queryParam = "search_query", limitParam = "max_results")),
            notes = "Built-in arXiv Atom API preset"
        ),
        Source(
            id = "preset_open_library",
            stablePresetId = "openlibrary_api",
            name = "Open Library",
            baseUrl = "https://openlibrary.org",
            type = SourceType.Api,
            tags = listOf("books", "metadata"),
            contentTypesSupported = "books",
            configJson = json.encodeToString(ApiSourceConfig(endpointPath = "/search.json", queryParam = "q", limitParam = "limit")),
            notes = "Built-in Open Library preset"
        ),
        Source(
            id = "preset_pmc",
            stablePresetId = "pmc_api",
            name = "PubMed Central",
            baseUrl = "https://eutils.ncbi.nlm.nih.gov",
            type = SourceType.Api,
            tags = listOf("papers", "biomedical"),
            contentTypesSupported = "papers",
            configJson = json.encodeToString(ApiSourceConfig(endpointPath = "/entrez/eutils/esearch.fcgi", queryParam = "term", limitParam = "retmax", extraParams = mapOf("db" to "pmc", "retmode" to "json"))),
            notes = "Built-in PubMed Central preset"
        ),
        Source(
            id = "preset_doab",
            stablePresetId = "doab_api",
            name = "DOAB",
            baseUrl = "https://directory.doabooks.org",
            type = SourceType.Api,
            tags = listOf("books", "oa"),
            contentTypesSupported = "books",
            configJson = json.encodeToString(ApiSourceConfig(endpointPath = "/rest/search", queryParam = "query", limitParam = "pageSize")),
            notes = "Built-in Directory of Open Access Books preset"
        ),
        Source(
            id = "preset_standard_ebooks",
            stablePresetId = "standardebooks_scrape",
            name = "Standard Ebooks",
            baseUrl = "https://standardebooks.org",
            type = SourceType.GenericWeb,
            notes = "OA books; PDF may be on landing page.",
            tags = listOf("books", "oa", "scrape"),
            contentTypesSupported = "books",
            enablePdfResolution = true,
            allowedPdfDomains = listOf("standardebooks.org"),
            configJson = json.encodeToString(ScrapeSourceConfig(
                searchUrlTemplate = "https://standardebooks.org/ebooks?query={query}",
                resultItemSelector = "main ol li, .books li",
                titleSelector = "h3 a, .title a, a",
                linkSelector = "h3 a, .title a, a",
                maxPages = 1,
                enablePdfResolution = true,
                allowedPdfDomains = listOf("standardebooks.org")
            ))
        ),
        Source(
            id = "preset_openstax",
            stablePresetId = "openstax_scrape",
            name = "OpenStax",
            baseUrl = "https://openstax.org",
            type = SourceType.GenericWeb,
            notes = "OA textbooks; PDF often linked on landing.",
            tags = listOf("books", "textbooks", "scrape"),
            contentTypesSupported = "books",
            enablePdfResolution = true,
            allowedPdfDomains = listOf("openstax.org"),
            configJson = json.encodeToString(ScrapeSourceConfig(
                searchUrlTemplate = "https://openstax.org/search?query={query}",
                resultItemSelector = "a[href*='/details/books/'], .search-result a[href]",
                titleSelector = ".title, h3, a",
                linkSelector = "a",
                maxPages = 1,
                enablePdfResolution = true,
                allowedPdfDomains = listOf("openstax.org")
            ))
        ),
        Source(
            id = "preset_doaj",
            stablePresetId = "doaj_scrape",
            name = "DOAJ",
            baseUrl = "https://doaj.org",
            type = SourceType.GenericWeb,
            notes = "Often landing-only; PDF depends on provider.",
            tags = listOf("papers", "journals", "scrape"),
            contentTypesSupported = "papers",
            enablePdfResolution = false,
            allowedPdfDomains = listOf("doaj.org"),
            configJson = json.encodeToString(ScrapeSourceConfig(
                searchUrlTemplate = "https://doaj.org/search/articles/{query}",
                resultItemSelector = "[data-testid='search-result'], .search-result",
                titleSelector = "h3 a, h2 a, a",
                linkSelector = "h3 a, h2 a, a",
                maxPages = 1,
                enablePdfResolution = false,
                allowedPdfDomains = listOf("doaj.org")
            ))
        ),
        Source(
            id = "preset_demo_scrape",
            name = "Demo Scrape Source",
            baseUrl = "asset://demo_scrape_search.html",
            type = SourceType.GenericWeb,
            configJson = json.encodeToString(ScrapeSourceConfig(searchUrlTemplate = "asset://demo_scrape_search.html?q={query}", resultItemSelector = ".result-item", titleSelector = ".title", linkSelector = ".title", pdfLinkSelector = ".pdf-link", maxPages = 1)),
            notes = "Local asset-based source used to validate scraping pipeline"
        )
    )

    val sourcesFlow: Flow<List<Source>> = dataStore.sourcesJson().map { raw ->
        raw?.let { runCatching { json.decodeFromString<List<Source>>(it) }.getOrNull() } ?: presets
    }

    val catalogStatusFlow: Flow<CatalogFetchStatus> = dataStore.presetCatalogStatusJson().map { raw ->
        raw?.let { runCatching { json.decodeFromString<CatalogFetchStatus>(it) }.getOrNull() } ?: CatalogFetchStatus()
    }

    suspend fun addSource(name: String, baseUrl: String, type: SourceType, notes: String = "", configJson: String?) {
        val updated = sourcesFlow.first() + Source(id = UUID.randomUUID().toString(), name = name, baseUrl = baseUrl, type = type, notes = notes, configJson = configJson)
        dataStore.saveSourcesJson(json.encodeToString(updated))
    }

    suspend fun updateSource(updatedSource: Source) {
        val updated = sourcesFlow.first().map { if (it.id == updatedSource.id) updatedSource else it }
        dataStore.saveSourcesJson(json.encodeToString(updated))
    }

    suspend fun toggleSource(id: String, enabled: Boolean) {
        val updated = sourcesFlow.first().map { if (it.id == id) it.copy(enabled = enabled) else it }
        dataStore.saveSourcesJson(json.encodeToString(updated))
    }

    suspend fun exportSourcesJson(): String = json.encodeToString(sourcesFlow.first())

    suspend fun exportSourcesAsCatalogJson(): String {
        val current = sourcesFlow.first()
        val catalog = PresetCatalog(
            catalogVersion = 1,
            updatedAt = Instant.now().toString(),
            presets = current.filter { it.stablePresetId != null }.map { it.toCatalogPreset() }
        )
        return json.encodeToString(catalog)
    }

    suspend fun previewImport(jsonPayload: String): SourceImportPreview {
        val imported = json.decodeFromString<List<Source>>(jsonPayload)
        val existing = sourcesFlow.first()
        val mergeResult = SourceImportMerger.merge(existing = existing, imported = imported)
        return SourceImportPreview(mergeResult.merged, mergeResult.replaced, mergeResult.added, existing)
    }

    suspend fun applyImport(preview: SourceImportPreview) {
        dataStore.saveSourcesJson(json.encodeToString(preview.mergedSources))
    }

    suspend fun fetchCatalog(url: String, allowHttpInDevMode: Boolean = false, pinnedDomain: String? = null): Result<PresetCatalog> = runCatching {
        val parsed = java.net.URI(url)
        require(parsed.scheme == "https" || (allowHttpInDevMode && parsed.scheme == "http")) { "Catalog URL must use HTTPS (or HTTP in development mode)" }
        pinnedDomain?.takeIf { it.isNotBlank() }?.let { require(parsed.host == it) { "Catalog host does not match pinned domain" } }

        val request = Request.Builder().url(url).build()
        val body = app.shosetsu.android.data.network.CortexHttpClient.instance.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Fetch failed: HTTP ${response.code}" }
            response.body?.string().orEmpty()
        }
        val catalog = json.decodeFromString<PresetCatalog>(body)
        require(catalog.catalogVersion == 1) { "Unsupported catalogVersion ${catalog.catalogVersion}" }
        require(catalog.presets.distinctBy { it.stablePresetId }.size == catalog.presets.size) { "Duplicate stablePresetId entries" }
        saveCatalogStatus(
            catalogStatusFlow.first().copy(
                lastFetchedAtIso = Instant.now().toString(),
                lastError = null,
                catalogUrl = url,
                pinnedDomain = pinnedDomain,
                developmentMode = allowHttpInDevMode
            )
        )
        catalog
    }.onFailure {
        val status = catalogStatusFlow.first()
        saveCatalogStatus(status.copy(lastError = it.message))
    }

    suspend fun importCatalogPresets(catalog: PresetCatalog, selectedPresetIds: Set<String>, overrideEnabledState: Boolean): SourceMergeResult {
        val existing = sourcesFlow.first()
        val existingByStable = existing.filter { it.stablePresetId != null }.associateBy { it.stablePresetId }
        val merged = existing.toMutableList()
        var replaced = 0
        var added = 0

        catalog.presets.filter { it.stablePresetId in selectedPresetIds }.forEach { preset ->
            val current = existingByStable[preset.stablePresetId]
            if (current != null) {
                val index = merged.indexOfFirst { it.id == current.id }
                if (index >= 0) {
                    merged[index] = preset.toSource(current, overrideEnabled = overrideEnabledState)
                    replaced++
                }
            } else {
                merged += preset.toSource(overrideEnabled = true)
                added++
            }
        }

        dataStore.saveSourcesJson(json.encodeToString(merged))
        val importedIds = catalog.presets.map { it.stablePresetId }.filter { it in selectedPresetIds }
        saveCatalogStatus(catalogStatusFlow.first().copy(importedPresetIds = importedIds))
        return SourceMergeResult(merged, replaced, added)
    }

    suspend fun saveCatalogStatus(status: CatalogFetchStatus) {
        dataStore.savePresetCatalogStatusJson(json.encodeToString(status))
    }

    suspend fun resetToDefaults() {
        dataStore.saveSourcesJson(json.encodeToString(presets))
    }

    private fun Source.toCatalogPreset(): CatalogPreset {
        val stableId = stablePresetId ?: "user_${id.take(12)}"
        return CatalogPreset(
            stablePresetId = stableId,
            name = name,
            type = type,
            baseUrl = baseUrl,
            enabledByDefault = enabled,
            notes = notes,
            tags = tags,
            contentTypesSupported = contentTypesSupported,
            enablePdfResolution = enablePdfResolution,
            allowedPdfDomains = allowedPdfDomains,
            limitOverride = limitOverride,
            configJson = configJson.orEmpty()
        )
    }
}
