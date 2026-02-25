package app.shosetsu.android.reader

import app.shosetsu.android.common.SettingKey
import app.shosetsu.android.domain.model.reader.ReaderStyle
import app.shosetsu.android.domain.model.reader.ReaderStyleExport
import app.shosetsu.android.domain.model.reader.ReaderStylePreset
import app.shosetsu.android.domain.repository.base.ISettingsRepository
import app.shosetsu.android.domain.repository.impl.ReaderStyleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderStyleRepositoryTest {
	@Test fun `serialization roundtrip`() {
		val preset = ReaderStylePreset("custom:1", "Mine", false, ReaderStyle())
		val encoded = Json.encodeToString(ReaderStylePreset.serializer(), preset)
		val decoded = Json.decodeFromString(ReaderStylePreset.serializer(), encoded)
		assertEquals(preset, decoded)
	}

	@Test fun `preset switching updates active flow`() = runBlocking {
		val repo = ReaderStyleRepository(FakeSettingsRepo())
		repo.duplicatePreset("builtin:classic")
		val customId = repo.presetsFlow.first().first { !it.builtin }.id
		repo.setActivePreset(customId)
		assertEquals(customId, repo.activePresetIdFlow.first())
	}

	@Test fun `import validates version`() = runBlocking {
		val repo = ReaderStyleRepository(FakeSettingsRepo())
		val bad = Json.encodeToString(ReaderStyleExport.serializer(), ReaderStyleExport(version = 99, activePresetId = "builtin:classic", presets = emptyList()))
		assertTrue(repo.importJson(bad).isFailure)
	}
}

private class FakeSettingsRepo : ISettingsRepository {
	private val map = hashMapOf<String, MutableStateFlow<Any>>()
	@Suppress("UNCHECKED_CAST") private fun <T : Any> flow(key: SettingKey<T>) = map.getOrPut(key.name) { MutableStateFlow(key.default as Any) } as MutableStateFlow<T>
	override fun getLongFlow(key: SettingKey<Long>): StateFlow<Long> = flow(key)
	override fun getStringFlow(key: SettingKey<String>): StateFlow<String> = flow(key)
	override fun getIntFlow(key: SettingKey<Int>): StateFlow<Int> = flow(key)
	override fun getBooleanFlow(key: SettingKey<Boolean>): StateFlow<Boolean> = flow(key)
	override fun getFloatFlow(key: SettingKey<Float>): StateFlow<Float> = flow(key)
	override fun getStringSetFlow(key: SettingKey<Set<String>>): StateFlow<Set<String>> = flow(key)
	override suspend fun getLong(key: SettingKey<Long>): Long = flow(key).value
	override suspend fun getString(key: SettingKey<String>): String = flow(key).value
	override suspend fun getInt(key: SettingKey<Int>): Int = flow(key).value
	override suspend fun getBoolean(key: SettingKey<Boolean>): Boolean = flow(key).value
	override suspend fun getStringSet(key: SettingKey<Set<String>>): Set<String> = flow(key).value
	override suspend fun getFloat(key: SettingKey<Float>): Float = flow(key).value
	override suspend fun setLong(key: SettingKey<Long>, value: Long) { flow(key).value = value }
	override suspend fun setString(key: SettingKey<String>, value: String) { flow(key).value = value }
	override suspend fun setInt(key: SettingKey<Int>, value: Int) { flow(key).value = value }
	override suspend fun setBoolean(key: SettingKey<Boolean>, value: Boolean) { flow(key).value = value }
	override suspend fun setStringSet(key: SettingKey<Set<String>>, value: Set<String>) { flow(key).value = value }
	override suspend fun setFloat(key: SettingKey<Float>, value: Float) { flow(key).value = value }
}
