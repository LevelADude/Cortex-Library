package app.shosetsu.android.ui.screens

import android.webkit.URLUtil
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.shosetsu.android.domain.config.ApiSourceConfig
import app.shosetsu.android.domain.config.ScrapeSourceConfig
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.SourceType
import app.shosetsu.android.ui.vm.SourcesViewModel
import kotlinx.serialization.encodeToString

@Composable
fun SourcesScreen(viewModel: SourcesViewModel) {
    val sources by viewModel.sources.collectAsState()
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SourceType.Api) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var endpointPath by remember { mutableStateOf("/works") }
    var queryParam by remember { mutableStateOf("search") }
    var extraParams by remember { mutableStateOf("") }
    var apiKeyHeader by remember { mutableStateOf("") }
    var apiKeyValue by remember { mutableStateOf("") }

    var searchUrlTemplate by remember { mutableStateOf("https://example.org/search?q={query}") }
    var resultItemSelector by remember { mutableStateOf(".result-item") }
    var titleSelector by remember { mutableStateOf(".title") }
    var linkSelector by remember { mutableStateOf("a") }
    var pdfSelector by remember { mutableStateOf("") }

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
            errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL (or asset://file.html)") }, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { selectedType = SourceType.Api }) { Text("API") }
                Button(onClick = { selectedType = SourceType.GenericWeb }) { Text("Scrape") }
            }

            if (selectedType == SourceType.Api) {
                OutlinedTextField(value = endpointPath, onValueChange = { endpointPath = it }, label = { Text("Endpoint Path") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = queryParam, onValueChange = { queryParam = it }, label = { Text("Query param") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = extraParams, onValueChange = { extraParams = it }, label = { Text("Extra params k=v&k2=v2") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiKeyHeader, onValueChange = { apiKeyHeader = it }, label = { Text("API key header name (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiKeyValue, onValueChange = { apiKeyValue = it }, label = { Text("API key value (optional)") }, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(value = searchUrlTemplate, onValueChange = { searchUrlTemplate = it }, label = { Text("Search URL template") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = resultItemSelector, onValueChange = { resultItemSelector = it }, label = { Text("Result item selector") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = titleSelector, onValueChange = { titleSelector = it }, label = { Text("Title selector") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = linkSelector, onValueChange = { linkSelector = it }, label = { Text("Link selector") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pdfSelector, onValueChange = { pdfSelector = it }, label = { Text("PDF selector (optional)") }, modifier = Modifier.fillMaxWidth())
            }

            Button(onClick = {
                val baseValid = baseUrl.startsWith("asset://") || URLUtil.isValidUrl(baseUrl)
                if (name.isBlank() || !baseValid) {
                    errorText = "Please provide a valid name and URL"
                    return@Button
                }
                val configJson = if (selectedType == SourceType.Api) {
                    if (endpointPath.isBlank() || queryParam.isBlank()) {
                        errorText = "API source requires endpoint path and query parameter"
                        return@Button
                    }
                    val extras = extraParams.split("&").mapNotNull {
                        val parts = it.split("=", limit = 2)
                        if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
                    }.toMap()
                    SourceConfigCodec.json.encodeToString(
                        ApiSourceConfig(
                            endpointPath = endpointPath,
                            queryParam = queryParam,
                            extraParams = extras,
                            headerApiKeyName = apiKeyHeader.ifBlank { null },
                            apiKey = apiKeyValue.ifBlank { null }
                        )
                    )
                } else {
                    if (searchUrlTemplate.isBlank() || resultItemSelector.isBlank() || titleSelector.isBlank() || linkSelector.isBlank()) {
                        errorText = "Scrape source requires template and selectors"
                        return@Button
                    }
                    SourceConfigCodec.json.encodeToString(
                        ScrapeSourceConfig(
                            searchUrlTemplate = searchUrlTemplate,
                            resultItemSelector = resultItemSelector,
                            titleSelector = titleSelector,
                            linkSelector = linkSelector,
                            pdfLinkSelector = pdfSelector.ifBlank { null }
                        )
                    )
                }

                viewModel.addSource(name, baseUrl, selectedType, configJson = configJson)
                name = ""
                baseUrl = ""
                errorText = null
            }) { Text("Add Source") }
        }
    }
}
