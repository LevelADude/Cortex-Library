package app.shosetsu.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ContentType {
    Paper,
    Book,
    Unknown
}
