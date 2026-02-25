package app.shosetsu.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.shosetsu.android.domain.model.DebugLevel
import app.shosetsu.android.ui.vm.DownloadsViewModel
import app.shosetsu.android.ui.vm.SettingsViewModel

@Composable
fun SettingsScreen(downloadsViewModel: DownloadsViewModel, settingsViewModel: SettingsViewModel) {
    val downloadDir by downloadsViewModel.downloadDirectory.collectAsState()
    val events by settingsViewModel.events.collectAsState()
    val persistLogs by settingsViewModel.persistLogs.collectAsState()
    var input by remember(downloadDir) { mutableStateOf(downloadDir.orEmpty()) }
    var errorsOnly by remember { mutableStateOf(false) }
    var sourceFilter by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    val filteredEvents = events.filter { (!errorsOnly || it.level == DebugLevel.Error) && (sourceFilter.isBlank() || it.sourceName?.contains(sourceFilter, true) == true) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text("Download preferences")
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Download directory (absolute path)") })
            Button(onClick = { downloadsViewModel.setDownloadDirectory(input) }) { Text("Save download directory") }
            Text("PDF-only mode is enabled. EPUB downloads are currently unsupported.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Persist debug logs")
                Switch(checked = persistLogs, onCheckedChange = settingsViewModel::setPersistLogs)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val logs = events.joinToString("\n") { "${it.timestamp} [${it.level}] ${it.category} ${it.sourceName.orEmpty()} ${it.message} ${it.details.orEmpty()}" }
                    clipboard.setText(AnnotatedString(logs))
                }) { Text("Copy logs") }
                Button(onClick = settingsViewModel::clearLogs) { Text("Clear logs") }
                Button(onClick = settingsViewModel::resetSettings) { Text("Reset settings defaults") }
            }
        }

        item {
            Text("Debug events", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !errorsOnly, onClick = { errorsOnly = false }, label = { Text("All") })
                FilterChip(selected = errorsOnly, onClick = { errorsOnly = true }, label = { Text("Errors only") })
            }
            OutlinedTextField(value = sourceFilter, onValueChange = { sourceFilter = it }, label = { Text("Filter by source") }, modifier = Modifier.fillMaxWidth())
        }

        items(filteredEvents.reversed()) { e ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("${e.level} • ${e.category} • ${e.sourceName ?: "global"}")
                Text(e.message)
                e.details?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
