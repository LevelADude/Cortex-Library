package app.shosetsu.android.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.shosetsu.android.ui.vm.DownloadsViewModel
import app.shosetsu.android.ui.vm.SearchViewModel
import app.shosetsu.android.ui.vm.SourcesViewModel
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel,
    sourcesViewModel: SourcesViewModel,
    downloadsViewModel: DownloadsViewModel,
    snackbar: SnackbarHostState
) {
    val sources by sourcesViewModel.sources.collectAsState()
    val state by searchViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Cortex Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.query,
                onValueChange = searchViewModel::onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search papers and books") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { searchViewModel.searchNow() }) { Text(if (state.isLoading) "Searching..." else "Search Enabled Sources") }
            Text("Enabled sources: ${sources.count { it.enabled }}")
            state.sourceErrors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        items(state.results) { result ->
            val sourceName = sources.firstOrNull { it.id == result.sourceId }?.name ?: "Unknown Source"
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(result.title, fontWeight = FontWeight.Bold)
                    Text("$sourceName • ${result.authors} • ${result.year}")
                    Text(result.snippet)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            result.landingUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                        }, enabled = result.landingUrl != null) { Text("Open landing page") }
                        Button(
                            onClick = {
                                downloadsViewModel.download(result, sourceName) { downloadRes ->
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            if (downloadRes.isSuccess) "Downloaded ${result.title}" else "Download failed: ${downloadRes.exceptionOrNull()?.message}"
                                        )
                                    }
                                }
                            },
                            enabled = result.pdfUrl != null
                        ) { Text("Download PDF") }
                        if (result.pdfUrl == null) {
                            Button(onClick = { scope.launch { snackbar.showSnackbar("No PDF available for this result") } }) {
                                Text("No PDF")
                            }
                        }
                    }
                }
            }
        }
    }
}
