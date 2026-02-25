package app.shosetsu.android.data.connector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OpenAlexParserTest {
    @Test
    fun parseOpenAlexMapsExpectedFields() {
        val json = javaClass.classLoader!!.getResource("openalex_sample.json")!!.readText()
        val results = ApiSourceConnector.parseOpenAlexResults(json, "preset_open_alex")

        assertEquals(1, results.size)
        val first = results.first()
        assertEquals("DNA Analysis in Modern Labs", first.title)
        assertEquals("Alice Doe, Bob Roe", first.authors)
        assertEquals("2021", first.year)
        assertEquals("https://journal.example.com/paper-123", first.landingUrl)
        assertNotNull(first.pdfUrl)
    }
}
