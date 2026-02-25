package app.shosetsu.android.ui.screens

import android.net.Uri
import android.webkit.URLUtil
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.shosetsu.android.domain.config.ApiSourceConfig
import app.shosetsu.android.domain.config.ScrapeSourceConfig
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.Source
import app.shosetsu.android.domain.model.SourceType
import app.shosetsu.android.ui.vm.SourcesViewModel
import kotlinx.serialization.encodeToString

@Composable
fun SourcesScreen(viewModel: SourcesViewModel) {
    val sources by viewModel.sources.collectAsState()
    val pendingImport by viewModel.pendingImport.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var editing by remember { mutableStateOf<Source?>(null) }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
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

    var enablePdfResolution by remember { mutableStateOf(false) }
    var allowedPdfDomainsCsv by remember { mutableStateOf("") }
    var limitOverrideText by remember { mutableStateOf("") }

    fun loadForEdit(source: Source) {
        editing = source
        name = source.name
        baseUrl = source.baseUrl
        notes = source.notes
        selectedType = source.type
        val api = SourceConfigCodec.parseApi(source)
        val scrape = SourceConfigCodec.parseScrape(source)
        if (source.configJson != null && api == null && scrape == null) errorText = "Config corrupted"
        if (api != null) {
            endpointPath = api.endpointPath
            queryParam = api.queryParam
            extraParams = api.extraParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            apiKeyHeader = api.headerApiKeyName.orEmpty()
            apiKeyValue = api.apiKey.orEmpty()
            enablePdfResolution = api.enablePdfResolution
            allowedPdfDomainsCsv = api.allowedPdfDomains.joinToString(",")
            limitOverrideText = api.limitOverride?.toString().orEmpty()
        }
        if (scrape != null) {
            searchUrlTemplate = scrape.searchUrlTemplate
            resultItemSelector = scrape.resultItemSelector
            titleSelector = scrape.titleSelector
            linkSelector = scrape.linkSelector
            pdfSelector = scrape.pdfLinkSelector.orEmpty()
            enablePdfResolution = scrape.enablePdfResolution
            allowedPdfDomainsCsv = scrape.allowedPdfDomains.joinToString(",")
            limitOverrideText = scrape.limitOverride?.toString().orEmpty()
        }
    }

    val createExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportSourcesJson { json ->
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            text?.let(viewModel::previewImport)
        }
    }

    pendingImport?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            confirmButton = { Button(onClick = viewModel::confirmImport) { Text("Apply import") } },
            dismissButton = { Button(onClick = viewModel::cancelImport) { Text("Cancel") } },
            title = { Text("Confirm source import") },
            text = { Text("Added: ${preview.added}, replaced: ${preview.replaced}. Previous list is kept as backup until you confirm.") }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Sources", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { createExportLauncher.launch("cortex_sources.json") }) { Text("Export JSON") }
                Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("Import JSON") }
                Button(onClick = {
                    viewModel.exportSourcesJson { clipboard.setText(AnnotatedString(it)) }
                }) { Text("Copy JSON") }
                Button(onClick = viewModel::resetDefaults) { Text("Reset sources defaults") }
            }
        }

        items(sources) { source ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, fontWeight = FontWeight.Bold)
                        Text(source.baseUrl)
                        Text(source.type.name)
                    }
                    Column {
                        Switch(checked = source.enabled, onCheckedChange = { viewModel.toggleSource(source.id, it) })
                        Button(onClick = { loadForEdit(source) }) { Text("Edit") }
                    }
                }
            }
        }

        item {
            Text(if (editing == null) "Add Source" else "Edit Source", style = MaterialTheme.typography.titleLarge)
            errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            testResult?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL (or asset://file.html)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { selectedType = SourceType.Api }) { Text("API") }
                Button(onClick = { selectedType = SourceType.GenericWeb }) { Text("Scrape") }
                Button(onClick = {
                    errorText = null
                    viewModel.clearTestResult()
                    limitOverrideText = ""
                    enablePdfResolution = false
                    allowedPdfDomainsCsv = ""
                }) { Text("Reset config") }
            }

            if (selectedType == SourceType.Api) {
                OutlinedTextField(value = endpointPath, onValueChange = { endpointPath = it }, label = { Text("Endpoint Path") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = queryParam, onValueChange = { queryParam = it }, label = { Text("Query param") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = extraParams, onValueChange = { extraParams = it }, label = { Text("Extra params k=v&k2=v2") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiKeyHeader, onValueChange = { apiKeyHeader = it }, label = { Text("API key header name (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiKeyValue, onValueChange = { apiKeyValue = it }, label = { Text("API key value (optional)") }, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(value = searchUrlTemplate, onValueChange = { searchUrlTemplate = it }, label = { Text("Search URL template ({query})") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = resultItemSelector, onValueChange = { resultItemSelector = it }, label = { Text("Result item selector") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = titleSelector, onValueChange = { titleSelector = it }, label = { Text("Title selector") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = linkSelector, onValueChange = { linkSelector = it }, label = { Text("Link selector") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pdfSelector, onValueChange = { pdfSelector = it }, label = { Text("PDF selector (optional)") }, modifier = Modifier.fillMaxWidth())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enable PDF resolution")
                Switch(checked = enablePdfResolution, onCheckedChange = { enablePdfResolution = it })
            }
            OutlinedTextField(value = allowedPdfDomainsCsv, onValueChange = { allowedPdfDomainsCsv = it }, label = { Text("Allowed PDF domains (comma-separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = limitOverrideText, onValueChange = { limitOverrideText = it }, label = { Text("Limit override (optional)") }, modifier = Modifier.fillMaxWidth())

            fun buildSource(): Source? {
                val baseValid = baseUrl.startsWith("asset://") || URLUtil.isValidUrl(baseUrl)
                if (name.isBlank() || !baseValid) {
                    errorText = "Name and valid URL/asset path required"
                    return null
                }
                val limitOverride = limitOverrideText.toIntOrNull()
                val allowedDomains = allowedPdfDomainsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }
                val configJson = if (selectedType == SourceType.Api) {
                    if (endpointPath.isBlank() || queryParam.isBlank()) {
                        errorText = "API source requires endpoint and query param"
                        return null
                    }
                    SourceConfigCodec.json.encodeToString(ApiSourceConfig(endpointPath = endpointPath, queryParam = queryParam, extraParams = parseExtraParams(extraParams), headerApiKeyName = apiKeyHeader.ifBlank { null }, apiKey = apiKeyValue.ifBlank { null }, limitOverride = limitOverride, enablePdfResolution = enablePdfResolution, allowedPdfDomains = allowedDomains))
                } else {
                    if (searchUrlTemplate.isBlank() || resultItemSelector.isBlank() || titleSelector.isBlank() || linkSelector.isBlank()) {
                        errorText = "Scrape source requires template and selectors"
                        return null
                    }
                    SourceConfigCodec.json.encodeToString(ScrapeSourceConfig(searchUrlTemplate = searchUrlTemplate, resultItemSelector = resultItemSelector, titleSelector = titleSelector, linkSelector = linkSelector, pdfLinkSelector = pdfSelector.ifBlank { null }, enablePdfResolution = enablePdfResolution, allowedPdfDomains = allowedDomains, limitOverride = limitOverride))
                }
                return Source(id = editing?.id ?: "temp_test", name = name, baseUrl = baseUrl, type = selectedType, enabled = true, notes = notes, configJson = configJson)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val candidate = buildSource() ?: return@Button
                    viewModel.testSource(candidate)
                }) { Text("Test") }

                Button(onClick = {
                    val candidate = buildSource() ?: return@Button
                    if (editing == null) {
                        viewModel.addSource(candidate.name, candidate.baseUrl, candidate.type, candidate.notes, candidate.configJson)
                    } else {
                        viewModel.updateSource(candidate.copy(id = editing!!.id, enabled = editing!!.enabled, createdAt = editing!!.createdAt))
                    }
                    editing = null
                    name = ""
                    baseUrl = ""
                    notes = ""
                    viewModel.clearTestResult()
                    errorText = null
                }) { Text(if (editing == null) "Add Source" else "Save") }
            }
        }
    }
}

private fun parseExtraParams(value: String): Map<String, String> {
    return value.split('&').mapNotNull { pair ->
        val split = pair.split('=', limit = 2)
        if (split.size != 2 || split[0].isBlank()) null else split[0].trim() to split[1].trim()
    }.toMap()
}
