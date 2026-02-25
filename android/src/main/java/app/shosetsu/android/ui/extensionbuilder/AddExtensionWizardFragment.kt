package app.shosetsu.android.ui.extensionbuilder

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import app.shosetsu.android.R
import app.shosetsu.android.common.ext.ComposeView
import app.shosetsu.android.extensionbuilder.ContentOutput
import app.shosetsu.android.extensionbuilder.ExtensionCapabilities
import app.shosetsu.android.extensionbuilder.HeaderEntry
import app.shosetsu.android.extensionbuilder.ParsingMode
import app.shosetsu.android.extensionbuilder.ValueSource
import app.shosetsu.android.view.compose.ShosetsuCompose
import app.shosetsu.android.view.controller.ShosetsuFragment
import app.shosetsu.android.view.controller.base.CollapsedToolBarController
import kotlinx.coroutines.launch

class AddExtensionWizardFragment : ShosetsuFragment(), CollapsedToolBarController {
	override val viewTitleRes: Int = R.string.add_extension
	private val viewModel: AddExtensionWizardViewModel by viewModels()

	override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.eventFlow.collect { event ->
					when (event) {
						is AddExtensionWizardEvent.ShowMessage -> Toast.makeText(requireContext(), getString(event.messageRes), Toast.LENGTH_SHORT).show()
						is AddExtensionWizardEvent.InstallSuccess -> {
							Toast.makeText(
								requireContext(),
								if (event.wasUpdate) getString(R.string.extension_builder_update_success) else "Extension installed successfully",
								Toast.LENGTH_SHORT
							).show()
							findNavController().popBackStack()
						}
					}
				}
			}
		}
	}

	override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedViewState: Bundle?): android.view.View {
		setViewTitle()
		return ComposeView { AddExtensionWizardView(viewModel) }
	}
}

@Composable
fun AddExtensionWizardView(viewModel: AddExtensionWizardViewModel) {
	val state by viewModel.state.collectAsState()

	ShosetsuCompose {
		Column(
			modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Text(text = stringResource(R.string.add_extension_wizard_title), style = MaterialTheme.typography.headlineSmall)
			Text(text = stringResource(R.string.add_extension_wizard_subtitle), style = MaterialTheme.typography.bodyMedium)

			when (state.step) {
				0 -> BasicInfoStep(state, viewModel)
				1 -> SourceStep(state, viewModel)
				2 -> CapabilityStep(state, viewModel)
				3 -> ParsingStep(state, viewModel)
				4 -> ReviewStep(state, viewModel)
			}

			WizardNav(state, onBack = viewModel::previousStep, onNext = viewModel::nextStep, onInstall = { viewModel.installExtension() })
		}
	}
}

@Composable
private fun BasicInfoStep(state: AddExtensionWizardUiState, viewModel: AddExtensionWizardViewModel) {
	OutlinedTextField(state.spec.name, { viewModel.updateBasic(name = it) }, label = { Text(stringResource(R.string.extension_builder_name)) }, modifier = Modifier.fillMaxWidth())
	OutlinedTextField(state.spec.lang, { viewModel.updateBasic(lang = it) }, label = { Text(stringResource(R.string.extension_builder_language)) }, modifier = Modifier.fillMaxWidth())
	OutlinedTextField(state.spec.author ?: "", { viewModel.updateBasic(author = it) }, label = { Text(stringResource(R.string.extension_builder_author)) }, modifier = Modifier.fillMaxWidth())
	OutlinedTextField(state.spec.version, { viewModel.updateBasic(version = it) }, label = { Text(stringResource(R.string.extension_builder_version)) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SourceStep(state: AddExtensionWizardUiState, viewModel: AddExtensionWizardViewModel) {
	OutlinedTextField(state.spec.baseUrl, { viewModel.updateSource(baseUrl = it) }, label = { Text(stringResource(R.string.extension_builder_base_url)) }, modifier = Modifier.fillMaxWidth())
	OutlinedTextField(state.spec.userAgent ?: "", { viewModel.updateSource(userAgent = it) }, label = { Text(stringResource(R.string.extension_builder_user_agent)) }, modifier = Modifier.fillMaxWidth())
	Text(stringResource(R.string.extension_builder_headers), style = MaterialTheme.typography.titleMedium)
	state.spec.headers.forEachIndexed { index, header ->
		HeaderRow(header, onKey = { viewModel.updateHeader(index, key = it) }, onValue = { viewModel.updateHeader(index, value = it) }, onRemove = { viewModel.removeHeader(index) })
	}
	OutlinedButton(onClick = viewModel::addHeader) { Text(stringResource(R.string.extension_builder_add_header)) }
}

@Composable
private fun HeaderRow(header: HeaderEntry, onKey: (String) -> Unit, onValue: (String) -> Unit, onRemove: () -> Unit) {
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
		OutlinedTextField(header.key, onKey, label = { Text(stringResource(R.string.extension_builder_header_key)) }, modifier = Modifier.weight(1f))
		OutlinedTextField(header.value, onValue, label = { Text(stringResource(R.string.extension_builder_header_value)) }, modifier = Modifier.weight(1f))
		OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.extension_builder_remove)) }
	}
}

@Composable
private fun CapabilityStep(state: AddExtensionWizardUiState, viewModel: AddExtensionWizardViewModel) {
	val caps = state.spec.capabilities
	CapabilityToggle(stringResource(R.string.extension_builder_cap_search), caps.supportsSearch) { viewModel.updateCapabilities(caps.copy(supportsSearch = it)) }
	CapabilityToggle(stringResource(R.string.extension_builder_cap_latest), caps.supportsLatest) { viewModel.updateCapabilities(caps.copy(supportsLatest = it)) }
	CapabilityToggle(stringResource(R.string.extension_builder_cap_genres), caps.supportsGenres) { viewModel.updateCapabilities(caps.copy(supportsGenres = it)) }
	CapabilityToggle(stringResource(R.string.extension_builder_cap_filters), caps.supportsFilters) { viewModel.updateCapabilities(caps.copy(supportsFilters = it)) }
	CapabilityToggle(stringResource(R.string.extension_builder_cap_multi_domain), caps.supportsMultipleDomains) { viewModel.updateCapabilities(caps.copy(supportsMultipleDomains = it)) }
}

@Composable
private fun ParsingStep(state: AddExtensionWizardUiState, viewModel: AddExtensionWizardViewModel) {
	Text(stringResource(R.string.parsing_manual_step_title), style = MaterialTheme.typography.titleLarge)
	CapabilityToggle(stringResource(R.string.parsing_enable_now_label), state.spec.parsing.enabled, viewModel::updateParsingEnabled)
	if (!state.spec.parsing.enabled) {
		Text(stringResource(R.string.parsing_disabled_hint), style = MaterialTheme.typography.bodySmall)
		return
	}
	ModeSelector(state.spec.parsing.mode, onMode = viewModel::updateParsingMode)
	if (state.spec.parsing.mode == ParsingMode.BASIC) {
		BasicParsingForm(state, viewModel)
	} else {
		Text(stringResource(R.string.parsing_advanced_hint), style = MaterialTheme.typography.bodySmall)
		OutlinedTextField(
			value = state.spec.parsing.luaOverrideSnippet,
			onValueChange = viewModel::updateAdvancedSnippet,
			label = { Text(stringResource(R.string.parsing_lua_override_label)) },
			modifier = Modifier.fillMaxWidth().height(180.dp)
		)
	}
}

@Composable
private fun ModeSelector(mode: ParsingMode, onMode: (ParsingMode) -> Unit) {
	Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		Row { RadioButton(selected = mode == ParsingMode.BASIC, onClick = { onMode(ParsingMode.BASIC) }); Text(stringResource(R.string.parsing_mode_basic)) }
		Row { RadioButton(selected = mode == ParsingMode.ADVANCED, onClick = { onMode(ParsingMode.ADVANCED) }); Text(stringResource(R.string.parsing_mode_advanced)) }
	}
}

@Composable
private fun BasicParsingForm(state: AddExtensionWizardUiState, viewModel: AddExtensionWizardViewModel) {
	val basic = state.spec.parsing.basic
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
			Text(stringResource(R.string.parsing_manual_intro_title), style = MaterialTheme.typography.titleMedium)
			Text(stringResource(R.string.parsing_manual_intro_b1), style = MaterialTheme.typography.bodySmall)
			Text(stringResource(R.string.parsing_manual_intro_b2), style = MaterialTheme.typography.bodySmall)
			Text(stringResource(R.string.parsing_manual_intro_b3), style = MaterialTheme.typography.bodySmall)
			Text(stringResource(R.string.parsing_manual_intro_b4), style = MaterialTheme.typography.bodySmall)
		}
	}

	SectionHelp(stringResource(R.string.parsing_section_listing_title))
	ParsingField(stringResource(R.string.parsing_field_book_card_label), stringResource(R.string.parsing_field_book_card_help), basic.itemSelector, required = true) { value -> viewModel.updateBasicParsing { it.copy(itemSelector = value) } }
	ParsingField(stringResource(R.string.parsing_field_title_label), stringResource(R.string.parsing_field_title_help), basic.titleSelector, required = true) { value -> viewModel.updateBasicParsing { it.copy(titleSelector = value) } }
	SourceSelector(stringResource(R.string.parsing_field_title_source_label), basic.titleFrom) { source -> viewModel.updateBasicParsing { it.copy(titleFrom = source) } }
	if (basic.titleFrom == ValueSource.ATTR) {
		ParsingField(stringResource(R.string.parsing_field_title_attr_label), stringResource(R.string.parsing_field_title_attr_help), basic.titleAttr.orEmpty()) { value -> viewModel.updateBasicParsing { b -> b.copy(titleAttr = value) } }
	}
	ParsingField(stringResource(R.string.parsing_field_link_selector_label), stringResource(R.string.parsing_field_link_selector_help), basic.urlSelector, required = true) { value -> viewModel.updateBasicParsing { it.copy(urlSelector = value) } }
	ParsingField(stringResource(R.string.parsing_field_link_attr_label), stringResource(R.string.parsing_field_link_attr_help), basic.urlAttr, required = true, isAttribute = true) { value -> viewModel.updateBasicParsing { it.copy(urlAttr = value) } }
	ParsingField(stringResource(R.string.parsing_field_image_selector_label), stringResource(R.string.parsing_field_image_selector_help), basic.imageSelector, required = true) { value -> viewModel.updateBasicParsing { it.copy(imageSelector = value) } }
	ParsingField(stringResource(R.string.parsing_field_image_attr_label), stringResource(R.string.parsing_field_image_attr_help), basic.imageAttr, required = true, isAttribute = true, note = stringResource(R.string.parsing_field_image_attr_note)) { value -> viewModel.updateBasicParsing { it.copy(imageAttr = value) } }
	SectionTestCard(state, ParsingTestSection.LISTING, R.string.parsing_test_listing_button) { viewModel.runParsingTestSection(ParsingTestSection.LISTING) }

	SectionHelp(stringResource(R.string.parsing_section_novel_title))
	ParsingField(stringResource(R.string.parsing_field_novel_title_label), stringResource(R.string.parsing_field_novel_title_help), basic.novelTitleSelector) { value -> viewModel.updateBasicParsing { it.copy(novelTitleSelector = value) } }
	ParsingField(stringResource(R.string.parsing_field_cover_label), stringResource(R.string.parsing_field_cover_help), basic.coverSelector) { value -> viewModel.updateBasicParsing { it.copy(coverSelector = value) } }
	ParsingField(stringResource(R.string.parsing_field_description_label), stringResource(R.string.parsing_field_description_help), basic.descriptionSelector) { value -> viewModel.updateBasicParsing { it.copy(descriptionSelector = value) } }
	ParsingField(stringResource(R.string.parsing_field_authors_label), stringResource(R.string.parsing_field_authors_help), basic.authorSelector) { value -> viewModel.updateBasicParsing { it.copy(authorSelector = value) } }
	ParsingField(stringResource(R.string.parsing_field_genres_label), stringResource(R.string.parsing_field_genres_help), basic.genreSelector) { value -> viewModel.updateBasicParsing { it.copy(genreSelector = value) } }
	ParsingField(stringResource(R.string.parsing_field_status_label), stringResource(R.string.parsing_field_status_help), basic.statusSelector) { value -> viewModel.updateBasicParsing { it.copy(statusSelector = value) } }
	SectionTestCard(state, ParsingTestSection.NOVEL, R.string.parsing_test_novel_button) { viewModel.runParsingTestSection(ParsingTestSection.NOVEL) }

	SectionHelp(stringResource(R.string.parsing_section_chapters_title))
	ParsingField(stringResource(R.string.parsing_field_chapter_item_label), stringResource(R.string.parsing_field_chapter_item_help), basic.chapterItemSelector) { value -> viewModel.updateBasicParsing { it.copy(chapterItemSelector = value) } }
	ParsingField(stringResource(R.string.parsing_field_chapter_title_label), stringResource(R.string.parsing_field_chapter_title_help), basic.chapterTitleSelector) { value -> viewModel.updateBasicParsing { it.copy(chapterTitleSelector = value) } }
	ParsingField(stringResource(R.string.parsing_field_chapter_url_label), stringResource(R.string.parsing_field_chapter_url_help), basic.chapterUrlSelector) { value -> viewModel.updateBasicParsing { it.copy(chapterUrlSelector = value) } }
	ParsingField(stringResource(R.string.parsing_field_chapter_url_attr_label), stringResource(R.string.parsing_field_chapter_url_attr_help), basic.chapterUrlAttr, required = true, isAttribute = true) { value -> viewModel.updateBasicParsing { it.copy(chapterUrlAttr = value) } }
	SectionTestCard(state, ParsingTestSection.CHAPTER_LIST, R.string.parsing_test_chapter_list_button) { viewModel.runParsingTestSection(ParsingTestSection.CHAPTER_LIST) }

	SectionHelp(stringResource(R.string.parsing_section_content_title))
	ParsingField(stringResource(R.string.parsing_field_content_label), stringResource(R.string.parsing_field_content_help), basic.contentSelector) { value -> viewModel.updateBasicParsing { it.copy(contentSelector = value) } }
	ParsingField(stringResource(R.string.parsing_field_remove_label), stringResource(R.string.parsing_field_remove_help), basic.removeSelector.orEmpty()) { value -> viewModel.updateBasicParsing { b -> b.copy(removeSelector = value) } }
	Text(stringResource(R.string.parsing_output_label), style = MaterialTheme.typography.labelLarge)
	Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		Row { RadioButton(selected = basic.contentOutput == ContentOutput.HTML, onClick = { viewModel.updateBasicParsing { it.copy(contentOutput = ContentOutput.HTML) } }); Text(stringResource(R.string.parsing_output_html)) }
		Row { RadioButton(selected = basic.contentOutput == ContentOutput.TEXT, onClick = { viewModel.updateBasicParsing { it.copy(contentOutput = ContentOutput.TEXT) } }); Text(stringResource(R.string.parsing_output_text)) }
	}
	SectionTestCard(state, ParsingTestSection.CHAPTER_CONTENT, R.string.parsing_test_chapter_content_button) { viewModel.runParsingTestSection(ParsingTestSection.CHAPTER_CONTENT) }
	ParsingTestPanel(state, viewModel)
}

@Composable
private fun SectionHelp(title: String) {
	Text(title, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun SourceSelector(label: String, source: ValueSource, onChange: (ValueSource) -> Unit) {
	Text(label, style = MaterialTheme.typography.labelLarge)
	Text(stringResource(R.string.parsing_source_help), style = MaterialTheme.typography.bodySmall)
	Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		Row { RadioButton(selected = source == ValueSource.TEXT, onClick = { onChange(ValueSource.TEXT) }); Text(stringResource(R.string.parsing_source_text_label)) }
		Row { RadioButton(selected = source == ValueSource.ATTR, onClick = { onChange(ValueSource.ATTR) }); Text(stringResource(R.string.parsing_source_attr_label)) }
	}
}

@Composable
private fun ParsingField(label: String, description: String, value: String, required: Boolean = false, isAttribute: Boolean = false, note: String? = null, onValueChange: (String) -> Unit) {
	OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
	Text(description, style = MaterialTheme.typography.bodySmall)
	note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
	selectorFieldWarning(value, required, isAttribute)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
	selectorFieldError(value, required, isAttribute)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

private fun selectorFieldError(value: String, required: Boolean, isAttribute: Boolean): Int? {
	val trimmed = value.trim()
	if (required && trimmed.isBlank()) return if (isAttribute) R.string.parsing_validation_required_attribute else R.string.parsing_validation_required_selector
	return null
}

private fun selectorFieldWarning(value: String, required: Boolean, isAttribute: Boolean): Int? {
	val trimmed = value.trim()
	if (!required && trimmed.isBlank()) return null
	if (!isAttribute && trimmed.contains(":nth-child")) return R.string.parsing_validation_warn_nth
	if (!isAttribute && trimmed.count { it == '>' } >= 3) return R.string.parsing_validation_warn_long
	if (trimmed.contains('"') || trimmed.contains('\'')) return if (isAttribute) R.string.parsing_validation_warn_attribute_plain else R.string.parsing_validation_warn_selector_quotes
	if (isAttribute && trimmed.contains(' ')) return R.string.parsing_validation_warn_attribute_plain
	return null
}

@Composable
private fun ParsingTestPanel(state: AddExtensionWizardUiState, viewModel: AddExtensionWizardViewModel) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text(stringResource(R.string.parsing_test_title), style = MaterialTheme.typography.titleMedium)
			OutlinedTextField(value = state.parsingTestInput.listingUrl, onValueChange = { viewModel.updateParsingTestUrls(listingUrl = it) }, label = { Text(stringResource(R.string.parsing_test_listing_url)) }, modifier = Modifier.fillMaxWidth())
			OutlinedTextField(value = state.parsingTestInput.novelUrl, onValueChange = { viewModel.updateParsingTestUrls(novelUrl = it) }, label = { Text(stringResource(R.string.parsing_test_novel_url)) }, modifier = Modifier.fillMaxWidth())
			OutlinedTextField(value = state.parsingTestInput.chapterUrl, onValueChange = { viewModel.updateParsingTestUrls(chapterUrl = it) }, label = { Text(stringResource(R.string.parsing_test_chapter_url)) }, modifier = Modifier.fillMaxWidth())
			Button(onClick = viewModel::runParsingTest, enabled = state.parsingTestInProgress == null) { Text(stringResource(R.string.parsing_test_run)) }
			Text(stringResource(R.string.parsing_test_phase_label), style = MaterialTheme.typography.bodySmall)
		}
	}
}

@Composable
private fun SectionTestCard(state: AddExtensionWizardUiState, section: ParsingTestSection, buttonLabel: Int, onRun: () -> Unit) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Button(onClick = onRun, enabled = state.parsingTestInProgress != section) { Text(stringResource(buttonLabel)) }
			if (state.parsingTestInProgress == section) Text(stringResource(R.string.parsing_test_loading))
			state.parsingTestResult?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
			state.parsingTestResult?.sectionErrors?.get(section)?.forEach { resId ->
				Text(stringResource(R.string.parsing_test_missing_field, stringResource(resId)), color = MaterialTheme.colorScheme.error)
			}
			when (section) {
				ParsingTestSection.LISTING -> state.parsingTestResult?.listing?.let { listing ->
					Text(stringResource(R.string.parsing_test_fetch_success, listing.fetch.finalUrl, listing.fetch.statusCode, listing.fetch.responseSizeChars))
					Text(stringResource(R.string.parsing_test_cards_found, listing.cardCount))
					Text(stringResource(R.string.parsing_test_title_matches, listing.titleMatchesInFirstCard))
					Text(stringResource(R.string.parsing_test_link_matches, listing.linkMatchesInFirstCard, listing.sampleLinkValue))
					Text(stringResource(R.string.parsing_test_image_matches, listing.imageMatchesInFirstCard, listing.sampleImageValue))
					listing.preview.firstOrNull()?.let { first ->
						Text(stringResource(R.string.parsing_test_first_item_title, first.title))
						Text(stringResource(R.string.parsing_test_preview_url, first.urlRaw, first.url))
						Text(stringResource(R.string.parsing_test_preview_img, first.imageUrlRaw, first.imageUrl))
					}
				}
				ParsingTestSection.NOVEL -> state.parsingTestResult?.novel?.let { novel ->
					Text(stringResource(R.string.parsing_test_fetch_success, novel.fetch.finalUrl, novel.fetch.statusCode, novel.fetch.responseSizeChars))
					Text(stringResource(R.string.parsing_test_novel_title_found, novel.titleFound.toString()))
					Text(stringResource(R.string.parsing_test_novel_cover_found, novel.coverFound.toString()))
					Text(stringResource(R.string.parsing_test_novel_description_length, novel.descriptionLength))
					Text(stringResource(R.string.parsing_test_novel_author_count, novel.authorCount))
					Text(stringResource(R.string.parsing_test_novel_genre_count, novel.genreCount))
				}
				ParsingTestSection.CHAPTER_LIST -> state.parsingTestResult?.chapterList?.let { list ->
					Text(stringResource(R.string.parsing_test_fetch_success, list.fetch.finalUrl, list.fetch.statusCode, list.fetch.responseSizeChars))
					Text(stringResource(R.string.parsing_test_chapter_count, list.chapterCount))
					list.preview.forEach { item ->
						Text("• ${item.title}")
						Text(stringResource(R.string.parsing_test_preview_url, item.urlRaw, item.url))
					}
				}
				ParsingTestSection.CHAPTER_CONTENT -> state.parsingTestResult?.chapter?.let { chapter ->
					Text(stringResource(R.string.parsing_test_fetch_success, chapter.fetch.finalUrl, chapter.fetch.statusCode, chapter.fetch.responseSizeChars))
					Text(stringResource(R.string.parsing_test_content_matched, chapter.contentMatched.toString()))
					Text(stringResource(R.string.parsing_test_output_length, chapter.outputLength))
					Text(stringResource(R.string.parsing_test_content_preview, chapter.preview))
				}
				ParsingTestSection.END_TO_END -> Unit
			}
		}
	}
}
@Composable
private fun CapabilityToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
	Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
		Text(label)
		Switch(checked = checked, onCheckedChange = onChecked)
	}
}

@Composable
private fun ReviewStep(state: AddExtensionWizardUiState, viewModel: AddExtensionWizardViewModel) {
	val clipboard = LocalClipboardManager.current
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
			Text(stringResource(R.string.extension_builder_review_summary), style = MaterialTheme.typography.titleMedium)
			Text(stringResource(R.string.extension_builder_summary_name_lang, state.spec.name, state.spec.lang))
			Text(state.spec.baseUrl)
			Text(stringResource(R.string.extension_builder_summary_version, state.spec.version))
			Text(stringResource(R.string.extension_builder_runtime_id, state.spec.runtimeId))
		}
	}
	if (state.validation.errors.isNotEmpty() || state.validation.warnings.isNotEmpty()) {
		Card(modifier = Modifier.fillMaxWidth()) {
			Column(Modifier.padding(12.dp)) {
				state.validation.errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
				state.validation.warnings.forEach { Text("• $it", color = MaterialTheme.colorScheme.tertiary) }
			}
		}
	}
	Text(stringResource(R.string.extension_builder_lua_preview), style = MaterialTheme.typography.titleMedium)
	Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) { Text(state.luaPreview, style = MaterialTheme.typography.bodySmall) }
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		Button(onClick = viewModel::saveSpec, enabled = state.flowState != WizardFlowState.Installing) { Text(stringResource(R.string.extension_builder_save_spec)) }
		Button(onClick = viewModel::generateAndSaveLua, enabled = state.flowState != WizardFlowState.Installing) { Text(stringResource(R.string.extension_builder_generate_lua)) }
		OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.luaPreview)) }, enabled = state.flowState != WizardFlowState.Installing) { Text(stringResource(R.string.extension_builder_copy_lua)) }
	}
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		Button(onClick = viewModel::previewGenerated, enabled = state.flowState != WizardFlowState.Installing) { Text(stringResource(R.string.extension_builder_preview_run)) }
		Button(onClick = { viewModel.installExtension(force = true) }, enabled = state.flowState != WizardFlowState.Installing) { Text(stringResource(R.string.extension_builder_install_anyway)) }
	}
	Text(stringResource(R.string.extension_builder_flow_state, state.flowState.name), style = MaterialTheme.typography.bodySmall)
	state.preview?.let { preview ->
		Card(modifier = Modifier.fillMaxWidth()) {
			Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text(stringResource(R.string.extension_builder_preview_meta), style = MaterialTheme.typography.titleSmall)
				Text(stringResource(R.string.extension_builder_preview_meta_line, preview.id, preview.name, preview.lang, preview.version))
				Text(stringResource(R.string.extension_builder_preview_search_listing, preview.hasSearch, preview.listings.size))
				preview.listingResults.takeIf { it.isNotEmpty() }?.let {
					LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(220.dp)) {
						items(it) { item -> Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text(item.title, style = MaterialTheme.typography.titleSmall); Text(item.url, style = MaterialTheme.typography.bodySmall) } } }
					}
				}
			}
		}
	}
}

@Composable
private fun WizardNav(state: AddExtensionWizardUiState, onBack: () -> Unit, onNext: () -> Unit, onInstall: () -> Unit) {
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		val isInstalling = state.flowState == WizardFlowState.Installing
		OutlinedButton(onClick = onBack, enabled = state.step > 0 && !isInstalling) { Text(stringResource(R.string.extension_builder_back)) }
		Button(onClick = onNext, enabled = state.canGoNext && !isInstalling) { Text(stringResource(R.string.extension_builder_next)) }
		Button(onClick = onInstall, enabled = state.step == 4 && state.canFinish && !isInstalling) { Text(stringResource(R.string.extension_builder_finish_install)) }
	}
	Spacer(modifier = Modifier.height(8.dp))
}
