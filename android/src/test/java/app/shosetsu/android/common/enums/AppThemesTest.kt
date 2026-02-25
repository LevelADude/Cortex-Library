package app.shosetsu.android.common.enums

import app.shosetsu.android.view.compose.resolveColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemesTest {

	@Test
	fun `selection order includes all theme options`() {
		assertEquals(
			listOf(
				AppThemes.FOLLOW_SYSTEM,
				AppThemes.LIGHT,
				AppThemes.DARK,
				AppThemes.EMERALD_MANUSCRIPT,
				AppThemes.MIDNIGHT_INK_GOLD,
			),
			AppThemes.selectionOrder
		)
	}

	@Test
	fun `follow system resolves to legacy light when system is light`() {
		val scheme = resolveColorScheme(themeVariant = AppThemes.FOLLOW_SYSTEM.key, isDark = false)
		assertEquals(0xFFA80000.toInt(), scheme.primary.value.toLong().toInt())
	}

	@Test
	fun `follow system resolves to rebuilt default dark when system is dark`() {
		val scheme = resolveColorScheme(themeVariant = AppThemes.FOLLOW_SYSTEM.key, isDark = true)
		assertEquals(0xFFD1B2FF.toInt(), scheme.primary.value.toLong().toInt())
		assertEquals(0xFFE1CCFF.toInt(), scheme.primaryContainer.value.toLong().toInt())
		assertEquals(0xFFC7B4EE.toInt(), scheme.secondary.value.toLong().toInt())
	}


	@Test
	fun `dark theme always resolves to dark scheme`() {
		val scheme = resolveColorScheme(themeVariant = AppThemes.DARK.key, isDark = false)
		assertEquals(0xFFD1B2FF.toInt(), scheme.primary.value.toLong().toInt())
	}

	@Test
	fun `emerald theme no longer uses cyan tertiary`() {
		val scheme = resolveColorScheme(themeVariant = AppThemes.EMERALD_MANUSCRIPT.key, isDark = false)
		assertEquals(0xFF1F7A5A.toInt(), scheme.tertiary.value.toLong().toInt())
	}

	@Test
	fun `midnight theme uses gold primary`() {
		val scheme = resolveColorScheme(themeVariant = AppThemes.MIDNIGHT_INK_GOLD.key, isDark = false)
		assertEquals(0xFFC2A14A.toInt(), scheme.primary.value.toLong().toInt())
	}

	@Test
	fun `new theme keys map correctly`() {
		assertEquals(AppThemes.EMERALD_MANUSCRIPT, AppThemes.fromKey(4))
		assertEquals(AppThemes.MIDNIGHT_INK_GOLD, AppThemes.fromKey(5))
		assertEquals(AppThemes.EMERALD_MANUSCRIPT, AppThemes.fromKey(0))
		assertTrue(AppThemes.selectionOrder.contains(AppThemes.EMERALD_MANUSCRIPT))
		assertTrue(AppThemes.selectionOrder.contains(AppThemes.MIDNIGHT_INK_GOLD))
	}
}
