package app.shosetsu.android.extensionbuilder

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Robust local persistence for extension builder specs and generated lua files. */
class ExtensionStorage(
	private val context: Context,
	private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }
) {

	private val rootDir: File by lazy {
		File(context.filesDir, "extension_builder").apply { mkdirs() }
	}
	private val specsFile: File by lazy { File(rootDir, "specs.json") }
	private val luaDir: File by lazy { File(rootDir, "lua").apply { mkdirs() } }

	suspend fun listSpecs(): StorageResult<List<ExtensionSpec>> = ioResult {
		readPayload().specs
	}

	suspend fun getSpec(id: String): StorageResult<ExtensionSpec?> = ioResult {
		readPayload().specs.firstOrNull { it.id == id }
	}

	suspend fun upsertSpec(spec: ExtensionSpec): StorageResult<Unit> = ioResult {
		val payload = readPayload()
		val updated = payload.specs.toMutableList()
		val index = updated.indexOfFirst { it.id == spec.id }
		if (index >= 0) updated[index] = spec else updated += spec
		writePayload(payload.copy(specs = updated))
	}

	suspend fun deleteSpec(id: String): StorageResult<Unit> = ioResult {
		val payload = readPayload()
		val updated = payload.specs.filterNot { it.id == id }
		writePayload(payload.copy(specs = updated))
		File(luaDir, "$id.lua").takeIf { it.exists() }?.delete()
	}

	suspend fun saveLua(specId: String, luaText: String): StorageResult<File> = ioResult {
		File(luaDir, "$specId.lua").apply { writeText(luaText) }
	}

	suspend fun loadLua(specId: String): StorageResult<String?> = ioResult {
		val file = File(luaDir, "$specId.lua")
		if (file.exists()) file.readText() else null
	}

	private suspend fun <T> ioResult(block: suspend () -> T): StorageResult<T> =
		withContext(Dispatchers.IO) {
			runCatching { block() }
				.fold(
					onSuccess = { StorageResult.Success(it) },
					onFailure = { StorageResult.Failure(it) }
				)
		}

	private fun readPayload(): SpecsPayload {
		if (!specsFile.exists()) return SpecsPayload()
		val parsed = runCatching {
			json.decodeFromString<SpecsPayload>(specsFile.readText())
		}.getOrElse {
			return SpecsPayload()
		}
		return if (parsed.version == STORAGE_VERSION) {
			parsed
		} else {
			SpecsPayload(version = STORAGE_VERSION, specs = parsed.specs)
		}
	}

	private fun writePayload(payload: SpecsPayload) {
		specsFile.writeText(json.encodeToString(payload))
	}

	@Serializable
	data class SpecsPayload(
		val version: Int = STORAGE_VERSION,
		val specs: List<ExtensionSpec> = emptyList()
	)

	companion object {
		const val STORAGE_VERSION: Int = 1
	}
}

sealed class StorageResult<out T> {
	data class Success<T>(val value: T) : StorageResult<T>()
	data class Failure(val throwable: Throwable) : StorageResult<Nothing>()
}
