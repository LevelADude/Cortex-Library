package app.shosetsu.android.domain.model.reader

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class ReaderStyle(
	val fontFamily: String = "default",
	val fontSizeSp: Float = 18f,
	val lineHeightEm: Float = 1.6f,
	val letterSpacingEm: Float = 0f,
	val paragraphSpacingEm: Float = 0.9f,
	val textAlign: ReaderTextAlign = ReaderTextAlign.Justify,
	val contentPaddingDp: Float = 20f,
	val maxContentWidthDp: Float = 720f,
	val frame: ReaderFrameStyle = ReaderFrameStyle(),
	val light: ReaderStyleColors = ReaderStyleColors.defaultLight(),
	val dark: ReaderStyleColors = ReaderStyleColors.defaultDark(),
	val textColors: ReaderTextColors = ReaderTextColors.default(),
) {
	fun clamped(): ReaderStyle = copy(
		fontSizeSp = fontSizeSp.coerceIn(12f, 34f).round1(),
		lineHeightEm = lineHeightEm.coerceIn(1.2f, 2.4f).round1(),
		letterSpacingEm = letterSpacingEm.coerceIn(-0.03f, 0.12f).round2(),
		paragraphSpacingEm = paragraphSpacingEm.coerceIn(0f, 2f).round1(),
		contentPaddingDp = contentPaddingDp.coerceIn(0f, 48f).round1(),
		maxContentWidthDp = maxContentWidthDp.coerceIn(360f, 1200f).round1(),
		frame = frame.clamped()
	)
}

@Serializable
enum class ReaderTextAlign { Start, Center, End, Justify }

@Serializable
data class ReaderFrameStyle(
	val borderColor: Long = 0xFFE0E0E0,
	val borderWidthDp: Float = 0f,
	val cornerRadiusDp: Float = 12f,
	val doubleFrame: Boolean = false,
) {
	fun clamped() = copy(
		borderWidthDp = borderWidthDp.coerceIn(0f, 8f).round1(),
		cornerRadiusDp = cornerRadiusDp.coerceIn(0f, 28f).round1()
	)
}

@Serializable
data class ReaderStyleColors(
	val text: Long,
	val background: Long,
	val link: Long,
	val chipBg: Long,
	val chipText: Long,
	val metaLabel: Long,
	val metaValue: Long,
) {
	companion object {
		fun defaultLight() = ReaderStyleColors(0xFF1D1D1D, 0xFFFFFBF5, 0xFF2B5CCB, 0xFFEFE2CF, 0xFF5A3F1E, 0xFF866A49, 0xFF2F2A24)
		fun defaultDark() = ReaderStyleColors(0xFFF2EDE4, 0xFF121212, 0xFF9AC0FF, 0xFF2E2A23, 0xFFF1D8B4, 0xFFB29D80, 0xFFEDE0CE)
	}
}

@Serializable
data class ReaderTextColors(
	val chapterTitle: Long = 0xFF5A3F1E,
	val bodyText: Long = 0xFF1D1D1D,
	val translatorLabel: Long = 0xFF866A49,
	val translatorValue: Long = 0xFF2F2A24,
	val chapterNumber: Long = 0xFF5A3F1E,
	val metadata: Long = 0xFF2F2A24,
) {
	companion object {
		fun default() = ReaderTextColors()
	}
}

@Serializable
data class ReaderStylePreset(
	val id: String,
	val name: String,
	val builtin: Boolean,
	val style: ReaderStyle,
)

@Serializable
data class ReaderStyleExport(
	val version: Int = 1,
	val activePresetId: String,
	val presets: List<ReaderStylePreset>,
)

fun builtInReaderStylePresets(): List<ReaderStylePreset> = listOf(
	ReaderStylePreset("builtin:classic", "Classic", true, ReaderStyle().clamped()),
	ReaderStylePreset("builtin:paper", "Paper", true, ReaderStyle(fontFamily = "serif", contentPaddingDp = 24f).clamped()),
	ReaderStylePreset("builtin:compact", "Compact", true, ReaderStyle(fontSizeSp = 16f, lineHeightEm = 1.45f, paragraphSpacingEm = 0.6f).clamped()),
)

private fun Float.round1() = ((this * 10f).roundToInt() / 10f)
private fun Float.round2() = ((this * 100f).roundToInt() / 100f)
