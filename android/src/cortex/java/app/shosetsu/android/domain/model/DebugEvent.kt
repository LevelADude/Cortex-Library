package app.shosetsu.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class DebugLevel { Info, Warn, Error }

@Serializable
data class DebugEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val level: DebugLevel,
    val category: String,
    val sourceId: String? = null,
    val sourceName: String? = null,
    val message: String,
    val details: String? = null
)
