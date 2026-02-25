package app.shosetsu.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "cortex_library")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = CortexRepository(applicationContext)
        val vmFactory = CortexViewModelFactory(repository)

        setContent {
            MaterialTheme {
                val viewModel: CortexViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = vmFactory)
                CortexApp(viewModel)
            }
        }
    }
}

@Serializable
data class Source(
    val id: String,
    val name: String,
    val baseUrl: String,
    val type: SourceType,
    val enabled: Boolean = true,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class SourceType { GenericWeb, Api }

@Serializable
data class SearchResult(
    val id: String,
    val sourceId: String,
    val title: String,
    val authors: String,
    val year: String,
    val snippet: String,
    val pdfUrl: String? = null,
    val landingUrl: String? = null,
    val fileSize: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class DownloadItem(
    val id: String,
    val title: String,
    val pdfUrl: String,
    val filePath: String,
    val status: String,
    val progress: Int,
    val addedAt: Long = System.currentTimeMillis(),
    val sourceName: String
)

class CortexRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val sourcesKey = stringPreferencesKey("sources")
    private val downloadsKey = stringPreferencesKey("downloads")

    private val presetSources = listOf(
        Source("preset_open_alex", "OpenAlex Demo", "https://openalex.org", SourceType.Api),
        Source("preset_arxiv", "arXiv Demo", "https://arxiv.org", SourceType.Api),
        Source("preset_doaj", "DOAJ Demo", "https://doaj.org", SourceType.GenericWeb),
    )

    val sourcesFlow: Flow<List<Source>> = context.dataStore.data.map { pref ->
        pref[sourcesKey]?.let {
            runCatching { json.decodeFromString<List<Source>>(it) }.getOrNull()
        } ?: presetSources
    }

    val downloadsFlow: Flow<List<DownloadItem>> = context.dataStore.data.map { pref ->
        pref[downloadsKey]?.let {
            runCatching { json.decodeFromString<List<DownloadItem>>(it) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun addSource(name: String, baseUrl: String, type: SourceType) {
        val current = sourcesFlow.stateOnce()
        val updated = current + Source(UUID.randomUUID().toString(), name, baseUrl, type)
        saveSources(updated)
    }

    suspend fun toggleSource(id: String, enabled: Boolean) {
        saveSources(sourcesFlow.stateOnce().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    suspend fun search(query: String, enabledSources: List<Source>): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        // TODO: Replace this mock implementation with real API/scraper integration per SourceType.
        return enabledSources.flatMap { source ->
            listOf(
                SearchResult(
                    id = UUID.randomUUID().toString(),
                    sourceId = source.id,
                    title = "$query - Introductory Paper",
                    authors = "A. Researcher, B. Scholar",
                    year = "2024",
                    snippet = "Mock result from ${source.name}.",
                    pdfUrl = TEST_PDF_URL,
                    landingUrl = source.baseUrl
                ),
                SearchResult(
                    id = UUID.randomUUID().toString(),
                    sourceId = source.id,
                    title = "$query - Extended Survey",
                    authors = "C. Author",
                    year = "2023",
                    snippet = "Second mocked hit from ${source.name}.",
                    pdfUrl = TEST_PDF_URL,
                    landingUrl = source.baseUrl
                )
            )
        }
    }

    suspend fun downloadPdf(result: SearchResult, sourceName: String): Result<DownloadItem> = runCatching {
        val safeTitle = result.title.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val fileName = if (safeTitle.endsWith(".pdf")) safeTitle else "$safeTitle.pdf"
        val dir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
        val file = File(dir, fileName)

        withContext(Dispatchers.IO) {
            URL(result.pdfUrl ?: TEST_PDF_URL).openStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }

        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            title = result.title,
            pdfUrl = result.pdfUrl ?: TEST_PDF_URL,
            filePath = file.absolutePath,
            status = "Completed",
            progress = 100,
            sourceName = sourceName
        )
        val updated = listOf(item) + downloadsFlow.stateOnce()
        saveDownloads(updated)
        item
    }

    private suspend fun saveSources(items: List<Source>) {
        context.dataStore.edit { it[sourcesKey] = json.encodeToString(items) }
    }

    private suspend fun saveDownloads(items: List<DownloadItem>) {
        context.dataStore.edit { it[downloadsKey] = json.encodeToString(items) }
    }

    companion object {
        const val TEST_PDF_URL = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
    }
}

private suspend fun <T> Flow<T>.stateOnce(): T = first()

class CortexViewModel(private val repository: CortexRepository) : ViewModel() {
    val sources = repository.sourcesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val downloads = repository.downloadsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _results = kotlinx.coroutines.flow.MutableStateFlow<List<SearchResult>>(emptyList())
    val results: Flow<List<SearchResult>> = _results

    fun search(query: String) = viewModelScope.launch {
        _results.value = repository.search(query, sources.value.filter { it.enabled })
    }

    fun addSource(name: String, baseUrl: String, type: SourceType) = viewModelScope.launch {
        repository.addSource(name, baseUrl, type)
    }

    fun toggleSource(id: String, enabled: Boolean) = viewModelScope.launch {
        repository.toggleSource(id, enabled)
    }

    fun download(result: SearchResult, sourceName: String, onDone: (Result<DownloadItem>) -> Unit) = viewModelScope.launch {
        onDone(repository.downloadPdf(result, sourceName))
    }
}

class CortexViewModelFactory(private val repository: CortexRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CortexViewModel(repository) as T
}

enum class Destinations(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Search("search", "Search", Icons.Default.Search),
    Sources("sources", "Sources", Icons.Default.Public),
    Downloads("downloads", "Downloads", Icons.Default.Download),
    Settings("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CortexApp(viewModel: CortexViewModel) {
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
            composable(Destinations.Search.route) { SearchScreen(viewModel, snackbarHostState) }
            composable(Destinations.Sources.route) { SourcesScreen(viewModel) }
            composable(Destinations.Downloads.route) { DownloadsScreen(viewModel) }
            composable(Destinations.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun SearchScreen(viewModel: CortexViewModel, snackbar: SnackbarHostState) {
    val sources by viewModel.sources.collectAsState()
    val results by viewModel.results.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Cortex Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search papers and books") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.search(query) }) { Text("Search Enabled Sources") }
            Text("Enabled sources: ${sources.count { it.enabled }}")
        }

        items(results) { result ->
            val sourceName = sources.firstOrNull { it.id == result.sourceId }?.name ?: "Unknown Source"
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(result.title, fontWeight = FontWeight.Bold)
                    Text("$sourceName • ${result.authors} • ${result.year}")
                    Text(result.snippet)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            result.landingUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                        }) { Text("Open Landing") }
                        Button(onClick = {
                            viewModel.download(result, sourceName) { downloadRes ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (downloadRes.isSuccess) "Downloaded ${result.title}" else "Download failed"
                                    )
                                }
                            }
                        }) { Text("Download PDF") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesScreen(viewModel: CortexViewModel) {
    val sources by viewModel.sources.collectAsState()
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Sources", style = MaterialTheme.typography.headlineMedium) }
        items(sources) { source ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, fontWeight = FontWeight.Bold)
                        Text(source.baseUrl)
                        Text(source.type.name)
                    }
                    Switch(checked = source.enabled, onCheckedChange = { viewModel.toggleSource(source.id, it) })
                }
            }
        }

        item {
            Text("Add Source", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (name.isNotBlank() && URLUtil.isValidUrl(baseUrl)) {
                        viewModel.addSource(name, baseUrl, SourceType.GenericWeb)
                        name = ""
                        baseUrl = ""
                    }
                }) { Text("Add Source") }
            }
        }
    }
}

@Composable
private fun DownloadsScreen(viewModel: CortexViewModel) {
    val downloads by viewModel.downloads.collectAsState()
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Downloads", style = MaterialTheme.typography.headlineMedium) }
        items(downloads) { item ->
            Card(modifier = Modifier.fillMaxWidth().clickable { openPdf(context, item.filePath) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Text("${item.sourceName} • ${item.status} (${item.progress}%)")
                    Text(item.filePath)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Theme and preference controls can be expanded here.")
        Text("TODO: migrate any generic settings from the previous app shell.")
    }
}

private fun openPdf(context: Context, filePath: String) {
    val file = File(filePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}
