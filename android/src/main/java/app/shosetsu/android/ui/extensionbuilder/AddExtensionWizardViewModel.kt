package app.shosetsu.android.ui.extensionbuilder

import android.app.Application
import android.os.NetworkOnMainThreadException
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.shosetsu.android.R
import app.shosetsu.android.application.ShosetsuApplication
import app.shosetsu.android.common.consts.DEFAULT_USER_AGENT
import app.shosetsu.android.extensionbuilder.ExtensionCapabilities
import app.shosetsu.android.extensionbuilder.ExtensionGenerator
import app.shosetsu.android.extensionbuilder.ExtensionSpec
import app.shosetsu.android.extensionbuilder.ExtensionSpecValidator
import app.shosetsu.android.extensionbuilder.ExtensionStorage
import app.shosetsu.android.extensionbuilder.HeaderEntry
import app.shosetsu.android.extensionbuilder.ParsingMode
import app.shosetsu.android.extensionbuilder.ParsingSpec
import app.shosetsu.android.extensionbuilder.BasicParsingSpec
import app.shosetsu.android.extensionbuilder.ContentOutput
import app.shosetsu.android.extensionbuilder.ValueSource
import app.shosetsu.android.extensionbuilder.LuaMetadataValidator
import app.shosetsu.android.extensionbuilder.StorageResult
import app.shosetsu.android.extensionbuilder.ValidationReport
import app.shosetsu.android.domain.model.local.GenericExtensionEntity
import app.shosetsu.android.domain.model.local.InstalledExtensionEntity
import app.shosetsu.android.domain.repository.base.IExtensionEntitiesRepository
import app.shosetsu.android.domain.repository.base.IExtensionsRepository
import app.shosetsu.android.domain.repository.base.INovelsRepository
import app.shosetsu.lib.ExtensionType
import app.shosetsu.lib.PAGE_INDEX
import app.shosetsu.lib.lua.LuaExtension
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.kodein.di.direct
import org.kodein.di.instance
import org.jsoup.Jsoup
import java.util.UUID
import java.util.concurrent.TimeUnit

class AddExtensionWizardViewModel(
	application: Application,
	private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

	private val storage = ExtensionStorage(application)
	private val validator = ExtensionSpecValidator()
	private val metadataValidator = LuaMetadataValidator()
	private val generator = ExtensionGenerator()
	private val httpClient = OkHttpClient.Builder()
		.callTimeout(15, TimeUnit.SECONDS)
		.build()
	private val extensionRepository: IExtensionsRepository = (application as ShosetsuApplication).di.direct.instance()
	private val extensionEntitiesRepository: IExtensionEntitiesRepository = (application as ShosetsuApplication).di.direct.instance()
	private val novelsRepository: INovelsRepository = (application as ShosetsuApplication).di.direct.instance()

	private val _state = MutableStateFlow(
		AddExtensionWizardUiState(step = savedStateHandle[STEP_KEY] ?: 0).refreshDerived(validator, generator)
	)
	val state: StateFlow<AddExtensionWizardUiState> = _state.asStateFlow()

	private val events = Channel<AddExtensionWizardEvent>(Channel.BUFFERED)
	val eventFlow = events.receiveAsFlow()
	private var installInFlight = false
	private var lastInstallAttemptAtMillis = 0L
	private var parsingTestJob: Job? = null

	fun updateBasic(name: String? = null, lang: String? = null, author: String? = null, version: String? = null) {
		_state.value = _state.value.copy(
			spec = _state.value.spec.copy(
				name = name ?: _state.value.spec.name,
				lang = lang ?: _state.value.spec.lang,
				author = author ?: _state.value.spec.author,
				version = version ?: _state.value.spec.version
			)
		).refreshDerived(validator, generator)
	}

	fun updateSource(baseUrl: String? = null, userAgent: String? = null) {
		_state.value = _state.value.copy(
			spec = _state.value.spec.copy(
				baseUrl = baseUrl ?: _state.value.spec.baseUrl,
				userAgent = userAgent ?: _state.value.spec.userAgent
			)
		).refreshDerived(validator, generator)
	}

	fun addHeader() {
		_state.value = _state.value.copy(
			spec = _state.value.spec.copy(headers = _state.value.spec.headers + HeaderEntry("", ""))
		)
	}

	fun updateHeader(index: Int, key: String? = null, value: String? = null) {
		val headers = _state.value.spec.headers.toMutableList()
		if (index !in headers.indices) return
		val current = headers[index]
		headers[index] = current.copy(key = key ?: current.key, value = value ?: current.value)
		_state.value = _state.value.copy(spec = _state.value.spec.copy(headers = headers))
			.refreshDerived(validator, generator)
	}

	fun removeHeader(index: Int) {
		val headers = _state.value.spec.headers.toMutableList()
		if (index !in headers.indices) return
		headers.removeAt(index)
		_state.value = _state.value.copy(spec = _state.value.spec.copy(headers = headers))
			.refreshDerived(validator, generator)
	}

	fun updateCapabilities(capabilities: ExtensionCapabilities) {
		_state.value = _state.value.copy(spec = _state.value.spec.copy(capabilities = capabilities), preview = null)
			.refreshDerived(validator, generator)
	}

	fun updateParsingEnabled(enabled: Boolean) {
		updateParsing { it.copy(enabled = enabled) }
	}

	fun updateParsingMode(mode: ParsingMode) {
		updateParsing { it.copy(mode = mode) }
	}

	fun updateAdvancedSnippet(snippet: String) {
		updateParsing { it.copy(luaOverrideSnippet = snippet) }
	}

	fun updateBasicParsing(transform: (BasicParsingSpec) -> BasicParsingSpec) {
		updateParsing { parsing -> parsing.copy(basic = transform(parsing.basic)) }
	}

	fun updateParsingTestUrls(listingUrl: String? = null, novelUrl: String? = null, chapterUrl: String? = null) {
		_state.value = _state.value.copy(
			parsingTestInput = _state.value.parsingTestInput.copy(
				listingUrl = listingUrl ?: _state.value.parsingTestInput.listingUrl,
				novelUrl = novelUrl ?: _state.value.parsingTestInput.novelUrl,
				chapterUrl = chapterUrl ?: _state.value.parsingTestInput.chapterUrl
			)
		)
	}

	fun runParsingTest() {
		runParsingTestSection(ParsingTestSection.END_TO_END)
	}

	fun runParsingTestSection(section: ParsingTestSection) {
		parsingTestJob?.cancel()
		parsingTestJob = viewModelScope.launch {
			val current = _state.value
			if (!current.spec.parsing.enabled || current.spec.parsing.mode != ParsingMode.BASIC) {
				_state.value = current.copy(parsingTestResult = ParsingTestResult(error = getApplication<Application>().getString(R.string.parsing_test_error_enable_basic)))
				return@launch
			}
			val basic = current.spec.parsing.basic
			val input = current.parsingTestInput
			val missingFields = section.missingFields(input, basic)
			if (missingFields.isNotEmpty()) {
				_state.value = current.copy(
					parsingTestResult = ParsingTestResult(
						error = getApplication<Application>().getString(R.string.parsing_test_error_missing_fields),
						sectionErrors = mapOf(section to missingFields)
					)
				)
				return@launch
			}

			_state.value = current.copy(parsingTestInProgress = section, parsingTestResult = null)
			try {
				val result = withContext(Dispatchers.IO) {
					val listing = if (section == ParsingTestSection.END_TO_END || section == ParsingTestSection.LISTING) parseListingDiagnostics(input.listingUrl.trim(), basic) else null
					val novel = if (section == ParsingTestSection.END_TO_END || section == ParsingTestSection.NOVEL) parseNovelDiagnostics(input.novelUrl.trim(), basic) else null
					val chapterList = if (section == ParsingTestSection.END_TO_END || section == ParsingTestSection.CHAPTER_LIST) parseChapterListDiagnostics(input.novelUrl.trim(), basic) else null
					val chapter = if (section == ParsingTestSection.END_TO_END || section == ParsingTestSection.CHAPTER_CONTENT) parseChapterDiagnostics(input.chapterUrl.trim(), basic) else null
					ParsingTestResult(listing = listing, novel = novel, chapter = chapter)
						.copy(chapterList = chapterList)
				}
				_state.value = _state.value.copy(parsingTestResult = result)
			} catch (error: Exception) {
				if (!isActive) throw error
				val errorMessage = if (error is NetworkOnMainThreadException) {
					getApplication<Application>().getString(R.string.parsing_test_error_network_main_thread)
				} else {
					getApplication<Application>().getString(R.string.parsing_test_error_with_phase, error.message ?: error::class.java.simpleName)
				}
				_state.value = _state.value.copy(parsingTestResult = ParsingTestResult(error = errorMessage))
			} finally {
				if (parsingTestJob === this.coroutineContext[kotlinx.coroutines.Job]) {
					_state.value = _state.value.copy(parsingTestInProgress = null)
				}
			}
		}
	}

	private fun parseListingDiagnostics(url: String, basic: BasicParsingSpec): ListingTestDiagnostics {
		val fetch = fetchHtml(url, "listing")
		val document = Jsoup.parse(fetch.html, fetch.finalUrl)
		val cards = document.select(basic.itemSelector)
		if (cards.isEmpty() && fetch.html.isNotBlank()) Log.d(TAG, "Listing test returned 0 items. HTML preview=${fetch.html.take(500)}")
		val firstCard = cards.firstOrNull()
		val listingPreview = cards.mapNotNull { card ->
			val titleNode = card.selectFirst(basic.titleSelector)
			val urlNode = card.selectFirst(basic.urlSelector)
			val imageNode = card.selectFirst(basic.imageSelector)
			val title = when (basic.titleFrom) {
				ValueSource.TEXT -> titleNode?.text().orEmpty()
				ValueSource.ATTR -> titleNode?.attr(basic.titleAttr.orEmpty()).orEmpty()
			}
			val rawUrl = urlNode?.attr(basic.urlAttr).orEmpty()
			val image = imageNode?.attr(basic.imageAttr).orEmpty()
			if (title.isBlank() || rawUrl.isBlank()) {
				null
			} else {
				PreviewNovelResult(
					title = title,
					imageUrl = normalizeUrl(image, fetch.finalUrl),
					url = normalizeUrl(rawUrl, fetch.finalUrl),
					imageUrlRaw = image,
					urlRaw = rawUrl
				)
			}
		}.take(3)

		return ListingTestDiagnostics(
			fetch = fetch,
			cardCount = cards.size,
			titleMatchesInFirstCard = firstCard?.select(basic.titleSelector)?.size ?: 0,
			linkMatchesInFirstCard = firstCard?.select(basic.urlSelector)?.size ?: 0,
			imageMatchesInFirstCard = firstCard?.select(basic.imageSelector)?.size ?: 0,
			sampleLinkValue = firstCard?.selectFirst(basic.urlSelector)?.attr(basic.urlAttr).orEmpty(),
			sampleImageValue = firstCard?.selectFirst(basic.imageSelector)?.attr(basic.imageAttr).orEmpty(),
			htmlDebugSnippet = fetch.html.take(300),
			preview = listingPreview
		)
	}

	private fun parseNovelDiagnostics(url: String, basic: BasicParsingSpec): NovelTestDiagnostics {
		val fetch = fetchHtml(url, "novel")
		val document = Jsoup.parse(fetch.html, fetch.finalUrl)
		val descriptionLength = document.select(basic.descriptionSelector).text().length
		val coverPresent = document.selectFirst(basic.coverSelector) != null
		val authorCount = basic.authorSelector.takeIf { it.isNotBlank() }?.let { document.select(it).size } ?: 0
		val genreCount = basic.genreSelector.takeIf { it.isNotBlank() }?.let { document.select(it).size } ?: 0
		return NovelTestDiagnostics(
			fetch = fetch,
			titleFound = document.selectFirst(basic.novelTitleSelector) != null,
			chaptersFound = document.select(basic.chapterItemSelector).size,
			coverFound = coverPresent,
			descriptionLength = descriptionLength,
			authorCount = authorCount,
			genreCount = genreCount
		)
	}

	private fun parseChapterListDiagnostics(url: String, basic: BasicParsingSpec): ChapterListTestDiagnostics {
		val fetch = fetchHtml(url, "chapter-list")
		val document = Jsoup.parse(fetch.html, fetch.finalUrl)
		val chapterItems = document.select(basic.chapterItemSelector)
		val preview = chapterItems.take(3).map { chapter ->
			val title = chapter.selectFirst(basic.chapterTitleSelector)?.text().orEmpty()
			val chapterUrl = chapter.selectFirst(basic.chapterUrlSelector)?.attr(basic.chapterUrlAttr).orEmpty()
			PreviewNovelResult(
				title = title,
				imageUrl = "",
				url = normalizeUrl(chapterUrl, fetch.finalUrl),
				urlRaw = chapterUrl
			)
		}
		return ChapterListTestDiagnostics(fetch = fetch, chapterCount = chapterItems.size, preview = preview)
	}

	private fun parseChapterDiagnostics(url: String, basic: BasicParsingSpec): ChapterTestDiagnostics {
		val fetch = fetchHtml(url, "chapter")
		val document = Jsoup.parse(fetch.html, fetch.finalUrl)
		val content = document.selectFirst(basic.contentSelector) ?: return ChapterTestDiagnostics(fetch = fetch, contentMatched = false, outputLength = 0)
		basic.removeSelector?.takeIf { it.isNotBlank() }?.let { content.select(it).remove() }
		val preview = when (basic.contentOutput) {
			ContentOutput.HTML -> content.outerHtml().take(300)
			ContentOutput.TEXT -> content.text().take(300)
		}
		val outputLength = when (basic.contentOutput) {
			ContentOutput.HTML -> content.outerHtml().length
			ContentOutput.TEXT -> content.text().length
		}
		return ChapterTestDiagnostics(fetch = fetch, contentMatched = true, outputLength = outputLength, preview = preview)
	}

	private fun fetchHtml(url: String, phase: String): FetchDiagnostics {
		val builder = Request.Builder().url(url)
		builder.header("User-Agent", _state.value.spec.userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT)
		_state.value.spec.headers.forEach { header ->
			if (header.key.isNotBlank() && header.value.isNotBlank()) {
				builder.header(header.key.trim(), header.value.trim())
			}
		}
		return httpClient.newCall(builder.build()).execute().use { response ->
			val html = response.body?.string().orEmpty()
			if (!response.isSuccessful) error("$phase fetch failed: HTTP ${response.code}")
			FetchDiagnostics(
				success = true,
				finalUrl = response.request.url.toString(),
				statusCode = response.code,
				responseSizeChars = html.length,
				html = html
			)
		}
	}

	private fun normalizeUrl(value: String, baseUrl: String): String {
		if (value.isBlank()) return ""
		if (value.startsWith("http")) return value
		if (value.startsWith("//")) return "https:$value"
		return java.net.URL(java.net.URL(baseUrl), value).toString()
	}

	private fun updateParsing(transform: (ParsingSpec) -> ParsingSpec) {
		_state.value = _state.value.copy(
			spec = _state.value.spec.copy(parsing = transform(_state.value.spec.parsing)),
			preview = null
		).refreshDerived(validator, generator)
	}

	fun nextStep() {
		val canAdvance = _state.value.step < 4 && _state.value.canGoNext
		if (!canAdvance) return
		val step = (_state.value.step + 1).coerceAtMost(4)
		savedStateHandle[STEP_KEY] = step
		_state.value = _state.value.copy(step = step)
	}

	fun previousStep() {
		val step = (_state.value.step - 1).coerceAtLeast(0)
		savedStateHandle[STEP_KEY] = step
		_state.value = _state.value.copy(step = step)
	}

	fun saveSpec() {
		viewModelScope.launch {
			when (storage.upsertSpec(_state.value.spec)) {
				is StorageResult.Success -> sendMessage(R.string.extension_builder_saved_spec)
				is StorageResult.Failure -> sendMessage(R.string.extension_builder_error_storage)
			}
		}
	}

	fun generateAndSaveLua() {
		viewModelScope.launch {
			val current = _state.value.copy(flowState = WizardFlowState.Validated).refreshDerived(validator, generator)
			_state.value = current
			if (!current.validation.isValid) {
				sendMessage(R.string.extension_builder_error_validation)
				return@launch
			}
			when (storage.saveLua(current.spec.id, current.luaPreview)) {
				is StorageResult.Success -> {
					_state.value = current.copy(flowState = WizardFlowState.Generated)
					sendMessage(R.string.extension_builder_saved_lua)
				}
				is StorageResult.Failure -> sendMessage(R.string.extension_builder_error_storage)
			}
		}
	}

	fun previewGenerated() {
		viewModelScope.launch {
			val current = _state.value.refreshDerived(validator, generator)
			if (!current.validation.isValid) {
				sendMessage(R.string.extension_builder_error_validation)
				_state.value = current.copy(
					flowState = WizardFlowState.PreviewFailure,
					diagnostics = current.validation.errors + current.validation.warnings
				)
				return@launch
			}

			val metadataValidation = metadataValidator.validate(current.luaPreview, current.spec)
			if (!metadataValidation.isValid) {
				_state.value = current.copy(
					flowState = WizardFlowState.PreviewFailure,
					diagnostics = metadataValidation.toDiagnostic()
				)
				sendMessage(R.string.extension_builder_preview_failed)
				return@launch
			}
			_state.value = current.copy(flowState = WizardFlowState.Previewing, preview = null)
			val result = runCatching {
				val extension = LuaExtension(current.luaPreview, current.spec.id)
				val extensionLang = extensionLanguage(extension, current.spec.lang)
				val listingResults = if (extension.listings.isNotEmpty()) {
					novelsRepository.getCatalogueData(extension, 0, mapOf(PAGE_INDEX to extension.startIndex))
						.map { PreviewNovelResult(it.title, it.imageURL ?: "", it.link) }
				} else {
					emptyList()
				}
				PreviewRuntimeResult(
					id = extension.formatterID,
					name = extension.name,
					lang = extensionLang,
					version = extension.exMetaData.version.toString(),
					hasSearch = extension.hasSearch,
					listings = extension.listings.map { it.name },
					listingResults = listingResults
				)
			}

			result.fold(
				onSuccess = {
					_state.value = _state.value.copy(flowState = WizardFlowState.PreviewSuccess, preview = it, diagnostics = emptyList())
					sendMessage(R.string.extension_builder_preview_success)
				},
				onFailure = { error ->
					val rawMessage = error.message ?: error::class.java.simpleName
					val friendlyMessage = if (rawMessage.contains("invalid metadata", ignoreCase = true)) {
						"Invalid extension metadata: missing/invalid Lua header JSON. Expected first non-empty line: -- {\"id\":<id>,\"ver\":\"<version>\",\"libVer\":\"1.0.0\",\"author\":\"<author>\",\"repo\":\"\",\"dep\":[]}"
					} else {
						rawMessage
					}
					_state.value = _state.value.copy(
						flowState = WizardFlowState.PreviewFailure,
						diagnostics = listOf(friendlyMessage, rawMessage)
					)
					sendMessage(R.string.extension_builder_preview_failed)
				}
			)
		}
	}

	fun installExtension(force: Boolean = false) {
		viewModelScope.launch {
			val now = System.currentTimeMillis()
			if (now - lastInstallAttemptAtMillis < INSTALL_DEBOUNCE_MS) return@launch
			lastInstallAttemptAtMillis = now

			val current = _state.value
			if (current.flowState == WizardFlowState.Installing || installInFlight) {
				sendMessage(R.string.extension_builder_install_in_progress)
				return@launch
			}
			if (!force && current.flowState != WizardFlowState.PreviewSuccess) {
				sendMessage(R.string.extension_builder_preview_required)
				return@launch
			}
			installInFlight = true
			_state.value = current.copy(flowState = WizardFlowState.Installing)
			var replacedExisting = false
			val op = runCatching {
				val extension = LuaExtension(current.luaPreview, current.spec.id)
				val extensionLang = extensionLanguage(extension, current.spec.lang)
				val genericEntity = GenericExtensionEntity(
					id = extension.formatterID,
					repoID = BUILDER_REPO_ID,
					name = extension.name,
					fileName = current.spec.id,
					imageURL = extension.imageURL,
					lang = extensionLang,
					version = extension.exMetaData.version,
					md5 = "builder-${System.currentTimeMillis()}",
					type = ExtensionType.LuaScript
				)
				extensionRepository.getInstalledExtension(extension.formatterID)?.let { existing ->
					replacedExisting = true
					extensionEntitiesRepository.uninstall(
						GenericExtensionEntity(
							id = existing.id,
							repoID = existing.repoID,
							name = existing.name,
							fileName = existing.fileName,
							imageURL = existing.imageURL,
							lang = existing.lang,
							version = existing.version,
							md5 = existing.md5,
							type = existing.type
						)
					)
				}
				extensionEntitiesRepository.save(genericEntity, extension, current.luaPreview.encodeToByteArray())
				val installed = extensionRepository.getInstalledExtension(extension.formatterID)
				if (installed == null) {
					extensionRepository.insert(
						InstalledExtensionEntity(
							id = genericEntity.id,
							repoID = genericEntity.repoID,
							name = genericEntity.name,
							fileName = genericEntity.fileName,
							imageURL = genericEntity.imageURL,
							lang = genericEntity.lang,
							version = genericEntity.version,
							md5 = genericEntity.md5,
							type = genericEntity.type,
							enabled = true,
							chapterType = extension.chapterType
						)
					)
				} else {
					extensionRepository.updateInstalledExtension(
						installed.copy(
							name = genericEntity.name,
							fileName = genericEntity.fileName,
							imageURL = genericEntity.imageURL,
							lang = genericEntity.lang,
							version = genericEntity.version,
							md5 = genericEntity.md5,
							type = genericEntity.type,
							chapterType = extension.chapterType,
							repoID = BUILDER_REPO_ID,
							enabled = true
						)
					)
				}
			}
			op.fold(
				onSuccess = {
					_state.value = _state.value.copy(flowState = WizardFlowState.Installed)
					events.send(AddExtensionWizardEvent.InstallSuccess(replacedExisting))
				},
				onFailure = {
					_state.value = _state.value.copy(flowState = WizardFlowState.PreviewFailure, diagnostics = listOf(it.stackTraceToString()))
					sendMessage(R.string.extension_builder_install_failed)
				}
			)
			installInFlight = false
		}
	}

	private suspend fun sendMessage(messageRes: Int) {
		events.send(AddExtensionWizardEvent.ShowMessage(messageRes))
	}

	private fun extensionLanguage(ext: LuaExtension, fallback: String): String {
		val metadata = ext.exMetaData
		val languageGetter = metadata.javaClass.methods.firstOrNull { method ->
			method.parameterCount == 0 && (method.name == "getLanguage" || method.name == "getLang")
		} ?: return fallback

		return (languageGetter.invoke(metadata) as? String).orEmpty().ifBlank { fallback }
	}

	companion object {
		private const val TAG = "AddExtensionWizardVM"
		private const val STEP_KEY = "add_extension_wizard_step"
		private const val INSTALL_DEBOUNCE_MS = 750L
		const val BUILDER_REPO_ID = -100
	}
}

sealed interface AddExtensionWizardEvent {
	data class ShowMessage(val messageRes: Int) : AddExtensionWizardEvent
	data class InstallSuccess(val wasUpdate: Boolean) : AddExtensionWizardEvent
}

enum class WizardFlowState {
	Idle, Validated, Generated, Previewing, PreviewSuccess, PreviewFailure, Installing, Installed
}

data class PreviewRuntimeResult(
	val id: Int,
	val name: String,
	val lang: String,
	val version: String,
	val hasSearch: Boolean,
	val listings: List<String>,
	val listingResults: List<PreviewNovelResult>
)

data class PreviewNovelResult(
	val title: String,
	val imageUrl: String,
	val url: String,
	val imageUrlRaw: String = "",
	val urlRaw: String = ""
)

data class AddExtensionWizardUiState(
	val step: Int = 0,
	val spec: ExtensionSpec = ExtensionSpec(
		id = "builder.${UUID.randomUUID()}",
		name = "",
		lang = "en",
		baseUrl = "",
		version = "1.0.0",
		author = "",
		createdAt = System.currentTimeMillis(),
		runtimeId = stableRuntimeId("builder")
	),
	val validation: ValidationReport = ValidationReport(emptyList(), emptyList()),
	val luaPreview: String = "",
	val flowState: WizardFlowState = WizardFlowState.Idle,
	val preview: PreviewRuntimeResult? = null,
	val diagnostics: List<String> = emptyList(),
	val parsingTestInput: ParsingTestInput = ParsingTestInput(),
	val parsingTestResult: ParsingTestResult? = null,
	val parsingTestInProgress: ParsingTestSection? = null
) {
	val canGoNext: Boolean
		get() = when (step) {
			0, 1, 2, 3 -> true
			else -> false
		}

	val canFinish: Boolean
		get() = flowState == WizardFlowState.PreviewSuccess || flowState == WizardFlowState.Installed
}

data class ParsingTestInput(
	val listingUrl: String = "",
	val novelUrl: String = "",
	val chapterUrl: String = ""
)

data class ParsingTestResult(
	val listing: ListingTestDiagnostics? = null,
	val novel: NovelTestDiagnostics? = null,
	val chapterList: ChapterListTestDiagnostics? = null,
	val chapter: ChapterTestDiagnostics? = null,
	val error: String? = null,
	val sectionErrors: Map<ParsingTestSection, List<Int>> = emptyMap()
)

data class FetchDiagnostics(
	val success: Boolean,
	val finalUrl: String,
	val statusCode: Int,
	val responseSizeChars: Int,
	val html: String
)

data class ListingTestDiagnostics(
	val fetch: FetchDiagnostics,
	val cardCount: Int,
	val titleMatchesInFirstCard: Int,
	val linkMatchesInFirstCard: Int,
	val imageMatchesInFirstCard: Int,
	val sampleLinkValue: String,
	val sampleImageValue: String,
	val htmlDebugSnippet: String,
	val preview: List<PreviewNovelResult>
)

data class NovelTestDiagnostics(
	val fetch: FetchDiagnostics,
	val titleFound: Boolean,
	val chaptersFound: Int,
	val coverFound: Boolean,
	val descriptionLength: Int,
	val authorCount: Int,
	val genreCount: Int
)

data class ChapterListTestDiagnostics(
	val fetch: FetchDiagnostics,
	val chapterCount: Int,
	val preview: List<PreviewNovelResult>
)

data class ChapterTestDiagnostics(
	val fetch: FetchDiagnostics,
	val contentMatched: Boolean,
	val outputLength: Int,
	val preview: String = ""
)

enum class ParsingTestSection {
	LISTING,
	NOVEL,
	CHAPTER_LIST,
	CHAPTER_CONTENT,
	END_TO_END;

	fun missingFields(input: ParsingTestInput, basic: BasicParsingSpec): List<Int> = when (this) {
		LISTING -> buildList {
			if (input.listingUrl.isBlank()) add(R.string.parsing_test_listing_url)
			if (basic.itemSelector.isBlank()) add(R.string.parsing_field_book_card_label)
			if (basic.titleSelector.isBlank()) add(R.string.parsing_field_title_label)
			if (basic.urlSelector.isBlank()) add(R.string.parsing_field_link_selector_label)
			if (basic.urlAttr.isBlank()) add(R.string.parsing_field_link_attr_label)
			if (basic.imageSelector.isBlank()) add(R.string.parsing_field_image_selector_label)
			if (basic.imageAttr.isBlank()) add(R.string.parsing_field_image_attr_label)
		}
		NOVEL -> buildList {
			if (input.novelUrl.isBlank()) add(R.string.parsing_test_novel_url)
			if (basic.novelTitleSelector.isBlank()) add(R.string.parsing_field_novel_title_label)
			if (basic.coverSelector.isBlank()) add(R.string.parsing_field_cover_label)
			if (basic.descriptionSelector.isBlank()) add(R.string.parsing_field_description_label)
		}
		CHAPTER_LIST -> buildList {
			if (input.novelUrl.isBlank()) add(R.string.parsing_test_novel_url)
			if (basic.chapterItemSelector.isBlank()) add(R.string.parsing_field_chapter_item_label)
			if (basic.chapterTitleSelector.isBlank()) add(R.string.parsing_field_chapter_title_label)
			if (basic.chapterUrlSelector.isBlank()) add(R.string.parsing_field_chapter_url_label)
			if (basic.chapterUrlAttr.isBlank()) add(R.string.parsing_field_chapter_url_attr_label)
		}
		CHAPTER_CONTENT -> buildList {
			if (input.chapterUrl.isBlank()) add(R.string.parsing_test_chapter_url)
			if (basic.contentSelector.isBlank()) add(R.string.parsing_field_content_label)
		}
		END_TO_END -> buildList {
			addAll(LISTING.missingFields(input, basic))
			addAll(NOVEL.missingFields(input, basic))
			addAll(CHAPTER_LIST.missingFields(input, basic))
			addAll(CHAPTER_CONTENT.missingFields(input, basic))
		}.distinct()
	}
}

fun stableRuntimeId(seed: String): Int {
	val h = seed.hashCode()
	return if (h == Int.MIN_VALUE) 1 else kotlin.math.abs(h)
}

private fun AddExtensionWizardUiState.refreshDerived(
	validator: ExtensionSpecValidator,
	generator: ExtensionGenerator
): AddExtensionWizardUiState {
	val updateSpec = spec.copy(runtimeId = stableRuntimeId(spec.id))
	val report = validator.validate(updateSpec)
	return copy(spec = updateSpec, validation = report, luaPreview = generator.generateLua(updateSpec))
}
