package app.shosetsu.android.data.repo

import app.shosetsu.android.domain.model.DownloadItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStateMachineTest {
    @Test
    fun deduplicates_active_and_completed_downloads() {
        val items = listOf(
            DownloadItem(id = "1", title = "A", pdfUrl = "https://x.org/a.pdf", filePath = "/tmp/a.pdf", status = "completed", progress = 100, sourceName = "x"),
            DownloadItem(id = "2", title = "B", pdfUrl = "https://x.org/b.pdf", filePath = "", status = "downloading", progress = 20, sourceName = "x")
        )

        assertFalse(DownloadStateMachine.shouldStartNew(items, "https://x.org/a.pdf"))
        assertFalse(DownloadStateMachine.shouldStartNew(items, "https://x.org/b.pdf"))
        assertTrue(DownloadStateMachine.shouldStartNew(items, "https://x.org/c.pdf"))
    }

    @Test
    fun retry_limit_is_two_retries() {
        val failed2 = DownloadItem(id = "1", title = "A", pdfUrl = "https://x.org/a.pdf", filePath = "", status = "failed", progress = 0, attempts = 2, sourceName = "x")
        val failed3 = failed2.copy(attempts = 3)

        assertTrue(DownloadStateMachine.nextRetryAllowed(failed2))
        assertFalse(DownloadStateMachine.nextRetryAllowed(failed3))
    }
}
