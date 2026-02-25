package app.shosetsu.android.extensionbuilder

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionSpecSerializationTest {

	@Test
	fun `basic parsing fields survive serialization roundtrip`() {
		val json = Json { ignoreUnknownKeys = true }
		val original = ExtensionSpec(
			id = "id",
			name = "Name",
			lang = "en",
			baseUrl = "https://example.com",
			createdAt = 1L,
			parsing = ParsingSpec(
				enabled = true,
				mode = ParsingMode.BASIC,
				basic = BasicParsingSpec(
					itemSelector = ".card",
					titleSelector = ".title",
					urlSelector = "a",
					urlAttr = "href",
					imageSelector = "img",
					imageAttr = "src",
					novelTitleSelector = "h1",
					coverSelector = "img.cover",
					descriptionSelector = ".desc",
					authorSelector = ".author a",
					genreSelector = ".genre a",
					chapterItemSelector = ".chapter",
					chapterTitleSelector = "a",
					chapterUrlSelector = "a",
					contentSelector = ".content"
				)
			)
		)

		val encoded = json.encodeToString(original)
		val decoded = json.decodeFromString<ExtensionSpec>(encoded)

		assertEquals(original.parsing, decoded.parsing)
	}
}
