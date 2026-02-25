package app.shosetsu.android.ui.settings

import androidx.compose.ui.graphics.Color
import java.util.Locale

internal data class ParsedCustomTheme(
	val background: Color,
	val surface: Color,
	val primary: Color,
	val onPrimary: Color,
	val onBackground: Color,
	val iconTint: Color?,
	val navSelected: Color?,
	val topBarContainer: Color?,
	val bottomBarContainer: Color?,
	val bottomBarSelected: Color?,
)

internal object CustomThemeParser {
	private val rgbDelimiters = Regex("[,;\\s]+")

	fun normalize(input: String): String? = parseColor(input)?.toHexString()

	fun parseAll(
		background: String,
		surface: String,
		primary: String,
		onPrimary: String,
		onBackground: String,
		iconTint: String,
		navSelected: String,
		topBarContainer: String,
		bottomBarContainer: String,
		bottomBarSelected: String,
	): ParsedCustomTheme? {
		val parsedBackground = parseColor(background) ?: return null
		val parsedSurface = parseColor(surface) ?: return null
		val parsedPrimary = parseColor(primary) ?: return null
		val parsedOnPrimary = parseColor(onPrimary) ?: return null
		val parsedOnBackground = parseColor(onBackground) ?: return null
		val parsedIconTint = parseColor(iconTint)
		val parsedNavSelected = parseColor(navSelected)
		val parsedTopBarContainer = parseColor(topBarContainer)
		val parsedBottomBarContainer = parseColor(bottomBarContainer)
		val parsedBottomBarSelected = parseColor(bottomBarSelected)

		return ParsedCustomTheme(
			background = parsedBackground,
			surface = parsedSurface,
			primary = parsedPrimary,
			onPrimary = parsedOnPrimary,
			onBackground = parsedOnBackground,
			iconTint = parsedIconTint,
			navSelected = parsedNavSelected,
			topBarContainer = parsedTopBarContainer,
			bottomBarContainer = parsedBottomBarContainer,
			bottomBarSelected = parsedBottomBarSelected,
		)
	}

	fun parseColor(rawInput: String): Color? {
		val input = rawInput.trim()
		if (input.isEmpty()) return null

		if (input.startsWith("#")) {
			val hex = input.removePrefix("#")
			val normalizedHex = when (hex.length) {
				6 -> "FF$hex"
				8 -> hex
				else -> return null
			}
			val colorLong = normalizedHex.toLongOrNull(16) ?: return null
			return Color(colorLong)
		}

		val parts = input.split(rgbDelimiters).filter { it.isNotBlank() }
		if (parts.size != 3) return null
		val red = parts[0].toIntOrNull()?.coerceIn(0, 255) ?: return null
		val green = parts[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
		val blue = parts[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
		return Color(red, green, blue)
	}
}

internal fun Color.toHexString(): String {
	val alpha = (alpha * 255f).toInt().coerceIn(0, 255)
	val red = (red * 255f).toInt().coerceIn(0, 255)
	val green = (green * 255f).toInt().coerceIn(0, 255)
	val blue = (blue * 255f).toInt().coerceIn(0, 255)
	return String.format(Locale.US, "#%02X%02X%02X%02X", alpha, red, green, blue)
}
