package app.shosetsu.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Source(
    val id: String,
    val name: String,
    val baseUrl: String,
    val type: SourceType,
    val enabled: Boolean = true,
    val notes: String = "",
    val configJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
