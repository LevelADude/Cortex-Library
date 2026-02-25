package app.shosetsu.android.extensionbuilder

private val SEMVERISH_REGEX = Regex("^\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?$")
private val LANG_REGEX = Regex("^[a-z]{2,3}(?:-[A-Za-z]{2})?$")
private val HEADER_KEY_REGEX = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
private val LUA_GLOBAL_ASSIGNMENT_REGEX = Regex("^\\s*[A-Za-z_][A-Za-z0-9_]*\\s*=")

/** Validates [ExtensionSpec] used by the extension builder flow. */
class ExtensionSpecValidator {

	fun validate(spec: ExtensionSpec): ValidationReport {
		val errors = mutableListOf<String>()
		val warnings = mutableListOf<String>()

		if (spec.name.isBlank()) errors += "Name is required"
		if (spec.lang.isBlank()) errors += "Language is required"
		if (spec.lang.isNotBlank() && !LANG_REGEX.matches(spec.lang.trim())) {
			warnings += "Language should use a standard code (for example: en, es, pt-BR)"
		}
		if (spec.baseUrl.isBlank()) {
			errors += "Base URL is required"
		} else if (!spec.baseUrl.startsWith("http://") && !spec.baseUrl.startsWith("https://")) {
			errors += "Base URL must start with http:// or https://"
		}

		if (!SEMVERISH_REGEX.matches(spec.version.trim())) {
			errors += "Version must look like semantic versioning (example: 1.0.0)"
		}

		val dedup = mutableSetOf<String>()
		spec.headers.forEachIndexed { index, header ->
			if (header.key.isBlank()) errors += "Header ${index + 1}: key is required"
			if (header.value.isBlank()) errors += "Header ${index + 1}: value is required"
			if (header.key.isNotBlank() && !HEADER_KEY_REGEX.matches(header.key.trim())) {
				errors += "Header ${index + 1}: invalid header key"
			}
			val normalized = header.key.trim().lowercase()
			if (normalized.isNotBlank() && !dedup.add(normalized)) {
				warnings += "Duplicate header key '${header.key.trim()}'"
			}
		}

		
		if (spec.runtimeId <= 0) errors += "Runtime ID must be a positive integer"
		if (spec.capabilities.supportsFilters && !spec.capabilities.supportsSearch) {
			warnings += "Filters are enabled but search is disabled"
		}

		if (spec.parsing.enabled) {
			validateParsing(spec, errors, warnings)
		}

		return ValidationReport(errors = errors, warnings = warnings)
	}

	private fun validateParsing(spec: ExtensionSpec, errors: MutableList<String>, warnings: MutableList<String>) {
		when (spec.parsing.mode) {
			ParsingMode.BASIC -> validateBasicParsing(spec.parsing.basic, errors, warnings)
			ParsingMode.ADVANCED -> validateAdvancedSnippet(spec.parsing.luaOverrideSnippet, errors)
		}
	}

	private fun validateBasicParsing(
		basic: BasicParsingSpec,
		errors: MutableList<String>,
		warnings: MutableList<String>
	) {
		requiredSelector(basic.itemSelector, "Book card selector (CSS)", errors, warnings)
		requiredSelector(basic.titleSelector, "Title selector inside the book card", errors, warnings)
		requiredSelector(basic.urlSelector, "Link selector inside the book card", errors, warnings)
		requiredAttribute(basic.urlAttr, "Link attribute name", errors, warnings)
		requiredSelector(basic.imageSelector, "Cover image selector inside the book card", errors, warnings)
		requiredAttribute(basic.imageAttr, "Cover image attribute", errors, warnings)
		requireAttributeSource(basic.titleFrom, basic.titleAttr, "Title attribute name", errors)

		optionalSelector(basic.novelTitleSelector, "Novel title selector", warnings)
		requireAttributeSource(basic.novelTitleFrom, basic.novelTitleAttr, "Novel title attribute name", errors)
		optionalSelector(basic.coverSelector, "Cover selector", warnings)
		optionalAttribute(basic.coverAttr, "Cover attribute name", warnings)
		optionalSelector(basic.descriptionSelector, "Description selector", warnings)
		optionalSelector(basic.authorSelector, "Authors selector", warnings)
		optionalSelector(basic.genreSelector, "Genres selector", warnings)
		optionalSelector(basic.chapterItemSelector, "Chapter item selector", warnings)
		optionalSelector(basic.chapterTitleSelector, "Chapter title selector", warnings)
		requireAttributeSource(basic.chapterTitleFrom, basic.chapterTitleAttr, "Chapter title attribute name", errors)
		optionalSelector(basic.chapterUrlSelector, "Chapter URL selector", warnings)
		optionalAttribute(basic.chapterUrlAttr, "Chapter URL attribute name", warnings)
		optionalSelector(basic.contentSelector, "Content selector", warnings)
		basic.removeSelector?.takeIf { it.isNotBlank() }?.let { maybeWarnSelector(it, "Remove selector(s)", warnings) }
	}

	private fun validateAdvancedSnippet(snippet: String, errors: MutableList<String>) {
		if (snippet.isBlank()) {
			errors += "Advanced mode requires a Lua override snippet"
			return
		}
		snippet.lineSequence().forEachIndexed { index, rawLine ->
			val line = rawLine.trimEnd()
			if (LUA_GLOBAL_ASSIGNMENT_REGEX.containsMatchIn(line)) {
				errors += "Global assignments are not allowed in Lua extensions; use local variables. (line ${index + 1})"
			}
		}
	}

	private fun requiredSelector(value: String, label: String, errors: MutableList<String>, warnings: MutableList<String>) {
		if (value.isBlank()) {
			errors += "$label is required"
			return
		}
		maybeWarnSelector(value, label, warnings)
	}

	private fun maybeWarnSelector(value: String, label: String, warnings: MutableList<String>) {
		val trimmed = value.trim()
		if (trimmed.isBlank()) {
			warnings += "$label looks blank. Paste a CSS selector from Inspect."
			return
		}
		if (trimmed != value) warnings += "$label has leading or trailing spaces."
		if (trimmed.contains(":nth-child")) {
			warnings += "$label looks too specific (contains :nth-child). Consider a class selector like .book-item."
		}
		if (trimmed.count { it == '>' } >= 3) {
			warnings += "$label looks too specific (long chain with >). Try a shorter repeating selector."
		}
		if (trimmed.contains('"') || trimmed.contains('\'')) {
			warnings += "$label should not include quote characters."
		}
	}

	private fun requiredAttribute(value: String, label: String, errors: MutableList<String>, warnings: MutableList<String>) {
		if (value.isBlank()) errors += "$label is required"
		maybeWarnAttribute(value, label, warnings)
	}

	private fun optionalSelector(value: String, label: String, warnings: MutableList<String>) {
		if (value.isNotBlank()) maybeWarnSelector(value, label, warnings)
	}

	private fun optionalAttribute(value: String, label: String, warnings: MutableList<String>) {
		if (value.isNotBlank()) maybeWarnAttribute(value, label, warnings)
	}

	private fun maybeWarnAttribute(value: String, label: String, warnings: MutableList<String>) {
		val trimmed = value.trim()
		if (trimmed.isBlank()) return
		if (trimmed.contains(' ') || trimmed.contains('"') || trimmed.contains('\'')) {
			warnings += "$label should be plain text like href or src (no spaces or quotes)."
		}
	}

	private fun requireAttributeSource(source: ValueSource, attr: String?, label: String, errors: MutableList<String>) {
		if (source == ValueSource.ATTR && attr.isNullOrBlank()) {
			errors += "$label is required when source is set to Attribute"
		}
	}
}

data class ValidationReport(
	val errors: List<String>,
	val warnings: List<String>
) {
	val isValid: Boolean get() = errors.isEmpty()
}
