package app.shosetsu.android.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destinations(val route: String, val label: String, val icon: ImageVector) {
    Search("search", "Search", Icons.Default.Search),
    Sources("sources", "Sources", Icons.Default.Public),
    Downloads("downloads", "Downloads", Icons.Default.Download),
    Settings("settings", "Settings", Icons.Default.Settings)
}
