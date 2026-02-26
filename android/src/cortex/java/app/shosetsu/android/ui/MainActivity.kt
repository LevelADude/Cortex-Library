package app.shosetsu.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.shosetsu.android.data.connector.ApiSourceConnector
import app.shosetsu.android.data.connector.ConnectorRegistry
import app.shosetsu.android.data.connector.ScrapeSourceConnector
import app.shosetsu.android.data.network.ConnectivityMonitor
import app.shosetsu.android.data.repo.DebugEventsRepository
import app.shosetsu.android.data.repo.DownloadsRepository
import app.shosetsu.android.data.repo.SearchRepository
import app.shosetsu.android.data.repo.SourcesRepository
import app.shosetsu.android.data.store.CortexDataStore
import app.shosetsu.android.ui.nav.Destinations
import app.shosetsu.android.ui.preview.PdfPreviewRenderer
import app.shosetsu.android.ui.screens.DownloadsScreen
import app.shosetsu.android.ui.screens.PdfPreviewScreen
import app.shosetsu.android.ui.screens.ResultDetailsScreen
import app.shosetsu.android.ui.screens.SearchScreen
import app.shosetsu.android.ui.screens.SettingsScreen
import app.shosetsu.android.ui.screens.SourcesScreen
import app.shosetsu.android.ui.vm.CortexViewModelFactory
import app.shosetsu.android.ui.vm.DownloadsViewModel
import app.shosetsu.android.ui.vm.SearchViewModel
import app.shosetsu.android.ui.vm.SourcesViewModel
import app.shosetsu.android.ui.vm.SettingsViewModel
import app.shosetsu.android.util.openPdf
import java.net.URLDecoder
import java.net.URLEncoder

// TODO(cortex): Migrate legacy `app.shosetsu.android` package namespace to a Cortex-specific namespace.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataStore = CortexDataStore(applicationContext)
        val sourcesRepository = SourcesRepository(dataStore)
        val downloadsRepository = DownloadsRepository(applicationContext, dataStore)
        val connectorRegistry = ConnectorRegistry(ApiSourceConnector(), ScrapeSourceConnector(applicationContext))
        val debugEventsRepository = DebugEventsRepository(dataStore)
        val connectivityMonitor = ConnectivityMonitor(applicationContext)
        val searchRepository = SearchRepository(connectorRegistry, dataStore, debugEventsRepository)

        setContent {
            MaterialTheme {
                val sourcesViewModel: SourcesViewModel = viewModel(factory = CortexViewModelFactory { SourcesViewModel(sourcesRepository, connectorRegistry, debugEventsRepository) })
                val downloadsViewModel: DownloadsViewModel = viewModel(factory = CortexViewModelFactory {
                    DownloadsViewModel(downloadsRepository, { sourcesViewModel.sources.value }, debugEventsRepository, dataStore)
                })
                val searchViewModel: SearchViewModel = viewModel(factory = CortexViewModelFactory {
                    SearchViewModel(searchRepository, { sourcesViewModel.sources.value }, connectivityMonitor.isOnline)
                })
                val settingsViewModel: SettingsViewModel = viewModel(factory = CortexViewModelFactory { SettingsViewModel(dataStore, debugEventsRepository, sourcesRepository) })
                CortexApp(searchViewModel, sourcesViewModel, downloadsViewModel, settingsViewModel)
            }
        }
    }
}

@Composable
fun CortexApp(
    searchViewModel: SearchViewModel,
    sourcesViewModel: SourcesViewModel,
    downloadsViewModel: DownloadsViewModel,
    settingsViewModel: SettingsViewModel
) {
    val detailsRoute = "result_details"
    val previewRoute = "preview/{filePath}"
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val previewRenderer = remember { PdfPreviewRenderer(maxPages = 5) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                Destinations.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = Destinations.Search.route, modifier = Modifier.padding(padding)) {
            composable(Destinations.Search.route) {
                SearchScreen(searchViewModel, sourcesViewModel, downloadsViewModel, snackbarHostState) {
                    navController.navigate(detailsRoute)
                }
            }
            composable(detailsRoute) {
                ResultDetailsScreen(searchViewModel, sourcesViewModel, downloadsViewModel, snackbarHostState) { filePath ->
                    val encoded = URLEncoder.encode(filePath, Charsets.UTF_8.name())
                    navController.navigate("preview/$encoded")
                }
            }
            composable(previewRoute) { entry ->
                val encodedPath = entry.arguments?.getString("filePath").orEmpty()
                val filePath = URLDecoder.decode(encodedPath, Charsets.UTF_8.name())
                PdfPreviewScreen(filePath = filePath, renderer = previewRenderer, onOpenExternal = {
                    openPdf(navController.context, filePath)
                })
            }
            composable(Destinations.Sources.route) { SourcesScreen(sourcesViewModel) }
            composable(Destinations.Downloads.route) {
                DownloadsScreen(downloadsViewModel) { filePath ->
                    val encoded = URLEncoder.encode(filePath, Charsets.UTF_8.name())
                    navController.navigate("preview/$encoded")
                }
            }
            composable(Destinations.Settings.route) { SettingsScreen(downloadsViewModel, settingsViewModel) }
        }
    }
}
