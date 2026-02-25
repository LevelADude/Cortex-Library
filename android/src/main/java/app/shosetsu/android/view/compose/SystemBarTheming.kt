package app.shosetsu.android.view.compose

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun MonogatariSystemBars(
	color: Color,
	forceLightSystemBarIcons: Boolean? = null,
) {
	val view = LocalView.current
	if (view.isInEditMode) return

	SideEffect {
		val activity = view.context as? Activity ?: return@SideEffect
		activity.applySystemBars(color, forceLightSystemBarIcons)
	}
}

fun Activity.applySystemBars(
	color: Color,
	forceLightSystemBarIcons: Boolean? = null,
) {
	window.statusBarColor = color.toArgb()
	window.navigationBarColor = color.toArgb()

	val useLightSystemBarIcons = forceLightSystemBarIcons ?: (color.luminance() <= 0.5f)
	WindowCompat.getInsetsController(window, window.decorView)?.apply {
		isAppearanceLightStatusBars = !useLightSystemBarIcons
		isAppearanceLightNavigationBars = !useLightSystemBarIcons
		systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
	}
}
