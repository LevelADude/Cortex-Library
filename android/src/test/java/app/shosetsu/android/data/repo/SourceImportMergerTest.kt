package app.shosetsu.android.data.repo

import app.shosetsu.android.domain.model.Source
import app.shosetsu.android.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceImportMergerTest {
    @Test
    fun merge_replacesByCompositeAndId() {
        val existing = listOf(
            Source(id = "1", name = "OpenAlex", baseUrl = "https://api.openalex.org", type = SourceType.Api),
            Source(id = "2", name = "DOAB", baseUrl = "https://directory.doabooks.org", type = SourceType.Api)
        )
        val imported = listOf(
            Source(id = "x", name = "OpenAlex", baseUrl = "https://api.openalex.org", type = SourceType.Api, notes = "updated"),
            Source(id = "2", name = "DOAB", baseUrl = "https://directory.doabooks.org", type = SourceType.Api, notes = "new"),
            Source(id = "3", name = "Custom", baseUrl = "https://example.org", type = SourceType.GenericWeb)
        )

        val merged = SourceImportMerger.merge(existing, imported)

        assertEquals(2, merged.replaced)
        assertEquals(1, merged.added)
        assertEquals(3, merged.merged.size)
    }

    @Test
    fun merge_replacesByStablePresetId_and_preservesUserSourceWithoutStableId() {
        val existing = listOf(
            Source(id = "1", stablePresetId = "openalex_api", name = "OpenAlex", baseUrl = "https://api.openalex.org", type = SourceType.Api, enabled = false),
            Source(id = "u1", name = "My Custom", baseUrl = "https://my.local", type = SourceType.GenericWeb)
        )
        val imported = listOf(
            Source(id = "x", stablePresetId = "openalex_api", name = "OpenAlex Updated", baseUrl = "https://api.openalex.org", type = SourceType.Api),
            Source(id = "u2", name = "Another", baseUrl = "https://example.org", type = SourceType.Api)
        )

        val merged = SourceImportMerger.merge(existing, imported)
        assertEquals(2, merged.replaced)
        assertEquals(0, merged.added)
        assertTrue(merged.merged.any { it.id == "u1" })
    }
}
