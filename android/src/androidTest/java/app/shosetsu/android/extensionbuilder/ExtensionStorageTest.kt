package app.shosetsu.android.extensionbuilder

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionStorageTest {

	@Test
	fun upsert_list_delete_roundtrip() = runBlocking {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val storage = ExtensionStorage(context)
		val spec = ExtensionSpec(
			id = "spec.test",
			name = "Spec",
			lang = "en",
			baseUrl = "https://example.com",
			version = "1.0.0",
			createdAt = 1L
		)

		storage.upsertSpec(spec)
		val list = (storage.listSpecs() as StorageResult.Success).value
		assertTrue(list.any { it.id == "spec.test" })

		storage.saveLua(spec.id, "print('x')")
		assertEquals("print('x')", (storage.loadLua(spec.id) as StorageResult.Success).value)

		storage.deleteSpec(spec.id)
		assertNull((storage.loadLua(spec.id) as StorageResult.Success).value)
	}
}
