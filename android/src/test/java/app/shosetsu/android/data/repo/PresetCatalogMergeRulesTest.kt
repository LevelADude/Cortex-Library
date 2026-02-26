package app.shosetsu.android.data.repo

import app.shosetsu.android.domain.model.Source
import app.shosetsu.android.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PresetCatalogMergeRulesTest {
    @Test
    fun toSource_preserves_enabled_when_not_overriding() {
        val existing = Source(
            id = "1",
            stablePresetId = "openalex_api",
            name = "OpenAlex",
            baseUrl = "https://api.openalex.org",
            type = SourceType.Api,
            enabled = false
        )
        val preset = CatalogPreset(
            stablePresetId = "openalex_api",
            name = "OpenAlex Updated",
            type = SourceType.Api,
            baseUrl = "https://api.openalex.org",
            enabledByDefault = true,
            notes = "n",
            tags = emptyList(),
            contentTypesSupported = "papers",
            enablePdfResolution = false,
            allowedPdfDomains = emptyList(),
            limitOverride = null,
            configJson = "{}"
        )

        val merged = preset.toSource(existing = existing, overrideEnabled = false)
        assertFalse(merged.enabled)
        assertEquals("OpenAlex Updated", merged.name)
    }
}
