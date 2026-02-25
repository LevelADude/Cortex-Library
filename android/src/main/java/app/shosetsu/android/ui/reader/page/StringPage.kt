package app.shosetsu.android.ui.reader.page

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import app.shosetsu.android.ui.reader.resolveReaderFontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.shosetsu.android.domain.model.reader.ReaderStylePreset
import app.shosetsu.android.domain.model.reader.ReaderTextAlign
import app.shosetsu.android.domain.model.reader.builtInReaderStylePresets
import app.shosetsu.android.view.compose.ScrollStateBar
import app.shosetsu.android.view.compose.ShosetsuCompose
import kotlinx.coroutines.launch

@Preview
@Composable
fun PreviewStringPageContent() {
	ShosetsuCompose {
		StringPageContent("preview", 0.0, 16f, {}, Color.Black.toArgb(), Color.White.toArgb(), false, {}, {}, builtInReaderStylePresets().first())
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StringPageContent(
	content: String,
	progress: Double,
	textSize: Float,
	onScroll: (perc: Double) -> Unit,
	textColor: Int,
	backgroundColor: Int,
	disableTextSelection: Boolean,
	onClick: () -> Unit,
	onDoubleClick: () -> Unit,
	stylePreset: ReaderStylePreset,
) {
	val style = stylePreset.style.clamped()
	val colors = style.light
	val textColors = style.textColors
	val fontFamily = resolveReaderFontFamily(style.fontFamily)
	val state = rememberScrollState()
	var first by remember { mutableStateOf(true) }
	if (state.isScrollInProgress) DisposableEffect(Unit) { onDispose { onScroll(if (state.value != 0) state.value.toDouble() / state.maxValue else 0.0) } }

	val body: @Composable () -> Unit = {
		Column(
			modifier = Modifier.fillMaxSize().verticalScroll(state).background(Color(backgroundColor)).padding(style.contentPaddingDp.dp).widthIn(max = style.maxContentWidthDp.dp)
		) {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				AssistChip(onClick = {}, label = { Text("Chapter", color = Color(textColors.chapterNumber)) }, colors = AssistChipDefaults.assistChipColors(containerColor = Color(colors.chipBg)))
				AssistChip(onClick = {}, label = { Text(stylePreset.name, color = Color(textColors.chapterTitle)) }, colors = AssistChipDefaults.assistChipColors(containerColor = Color(colors.chipBg)))
			}
			Text("Translator", color = Color(textColors.translatorLabel), modifier = Modifier.padding(top = 8.dp), fontFamily = fontFamily)
			Text("Community", color = Color(textColors.translatorValue), modifier = Modifier.padding(bottom = 8.dp), fontFamily = fontFamily)
			Text(content, fontSize = style.fontSizeSp.sp, lineHeight = style.lineHeightEm.em, letterSpacing = style.letterSpacingEm.em, modifier = Modifier.fillMaxSize().border(style.frame.borderWidthDp.dp, Color(style.frame.borderColor), RoundedCornerShape(style.frame.cornerRadiusDp.dp)).padding(8.dp), color = Color(textColors.bodyText), fontFamily = fontFamily, textAlign = when (style.textAlign) { ReaderTextAlign.Start -> TextAlign.Start; ReaderTextAlign.Center -> TextAlign.Center; ReaderTextAlign.End -> TextAlign.End; ReaderTextAlign.Justify -> TextAlign.Justify })
		}
	}

	ScrollStateBar(state) {
		if (disableTextSelection) body() else SelectionContainer(modifier = Modifier.combinedClickable(onDoubleClick = onDoubleClick, onClick = onClick, interactionSource = remember { MutableInteractionSource() }, indication = null)) { body() }
	}
	if (state.maxValue != 0 && state.maxValue != Int.MAX_VALUE && first) LaunchedEffect(progress) { launch { state.scrollTo((state.maxValue * progress).toInt()); first = false } }
}
