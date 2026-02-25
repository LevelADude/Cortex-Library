package app.shosetsu.android.view.compose

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
	context: Context,
	title: @Composable () -> Unit,
	modifier: Modifier = Modifier,
	navigationIcon: @Composable () -> Unit = {},
	actions: @Composable RowScope.() -> Unit = {},
	scrollBehavior: TopAppBarScrollBehavior? = null,
	colors: TopAppBarColors = themedTopAppBarColors(context),
) {
	TopAppBar(
		modifier = modifier,
		title = title,
		navigationIcon = navigationIcon,
		actions = actions,
		scrollBehavior = scrollBehavior,
		colors = colors,
	)
}
