package app.shosetsu.android.data.connector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArxivParserTest {
    @Test
    fun parseArxivMapsExpectedFields() {
        val xml = javaClass.classLoader!!.getResource("arxiv_sample.xml")!!.readText()
        val results = ApiSourceConnector.parseArxivResults(xml, "preset_arxiv")

        assertEquals(1, results.size)
        val first = results.first()
        assertEquals("A Test arXiv Paper", first.title)
        assertEquals("Jane Doe, John Roe", first.authors)
        assertEquals("2024", first.year)
        assertTrue(first.landingUrl!!.contains("arxiv.org/abs/2401.12345"))
        assertTrue(first.pdfUrl!!.contains("arxiv.org/pdf/2401.12345"))
    }
}

