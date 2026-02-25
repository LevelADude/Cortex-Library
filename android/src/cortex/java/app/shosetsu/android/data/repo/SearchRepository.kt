package app.shosetsu.android.data.repo

import app.shosetsu.android.data.connector.ConnectorRegistry
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source

data class SearchResponse(
    val results: List<SearchResult>,
    val sourceErrors: List<String>
)

class SearchRepository(private val connectorRegistry: ConnectorRegistry) {
    private data class CacheEntry(val timestampMs: Long, val response: SearchResponse)
    private val cache = mutableMapOf<String, CacheEntry>()
    private val ttlMs = 60_000L

    suspend fun search(query: String, enabledSources: List<Source>): SearchResponse {
        if (query.isBlank()) return SearchResponse(emptyList(), emptyList())
        val now = System.currentTimeMillis()
        val cacheKey = query.trim().lowercase() + ":" + enabledSources.joinToString(",") { it.id }
        cache[cacheKey]?.takeIf { now - it.timestampMs <= ttlMs }?.let { return it.response }

        val merged = mutableListOf<SearchResult>()
        val errors = mutableListOf<String>()

        enabledSources.forEach { source ->
            runCatching {
                connectorRegistry.forSource(source).search(source, query)
            }.onSuccess { sourceResults ->
                merged += sourceResults.map { it.copy(sourceId = source.id) }
            }.onFailure { throwable ->
                errors += "${source.name} failed: ${throwable.message ?: "Unknown error"}"
            }
        }

        return SearchResponse(merged, errors).also {
            cache[cacheKey] = CacheEntry(now, it)
        }
    }
}
