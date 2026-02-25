package app.shosetsu.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.shosetsu.android.ui.vm.DownloadsViewModel

@Composable
fun SettingsScreen(downloadsViewModel: DownloadsViewModel) {
    val downloadDir by downloadsViewModel.downloadDirectory.collectAsState()
    var input by remember(downloadDir) { mutableStateOf(downloadDir.orEmpty()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Download preferences")
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Download directory (absolute path)") }
        )
        Button(onClick = { downloadsViewModel.setDownloadDirectory(input) }) { Text("Save download directory") }
        Text("PDF-only mode is enabled. EPUB downloads are currently unsupported.")
        Text("TODO: add source-level rate limit, auth secrets handling, and OpenLibrary borrow flow with authentication.")
    }
}
