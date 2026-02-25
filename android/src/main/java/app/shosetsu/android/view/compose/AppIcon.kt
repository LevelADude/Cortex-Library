package app.shosetsu.android.view.compose

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun AppIcon(
	imageVector: ImageVector,
	contentDescription: String?,
	modifier: Modifier = Modifier,
) {
	Icon(
		imageVector = imageVector,
		contentDescription = contentDescription,
		modifier = modifier,
	)
}

@Composable
fun AppIcon(
	painter: Painter,
	contentDescription: String?,
	modifier: Modifier = Modifier,
) {
	Icon(
		painter = painter,
		contentDescription = contentDescription,
		modifier = modifier.size(24.dp),
	)
}
