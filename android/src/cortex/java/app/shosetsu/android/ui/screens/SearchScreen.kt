package app.shosetsu.android.ui.screens

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.shosetsu.android.data.repo.ContentTypeFilter
import app.shosetsu.android.data.repo.SearchSortMode
import app.shosetsu.android.domain.model.ContentType
import app.shosetsu.android.ui.vm.DownloadsViewModel
import app.shosetsu.android.ui.vm.SearchViewModel
import app.shosetsu.android.ui.vm.SourcesViewModel
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel,
    sourcesViewModel: SourcesViewModel,
    downloadsViewModel: DownloadsViewModel,
    snackbar: SnackbarHostState,
    onOpenDetails: () -> Unit
) {
    val sources by sourcesViewModel.sources.collectAsState()
    val state by searchViewModel.uiState.collectAsState()
    val resolvingIds by downloadsViewModel.resolvingIds.collectAsState()
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text("Only with PDF")
                Switch(checked = state.onlyWithPdf, onCheckedChange = searchViewModel::setOnlyWithPdf)
            }

            Text("Content")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContentTypeFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.contentTypeFilter == filter,
                        onClick = { searchViewModel.setContentTypeFilter(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    ContentTypeFilter.All -> "All"
                                    ContentTypeFilter.BooksOnly -> "Books only"
                                    ContentTypeFilter.PapersOnly -> "Papers only"
                                }
                            )
                        }
                    )
                }
            }

            Text("Sort")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchSortMode.entries.forEach { sortMode ->
                    FilterChip(selected = state.sortMode == sortMode, onClick = { searchViewModel.setSortMode(sortMode) }, label = { Text(sortMode.name) })
                }
            }

            Text("Filter Sources")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.filter { it.enabled }.forEach { source ->
                    val selected = source.id in state.sourceFilterIds
                    AssistChip(onClick = { searchViewModel.toggleSourceFilter(source.id) }, label = { Text(source.name) })
                    if (selected) Text("✓", modifier = Modifier.padding(top = 8.dp))
                }
            }
            state.sourceErrors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        items(state.results) { result ->
            val sourceName = sources.firstOrNull { it.id == result.sourceId }?.name ?: "Unknown Source"
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(result.title, fontWeight = FontWeight.Bold)
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                when (result.contentType) {
                                    ContentType.Paper -> "Paper"
                                    ContentType.Book -> "Book"
                                    ContentType.Unknown -> "Unknown"
                                }
                            )
                        }
                    )
                    Text("$sourceName • ${result.authors} • ${result.year}")
                    Text(result.snippet)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            searchViewModel.setSelectedResult(result)
                            onOpenDetails()
                        }) { Text("Details") }
                        Button(
                            onClick = {
                                downloadsViewModel.download(result, sourceName) { downloadRes, resolved ->
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            if (downloadRes.isSuccess) {
                                                "Downloaded ${result.title}"
                                            } else {
                                                if (resolved.pdfUrl == null) "No PDF found. Open landing page from details." else "Download failed: ${downloadRes.exceptionOrNull()?.message}"
                                            }
                                        )
                                    }
                                }
                            },
                            enabled = result.id !in resolvingIds
                        ) { Text(if (result.id in resolvingIds) "Resolving..." else "Download") }
                    }
                }
            }
        }
    }
}
