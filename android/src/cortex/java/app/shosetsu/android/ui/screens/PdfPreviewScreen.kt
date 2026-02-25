package app.shosetsu.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.shosetsu.android.ui.preview.PdfPreviewRenderer

@Composable
fun PdfPreviewScreen(
    filePath: String,
    renderer: PdfPreviewRenderer,
    onOpenExternal: () -> Unit
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    var previewState by remember { mutableStateOf<Result<PdfPreviewRenderer.PreviewPage>?>(null) }
    var cachedPage by remember { mutableStateOf<PdfPreviewRenderer.PreviewPage?>(null) }

    LaunchedEffect(filePath, pageIndex) {
        previewState = renderer.open(filePath, pageIndex)
        previewState?.getOrNull()?.let { cachedPage = it }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("PDF Preview", style = MaterialTheme.typography.headlineSmall)
        val page = cachedPage
        if (page != null) {
            Image(
                bitmap = page.bitmap.asImageBitmap(),
                contentDescription = "PDF preview page",
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Text("${page.pageIndex + 1}/${page.totalPreviewPages}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = page.pageIndex > 0) { Text("Prev") }
                Button(
                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost(page.totalPreviewPages - 1) },
                    enabled = page.pageIndex < page.totalPreviewPages - 1
                ) { Text("Next") }
            }
        } else {
            Text(previewState?.exceptionOrNull()?.message ?: "Rendering preview...")
        }
        Button(onClick = onOpenExternal, modifier = Modifier.fillMaxWidth()) { Text("Open full PDF") }
    }
}
