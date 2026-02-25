package app.shosetsu.android.domain

import app.shosetsu.android.domain.model.Source

class SourceTestSession {
    suspend fun runTest(source: Source, tester: suspend (Source) -> String): String = tester(source)
    suspend fun save(source: Source, saver: suspend (Source) -> Unit) = saver(source)
}
