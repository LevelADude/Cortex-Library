package app.shosetsu.android.extensionbuilder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionSpecValidatorTest {

	private val validator = ExtensionSpecValidator()

	@Test
	fun `valid spec passes`() {
		val result = validator.validate(
			ExtensionSpec(
				id = "id",
				name = "My Source",
				lang = "en",
				baseUrl = "https://example.com",
				version = "1.2.3",
				createdAt = 1L,
				runtimeId = 12345
			)
		)
		assertTrue(result.isValid)
	}

	@Test
	fun `invalid url fails`() {
		val result = validator.validate(
			ExtensionSpec(
				id = "id",
				name = "My Source",
				lang = "en",
				baseUrl = "example.com",
				version = "1.0.0",
				createdAt = 1L,
				runtimeId = 12345
			)
		)
		assertFalse(result.isValid)
	}
}
