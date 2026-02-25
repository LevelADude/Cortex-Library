package app.shosetsu.android.domain.repository.impl

import app.shosetsu.android.common.SettingKey
import app.shosetsu.android.domain.model.reader.*
import app.shosetsu.android.domain.repository.base.IReaderStyleRepository
import app.shosetsu.android.domain.repository.base.ISettingsRepository
import app.shosetsu.android.common.ext.logE
import app.shosetsu.android.common.ext.logI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

class ReaderStyleRepository(
	private val settings: ISettingsRepository,
) : IReaderStyleRepository {
	private val json = Json { ignoreUnknownKeys = true }
	private val defaultPresets = builtInReaderStylePresets()

	override val presetsFlow: Flow<List<ReaderStylePreset>> = settings.getStringFlow(SettingKey.ReaderStylePresetsJson)
		.combine(settings.getStringFlow(SettingKey.ReaderStyleActivePresetId)) { raw, _ -> decodePresets(raw) }

	override val activePresetIdFlow: Flow<String> = settings.getStringFlow(SettingKey.ReaderStyleActivePresetId)

	override val activePresetFlow: Flow<ReaderStylePreset> = presetsFlow.combine(activePresetIdFlow) { presets, id ->
		presets.find { it.id == id } ?: presets.firstOrNull() ?: defaultPresets.first()
	}

	override suspend fun setActivePreset(id: String) = settings.setString(SettingKey.ReaderStyleActivePresetId, id)

	override suspend fun upsertPreset(preset: ReaderStylePreset) {
		val current = presetsFlow.first().toMutableList()
		val idx = current.indexOfFirst { it.id == preset.id }
		if (idx >= 0) current[idx] = preset.copy(style = preset.style.clamped()) else current.add(preset.copy(style = preset.style.clamped()))
		persist(current)
	}

	override suspend fun deletePreset(id: String) {
		val current = presetsFlow.first().toMutableList()
		val target = current.find { it.id == id } ?: return
		if (target.builtin) return
		current.removeAll { it.id == id }
		persist(current)
		val active = activePresetIdFlow.first()
		if (active == id) settings.setString(SettingKey.ReaderStyleActivePresetId, builtInReaderStylePresets().first().id)
	}

	override suspend fun duplicatePreset(id: String): ReaderStylePreset? {
		val source = presetsFlow.first().find { it.id == id } ?: return null
		val dup = source.copy(id = "custom:${UUID.randomUUID()}", name = "${source.name} Copy", builtin = false)
		upsertPreset(dup)
		return dup
	}

	override suspend fun resetBuiltIns() {
		val customs = presetsFlow.first().filterNot { it.builtin }
		persist(defaultPresets + customs)
		logI("Reader styles reset to built-ins + customs")
	}

	override suspend fun exportJson(): String {
		val export = ReaderStyleExport(
			activePresetId = activePresetIdFlow.first(),
			presets = presetsFlow.first(),
		)
		return json.encodeToString(ReaderStyleExport.serializer(), export)
	}

	override suspend fun importJson(jsonString: String): Result<Unit> = runCatching {
		val parsed = json.decodeFromString(ReaderStyleExport.serializer(), jsonString)
		require(parsed.version == 1) { "Unsupported preset version" }
		val merged = (builtInReaderStylePresets() + parsed.presets.filterNot { it.builtin }).distinctBy { it.id }
		persist(merged)
		settings.setString(SettingKey.ReaderStyleActivePresetId, parsed.activePresetId)
	}

	override fun parseCssToStylePatch(css: String): ReaderStylePreset? {
		val lower = css.lowercase()
		val size = Regex("font-size\\s*:\\s*(\\d+(?:\\.\\d+)?)px").find(lower)?.groupValues?.get(1)?.toFloatOrNull()
		val line = Regex("line-height\\s*:\\s*(\\d+(?:\\.\\d+)?)").find(lower)?.groupValues?.get(1)?.toFloatOrNull()
		val text = Regex("color\\s*:\\s*#([0-9a-f]{6})").find(lower)?.groupValues?.get(1)
		val bg = Regex("background(?:-color)?\\s*:\\s*#([0-9a-f]{6})").find(lower)?.groupValues?.get(1)
		if (size == null && line == null && text == null && bg == null) return null
		val style = ReaderStyle(
			fontSizeSp = size?.div(1.2f) ?: 18f,
			lineHeightEm = line ?: 1.6f,
			light = ReaderStyleColors.defaultLight().copy(
				text = text?.toLong(16)?.or(0xFF000000) ?: ReaderStyleColors.defaultLight().text,
				background = bg?.toLong(16)?.or(0xFF000000) ?: ReaderStyleColors.defaultLight().background,
			),
			dark = ReaderStyleColors.defaultDark(),
		).clamped()
		return ReaderStylePreset(
			id = "custom:${UUID.randomUUID()}",
			name = "Imported CSS",
			builtin = false,
			style = style,
		)
	}

	private suspend fun persist(presets: List<ReaderStylePreset>) {
		settings.setString(SettingKey.ReaderStylePresetsJson, json.encodeToString(ListSerializer(ReaderStylePreset.serializer()), presets))
	}

	private fun decodePresets(raw: String): List<ReaderStylePreset> {
		if (raw.isBlank()) {
			logI("Reader styles empty; using built-in defaults")
			return defaultPresets
		}
		return runCatching {
			val parsed = json.decodeFromString(ListSerializer(ReaderStylePreset.serializer()), raw)
			(defaultPresets + parsed.filterNot { it.builtin })
				.map { it.copy(style = it.style.clamped()) }
				.distinctBy { it.id }
				.ifEmpty { defaultPresets }
		}.getOrElse {
			logE("Failed to decode reader style presets", it)
			defaultPresets
		}
	}
}
