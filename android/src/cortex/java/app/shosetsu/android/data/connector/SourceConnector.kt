package app.shosetsu.android.data.connector

import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source

interface SourceConnector {
    suspend fun search(source: Source, query: String, limit: Int = 25): List<SearchResult>
}
