package app.shosetsu.android.extensionbuilder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionGeneratorTest {

	private val baseSpec = ExtensionSpec(
		id = "builder.id",
		name = "Builder",
		lang = "en",
		baseUrl = "https://example.com",
		version = "1.0.0",
		createdAt = 1L,
		runtimeId = 12345
	)

	@Test
	fun `generator output remains template when parsing disabled`() {
		val lua = ExtensionGenerator().generateLua(baseSpec)
		assertTrue(lua.contains("local function parseListingDocument(document, requestURL, sourceLabel)"))
		assertTrue(lua.contains("local selectors = { \".novel-item\""))
		assertTrue(lua.contains("return \"\""))
	}

	@Test
	fun `generator output contains parsing logic when basic parsing enabled`() {
		val lua = ExtensionGenerator().generateLua(
			baseSpec.copy(
				parsing = ParsingSpec(
					enabled = true,
					mode = ParsingMode.BASIC,
					basic = BasicParsingSpec(
						itemSelector = ".novel-item",
						titleSelector = ".title",
						urlSelector = "a",
						urlAttr = "href",
						imageSelector = "img",
						imageAttr = "src",
						novelTitleSelector = "h1",
						coverSelector = ".cover img",
						descriptionSelector = ".desc",
						authorSelector = ".author a",
						genreSelector = ".genre a",
						chapterItemSelector = ".chapter-item",
						chapterTitleSelector = "a",
						chapterUrlSelector = "a",
						contentSelector = ".chapter-content"
					)
				)
			)
		)

		assertTrue(lua.contains("local cards = trySelect(document, \".novel-item\")"))
		assertTrue(lua.contains("local function parseNovel(novelURL, loadChapters)"))
		assertTrue(lua.contains("local contentNode = firstElement(document, \".chapter-content\")"))
		assertTrue(lua.contains("normalizeURL"))
	}

	@Test
	fun `advanced snippet global assignment is rejected`() {
		val result = ExtensionSpecValidator().validate(
			baseSpec.copy(
				parsing = ParsingSpec(
					enabled = true,
					mode = ParsingMode.ADVANCED,
					luaOverrideSnippet = "x = 1\nlocal y = 2"
				)
			)
		)
		assertFalse(result.isValid)
		assertTrue(result.errors.any { it.contains("Global assignments are not allowed") })
	}
}
