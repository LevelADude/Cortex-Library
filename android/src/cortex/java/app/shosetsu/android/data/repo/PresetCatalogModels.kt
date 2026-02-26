package app.shosetsu.android.data.repo

import app.shosetsu.android.domain.model.Source
import app.shosetsu.android.domain.model.SourceType
import kotlinx.serialization.Serializable

@Serializable
data class PresetCatalog(
    val catalogVersion: Int,
    val updatedAt: String,
    val presets: List<CatalogPreset>
)

@Serializable
data class CatalogPreset(
    val stablePresetId: String,
    val name: String,
    val type: SourceType,
    val baseUrl: String,
    val enabledByDefault: Boolean,
    val notes: String,
    val tags: List<String>,
    val contentTypesSupported: String,
    val enablePdfResolution: Boolean,
    val allowedPdfDomains: List<String>,
    val limitOverride: Int? = null,
    val configJson: String
)

@Serializable
data class CatalogFetchStatus(
    val lastFetchedAtIso: String? = null,
    val lastError: String? = null,
    val catalogUrl: String? = null,
    val pinnedDomain: String? = null,
    val importedPresetIds: List<String> = emptyList(),
    val developmentMode: Boolean = false
)

fun CatalogPreset.toSource(existing: Source? = null, overrideEnabled: Boolean = false): Source {
    val preservedEnabled = if (overrideEnabled || existing == null) enabledByDefault else existing.enabled
    return Source(
        id = existing?.id ?: "preset_$stablePresetId",
        stablePresetId = stablePresetId,
        name = name,
        baseUrl = baseUrl,
        type = type,
        enabled = preservedEnabled,
        notes = notes,
        tags = tags,
        contentTypesSupported = contentTypesSupported,
        enablePdfResolution = enablePdfResolution,
        allowedPdfDomains = allowedPdfDomains,
        limitOverride = limitOverride,
        configJson = configJson,
        createdAt = existing?.createdAt ?: System.currentTimeMillis()
    )
}
