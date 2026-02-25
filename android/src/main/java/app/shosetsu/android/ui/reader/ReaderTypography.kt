package app.shosetsu.android.ui.reader

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import app.shosetsu.android.BuildConfig
import app.shosetsu.android.common.SettingKey
import app.shosetsu.android.domain.repository.base.ISettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL

@Immutable
data class ReaderFontOption(val id: String, val label: String, val fontFamily: FontFamily, val isLibraryFont: Boolean = false)

@Serializable
data class LibraryInstalledFontMetadata(
	val id: String,
	val name: String,
	val source: String,
	val downloadUrl: String,
	val filePath: String,
	val version: Int = 1,
)

@Serializable
@Immutable
data class PublicFontLibraryEntry(
	val id: String,
	val name: String,
	val previewText: String,
	val source: String,
	val downloadUrl: String,
	val category: String = "sans-serif",
	val popularity: Int = 0,
)

@Serializable
private data class PublicFontLibraryCache(val entries: List<PublicFontLibraryEntry>, val version: Int = 1)

private val builtInReaderFontOptions = listOf(
	ReaderFontOption("default", "Default", FontFamily.Default),
	ReaderFontOption("sans", "Sans Serif", FontFamily.SansSerif),
	ReaderFontOption("serif", "Serif", FontFamily.Serif),
	ReaderFontOption("mono", "Monospace", FontFamily.Monospace),
	ReaderFontOption("cursive", "Cursive", FontFamily.Cursive),
)

object ReaderFontCatalog {
	private const val catalogAssetPath = "reader_font_catalog.json"
	private val json = Json { ignoreUnknownKeys = true }
	private val entriesState = MutableStateFlow<List<PublicFontLibraryEntry>>(emptyList())

	val entries: StateFlow<List<PublicFontLibraryEntry>> = entriesState.asStateFlow()

	suspend fun load(context: Context, settings: ISettingsRepository) {
		withContext(Dispatchers.IO) {
			val cached = settings.getString(SettingKey.ReaderLibraryFontCatalogCacheJson)
			if (cached.isNotBlank()) {
				runCatching {
					val parsed = json.decodeFromString(PublicFontLibraryCache.serializer(), cached)
					if (parsed.entries.isNotEmpty()) {
						entriesState.value = parsed.entries
					}
				}
			}

			if (entriesState.value.isNotEmpty()) return@withContext

			val bundled = runCatching {
				context.assets.open(catalogAssetPath).bufferedReader().use { reader ->
					json.decodeFromString(ListSerializer(PublicFontLibraryEntry.serializer()), reader.readText())
				}
			}.getOrElse {
				if (BuildConfig.DEBUG) Log.e("ReaderFontCatalog", "Failed to load bundled catalog", it)
				emptyList()
			}

			if (bundled.isNotEmpty()) {
				entriesState.value = bundled
				settings.setString(
					SettingKey.ReaderLibraryFontCatalogCacheJson,
					json.encodeToString(PublicFontLibraryCache.serializer(), PublicFontLibraryCache(bundled)),
				)
			}
		}
	}
}

object ReaderFontRegistry {
	private val json = Json { ignoreUnknownKeys = true }
	private val fontDirectoryName = "reader_fonts"
	private val options = MutableStateFlow(builtInReaderFontOptions)
	private var metadata: LibraryInstalledFontMetadata? = null

	val fontOptions: StateFlow<List<ReaderFontOption>> = options.asStateFlow()
	val installedMetadata: LibraryInstalledFontMetadata?
		get() = metadata

	suspend fun initialize(context: Context, settings: ISettingsRepository) {
		ReaderFontCatalog.load(context, settings)
		val raw = settings.getString(SettingKey.ReaderLibraryFontMetadataJson)
		if (raw.isBlank()) {
			options.value = builtInReaderFontOptions
			metadata = null
			return
		}
		val parsed = runCatching { json.decodeFromString(LibraryInstalledFontMetadata.serializer(), raw) }.getOrNull()
		if (parsed == null) {
			settings.setString(SettingKey.ReaderLibraryFontMetadataJson, "")
			options.value = builtInReaderFontOptions
			metadata = null
			return
		}
		val fontFile = File(parsed.filePath)
		if (!fontFile.exists()) {
			settings.setString(SettingKey.ReaderLibraryFontMetadataJson, "")
			options.value = builtInReaderFontOptions
			metadata = null
			return
		}
		register(parsed)
	}

	suspend fun installSingleLibraryFont(context: Context, settings: ISettingsRepository, entry: PublicFontLibraryEntry): Result<LibraryInstalledFontMetadata> = withContext(Dispatchers.IO) {
		runCatching {
			val rootDir = File(context.filesDir, fontDirectoryName).apply { mkdirs() }
			val previous = metadata
			val target = File(rootDir, "${entry.id.substringAfter(':')}.ttf")
			URL(entry.downloadUrl).openStream().use { input ->
				target.outputStream().use { output -> input.copyTo(output) }
			}
			val next = LibraryInstalledFontMetadata(
				id = entry.id,
				name = entry.name,
				source = entry.source,
				downloadUrl = entry.downloadUrl,
				filePath = target.absolutePath,
			)
			register(next)
			settings.setString(SettingKey.ReaderLibraryFontMetadataJson, json.encodeToString(LibraryInstalledFontMetadata.serializer(), next))
			if (previous != null && previous.filePath != next.filePath) {
				File(previous.filePath).delete()
			}
			next
		}
	}

	fun previewFont(context: Context, entry: PublicFontLibraryEntry): FontFamily? {
		val previewDir = File(context.cacheDir, "reader_font_previews").apply { mkdirs() }
		val previewFile = File(previewDir, "${entry.id.substringAfter(':')}.ttf")
		if (!previewFile.exists()) {
			runCatching {
				URL(entry.downloadUrl).openStream().use { input ->
					previewFile.outputStream().use { output -> input.copyTo(output) }
				}
			}.getOrElse { return null }
		}
		return runCatching { FontFamily(Typeface.createFromFile(previewFile)) }.getOrNull()
	}

	suspend fun clearLibraryFont(settings: ISettingsRepository) {
		withContext(Dispatchers.IO) {
			metadata?.let { File(it.filePath).delete() }
			metadata = null
			options.value = builtInReaderFontOptions
			settings.setString(SettingKey.ReaderLibraryFontMetadataJson, "")
		}
	}

	fun resolve(id: String): FontFamily {
		val option = options.value.firstOrNull { it.id == id }
		if (option != null) return option.fontFamily
		return FontFamily.Default
	}

	private fun register(next: LibraryInstalledFontMetadata) {
		val typeface = Typeface.createFromFile(next.filePath)
		val libraryOption = ReaderFontOption(next.id, "${next.name} (Library)", FontFamily(typeface), isLibraryFont = true)
		metadata = next
		options.value = builtInReaderFontOptions + libraryOption
		if (BuildConfig.DEBUG) Log.d("ReaderFontRegistry", "Registered library font ${next.id}")
	}
}

val readerFontOptions: List<ReaderFontOption>
	get() = ReaderFontRegistry.fontOptions.value

fun resolveReaderFontFamily(id: String): FontFamily = ReaderFontRegistry.resolve(id)
