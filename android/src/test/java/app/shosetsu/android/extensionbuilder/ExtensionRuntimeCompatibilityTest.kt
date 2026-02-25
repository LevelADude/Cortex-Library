package app.shosetsu.android.extensionbuilder

import app.shosetsu.lib.lua.LuaExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRuntimeCompatibilityTest {

	@Test
	fun `generated lua exposes required metadata and functions for runtime`() {
		val lua = ExtensionGenerator().generateLua(
			ExtensionSpec(
				id = "builder.runtime",
				name = "Runtime",
				lang = "en",
				baseUrl = "https://example.com",
				version = "1.0.0",
				createdAt = 1L,
				runtimeId = 778899
			)
		)

		assertTrue(lua.contains("id = ID"))
		assertTrue(lua.contains("name = NAME"))
		assertTrue(lua.contains("lang = LANG"))
		assertTrue(lua.contains("local function parseNovel(novelURL, loadChapters)"))
		assertTrue(lua.contains("local function getPassage(chapterURL)"))
		assertTrue(lua.contains("search = search"))
	}

	@Test
	fun `runtime can instantiate generated extension and read metadata`() {
		val lua = ExtensionGenerator().generateLua(
			ExtensionSpec(
				id = "builder.runtime",
				name = "Runtime",
				lang = "en",
				baseUrl = "https://example.com",
				version = "1.0.0",
				createdAt = 1L,
				runtimeId = 778899
			)
		)

		val extension = LuaExtension(lua, "builder.runtime")

		assertEquals(778899, extension.formatterID)
		assertEquals("Runtime", extension.exMetaData.name)
		assertEquals("en", extension.exMetaData.lang)
		assertTrue(extension.exMetaData.hasSearch)
		assertFalse(extension.listings.isEmpty())
	}
}
