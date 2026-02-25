package app.shosetsu.android.ui.preview

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfPreviewRenderer(private val maxPages: Int = 5) {
    data class PreviewPage(val pageIndex: Int, val totalPreviewPages: Int, val bitmap: Bitmap)

    companion object {
        fun previewPageCount(totalPages: Int, maxPages: Int): Int = minOf(totalPages.coerceAtLeast(0), maxPages.coerceAtLeast(1))
        fun safePageIndex(requestedIndex: Int, previewPages: Int): Int = requestedIndex.coerceIn(0, (previewPages - 1).coerceAtLeast(0))
    }

    suspend fun open(filePath: String, pageIndex: Int): Result<PreviewPage> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(filePath)
            require(file.exists()) { "PDF file not found" }
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount
                    require(pageCount > 0) { "PDF has no pages" }
                    val previewCount = previewPageCount(pageCount, maxPages)
                    val safeIndex = safePageIndex(pageIndex, previewCount)
                    renderer.openPage(safeIndex).use { page ->
                        val width = (page.width * 0.6f).toInt().coerceAtLeast(300)
                        val height = (page.height * 0.6f).toInt().coerceAtLeast(400)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        PreviewPage(safeIndex, previewCount, bitmap)
                    }
                }
            }
        }
    }
}
