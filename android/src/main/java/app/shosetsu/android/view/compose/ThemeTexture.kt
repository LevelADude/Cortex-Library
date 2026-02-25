package app.shosetsu.android.view.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

fun Modifier.subtleThemeTexture(enabled: Boolean, tint: Color): Modifier {
	if (!enabled) return this
	return this.drawWithContent {
		drawContent()
		val diagonalAlpha = 0.035f
		val grainAlpha = 0.02f
		val step = size.minDimension / 8f
		var start = -size.height
		while (start < size.width) {
			drawLine(
				color = tint.copy(alpha = diagonalAlpha),
				start = Offset(start, 0f),
				end = Offset(start + size.height, size.height),
				strokeWidth = 1f,
			)
			start += step
		}

		val dotStep = size.minDimension / 12f
		var x = dotStep / 2f
		while (x < size.width) {
			var y = dotStep / 2f
			while (y < size.height) {
				drawCircle(
					color = tint.copy(alpha = grainAlpha),
					radius = 0.7f,
					center = Offset(x, y),
				)
				y += dotStep
			}
			x += dotStep
		}
	}
}
