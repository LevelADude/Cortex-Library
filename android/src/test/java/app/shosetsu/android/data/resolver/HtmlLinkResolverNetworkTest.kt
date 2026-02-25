package app.shosetsu.android.data.resolver

import app.shosetsu.android.domain.config.ScrapeSourceConfig
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source
import app.shosetsu.android.domain.model.SourceType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlLinkResolverNetworkTest {
    @Test
    fun resolverRespectsAllowListAndPdfContentType() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html><body><a href='/file.pdf'>PDF</a></body></html>")
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf")) // HEAD
        server.start()

        val host = server.hostName
        val source = Source(
            id = "scrape",
            name = "Scrape",
            baseUrl = server.url("/").toString(),
            type = SourceType.GenericWeb,
            configJson = SourceConfigCodec.json.encodeToString(
                ScrapeSourceConfig(
                    searchUrlTemplate = server.url("/search?q={query}").toString(),
                    resultItemSelector = ".r",
                    titleSelector = ".t",
                    linkSelector = ".t",
                    enablePdfResolution = true,
                    allowedPdfDomains = listOf(host)
                )
            )
        )
        val resolver = HtmlLinkResolver { source }
        val resolved = resolver.resolve(
            SearchResult(
                id = "1",
                sourceId = "scrape",
                title = "T",
                authors = "A",
                year = "2024",
                snippet = "",
                landingUrl = server.url("/landing").toString()
            )
        )
        assertEquals(server.url("/file.pdf").toString(), resolved.pdfUrl)
        server.shutdown()
    }

    @Test
    fun resolverRejectsDisallowedDomain() = runBlocking {
        val source = Source(
            id = "scrape",
            name = "Scrape",
            baseUrl = "https://example.org",
            type = SourceType.GenericWeb,
            configJson = SourceConfigCodec.json.encodeToString(
                ScrapeSourceConfig(
                    searchUrlTemplate = "https://example.org?q={query}",
                    resultItemSelector = ".r",
                    titleSelector = ".t",
                    linkSelector = ".t",
                    enablePdfResolution = true,
                    allowedPdfDomains = listOf("trusted.org")
                )
            )
        )
        val resolver = HtmlLinkResolver { source }
        val resolved = resolver.resolve(
            SearchResult(
                id = "1",
                sourceId = "scrape",
                title = "T",
                authors = "A",
                year = "2024",
                snippet = "",
                landingUrl = "https://example.org/landing"
            )
        )
        assertNull(resolved.pdfUrl)
    }
}
