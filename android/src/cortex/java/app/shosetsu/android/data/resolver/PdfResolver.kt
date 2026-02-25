package app.shosetsu.android.data.resolver

import app.shosetsu.android.domain.model.SearchResult

fun interface PdfResolver {
    suspend fun resolve(result: SearchResult): SearchResult
}

