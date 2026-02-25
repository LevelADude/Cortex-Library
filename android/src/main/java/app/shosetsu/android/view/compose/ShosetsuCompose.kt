package app.shosetsu.android.view.compose

import android.content.Context
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import app.shosetsu.android.R
import app.shosetsu.android.common.enums.AppThemes

/*
 * This file is part of shosetsu.
 *
 * shosetsu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * shosetsu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with shosetsu.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * To provide unified connection
 *
 * @since 28 / 06 / 2022
 * @author Doomsdayrs
 */
@Composable
fun ShosetsuCompose(
	context: Context = LocalContext.current,
	content: @Composable () -> Unit
) {
	ProvideShosetsuTheme(context = context) {
		val themeVariant = context.resolveIntAttr(R.attr.appThemeVariant)
		val isDefaultDarkTheme = when (themeVariant) {
			AppThemes.DARK.key -> true
			AppThemes.FOLLOW_SYSTEM.key -> context.isDarkModeEnabled()
			else -> false
		}
		MonogatariSystemBars(
			color = if (isDefaultDarkTheme) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primaryContainer,
			forceLightSystemBarIcons = if (isDefaultDarkTheme) true else null,
		)
		Surface(
			modifier = Modifier
				.nestedScroll(rememberNestedScrollInteropConnection())
				.subtleThemeTexture(
					enabled = isTexturedThemeVariant(themeVariant),
					tint = MaterialTheme.colorScheme.onBackground,
				),
			color = MaterialTheme.colorScheme.background,
			content = content
		)
	}
}


private fun Context.isDarkModeEnabled(): Boolean {
	val nightModeFlags = resources.configuration.uiMode and UI_MODE_NIGHT_MASK
	return nightModeFlags == UI_MODE_NIGHT_YES
}
