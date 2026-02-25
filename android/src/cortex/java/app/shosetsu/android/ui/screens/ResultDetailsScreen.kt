package app.shosetsu.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
fun ResultDetailsScreen(
    searchViewModel: SearchViewModel,
    sourcesViewModel: SourcesViewModel,
    downloadsViewModel: DownloadsViewModel,
    snackbar: SnackbarHostState
) {
    val state by searchViewModel.uiState.collectAsState()
    val sources by sourcesViewModel.sources.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result = state.selectedResult
    val sourceName = sources.firstOrNull { it.id == result?.sourceId }?.name ?: "Unknown Source"

    if (result == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("No result selected")
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(result.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Authors: ${result.authors}")
        Text("Year: ${result.year}")
        Text("Source: $sourceName")
        Text(result.snippet)
        Text("Landing: ${result.landingUrl ?: "N/A"}")
        Text("PDF: ${result.pdfUrl ?: "Not resolved"}")

        Button(onClick = {
            result.landingUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
        }, enabled = result.landingUrl != null, modifier = Modifier.fillMaxWidth()) { Text("Open landing") }

        Button(onClick = {
            downloadsViewModel.download(result, sourceName) { downloadRes, resolved ->
                searchViewModel.setSelectedResult(resolved)
                scope.launch {
                    snackbar.showSnackbar(
                        if (downloadRes.isSuccess) "Downloaded ${result.title}" else if (resolved.pdfUrl == null) "No PDF found" else "Download failed"
                    )
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Resolve PDF + Download") }
    }
}

