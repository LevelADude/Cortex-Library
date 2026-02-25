package app.shosetsu.android.extensionbuilder

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val METADATA_HEADER_PREFIX = "--"
private const val UTF8_BOM = '\uFEFF'

/**
 * Validates the Lua metadata comment header expected by Shosetsu runtimes.
 * Metadata fields are: id, ver, libVer, author, repo, dep.
 */
class LuaMetadataValidator(
	private val json: Json = Json
) {

	fun validate(lua: String, spec: ExtensionSpec): MetadataValidationResult {
		val errors = mutableListOf<String>()

		val firstNonEmpty = lua.lineSequence().firstOrNull { it.isNotBlank() }
		if (firstNonEmpty == null) {
			errors += "Lua is empty"
			return MetadataValidationResult(errors)
		}
		val candidate = firstNonEmpty.removePrefix(UTF8_BOM.toString()).trimStart()

		if (!candidate.startsWith(METADATA_HEADER_PREFIX)) {
			errors += "missing/invalid Lua header JSON. Expected first non-empty line: -- {...}. Found: $candidate"
			return MetadataValidationResult(errors)
		}

		val metadataJson = candidate.removePrefix(METADATA_HEADER_PREFIX).trim()
		if (!metadataJson.startsWith("{") || !metadataJson.endsWith("}")) {
			errors += "missing/invalid Lua header JSON. Expected first non-empty line: -- {...}. Found: $candidate"
			return MetadataValidationResult(errors)
		}

		val metadata = runCatching {
			json.parseToJsonElement(metadataJson).jsonObject
		}.getOrElse {
			errors += "missing/invalid Lua header JSON. Expected first non-empty line: -- {...}. Found: $candidate"
			return MetadataValidationResult(errors)
		}

		validateInt(metadata, "id", spec.runtimeId, errors)
		validateString(metadata, "ver", spec.version, errors)
		validateString(metadata, "libVer", "1.0.0", errors)
		validateString(metadata, "author", spec.author.orEmpty(), errors)
		validateString(metadata, "repo", "", errors)
		if (metadata["dep"]?.jsonArray == null) {
			errors += "metadata.dep is missing or not an array"
		}

		if (!lua.contains("baseURL =")) {
			errors += "missing required runtime field: baseURL"
		}

		return MetadataValidationResult(errors)
	}

	private fun validateInt(metadata: JsonObject, key: String, expected: Int, errors: MutableList<String>) {
		val value = metadata[key]?.jsonPrimitive?.intOrNull
		if (value == null) {
			errors += "metadata.$key is missing or not a number"
		} else if (value != expected) {
			errors += "metadata.$key mismatch (expected $expected, got $value)"
		}
	}

	private fun validateString(metadata: JsonObject, key: String, expected: String, errors: MutableList<String>) {
		val value = metadata[key]?.jsonPrimitive?.contentOrNull
		if (value == null) {
			errors += "metadata.$key is missing or not a string"
		} else if (value != expected) {
			errors += "metadata.$key mismatch (expected '$expected', got '$value')"
		}
	}
}

data class MetadataValidationResult(
	val errors: List<String>
) {
	val isValid: Boolean get() = errors.isEmpty()

	fun toDiagnostic(): List<String> {
		if (isValid) return emptyList()
		return listOf(
			"Invalid extension metadata: missing/invalid Lua header JSON. Expected first non-empty line: -- {\"id\":<id>,\"ver\":\"<version>\",\"libVer\":\"1.0.0\",\"author\":\"<author>\",\"repo\":\"\",\"dep\":[]}",
		) + errors
	}
}
