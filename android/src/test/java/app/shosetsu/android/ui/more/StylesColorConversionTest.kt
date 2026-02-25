package app.shosetsu.android.ui.more

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class StylesColorConversionTest {
	@Test
	fun `color conversion keeps argb channels`() {
		val selected = Color(0xFF00FF00)
		val stored = selected.toArgb().toLong() and 0xFFFFFFFFL
		assertEquals(0xFF00FF00, stored)
	}
}
