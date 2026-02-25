package app.shosetsu.android.extensionbuilder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaMetadataValidatorTest {

	private val spec = ExtensionSpec(
		id = "builder.runtime",
		name = "Runtime",
		lang = "en",
		baseUrl = "https://example.com",
		version = "1.0.0",
		author = "Tester",
		createdAt = 1L,
		runtimeId = 778899
	)

	@Test
	fun `validator accepts generated header metadata`() {
		val lua = ExtensionGenerator().generateLua(spec)
		val result = LuaMetadataValidator().validate(lua, spec)

		assertTrue(result.isValid)
	}

	@Test
	fun `validator accepts header with leading whitespace`() {
		val lua = """
		    \t --   {"id":778899,"ver":"1.0.0","libVer":"1.0.0","author":"Tester","repo":"","dep":[]}
			return { baseURL = "https://example.com" }
		""".trimIndent()

		val result = LuaMetadataValidator().validate(lua, spec)

		assertTrue(result.isValid)
	}

	@Test
	fun `validator strips utf8 bom on first line`() {
		val lua = "\uFEFF-- {\"id\":778899,\"ver\":\"1.0.0\",\"libVer\":\"1.0.0\",\"author\":\"Tester\",\"repo\":\"\",\"dep\":[]}\nreturn { baseURL = \"https://example.com\" }"
		val result = LuaMetadataValidator().validate(lua, spec)

		assertTrue(result.isValid)
	}

	@Test
	fun `validator reports invalid metadata header details`() {
		val lua = """
			-- {"id":1,"ver":"0.0.1","libVer":"0.9.0","author":"wrong","repo":"x","dep":{}}
			return { baseURL = "https://example.com" }
		""".trimIndent()
		val result = LuaMetadataValidator().validate(lua, spec)

		assertFalse(result.isValid)
		assertTrue(result.errors.any { it.contains("metadata.id mismatch") })
		assertTrue(result.errors.any { it.contains("metadata.ver mismatch") })
		assertTrue(result.errors.any { it.contains("metadata.libVer mismatch") })
		assertTrue(result.errors.any { it.contains("metadata.author mismatch") })
		assertTrue(result.errors.any { it.contains("metadata.dep is missing or not an array") })
	}

	@Test
	fun `validator diagnostics include first non-empty line when header is invalid`() {
		val lua = """
			  print("hello")
			return { baseURL = "https://example.com" }
		""".trimIndent()
		val result = LuaMetadataValidator().validate(lua, spec)

		assertFalse(result.isValid)
		assertTrue(result.errors.any { it.contains("Found: print(\"hello\")") })
	}
}
