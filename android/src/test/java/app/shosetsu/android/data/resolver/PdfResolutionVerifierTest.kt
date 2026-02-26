package app.shosetsu.android.data.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfResolutionVerifierTest {
    private val verifier = PdfResolutionVerifier()

    @Test
    fun allowlist_checks_final_host() {
        assertTrue(verifier.isAllowedDomain("https://files.standardebooks.org/book.pdf", listOf("standardebooks.org")))
        assertFalse(verifier.isAllowedDomain("https://evil.org/book.pdf", listOf("standardebooks.org")))
    }

    @Test
    fun parseContentDisposition_filename() {
        val basic = verifier.parseFilenameFromContentDisposition("attachment; filename=\"paper.pdf\"")
        val utf = verifier.parseFilenameFromContentDisposition("attachment; filename*=UTF-8''Open%20Book.pdf")
        assertEquals("paper.pdf", basic)
        assertEquals("Open Book.pdf", utf)
    }
}
