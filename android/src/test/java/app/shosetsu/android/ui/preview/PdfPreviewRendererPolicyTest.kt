package app.shosetsu.android.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPreviewRendererPolicyTest {
    @Test
    fun previewPageCountIsCappedAtMaxPages() {
        assertEquals(5, PdfPreviewRenderer.previewPageCount(totalPages = 10, maxPages = 5))
        assertEquals(3, PdfPreviewRenderer.previewPageCount(totalPages = 3, maxPages = 5))
    }

    @Test
    fun safePageIndexIsClamped() {
        assertEquals(0, PdfPreviewRenderer.safePageIndex(-1, previewPages = 5))
        assertEquals(4, PdfPreviewRenderer.safePageIndex(12, previewPages = 5))
    }
}
