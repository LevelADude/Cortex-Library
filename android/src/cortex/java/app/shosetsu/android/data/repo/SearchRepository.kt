package app.shosetsu.android.data.repo

import app.shosetsu.android.data.connector.ConnectorRegistry
import app.shosetsu.android.data.store.CortexDataStore
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.ContentType
import app.shosetsu.android.domain.model.DebugLevel
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable

enum class SearchSortMode { Relevance, Newest, TitleAZ }
enum class ContentTypeFilter { All, BooksOnly, PapersOnly }

data class SearchOptions(
    val onlyWithPdf: Boolean = false,
    val sourceFilterIds: Set<String> = emptySet(),
    val sortMode: SearchSortMode = SearchSortMode.Relevance,
    val contentTypeFilter: ContentTypeFilter = ContentTypeFilter.All
)

data class SearchResponse(
    val results: List<SearchResult>,
    val sourceErrors: List<String>,
    val fromCache: Boolean = false
)

@Serializable
private data class PersistedSearchCache(val entries: List<PersistedSearchCacheEntry>)

@Serializable
private data class PersistedSearchCacheEntry(
    val key: String,
    val timestampMs: Long,
    val response: SearchResponseSerializable
)

@Serializable
private data class SearchResponseSerializable(
    val results: List<SearchResult>,
    val sourceErrors: List<String>
)

class SearchRepository(
    private val connectorRegistry: ConnectorRegistry,
    private val dataStore: CortexDataStore,
    private val debugEventsRepository: DebugEventsRepository
) {
    private data class CacheEntry(val timestampMs: Long, val response: SearchResponse)
    private val cache = linkedMapOf<String, CacheEntry>()
    private val ttlMs = 60_000L
    private val maxPersistedEntries = 10
    private val json = SourceConfigCodec.json

    suspend fun search(query: String, enabledSources: List<Source>, options: SearchOptions = SearchOptions(), offlineMode: Boolean = false): SearchResponse {
        if (query.isBlank()) return SearchResponse(emptyList(), emptyList())
        val now = System.currentTimeMillis()
        val filteredSources = enabledSources.filter { options.sourceFilterIds.isEmpty() || it.id in options.sourceFilterIds }
        val cacheKey = buildCacheKey(query, filteredSources, options)

        if (offlineMode) {
            val cached = cachedResponse(cacheKey, now)
            debugEventsRepository.log(DebugLevel.Info, "search", "Offline search returned cached data", details = "query=$query cached=${cached != null}")
            return cached ?: SearchResponse(emptyList(), listOf("Offline and no cached results available"), fromCache = true)
        }

        cachedResponse(cacheKey, now)?.let { return it }

        val merged = mutableListOf<SearchResult>()
        val errors = mutableListOf<String>()
        val semaphore = Semaphore(3)

        coroutineScope {
            filteredSources.map { source ->
                async {
                    semaphore.withPermit {
                        val started = System.currentTimeMillis()
                        debugEventsRepository.log(DebugLevel.Info, "search", "Source search started", source.id, source.name, "query=$query")
                        runCatching {
                            connectorRegistry.forSource(source).search(source, query)
                        }.onSuccess { sourceResults ->
                            synchronized(merged) { merged += sourceResults.map { it.copy(sourceId = source.id) } }
                            debugEventsRepository.log(
                                DebugLevel.Info,
                                "search",
                                "Source search finished",
                                source.id,
                                source.name,
                                "durationMs=${System.currentTimeMillis() - started}, results=${sourceResults.size}"
                            )
                        }.onFailure { throwable ->
                            synchronized(errors) { errors += "${source.name} failed: ${throwable.message ?: "Unknown error"}" }
                            debugEventsRepository.log(
                                DebugLevel.Error,
                                "search",
                                "Source search failed",
                                source.id,
                                source.name,
                                throwable.message
                            )
                        }
                    }
                }
            }.awaitAll()
        }

        val finalResults = merged
            .let { if (options.onlyWithPdf) it.filter { result -> result.pdfUrl != null } else it }
            .let { list ->
                when (options.contentTypeFilter) {
                    ContentTypeFilter.All -> list
                    ContentTypeFilter.BooksOnly -> list.filter { it.contentType == ContentType.Book }
                    ContentTypeFilter.PapersOnly -> list.filter { it.contentType == ContentType.Paper }
                }
            }
            .let { list ->
                when (options.sortMode) {
                    SearchSortMode.Relevance -> list
                    SearchSortMode.Newest -> list.sortedByDescending { it.year.toIntOrNull() ?: 0 }
                    SearchSortMode.TitleAZ -> list.sortedBy { it.title.lowercase() }
                }
            }

        val response = SearchResponse(finalResults, errors)
        cache[cacheKey] = CacheEntry(now, response)
        persistCache(cacheKey, now, response)
        return response
    }

    private suspend fun cachedResponse(cacheKey: String, now: Long): SearchResponse? {
        cache[cacheKey]?.takeIf { now - it.timestampMs <= ttlMs }?.let { return it.response.copy(fromCache = true) }
        val persisted = readPersistedCache()[cacheKey] ?: return null
        val fromDisk = SearchResponse(persisted.response.results, persisted.response.sourceErrors, fromCache = true)
        cache[cacheKey] = CacheEntry(persisted.timestampMs, fromDisk)
        return fromDisk
    }

    private suspend fun persistCache(cacheKey: String, now: Long, response: SearchResponse) {
        val updated = readPersistedCache().toMutableMap().apply {
            this[cacheKey] = PersistedSearchCacheEntry(cacheKey, now, SearchResponseSerializable(response.results, response.sourceErrors))
        }
        val trimmed = SearchCachePolicy.trimNewest(updated.values.toList(), maxPersistedEntries) { it.timestampMs }
        dataStore.saveSearchCacheJson(json.encodeToString(PersistedSearchCache(trimmed)))
    }

    private suspend fun readPersistedCache(): Map<String, PersistedSearchCacheEntry> {
        return dataStore.searchCacheJson().first()?.let {
            runCatching { json.decodeFromString<PersistedSearchCache>(it) }.getOrNull()
        }?.entries?.associateBy { it.key }.orEmpty()
    }

    private fun buildCacheKey(query: String, filteredSources: List<Source>, options: SearchOptions): String = buildString {
        append(query.trim().lowercase())
        append(":")
        append(filteredSources.joinToString(",") { it.id })
        append(":pdf=")
        append(options.onlyWithPdf)
        append(":sort=")
        append(options.sortMode.name)
        append(":ctype=")
        append(options.contentTypeFilter.name)
    }
}
