package app.shosetsu.android.data.connector

import app.shosetsu.android.domain.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PmcParserTest {
    @Test
    fun parsePmcSummaryMapsPmcidAndPdfPattern() {
        val json = javaClass.classLoader!!.getResource("pmc_esummary_sample.json")!!.readText()
        val results = ApiSourceConnector.parsePmcResults(json, "preset_pmc")

        assertEquals(1, results.size)
        val first = results.first()
        assertEquals(ContentType.Paper, first.contentType)
        assertEquals("PMC7891011", first.identifiers?.get("PMCID"))
        assertEquals("https://pmc.ncbi.nlm.nih.gov/articles/PMC7891011/pdf/", first.pdfUrl)
        assertTrue(first.landingUrl!!.contains("PMC7891011"))
    }
}
