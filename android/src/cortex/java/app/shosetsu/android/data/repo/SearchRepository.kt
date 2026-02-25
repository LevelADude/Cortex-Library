package app.shosetsu.android.data.repo

import app.shosetsu.android.data.connector.ConnectorRegistry
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class SearchSortMode { Relevance, Newest, TitleAZ }

data class SearchOptions(
    val onlyWithPdf: Boolean = false,
    val sourceFilterIds: Set<String> = emptySet(),
    val sortMode: SearchSortMode = SearchSortMode.Relevance
)

data class SearchResponse(
    val results: List<SearchResult>,
    val sourceErrors: List<String>
)

class SearchRepository(private val connectorRegistry: ConnectorRegistry) {
    private data class CacheEntry(val timestampMs: Long, val response: SearchResponse)
    private val cache = mutableMapOf<String, CacheEntry>()
    private val ttlMs = 60_000L

    suspend fun search(query: String, enabledSources: List<Source>, options: SearchOptions = SearchOptions()): SearchResponse {
        if (query.isBlank()) return SearchResponse(emptyList(), emptyList())
        val now = System.currentTimeMillis()
        val filteredSources = enabledSources.filter { options.sourceFilterIds.isEmpty() || it.id in options.sourceFilterIds }
        val cacheKey = buildString {
            append(query.trim().lowercase())
            append(":")
            append(filteredSources.joinToString(",") { it.id })
            append(":pdf=")
            append(options.onlyWithPdf)
            append(":sort=")
            append(options.sortMode.name)
        }
        cache[cacheKey]?.takeIf { now - it.timestampMs <= ttlMs }?.let { return it.response }

        val merged = mutableListOf<SearchResult>()
        val errors = mutableListOf<String>()
        val semaphore = Semaphore(3)

        coroutineScope {
            filteredSources.map { source ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            connectorRegistry.forSource(source).search(source, query)
                        }.onSuccess { sourceResults ->
                            synchronized(merged) {
                                merged += sourceResults.map { it.copy(sourceId = source.id) }
                            }
                        }.onFailure { throwable ->
                            synchronized(errors) {
                                errors += "${source.name} failed: ${throwable.message ?: "Unknown error"}"
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        val finalResults = merged
            .let { if (options.onlyWithPdf) it.filter { result -> result.pdfUrl != null } else it }
            .let { list ->
                when (options.sortMode) {
                    SearchSortMode.Relevance -> list
                    SearchSortMode.Newest -> list.sortedByDescending { it.year.toIntOrNull() ?: 0 }
                    SearchSortMode.TitleAZ -> list.sortedBy { it.title.lowercase() }
                }
            }

        return SearchResponse(finalResults, errors).also {
            cache[cacheKey] = CacheEntry(now, it)
        }
    }
}
