package app.shosetsu.android.data.connector

import android.content.Context
import app.shosetsu.android.data.network.CortexHttpClient
import app.shosetsu.android.domain.config.ScrapeSourceConfig
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.UUID

class ScrapeSourceConnector(private val context: Context) : SourceConnector {
    override suspend fun search(source: Source, query: String, limit: Int): List<SearchResult> {
        val config = SourceConfigCodec.parseScrape(source) ?: return emptyList()
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val initialUrl = config.searchUrlTemplate.replace("{query}", encoded)

        val cappedLimit = config.limitOverride ?: limit
        val allResults = mutableListOf<SearchResult>()
        var pageUrl: String? = initialUrl
        var page = 0
        while (pageUrl != null && page < config.maxPages && allResults.size < cappedLimit) {
            val html = fetchHtml(pageUrl)
            val document = Jsoup.parse(html, source.baseUrl)
            allResults += parseFromDocument(document, source.id, config).take(cappedLimit - allResults.size)
            pageUrl = config.nextPageSelector?.let { selector ->
                document.selectFirst(selector)?.absUrl("href").takeUnless { it.isNullOrBlank() }
            }
            page++
        }
        return allResults
    }

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        if (url.startsWith("asset://")) {
            val assetPath = url.removePrefix("asset://").substringBefore("?")
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        } else {
            val request = Request.Builder().url(url).build()
            CortexHttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
        }
    }

    companion object {
        fun parseFromDocument(document: Document, sourceId: String, config: ScrapeSourceConfig): List<SearchResult> {
            return document.select(config.resultItemSelector).mapNotNull { element ->
                val title = element.selectFirst(config.titleSelector)?.text()?.trim().orEmpty()
                val landing = element.selectFirst(config.linkSelector)?.absUrl("href")
                    .takeUnless { it.isNullOrBlank() }
                    ?: element.selectFirst(config.linkSelector)?.attr("href")
                if (title.isBlank() || landing.isNullOrBlank()) return@mapNotNull null
                val pdf = config.pdfLinkSelector?.let { selector ->
                    element.selectFirst(selector)?.absUrl("href").takeUnless { it.isNullOrBlank() }
                        ?: element.selectFirst(selector)?.attr("href")?.takeIf { it.endsWith(".pdf", true) }
                }
                SearchResult(
                    id = UUID.randomUUID().toString(),
                    sourceId = sourceId,
                    title = title,
                    authors = "",
                    year = "",
                    snippet = "Scraped result",
                    pdfUrl = pdf,
                    landingUrl = landing
                )
            }
        }
    }
}
