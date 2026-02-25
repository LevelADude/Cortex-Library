package app.shosetsu.android.view.compose

import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.shosetsu.android.R
import app.shosetsu.android.common.SettingKey
import app.shosetsu.android.ui.settings.CustomThemeParser

private val MonogatariLightColorScheme = lightColorScheme(
	primary = Color(0xFF0B5D66),
	onPrimary = Color(0xFFFFFFFF),
	secondary = Color(0xFF4F6471),
	onSecondary = Color(0xFFFFFFFF),
	tertiary = Color(0xFFB56A35),
	onTertiary = Color(0xFFFFFFFF),
	background = Color(0xFFF7F1E8),
	onBackground = Color(0xFF1D1B18),
	surface = Color(0xFFFFF8F0),
	onSurface = Color(0xFF1D1B18),
	surfaceVariant = Color(0xFFE7DED3),
	onSurfaceVariant = Color(0xFF4C463F),
	error = Color(0xFFB3261E),
	onError = Color(0xFFFFFFFF),
	outline = Color(0xFF80766B),
)

private val LegacyLightColorScheme = lightColorScheme(
	primary = Color(0xFFA80000),
	onPrimary = Color(0xFFFFFFFF),
	secondary = Color(0xFF8E3B3B),
	onSecondary = Color(0xFFFFFFFF),
	background = Color(0xFFFFFFFF),
	onBackground = Color(0xFF1A1A1A),
	surface = Color(0xFFFFFFFF),
	onSurface = Color(0xFF1A1A1A),
	surfaceVariant = Color(0xFFEAEAEA),
	onSurfaceVariant = Color(0xFF404040),
	error = Color(0xFFB3261E),
	onError = Color(0xFFFFFFFF),
	outline = Color(0xFF777777),
)

internal val DefaultDarkColorScheme = darkColorScheme(
	primary = Color(0xFFD0BCFF),
	onPrimary = Color(0xFF2A1F3F),
	primaryContainer = Color(0xFFE7D7FF),
	onPrimaryContainer = Color(0xFF24193A),
	secondary = Color(0xFFCCC2DC),
	onSecondary = Color(0xFF2E2836),
	secondaryContainer = Color(0xFF4A4458),
	onSecondaryContainer = Color(0xFFE8DEF8),
	tertiary = Color(0xFFEFB8C8),
	onTertiary = Color(0xFF3E2532),
	background = Color(0xFF141218),
	onBackground = Color(0xFFE6E0E9),
	surface = Color(0xFF1D1B20),
	onSurface = Color(0xFFE6E0E9),
	surfaceVariant = Color(0xFF49454F),
	onSurfaceVariant = Color(0xFFCAC4D0),
	inverseSurface = Color(0xFFE6E0E9),
	inverseOnSurface = Color(0xFF322F35),
	error = Color(0xFFF2B8B5),
	onError = Color(0xFF601410),
	outline = Color(0xFF938F99),
	outlineVariant = Color(0xFF49454F),
)

private val EmeraldManuscriptColorScheme = lightColorScheme(
	primary = Color(0xFF114B47),
	onPrimary = Color(0xFFF3EBDD),
	primaryContainer = Color(0xFF1B5F5A),
	onPrimaryContainer = Color(0xFFF3EBDD),
	secondary = Color(0xFF2D6A4F),
	onSecondary = Color(0xFFF3EBDD),
	tertiary = Color(0xFF1F7A5A),
	onTertiary = Color(0xFFF8F1E4),
	background = Color(0xFFF8F1E4),
	onBackground = Color(0xFF1A1A1A),
	surface = Color(0xFFEDE4D6),
	onSurface = Color(0xFF1A1A1A),
	surfaceVariant = Color(0xFFE3D7C6),
	onSurfaceVariant = Color(0xFF2A2A2A),
	outline = Color(0xFF8C9A7A),
	error = Color(0xFF8B2E2E),
)

private val MidnightInkGoldColorScheme = darkColorScheme(
	primary = Color(0xFFC2A14A),
	onPrimary = Color(0xFF1A1A1A),
	primaryContainer = Color(0xFF2A3550),
	onPrimaryContainer = Color(0xFFF5EFE6),
	secondary = Color(0xFF9B8240),
	onSecondary = Color(0xFFF5EFE6),
	tertiary = Color(0xFFE0C777),
	onTertiary = Color(0xFF1A1A1A),
	background = Color(0xFF151E33),
	onBackground = Color(0xFFF5EFE6),
	surface = Color(0xFF1A243D),
	onSurface = Color(0xFFF5EFE6),
	surfaceVariant = Color(0xFF232F4D),
	onSurfaceVariant = Color(0xFFE7E0D6),
	outline = Color(0xFF7D8A96),
	error = Color(0xFFB24C4C),
	inversePrimary = Color(0xFF2A3550),
)



@Composable
fun MonogatariTheme(
	theme: app.shosetsu.android.common.enums.AppThemes,
	context: Context = androidx.compose.ui.platform.LocalContext.current,
	content: @Composable () -> Unit,
) {
	val customThemeSettings = rememberCustomThemeSettings(context)
	val isDark = when (theme) {
		app.shosetsu.android.common.enums.AppThemes.DARK -> true
		app.shosetsu.android.common.enums.AppThemes.MIDNIGHT_INK,
		app.shosetsu.android.common.enums.AppThemes.MIDNIGHT_INK_GOLD -> false
		app.shosetsu.android.common.enums.AppThemes.LIGHT,
		app.shosetsu.android.common.enums.AppThemes.EMERALD_MANUSCRIPT -> false
		app.shosetsu.android.common.enums.AppThemes.FOLLOW_SYSTEM -> context.isDarkModeEnabled()
	}
	MaterialTheme(
		colorScheme = resolveColorScheme(
			themeVariant = theme.key,
			isDark = isDark,
			context = context,
			customTheme = customThemeSettings,
		),
		content = content,
	)
}
@Composable
fun ProvideShosetsuTheme(
	context: Context,
	content: @Composable () -> Unit,
) {
	val customThemeSettings = rememberCustomThemeSettings(context)
	val resolvedColorScheme = remember(customThemeSettings) {
		resolveColorScheme(
			themeVariant = context.resolveThemeVariant(),
			isDark = context.isDarkModeEnabled(),
			context = context,
			customTheme = customThemeSettings,
		)
	}
	MaterialTheme(
		colorScheme = resolvedColorScheme,
		content = content,
	)
}


private data class CustomThemePreferenceState(
	val enabled: Boolean,
	val background: String,
	val surface: String,
	val primary: String,
	val onPrimary: String,
	val onBackground: String,
	val iconTint: String,
	val navSelected: String,
	val topBarContainer: String,
	val bottomBarContainer: String,
	val bottomBarSelected: String,
)

@Composable
private fun rememberCustomThemeSettings(context: Context): CustomThemePreferenceState {
	val preferences = remember {
		context.getSharedPreferences("settings", Context.MODE_PRIVATE)
	}
	var updateTrigger by remember { mutableStateOf(0) }

	DisposableEffect(preferences) {
		val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
			if (changedKey?.startsWith("custom_theme_") == true) {
				updateTrigger++
			}
		}
		preferences.registerOnSharedPreferenceChangeListener(listener)
		onDispose {
			preferences.unregisterOnSharedPreferenceChangeListener(listener)
		}
	}

	return remember(updateTrigger) {
		CustomThemePreferenceState(
			enabled = preferences.getBoolean(SettingKey.CustomThemeEnabled.name, SettingKey.CustomThemeEnabled.default),
			background = preferences.getString(SettingKey.CustomThemeBackground.name, SettingKey.CustomThemeBackground.default).orEmpty(),
			surface = preferences.getString(SettingKey.CustomThemeSurface.name, SettingKey.CustomThemeSurface.default).orEmpty(),
			primary = preferences.getString(SettingKey.CustomThemePrimary.name, SettingKey.CustomThemePrimary.default).orEmpty(),
			onPrimary = preferences.getString(SettingKey.CustomThemeOnPrimary.name, SettingKey.CustomThemeOnPrimary.default).orEmpty(),
			onBackground = preferences.getString(SettingKey.CustomThemeOnBackground.name, SettingKey.CustomThemeOnBackground.default).orEmpty(),
			iconTint = preferences.getString(SettingKey.CustomThemeIconTint.name, SettingKey.CustomThemeIconTint.default).orEmpty(),
			navSelected = preferences.getString(SettingKey.CustomThemeNavSelected.name, SettingKey.CustomThemeNavSelected.default).orEmpty(),
			topBarContainer = preferences.getString(SettingKey.CustomThemeTopBarContainer.name, SettingKey.CustomThemeTopBarContainer.default).orEmpty(),
			bottomBarContainer = preferences.getString(SettingKey.CustomThemeBottomBarContainer.name, SettingKey.CustomThemeBottomBarContainer.default).orEmpty(),
			bottomBarSelected = preferences.getString(SettingKey.CustomThemeBottomBarSelected.name, SettingKey.CustomThemeBottomBarSelected.default).orEmpty(),
		)
	}
}

private fun resolveColorScheme(themeVariant: Int, isDark: Boolean, context: Context, customTheme: CustomThemePreferenceState? = null): ColorScheme {
	val baseScheme = when (themeVariant) {
		0 -> {
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
				if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
			} else {
				if (isDark) DefaultDarkColorScheme else MonogatariLightColorScheme
			}
		}
		4 -> EmeraldManuscriptColorScheme
		5 -> MidnightInkGoldColorScheme
		1 -> LegacyLightColorScheme
		2 -> DefaultDarkColorScheme
		3 -> if (isDark) DefaultDarkColorScheme else LegacyLightColorScheme
		else -> MonogatariLightColorScheme
	}

	if (customTheme?.enabled != true) return baseScheme

	val parsedCustomTheme = CustomThemeParser.parseAll(
		background = customTheme.background,
		surface = customTheme.surface,
		primary = customTheme.primary,
		onPrimary = customTheme.onPrimary,
		onBackground = customTheme.onBackground,
		iconTint = customTheme.iconTint,
		navSelected = customTheme.navSelected,
		topBarContainer = customTheme.topBarContainer,
		bottomBarContainer = customTheme.bottomBarContainer,
		bottomBarSelected = customTheme.bottomBarSelected,
	) ?: return baseScheme

	return when (themeVariant) {
		else -> baseScheme.copy(
			background = parsedCustomTheme.background,
			onBackground = parsedCustomTheme.onBackground,
			surface = parsedCustomTheme.surface,
			onSurface = parsedCustomTheme.onBackground,
			primary = parsedCustomTheme.primary,
			onPrimary = parsedCustomTheme.onPrimary,
			primaryContainer = parsedCustomTheme.surface,
			onPrimaryContainer = parsedCustomTheme.onBackground,
			secondary = parsedCustomTheme.iconTint ?: parsedCustomTheme.primary,
			onSecondary = parsedCustomTheme.onPrimary,
			secondaryContainer = parsedCustomTheme.surface,
			onSecondaryContainer = parsedCustomTheme.onBackground,
			tertiary = parsedCustomTheme.iconTint ?: parsedCustomTheme.primary,
			onTertiary = parsedCustomTheme.onPrimary,
			surfaceVariant = parsedCustomTheme.surface,
			onSurfaceVariant = parsedCustomTheme.onBackground,
		)
	}
}

private fun Context.resolveThemeVariant(): Int =
	resolveIntAttr(R.attr.appThemeVariant)

private fun Context.isDarkModeEnabled(): Boolean {
	val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
	return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
}

internal fun Context.resolveIntAttr(@AttrRes attr: Int): Int = TypedValue().let { typedValue ->
	if (theme.resolveAttribute(attr, typedValue, true)) typedValue.data else 0
}


internal fun isTexturedThemeVariant(themeVariant: Int): Boolean =
	themeVariant == 4 || themeVariant == 5


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun themedTopAppBarColors(context: Context): TopAppBarColors {
	val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
	if (sharedPreferences.getBoolean(SettingKey.CustomThemeEnabled.name, false)) {
		val customTopBarColor = sharedPreferences
			.getString(SettingKey.CustomThemeTopBarContainer.name, SettingKey.CustomThemeTopBarContainer.default)
			.orEmpty()
			.let(CustomThemeParser::parseColor)
			?: MaterialTheme.colorScheme.surface
		return TopAppBarDefaults.topAppBarColors(
			containerColor = customTopBarColor,
			titleContentColor = MaterialTheme.colorScheme.onSurface,
			navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
			actionIconContentColor = MaterialTheme.colorScheme.onSurface,
		)
	}

	return when (context.resolveIntAttr(R.attr.appThemeVariant)) {
		4 -> TopAppBarDefaults.topAppBarColors(
			containerColor = Color(0xFF114B47),
			titleContentColor = Color(0xFFE6F4E8),
			navigationIconContentColor = Color(0xFFE6F4E8),
			actionIconContentColor = Color(0xFFE6F4E8),
		)

		5 -> TopAppBarDefaults.topAppBarColors(
			containerColor = Color(0xFF0F172A),
			titleContentColor = Color(0xFFF5EFE6),
			navigationIconContentColor = Color(0xFFF5EFE6),
			actionIconContentColor = Color(0xFFF5EFE6),
		)

		else -> TopAppBarDefaults.topAppBarColors(
			containerColor = MaterialTheme.colorScheme.surface,
			titleContentColor = MaterialTheme.colorScheme.onSurface,
			navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
			actionIconContentColor = MaterialTheme.colorScheme.onSurface,
		)
	}
}
