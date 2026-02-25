package app.shosetsu.android.extensionbuilder

import kotlinx.serialization.Serializable

/**
 * Builder-side extension specification used to generate a Lua extension scaffold.
 */
@Serializable
data class ExtensionSpec(
	val id: String,
	val name: String,
	val lang: String,
	val baseUrl: String,
	val version: String = "1.0.0",
	val author: String? = null,
	val createdAt: Long,
	val runtimeId: Int = 1,
	val userAgent: String? = null,
	val headers: List<HeaderEntry> = emptyList(),
	val capabilities: ExtensionCapabilities = ExtensionCapabilities(),
	val parsing: ParsingSpec = ParsingSpec()
)

@Serializable
data class HeaderEntry(
	val key: String,
	val value: String
)

@Serializable
data class ExtensionCapabilities(
	val supportsSearch: Boolean = true,
	val supportsLatest: Boolean = true,
	val supportsGenres: Boolean = false,
	val supportsFilters: Boolean = false,
	val supportsMultipleDomains: Boolean = false
)

@Serializable
data class ParsingSpec(
	val enabled: Boolean = false,
	val mode: ParsingMode = ParsingMode.BASIC,
	val basic: BasicParsingSpec = BasicParsingSpec(),
	val luaOverrideSnippet: String = ""
)

@Serializable
enum class ParsingMode {
	BASIC,
	ADVANCED
}

@Serializable
data class BasicParsingSpec(
	val itemSelector: String = "",
	val titleSelector: String = "",
	val titleFrom: ValueSource = ValueSource.TEXT,
	val titleAttr: String? = null,
	val urlSelector: String = "a",
	val urlAttr: String = "href",
	val imageSelector: String = "img",
	val imageAttr: String = "src",
	val novelTitleSelector: String = "",
	val novelTitleFrom: ValueSource = ValueSource.TEXT,
	val novelTitleAttr: String? = null,
	val coverSelector: String = "",
	val coverAttr: String = "src",
	val descriptionSelector: String = "",
	val authorSelector: String = "",
	val genreSelector: String = "",
	val statusSelector: String = "",
	val chapterItemSelector: String = "",
	val chapterTitleSelector: String = "",
	val chapterTitleFrom: ValueSource = ValueSource.TEXT,
	val chapterTitleAttr: String? = null,
	val chapterUrlSelector: String = "a",
	val chapterUrlAttr: String = "href",
	val contentSelector: String = "",
	val removeSelector: String? = null,
	val contentOutput: ContentOutput = ContentOutput.HTML
)

@Serializable
enum class ValueSource {
	TEXT,
	ATTR
}

@Serializable
enum class ContentOutput {
	HTML,
	TEXT
}
