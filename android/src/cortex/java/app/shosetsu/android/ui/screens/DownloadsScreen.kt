package app.shosetsu.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.shosetsu.android.ui.vm.DownloadsViewModel
import app.shosetsu.android.util.openPdf

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel) {
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
