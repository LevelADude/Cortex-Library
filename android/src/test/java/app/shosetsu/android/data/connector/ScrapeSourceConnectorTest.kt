package app.shosetsu.android.data.connector

import app.shosetsu.android.domain.config.ScrapeSourceConfig
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrapeSourceConnectorTest {
    @Test
    fun parseFromDocumentExtractsLandingAndOptionalPdf() {
        val html = """
            <html><body>
              <div class='result-item'>
                <a class='title' href='https://example.org/a'>A</a>
                <a class='pdf-link' href='https://example.org/a.pdf'>PDF</a>
              </div>
              <div class='result-item'>
                <a class='title' href='https://example.org/b'>B</a>
              </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://example.org")
        val config = ScrapeSourceConfig(
            searchUrlTemplate = "https://example.org/search?q={query}",
            resultItemSelector = ".result-item",
            titleSelector = ".title",
            linkSelector = ".title",
            pdfLinkSelector = ".pdf-link"
        )

        val results = ScrapeSourceConnector.parseFromDocument(doc, "demo", config)
        assertEquals(2, results.size)
        assertEquals("https://example.org/a.pdf", results[0].pdfUrl)
        assertNull(results[1].pdfUrl)
        assertEquals("https://example.org/b", results[1].landingUrl)
    }
}
