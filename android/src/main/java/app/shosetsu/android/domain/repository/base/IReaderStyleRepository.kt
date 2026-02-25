package app.shosetsu.android.domain.repository.base

import app.shosetsu.android.domain.model.reader.ReaderStyleExport
import app.shosetsu.android.domain.model.reader.ReaderStylePreset
import kotlinx.coroutines.flow.Flow

interface IReaderStyleRepository {
	val presetsFlow: Flow<List<ReaderStylePreset>>
	val activePresetIdFlow: Flow<String>
	val activePresetFlow: Flow<ReaderStylePreset>
	suspend fun setActivePreset(id: String)
	suspend fun upsertPreset(preset: ReaderStylePreset)
	suspend fun deletePreset(id: String)
	suspend fun duplicatePreset(id: String): ReaderStylePreset?
	suspend fun resetBuiltIns()
	suspend fun exportJson(): String
	suspend fun importJson(json: String): Result<Unit>
	fun parseCssToStylePatch(css: String): ReaderStylePreset?
}
