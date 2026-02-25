package app.shosetsu.android.data.resolver

import app.shosetsu.android.domain.config.ScrapeSourceConfig
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source
import app.shosetsu.android.domain.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PdfResolverChainTest {
    @Test
    fun arxivResolverConvertsAbsToPdf() = runBlocking {
        val chain = PdfResolverChain(listOf(DirectUrlResolver(), ArxivResolver()))
        val input = SearchResult(
            id = "1",
            sourceId = "preset_arxiv",
            title = "t",
            authors = "a",
            year = "2024",
            snippet = "",
            landingUrl = "https://arxiv.org/abs/2401.12345v1"
        )
        val output = chain.resolve(input)
        assertEquals("https://arxiv.org/pdf/2401.12345.pdf", output.pdfUrl)
    }

    @Test
    fun htmlResolverFindsPdfLink() = runBlocking {
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
                    allowedPdfDomains = listOf("example.org")
                )
            )
        )
        val resolver = HtmlLinkResolver { source }
        val html = """
            <html><body><a href="/download/book.pdf">PDF</a></body></html>
        """.trimIndent()
        val found = HtmlLinkResolver.extractPdfFromHtml(html, "https://example.org/book")
        assertNotNull(found)
    }
}

