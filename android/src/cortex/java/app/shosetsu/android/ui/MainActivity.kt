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
import app.shosetsu.android.data.repo.DownloadsRepository
import app.shosetsu.android.data.repo.SearchRepository
import app.shosetsu.android.data.repo.SourcesRepository
import app.shosetsu.android.data.store.CortexDataStore
import app.shosetsu.android.ui.nav.Destinations
import app.shosetsu.android.ui.screens.DownloadsScreen
import app.shosetsu.android.ui.screens.SearchScreen
import app.shosetsu.android.ui.screens.SettingsScreen
import app.shosetsu.android.ui.screens.SourcesScreen
import app.shosetsu.android.ui.vm.CortexViewModelFactory
import app.shosetsu.android.ui.vm.DownloadsViewModel
import app.shosetsu.android.ui.vm.SearchViewModel
import app.shosetsu.android.ui.vm.SourcesViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataStore = CortexDataStore(applicationContext)
        val sourcesRepository = SourcesRepository(dataStore)
        val downloadsRepository = DownloadsRepository(applicationContext, dataStore)
        val connectorRegistry = ConnectorRegistry(ApiSourceConnector(), ScrapeSourceConnector(applicationContext))
        val searchRepository = SearchRepository(connectorRegistry)

        setContent {
            MaterialTheme {
                val sourcesViewModel: SourcesViewModel = viewModel(factory = CortexViewModelFactory { SourcesViewModel(sourcesRepository) })
                val downloadsViewModel: DownloadsViewModel = viewModel(factory = CortexViewModelFactory { DownloadsViewModel(downloadsRepository) })
                val searchViewModel: SearchViewModel = viewModel(factory = CortexViewModelFactory {
                    SearchViewModel(searchRepository) { sourcesViewModel.sources.value }
                })
                CortexApp(searchViewModel, sourcesViewModel, downloadsViewModel)
            }
        }
    }
}

@Composable
fun CortexApp(
    searchViewModel: SearchViewModel,
    sourcesViewModel: SourcesViewModel,
    downloadsViewModel: DownloadsViewModel
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

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
                SearchScreen(searchViewModel, sourcesViewModel, downloadsViewModel, snackbarHostState)
            }
            composable(Destinations.Sources.route) { SourcesScreen(sourcesViewModel) }
            composable(Destinations.Downloads.route) { DownloadsScreen(downloadsViewModel) }
            composable(Destinations.Settings.route) { SettingsScreen() }
        }
    }
}
