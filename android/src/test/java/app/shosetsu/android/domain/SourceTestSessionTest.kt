package app.shosetsu.android.domain

import app.shosetsu.android.domain.model.Source
import app.shosetsu.android.domain.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceTestSessionTest {
    @Test
    fun runTest_doesNotSaveUntilSaveCalled() = runBlocking {
        val session = SourceTestSession()
        var savedCount = 0
        val source = Source("id", "name", "https://example.org", SourceType.Api)

        session.runTest(source) { "ok" }
        assertEquals(0, savedCount)

        session.save(source) { savedCount += 1 }
        assertEquals(1, savedCount)
    }
}
