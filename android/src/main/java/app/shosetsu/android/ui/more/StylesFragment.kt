package app.shosetsu.android.ui.more

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.navigation.fragment.findNavController
import app.shosetsu.android.BuildConfig
import app.shosetsu.android.R
import app.shosetsu.android.domain.model.reader.ReaderStyle
import app.shosetsu.android.domain.model.reader.ReaderTextAlign
import app.shosetsu.android.domain.model.reader.ReaderTextColors
import app.shosetsu.android.domain.model.reader.builtInReaderStylePresets
import app.shosetsu.android.domain.repository.base.IReaderStyleRepository
import app.shosetsu.android.domain.repository.base.ISettingsRepository
import app.shosetsu.android.ui.reader.PublicFontLibraryEntry
import app.shosetsu.android.ui.reader.ReaderFontRegistry
import app.shosetsu.android.ui.reader.ReaderFontCatalog
import app.shosetsu.android.ui.reader.resolveReaderFontFamily
import app.shosetsu.android.view.compose.ShosetsuCompose
import app.shosetsu.android.view.controller.ShosetsuFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.instance

class StylesFragment : ShosetsuFragment(), DIAware {
	override val di: DI by closestDI()
	private val repo: IReaderStyleRepository by instance()
	private val settingsRepo: ISettingsRepository by instance()
	override val viewTitleRes: Int = R.string.styles
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		setViewTitle()
		if (BuildConfig.DEBUG) Log.d(TAG, "Opening Styles screen")
		return ComposeView(requireContext()).apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent { ShosetsuCompose { StylesScreen(repo, settingsRepo) { findNavController().popBackStack() } } }
		}
	}

	companion object {
		const val TAG = "StylesFragment"
	}
}

enum class TextColorTarget(val label: String) {
	ChapterTitle("Chapter title"), BodyText("Body text"), TranslatorLabel("Translator label"), TranslatorValue("Translator line"), ChapterNumber("Chapter number"), Metadata("Metadata")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StylesScreen(repo: IReaderStyleRepository, settingsRepo: ISettingsRepository, back: () -> Unit) {
	val scope = rememberCoroutineScope()
	val defaultPreset = remember { builtInReaderStylePresets().first() }
	val presets by repo.presetsFlow.collectAsState(initial = emptyList())
	val activeFromRepo by repo.activePresetFlow.collectAsState(initial = defaultPreset)
	val activePreset = remember(activeFromRepo.id, presets) { presets.find { it.id == activeFromRepo.id } ?: presets.firstOrNull() ?: defaultPreset }
	val fontOptions by ReaderFontRegistry.fontOptions.collectAsState()
	val context = LocalContext.current
	var stagedPresetId by remember(activePreset.id) { mutableStateOf(activePreset.id) }
	var stagedStyle by remember(activePreset.id) { mutableStateOf(activePreset.style) }
	var colorDialogTarget by remember { mutableStateOf<TextColorTarget?>(null) }
	var showDiscardDialog by remember { mutableStateOf(false) }
	var showFontLibrary by remember { mutableStateOf(false) }
	var installStatus by remember { mutableStateOf<String?>(null) }
	val hasUnsavedChanges = stagedPresetId != activePreset.id || stagedStyle != activePreset.style

	BackHandler(enabled = hasUnsavedChanges) { showDiscardDialog = true }

	Column(Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text("Reader Styles") },
			navigationIcon = {
				TextButton(onClick = {
					if (hasUnsavedChanges) showDiscardDialog = true else back()
				}) { Text("Back") }
			},
		)

		if (hasUnsavedChanges) {
			Text("Unsaved changes", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp))
		}
		installStatus?.let { Text(it, modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.secondary) }

		LazyColumn(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
			item { Text("Presets") }
			items(presets, key = { it.id }) { preset ->
				Row(
					Modifier.fillMaxWidth().clickable { stagedPresetId = preset.id; stagedStyle = preset.style }
						.border(1.dp, if (preset.id == stagedPresetId) MaterialTheme.colorScheme.primary else Color.Gray, RoundedCornerShape(8.dp)).padding(8.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
				) { Text(preset.name) }
			}
			item {
				Text("Live Preview")
				ReaderStylePreview(stagedStyle)
			}
			item {
				EditorControls(
					style = stagedStyle,
					fontOptions = fontOptions,
					update = { stagedStyle = it.clamped() },
					onColorEdit = { colorDialogTarget = it },
					onResetColors = { stagedStyle = stagedStyle.copy(textColors = ReaderTextColors.default()) },
					onBrowseFonts = { showFontLibrary = true },
				)
			}
		}

		Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			OutlinedButton(modifier = Modifier.weight(1f), onClick = {
				stagedPresetId = activePreset.id
				stagedStyle = activePreset.style
			}) { Text("Reset") }
			Button(modifier = Modifier.weight(1f), enabled = hasUnsavedChanges, onClick = {
				scope.launch(Dispatchers.IO) {
					val selectedPreset = presets.find { it.id == stagedPresetId } ?: activePreset
					repo.upsertPreset(selectedPreset.copy(style = stagedStyle))
					repo.setActivePreset(stagedPresetId)
				}
			}) { Text("Apply") }
		}
	}

	if (showDiscardDialog) {
		AlertDialog(
			onDismissRequest = { showDiscardDialog = false },
			title = { Text("Discard changes?") },
			text = { Text("Your staged style changes have not been applied.") },
			confirmButton = { TextButton(onClick = { showDiscardDialog = false; back() }) { Text("Discard") } },
			dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") } },
		)
	}

	if (showFontLibrary) {
		FontLibraryDialog(
			installedId = ReaderFontRegistry.installedMetadata?.id,
			onDismiss = { showFontLibrary = false },
			isInstalling = installStatus?.startsWith("Downloading") == true || installStatus?.startsWith("Installing") == true,
			onInstall = { entry ->
				scope.launch {
					installStatus = "Downloading ${entry.name}..."
					val result = ReaderFontRegistry.installSingleLibraryFont(context = context, settings = settingsRepo, entry = entry)
					result.onSuccess {
						installStatus = "Installed ${it.name}. Previous library font removed."
						stagedStyle = stagedStyle.copy(fontFamily = it.id)
					}.onFailure {
						installStatus = "Install failed: ${it.message ?: "unknown error"}"
					}
				}
			},
		)
	}

	colorDialogTarget?.let { target ->
		ColorPickerDialog(
			title = target.label,
			initialColor = Color(stagedStyle.textColors.forTarget(target).toInt()),
			onDismiss = { colorDialogTarget = null },
			onConfirm = { selected ->
				val colorLong = selected.toArgb().toLong() and 0xFFFFFFFFL
				stagedStyle = stagedStyle.copy(textColors = stagedStyle.textColors.withTarget(target, colorLong))
				colorDialogTarget = null
			},
		)
	}
}

@Composable
private fun EditorControls(
	style: ReaderStyle,
	fontOptions: List<app.shosetsu.android.ui.reader.ReaderFontOption>,
	update: (ReaderStyle) -> Unit,
	onColorEdit: (TextColorTarget) -> Unit,
	onResetColors: () -> Unit,
	onBrowseFonts: () -> Unit,
) {
	Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text("Font family")
		fontOptions.forEach { option ->
			Row(
				modifier = Modifier.fillMaxWidth().border(1.dp, if (style.fontFamily == option.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)).clickable { update(style.copy(fontFamily = option.id)) }.padding(10.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Text(option.label, fontFamily = option.fontFamily)
				Text(if (option.isLibraryFont) "Installed" else "Aa", fontFamily = option.fontFamily)
			}
		}
		OutlinedButton(onClick = onBrowseFonts) { Text("Browse Library") }
		Text("Font size ${style.fontSizeSp}")
		Slider(style.fontSizeSp, { update(style.copy(fontSizeSp = it)) }, valueRange = 12f..34f)
		Text("Line height ${style.lineHeightEm}")
		Slider(style.lineHeightEm, { update(style.copy(lineHeightEm = it)) }, valueRange = 1.2f..2.4f)
		Row { ReaderTextAlign.entries.forEach { a -> Text(a.name, Modifier.clickable { update(style.copy(textAlign = a)) }.padding(4.dp)) } }
		Text("Text colors")
		TextColorTarget.entries.forEach { target ->
			Row(modifier = Modifier.fillMaxWidth().clickable { onColorEdit(target) }, horizontalArrangement = Arrangement.SpaceBetween) {
				Text(target.label)
				Box(Modifier.size(20.dp).background(Color(style.textColors.forTarget(target).toInt()), CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
			}
		}
		TextButton(onClick = onResetColors) { Text("Reset to defaults") }
	}
}

@Composable
private fun FontLibraryDialog(
	installedId: String?,
	onDismiss: () -> Unit,
	onInstall: (PublicFontLibraryEntry) -> Unit,
	isInstalling: Boolean,
) {
	var query by remember { mutableStateOf("") }
	var sortMode by remember { mutableStateOf("Popularity") }
	var categoryFilter by remember { mutableStateOf("All") }
	val catalog by ReaderFontCatalog.entries.collectAsState()
	val isLoading = catalog.isEmpty()
	val filtered = remember(catalog, query, sortMode, categoryFilter) {
		catalog.asSequence()
			.filter { if (query.isBlank()) true else it.name.contains(query, ignoreCase = true) }
			.filter { if (categoryFilter == "All") true else it.category.equals(categoryFilter, ignoreCase = true) }
			.let {
				when (sortMode) {
					"Alphabetical" -> it.sortedBy { entry -> entry.name }
					else -> it.sortedByDescending { entry -> entry.popularity }
				}
			}
			.toList()
	}
	val categories = remember(catalog) { listOf("All", "sans-serif", "serif", "monospace", "display", "handwriting") }

	Dialog(onDismissRequest = onDismiss) {
		Surface(
			shape = RoundedCornerShape(20.dp),
			tonalElevation = 4.dp,
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 420.dp, max = 700.dp)
		) {
			Column(modifier = Modifier.fillMaxWidth().imePadding()) {
				Text("Font Library", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.weight(1f)
						.padding(horizontal = 16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					OutlinedTextField(
						value = query,
						onValueChange = { value: String -> query = value },
						modifier = Modifier.fillMaxWidth(),
						label = { Text("Search") },
						placeholder = { Text("Search fonts…") },
						singleLine = true,
					)
					OutlinedButton(onClick = { sortMode = if (sortMode == "Popularity") "Alphabetical" else "Popularity" }, modifier = Modifier.defaultMinSize(minHeight = 36.dp)) {
						Text("Sort: $sortMode", maxLines = 1, overflow = TextOverflow.Ellipsis)
					}
					LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
						items(categories) { cat ->
							OutlinedButton(
								onClick = { categoryFilter = cat },
								modifier = Modifier.defaultMinSize(minHeight = 36.dp),
							) {
								Text(if (categoryFilter == cat) "✓ $cat" else cat, maxLines = 1, overflow = TextOverflow.Ellipsis)
							}
						}
					}

					when {
						isLoading -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
							CircularProgressIndicator()
						}
						filtered.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
							Text(if (query.isBlank()) "No fonts available." else "No fonts found")
						}
						else -> LazyColumn(
							modifier = Modifier.fillMaxWidth().weight(1f),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							itemsIndexed(filtered, key = { _, it -> it.id }) { _, font ->
								FontLibraryCard(font = font, isInstalled = installedId == font.id, isInstalling = isInstalling, onInstall = onInstall)
							}
						}
					}
				}
				Divider(color = MaterialTheme.colorScheme.outlineVariant)
				Row(
					modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp).navigationBarsPadding(),
					horizontalArrangement = Arrangement.End,
				) {
					TextButton(onClick = onDismiss) { Text("Close") }
				}
			}
		}
	}
}

@Composable
private fun FontLibraryCard(font: PublicFontLibraryEntry, isInstalled: Boolean, isInstalling: Boolean, onInstall: (PublicFontLibraryEntry) -> Unit) {
	val context = LocalContext.current
	var previewFont by remember(font.id) { mutableStateOf<FontFamily?>(null) }
	LaunchedEffect(font.id) {
		previewFont = withContext(Dispatchers.IO) { ReaderFontRegistry.previewFont(context, font) }
	}
	Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(12.dp)) {
		Text(font.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
		Text(font.source, color = MaterialTheme.colorScheme.secondary)
		Text(font.previewText, fontFamily = previewFont ?: FontFamily.Default, maxLines = 2, overflow = TextOverflow.Ellipsis)
		Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
			Text(font.category, maxLines = 1)
			if (isInstalled) {
				Text("Installed", color = MaterialTheme.colorScheme.primary)
			} else {
				TextButton(onClick = { onInstall(font) }, enabled = !isInstalling) { Text(if (isInstalling) "Installing..." else "Install") }
			}
		}
	}
}

@Composable
private fun ReaderStylePreview(style: ReaderStyle) {
	val colors = style.light
	val textColors = style.textColors
	val fontFamily = resolveReaderFontFamily(style.fontFamily)
	Surface(
		color = Color(colors.background.toInt()),
		modifier = Modifier.fillMaxWidth().border(1.dp, Color(style.frame.borderColor.toInt())).padding(style.contentPaddingDp.dp),
	) {
		Column(Modifier.widthIn(max = style.maxContentWidthDp.dp).padding(8.dp)) {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				AssistChip(onClick = {}, label = { Text("#12", color = Color(textColors.chapterNumber.toInt())) }, colors = AssistChipDefaults.assistChipColors(containerColor = Color(colors.chipBg.toInt())))
				AssistChip(onClick = {}, label = { Text("The City of Rain", color = Color(textColors.chapterTitle.toInt())) }, colors = AssistChipDefaults.assistChipColors(containerColor = Color(colors.chipBg.toInt())))
			}
			Text("Translator", color = Color(textColors.translatorLabel.toInt()), fontFamily = fontFamily)
			Text("Anon Team", color = Color(textColors.translatorValue.toInt()), fontFamily = fontFamily)
			Text(
				"Preview paragraph with a link color sample.",
				fontSize = style.fontSizeSp.sp,
				lineHeight = style.lineHeightEm.em,
				fontFamily = fontFamily,
				modifier = Modifier.fillMaxWidth().padding(8.dp),
				color = Color(textColors.bodyText.toInt()),
				textAlign = when (style.textAlign) {
					ReaderTextAlign.Start -> TextAlign.Start
					ReaderTextAlign.Center -> TextAlign.Center
					ReaderTextAlign.End -> TextAlign.End
					ReaderTextAlign.Justify -> TextAlign.Justify
				},
			)
		}
	}
}

@Composable
private fun ColorPickerDialog(title: String, initialColor: Color, onDismiss: () -> Unit, onConfirm: (Color) -> Unit) {
	val hsv = FloatArray(3)
	android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
	var hue by remember { mutableStateOf(hsv[0]) }
	var saturation by remember { mutableStateOf(hsv[1]) }
	var value by remember { mutableStateOf(hsv[2]) }
	var hex by remember { mutableStateOf(initialColor.toHex()) }
	val selected = remember(hue, saturation, value) { Color.hsv(hue, saturation, value) }

	LaunchedEffect(selected) { hex = selected.toHex() }

	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Confirm") } },
		dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
		title = { Text(title) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				Box(Modifier.fillMaxWidth().height(34.dp).background(selected, RoundedCornerShape(8.dp)))
				HSVPalette(
					hue = hue,
					saturation = saturation,
					value = value,
					onChange = { s, v -> saturation = s; value = v },
				)
				Text("Hue")
				Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
				Text("Value")
				Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
				BasicTextField(
					value = hex,
					onValueChange = {
						hex = it
						parseHexColor(it)?.let { parsed ->
							val parsedHsv = FloatArray(3)
							android.graphics.Color.colorToHSV(parsed.toArgb(), parsedHsv)
							hue = parsedHsv[0]
							saturation = parsedHsv[1]
							value = parsedHsv[2]
						}
					},
					modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).padding(8.dp),
					textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground),
				)
				if (ColorUtils.calculateContrast(selected.toArgb(), MaterialTheme.colorScheme.background.toArgb()) < 4.5) {
					Text("Contrast warning: this text may be hard to read.", color = MaterialTheme.colorScheme.error)
				}
			}
		},
	)
}

@Composable
private fun HSVPalette(hue: Float, saturation: Float, value: Float, onChange: (Float, Float) -> Unit) {
	Box(modifier = Modifier.fillMaxWidth().height(170.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
		Canvas(
			modifier = Modifier.fillMaxSize().pointerInput(hue) {
				detectDragGestures { change, _ ->
					val x = (change.position.x / size.width).coerceIn(0f, 1f)
					val y = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
					onChange(x, y)
				}
			},
		) {
			drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
			drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
			val px = saturation * size.width
			val py = (1f - value) * size.height
			drawCircle(color = Color.White, radius = 10f, center = Offset(px, py))
		}
	}
}

private fun parseHexColor(value: String): Color? {
	val normalized = value.removePrefix("#")
	return when (normalized.length) {
		6 -> normalized.toLongOrNull(16)?.let { Color((0xFF000000 or it).toInt()) }
		8 -> normalized.toLongOrNull(16)?.let { Color(it.toInt()) }
		else -> null
	}
}

private fun Color.toHex(): String = String.format("#%08X", this.toArgb())

private fun ReaderTextColors.forTarget(target: TextColorTarget): Long = when (target) {
	TextColorTarget.ChapterTitle -> chapterTitle
	TextColorTarget.BodyText -> bodyText
	TextColorTarget.TranslatorLabel -> translatorLabel
	TextColorTarget.TranslatorValue -> translatorValue
	TextColorTarget.ChapterNumber -> chapterNumber
	TextColorTarget.Metadata -> metadata
}

private fun ReaderTextColors.withTarget(target: TextColorTarget, value: Long): ReaderTextColors = when (target) {
	TextColorTarget.ChapterTitle -> copy(chapterTitle = value)
	TextColorTarget.BodyText -> copy(bodyText = value)
	TextColorTarget.TranslatorLabel -> copy(translatorLabel = value)
	TextColorTarget.TranslatorValue -> copy(translatorValue = value)
	TextColorTarget.ChapterNumber -> copy(chapterNumber = value)
	TextColorTarget.Metadata -> copy(metadata = value)
}
