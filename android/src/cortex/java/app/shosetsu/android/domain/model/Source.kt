package app.shosetsu.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Source(
    val id: String,
    val stablePresetId: String? = null,
    val name: String,
    val baseUrl: String,
    val type: SourceType,
    val enabled: Boolean = true,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val contentTypesSupported: String = "both",
    val enablePdfResolution: Boolean = false,
    val allowedPdfDomains: List<String> = emptyList(),
    val limitOverride: Int? = null,
    val configJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
